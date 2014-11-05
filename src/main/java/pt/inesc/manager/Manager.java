/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.mortbay.log.Log;

import pt.inesc.SharedProperties;
import pt.inesc.manager.branchTree.BranchNode;
import pt.inesc.manager.branchTree.BranchTree;
import pt.inesc.manager.communication.GroupCom;
import pt.inesc.manager.communication.GroupCom.NodeGroup;
import pt.inesc.manager.graph.ExecListWrapper;
import pt.inesc.manager.graph.GraphShuttle;
import pt.inesc.manager.utils.CleanVoldemort;
import pt.inesc.manager.utils.MonitorW;
import pt.inesc.manager.utils.NotifyEvent;
import pt.inesc.replay.core.ReplayMode;
import pt.inesc.undo.proto.FromManagerProto;
import pt.inesc.undo.proto.FromManagerProto.ExecList;
import pt.inesc.undo.proto.FromManagerProto.ProxyMsg;
import pt.inesc.undo.proto.FromManagerProto.ToDataNode;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager;

public class Manager {
    private static final Logger LOGGER = LogManager.getLogger(Manager.class.getName());
    private static int MAX_CONCURRENT_CLIENTS_PER_REPLAY_NODE = 30;

    public final GroupCom group;

    public MonitorW ackWaiter = new MonitorW();
    public GraphShuttle graph = new GraphShuttle();
    private static ServiceManager service;
    public BranchTree branches = new BranchTree();

    public static void main(String[] args) throws Exception {
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        URL[] urls = ((URLClassLoader) cl).getURLs();

        for (URL url : urls) {
            System.out.println(url.getFile());
        }

        DOMConfigurator.configure("log4j.xml");
        LOGGER.setLevel(Level.DEBUG);
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }


    public Manager() throws Exception {
        service = new ServiceManager(this);
        service.start();
        group = new GroupCom();
    }

    /* ------------------- Operations ----------------- */

    /**
     * Perform request replay. If attackSource is null or isEmpty, then replay every
     * request.
     * Otherwise, perform selective replay
     * 
     * @param parentCommit
     * @param parentBranch
     * @param attackSource
     * @throws Exception
     */
    public void replay(long parentCommit, short parentBranch, ReplayMode replayMode, List<Long> attackSource) throws Exception {
        // get requests to send
        LOGGER.info("Replay based on branch: " + parentBranch + " and commit: " + parentCommit);

        short newBranch = branches.fork(parentCommit, parentBranch);
        LinkedList<BranchNode> path = branches.getPath(parentCommit, newBranch);

        // notify the database nodes about the path of the redo branch
        LOGGER.warn("Sending new path: " + path);
        sendNewRedoBranch(path);

        // Get execution list
        LOGGER.warn("Get the excution list");
        ExecListWrapper execListWrapper = graph.replay(parentCommit, replayMode, attackSource);
        LOGGER.warn("Got the excution list");

        long biggest = execListWrapper.latestRequest;

        // TODO invoke the infrastructure to start the replay nodes, get their addresses
        // and set as target
        // infrastructrure.startReplay()
        LOGGER.warn("Will replay " + countRequests(execListWrapper.list) + " requests");

        orderNodesToReplay(execListWrapper.list, newBranch, replayMode);

        LOGGER.warn("Replay completed");


        // enable restrain in the proxy (should be done at the end)
        long smallestIdWithRestrain = enableRestrain();
        LOGGER.warn("Restrain enabled");


        // Get the requests left
        execListWrapper = graph.replay(biggest, replayMode, attackSource);

        truncateList(execListWrapper.list, smallestIdWithRestrain);
        LOGGER.warn("Will replay the remaining" + countRequests(execListWrapper.list) + " requests");
        orderNodesToReplay(execListWrapper.list, newBranch, replayMode);

        LOGGER.warn("Requests performed during the replay are replayed");

        // disable retrain and change to new branch
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setRedoOver(true).build(), NodeGroup.DATABASE);
        LOGGER.warn("Set new branch: " + newBranch);
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranch).setRestrain(false).build(), NodeGroup.PROXY);
        LOGGER.info("Replay is over");
    }


    private int countRequests(List<List<Long>> lists) {
        int counter = 0;
        Iterator<List<Long>> it = lists.iterator();
        while (it.hasNext()) {
            List<Long> list = it.next();
            Iterator<Long> itList = list.iterator();
            while (itList.hasNext()) {
                if (itList.next() != -1) {
                    counter++;
                }
            }
        }
        return counter;
    }


    private void truncateList(List<List<Long>> lists, long smallestIdWithRestrain) {
        Iterator<List<Long>> it = lists.iterator();
        while (it.hasNext()) {
            List<Long> list = it.next();
            Iterator<Long> itList = list.iterator();
            while (itList.hasNext()) {
                if (itList.next() > smallestIdWithRestrain) {
                    itList.remove();
                }
            }
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    private void sendNewRedoBranch(LinkedList<BranchNode> path) throws Exception {
        FromManagerProto.ToDataNode.Builder b = FromManagerProto.ToDataNode.newBuilder();
        for (BranchNode n : path) {
            b.addPathBranch(n.branch);
            b.addPathCommit(n.commit);
        }
        group.broadcastWithAck(b.build(), NodeGroup.DATABASE);
    }


    private boolean orderNodesToReplay(List<List<Long>> execLists, short parentBranch, ReplayMode replayMode) throws Exception {
        ackWaiter.set(group.countReplayNodes());

        int execListsToReplay = 0;
        // inform the slaves which requests will be replayed
        List<InetSocketAddress> replayInstances = group.getReplayInstances();

        execLists = setUpperLimitOfConcurrentThreads(execLists, replayInstances.size());

        StringBuilder sb = new StringBuilder();
        for (List<Long> execList : execLists) {
            if (execList == null || execList.size() == 0)
                continue;
            sb.append("[");
            for (Long l : execList) {
                sb.append(l);
                sb.append(",");
            }
            sb.append("]\n");

        }
        Log.info("replay list: " + sb.toString());

        int i = 0;
        for (List<Long> execList : execLists) {
            if (execList == null || execList.size() == 0)
                continue;
            execListsToReplay++;
            ExecList msg = FromManagerProto.ExecList.newBuilder()
                                                    .addAllRid(execList)
                                                    .setBranch(parentBranch)
                                                    .setStart(false)
                                                    .setReplayMode(replayMode.toString())
                                                    .setTargetHost(SharedProperties.LOAD_BALANCER_ADDRESS.getHostName())
                                                    .setTargetPort(SharedProperties.LOAD_BALANCER_ADDRESS.getPort())
                                                    .build();

            group.send(msg, replayInstances.get(i++ % replayInstances.size()));
        }
        if (execListsToReplay == 0) {
            LOGGER.warn("No requests to replay");
            return false;
        }

        // order to replay the requests
        ExecList startMsg = FromManagerProto.ExecList.newBuilder()
                                                     .setReplayMode(replayMode.toString())
                                                     .setBranch(parentBranch)
                                                     .setStart(true)
                                                     .setTargetHost(SharedProperties.LOAD_BALANCER_ADDRESS.getHostName())
                                                     .setTargetPort(SharedProperties.LOAD_BALANCER_ADDRESS.getPort())
                                                     .build();
        group.send(startMsg, NodeGroup.REDO);
        // wait for ack
        ackWaiter.waitUntilZero();
        return true;
    }

    /**
     * Checks if the number of exec lists is not bigger than the expected maximum number
     * of concurrent replay threads. If so, it trims the number of lists by merging lists
     * 
     * @param execLists
     * @param nReplayNodes
     * @return
     */
    private List<List<Long>> setUpperLimitOfConcurrentThreads(List<List<Long>> execLists, int nReplayNodes) {
        int maxNumberOfLists = MAX_CONCURRENT_CLIENTS_PER_REPLAY_NODE * nReplayNodes;
        if (execLists.size() < maxNumberOfLists) {
            return execLists;
        }
        ArrayList<List<Long>> finalList = new ArrayList<List<Long>>();
        int i = 0;
        for (List<Long> list : execLists) {
            if (i++ < maxNumberOfLists) {
                finalList.add(list);
            } else {
                List<Long> previousList = finalList.get(i % maxNumberOfLists);
                previousList.addAll(list);
            }
        }
        return finalList;
    }

    private long enableRestrain() throws Exception {
        MsgToManager answer = group.sendWithResponse(FromManagerProto.ProxyMsg.newBuilder().setRestrain(true).build(), NodeGroup.PROXY);
        return answer.getAckProxy().getCurrentId();
    }






    public void newCommit(long commit) throws Exception {
        branches.addCommit(commit);
        ToDataNode msg = FromManagerProto.ToDataNode.newBuilder().setNewCommit(commit).build();
        group.broadcast(msg, NodeGroup.DATABASE);
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(commit));
        new NotifyEvent("Commit done: " + dateString, commit).start();
    }

    public void deleteBranch(Short branch) {
        throw new NotImplementedException("delete branch");
    }

    public void changeToBranch(Short branchId) throws Exception {
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(branchId).setRestrain(false).build(), NodeGroup.PROXY);
    }

    public void newBranch(long parentCommit, short parentBranch) throws Exception {
        LOGGER.info("Create branch based on branch: " + parentBranch + " and commit: " + parentCommit);

        short newBranch = branches.fork(parentCommit, parentBranch);
        LinkedList<BranchNode> path = branches.getPath(parentCommit, newBranch);
        // notify the database nodes about the path of the redo branch
        sendNewRedoBranch(path);
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setRedoOver(true).build(), NodeGroup.DATABASE);
        LOGGER.warn("Set new branch: " + newBranch);
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranch).setRestrain(false).build(), NodeGroup.PROXY);
        LOGGER.info("Branch is created");
    }

    /* --------- Get requests per database entry ----- */
    public void getRequestsPerDatabaseEntry(String store, String key) {
        // TODO contact the stub, get the request list
    }

    /* -------------------- Graph -------------------- */
    public void showGraph() {
        graph.display();

    }

    public void saveGraph(String fileName) throws IOException {
        FileOutputStream fout = new FileOutputStream(fileName);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(graph);
        oos.close();
    }

    public void loadGraph(String fileName) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(fileName);
        ObjectInputStream ois = new ObjectInputStream(fin);
        graph = (GraphShuttle) ois.readObject();
        ois.close();

    }

    public void resetGraph() {
        graph.reset();
    }

    public void addDependencies(long rid, List<Long> dependencyList) {
        graph.addDependencies(rid, dependencyList);
    }



    /* ------------ Advanced ------------------- */
    public void proxyTimeTravel(long time) throws Exception {
        ProxyMsg msg = FromManagerProto.ProxyMsg.newBuilder().setTimeTravel(time).build();
        group.send(msg, NodeGroup.PROXY);
    }


    /* -------------------- Cleans -------------------- */

    public void resetDatabaseAccessLists() {
        try {
            group.broadcast(FromManagerProto.ToDataNode.newBuilder().setResetDependencies(true).build(), NodeGroup.DATABASE);
        } catch (Exception e) {
            LOGGER.error("Database proxy not available: you must access to database at least once to instantiate the proxy");
        }
    }


    /**
     * Clean all voldemort stores
     * 
     * @param voldemortStores
     */
    public void cleanVoldemort(String[] voldemortStores) {
        CleanVoldemort.clean(voldemortStores, group.getDatabaseNodes());
    }

    public void resetBranch() {
        branches = new BranchTree();
        try {
            group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(0).setCommit(0).build(), NodeGroup.PROXY);
        } catch (Exception e) {
            LOGGER.error("Proxy not available");
        }
    }

    public void showDatabaseStats() throws Exception {
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setShowStats(true).build(), NodeGroup.DATABASE);
    }


    public void showDatabaseMap() throws Exception {
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setShowMap(true).build(), NodeGroup.DATABASE);

    }




}
