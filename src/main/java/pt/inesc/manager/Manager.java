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
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.SharedProperties;
import pt.inesc.manager.branchTree.BranchTree;
import pt.inesc.manager.communication.GroupCom;
import pt.inesc.manager.communication.GroupCom.NodeGroup;
import pt.inesc.manager.graph.ExecListWrapper;
import pt.inesc.manager.graph.GraphShuttle;
import pt.inesc.manager.utils.CleanVoldemort;
import pt.inesc.manager.utils.MonitorLatchManager;
import pt.inesc.manager.utils.NotifyEvent;
import pt.inesc.proxy.ProxyWorker;
import pt.inesc.replay.core.ReplayMode;
import pt.inesc.undo.proto.FromManagerProto;
import pt.inesc.undo.proto.FromManagerProto.ExecList;
import pt.inesc.undo.proto.FromManagerProto.ProxyMsg;
import pt.inesc.undo.proto.FromManagerProto.ToDataNode;
import pt.inesc.undo.proto.FromManagerProto.ToDataNode.BranchPath.Builder;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager;
import voldemort.undoTracker.SendDependencies;
import voldemort.undoTracker.branching.BranchPath;
import voldemort.undoTracker.map.VersionShuttle;

public class Manager {
    private static final Logger LOGGER = LogManager.getLogger(Manager.class.getName());
    /**
     * Tolerance so the dependencies can be sent
     */
    private static final long WAIT_FOR_DEPENDENCIES = Math.max(SendDependencies.REFRESH_PERIOD, ProxyWorker.FLUSH_PERIODICITY) + 5000;
    private static int MAX_CONCURRENT_CLIENTS_PER_REPLAY_NODE = 50;

    public final GroupCom group;

    public MonitorLatchManager ackWaiter = new MonitorLatchManager();
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
        LOGGER.setLevel(Level.ALL);
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
     * @param parentSnapshot
     * @param parentBranch
     * @param attackSource
     * @throws Exception
     */
    public void replay(long parentSnapshot, ReplayMode replayMode, List<Long> attackSource) throws Exception {
        // get requests to send
        LOGGER.info("Replay based on snapshot: " + parentSnapshot);

        BranchPath newBranchPath = branches.fork(parentSnapshot);

        // notify the database nodes about the path of the replay branch
        LOGGER.warn("Sending new path: " + newBranchPath);
        sendNewReplayBranch(newBranchPath);

        // // Get execution list
        LOGGER.warn("Get the excution list");
        ExecListWrapper execListWrapper = graph.replay(parentSnapshot, replayMode, attackSource);
        LOGGER.warn("Got the excution list");

        long biggest = execListWrapper.latestRequest;
        System.out.println("Biggest request executed: " + biggest);
        // TODO invoke the infrastructure to start the replay nodes, get their
        // addresses and set as target infrastructrure.startReplay()
        LOGGER.warn("Will replay " + countRequests(execListWrapper.list) + " requests");

        orderNodesToReplay(execListWrapper.list, newBranchPath.branch, replayMode);

        LOGGER.warn("Replay completed");


        // enable restrain in the proxy (should be done at the end)
        long smallestIdWithRestrain = enableRestrain();
        LOGGER.warn("Restrain enabled, current proxy timestamp is: " + smallestIdWithRestrain);

        /**
         * Wait for all dependencies to arrive
         */
        Thread.sleep(WAIT_FOR_DEPENDENCIES);

        // Get the requests left
        execListWrapper = graph.replay(biggest, replayMode, attackSource);
        int requestsLeft = countRequests(execListWrapper.list);
        LOGGER.warn("before truncate: " + requestsLeft + " requests");
        if (requestsLeft > 1) {
            truncateList(execListWrapper.list, smallestIdWithRestrain);
        }
        LOGGER.warn("Will replay the remaining" + countRequests(execListWrapper.list) + " requests");
        // debugExecutionList(execListWrapper.list);

        orderNodesToReplay(execListWrapper.list, newBranchPath.branch, replayMode);

        LOGGER.warn("Requests performed during the replay are replayed");

        // disable retrain and change to new branch
        group.send(FromManagerProto.ToDataNode.newBuilder().setReplayOver(true).build(), NodeGroup.DATABASE);
        LOGGER.warn("Set new branch: " + newBranchPath.branch);
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranchPath.branch).setRestrain(false).build(), NodeGroup.PROXY);
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

    private void sendNewReplayBranch(BranchPath path) throws Exception {
        FromManagerProto.ToDataNode.Builder b = FromManagerProto.ToDataNode.newBuilder();
        Builder bPath = ToDataNode.BranchPath.newBuilder();
        for (VersionShuttle v : path.versions) {
            bPath.addVersions(v.sid);
        }
        bPath.setBranch(path.branch);
        bPath.setLatestVersion(path.latestVersion.sid);

        b.setBranchPath(bPath);
        group.sendWithAck(b.build(), NodeGroup.DATABASE);
    }

    private boolean orderNodesToReplay(List<List<Long>> execLists, short parentBranch, ReplayMode replayMode) throws Exception {
        int nReplayNodes = group.countReplayNodes();
        LOGGER.warn(nReplayNodes + " replay nodes available");

        int execListsToReplay = 0;
        // inform the slaves which requests will be replayed
        List<InetSocketAddress> replayInstances = group.getReplayInstances();

        execLists = setUpperLimitOfConcurrentThreads(execLists, replayInstances.size());
        // debugExecutionList(execLists);

        ackWaiter.set(Math.min(execLists.size(), nReplayNodes));

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
        List<MsgToManager> answer = group.sendWithResponse(FromManagerProto.ProxyMsg.newBuilder().setRestrain(true).build(),
                                                           NodeGroup.PROXY);
        return answer.get(0).getAckProxy().getCurrentId();
    }






    public long newSnapshot(long delaySeconds) throws Exception {
        long momment = branches.snapshot(delaySeconds);
        ToDataNode msg = FromManagerProto.ToDataNode.newBuilder().setNewSnapshot(momment).build();
        group.send(msg, NodeGroup.DATABASE);
        long milliseconds = (long) (momment / ProxyWorker.MULTIPLICATION_FACTOR);
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(milliseconds));
        new NotifyEvent("Snapshot done: " + dateString, milliseconds).start();
        return milliseconds;
    }

    public void deleteBranch(Short branch) {
        throw new NotImplementedException("delete branch");
    }

    public void changeToBranch(Short branchId) throws Exception {
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(branchId).setRestrain(false).build(), NodeGroup.PROXY);
    }

    public void newBranch(long parentSnapshot) throws Exception {
        LOGGER.info("Create branch based on snapshot: " + parentSnapshot);
        BranchPath path = branches.fork(parentSnapshot);
        // notify the database nodes about the path of the replay branch
        sendNewReplayBranch(path);
        group.send(FromManagerProto.ToDataNode.newBuilder().setReplayOver(true).build(), NodeGroup.DATABASE);
        LOGGER.warn("Set new branch: " + path.branch);
        group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(path.branch).setRestrain(false).build(), NodeGroup.PROXY);
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
            group.send(FromManagerProto.ToDataNode.newBuilder().setResetDependencies(true).build(), NodeGroup.DATABASE);
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
            group.send(FromManagerProto.ProxyMsg.newBuilder().setBranch(0).build(), NodeGroup.PROXY);
        } catch (Exception e) {
            LOGGER.error("Proxy not available");
        }
    }

    public void showDatabaseStats() throws Exception {
        group.send(FromManagerProto.ToDataNode.newBuilder().setShowStats(true).build(), NodeGroup.DATABASE);
    }


    public void showDatabaseMap() throws Exception {
        group.send(FromManagerProto.ToDataNode.newBuilder().setShowMap(true).build(), NodeGroup.DATABASE);

    }

    // private void debugExecutionList(List<List<Long>> execLists) {
    // StringBuilder sb = new StringBuilder();
    // for (List<Long> execList : execLists) {
    // if (execList == null || execList.size() == 0)
    // continue;
    // sb.append("[");
    // for (Long l : execList) {
    // sb.append(l);
    // sb.append(",");
    // }
    // sb.append("]\n");
    //
    // }
    // LOGGER.info("replay list: " + sb.toString());
    // }


}
