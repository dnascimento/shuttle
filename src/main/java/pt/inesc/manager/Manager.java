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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.manager.branchTree.BranchNode;
import pt.inesc.manager.branchTree.BranchTree;
import pt.inesc.manager.communication.GroupCom;
import pt.inesc.manager.communication.GroupCom.NodeGroup;
import pt.inesc.manager.graph.GraphShuttle;
import pt.inesc.manager.utils.CleanVoldemort;
import pt.inesc.manager.utils.NotifyEvent;
import pt.inesc.replay.core.ReplayMode;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ExecList;
import undo.proto.FromManagerProto.ProxyMsg;
import undo.proto.FromManagerProto.ToDataNode;

public class Manager {
    private static final Logger LOGGER = LogManager.getLogger(Manager.class.getName());

    public static final InetSocketAddress MANAGER_ADDR = new InetSocketAddress("localhost", 11000);
    public GroupCom group = new GroupCom();

    public Object ackWaiter = new Object();
    GraphShuttle graph = new GraphShuttle();
    private static ServiceManager service;
    public BranchTree branches = new BranchTree();

    public static void main(String[] args) throws IOException {
        DOMConfigurator.configure("log4j.xml");

        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }

    public Manager() throws IOException {
        service = new ServiceManager(this);
        service.start();
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
        group.sendNewRedoBranch(path);

        // enable restrain in the proxy (should be done at the end)
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setRestrain(true).build(), NodeGroup.PROXY, false);


        // Get execution list
        List<List<Long>> execLists = graph.replay(parentCommit, replayMode, attackSource);


        // TODO invoke the infrastructure to start the replay nodes
        // infrastructrure.startReplay()

        // inform the slaves which requests will be replayed
        for (List<Long> execList : execLists) {
            if (execList == null || execList.size() == 0)
                continue;
            ExecList msg = FromManagerProto.ExecList.newBuilder()
                                                    .addAllRid(execList)
                                                    .setBranch(parentBranch)
                                                    .setStart(false)
                                                    .setReplayMode(replayMode.toString())
                                                    .build();
            group.broadcast(msg, NodeGroup.REDO, false);
        }

        // order to replay the requests
        ExecList startMsg = FromManagerProto.ExecList.newBuilder()
                                                     .setReplayMode(replayMode.toString())
                                                     .setBranch(parentBranch)
                                                     .setStart(true)
                                                     .build();
        group.broadcast(startMsg, NodeGroup.REDO, false);
        // wait for ack
        synchronized (ackWaiter) {
            ackWaiter.wait();
        }

        // disable retrain and change to new branch
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setRedoOver(true).build(), NodeGroup.DATABASE, false);
        LOGGER.warn("Set new branch: " + newBranch);
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranch).setRestrain(false).build(), NodeGroup.PROXY, false);
        LOGGER.info("Replay is over");
    }


    public void newCommit(long commit) throws Exception {
        branches.addCommit(commit);
        ToDataNode msg = FromManagerProto.ToDataNode.newBuilder().setNewCommit(commit).build();
        group.broadcast(msg, NodeGroup.DATABASE, false);
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(commit));
        new NotifyEvent("Commit done: " + dateString, commit).start();
    }

    public void deleteBranch(Short branch) {
        throw new NotImplementedException("delete branch");
    }

    public void changeToBranch(Short branchId) throws IOException {
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setBranch(branchId).setRestrain(false).build(), NodeGroup.PROXY, false);
    }

    public void newBranch(long parentCommit, short parentBranch) throws Exception {
        LOGGER.info("Create branch based on branch: " + parentBranch + " and commit: " + parentCommit);

        short newBranch = branches.fork(parentCommit, parentBranch);
        LinkedList<BranchNode> path = branches.getPath(parentCommit, newBranch);
        // notify the database nodes about the path of the redo branch
        group.sendNewRedoBranch(path);
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setRedoOver(true).build(), NodeGroup.DATABASE, false);
        LOGGER.warn("Set new branch: " + newBranch);
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranch).setRestrain(false).build(), NodeGroup.PROXY, false);
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
    public void proxyTimeTravel(long time) throws IOException {
        ProxyMsg msg = FromManagerProto.ProxyMsg.newBuilder().setTimeTravel(time).build();
        group.unicast(msg, NodeGroup.PROXY, false);
    }


    /* -------------------- Cleans -------------------- */

    public void resetDatabaseAccessLists() {
        try {
            group.broadcast(FromManagerProto.ToDataNode.newBuilder().setResetDependencies(true).build(), NodeGroup.DATABASE, false);
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
            group.unicast(FromManagerProto.ProxyMsg.newBuilder().setBranch(0).setCommit(0).build(), NodeGroup.PROXY, false);
        } catch (IOException e) {
            LOGGER.error("Proxy not available");
        }
    }








}
