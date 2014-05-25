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

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.manager.branchTree.BranchNode;
import pt.inesc.manager.branchTree.BranchTree;
import pt.inesc.manager.communication.GroupCom;
import pt.inesc.manager.communication.GroupCom.NodeGroup;
import pt.inesc.manager.graph.DependencyGraph;
import pt.inesc.manager.utils.CleanVoldemort;
import pt.inesc.manager.utils.NotifyEvent;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ExecList;
import undo.proto.FromManagerProto.ProxyMsg;
import undo.proto.FromManagerProto.ToDataNode;

public class Manager {
    private static final Logger log = LogManager.getLogger(Manager.class.getName());

    public static final InetSocketAddress MANAGER_ADDR = new InetSocketAddress("localhost", 11000);
    public GroupCom group = new GroupCom();

    public Object ackWaiter = new Object();
    private static final String GRAPH_FILE = "graph.obj";
    DependencyGraph graph;
    private static ServiceManager service;
    public BranchTree branches = new BranchTree();

    public static void main(String[] args) throws IOException {
        DOMConfigurator.configure("log4j.xml");
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }

    public Manager() throws IOException {
        graph = new DependencyGraph();
        service = new ServiceManager(this);
        service.start();
    }


    /* ------------------- Operations ----------------- */
    public void redo(long parentCommit, short parentBranch) throws Exception {
        // get requests to send
        List<Long> roots = graph.getRoots();
        if (roots.size() == 0) {
            log.error("No roots available");
            return;
        }

        short newBranch = branches.fork(parentCommit, parentBranch);
        LinkedList<BranchNode> path = branches.getPath(parentCommit, newBranch);
        // notify the database nodes about the path of the redo branch
        group.sendNewRedoBranch(path);
        // enable restrain in the proxy
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setRestrain(true).build(), NodeGroup.PROXY, false);


        redoRequests(parentCommit, newBranch, roots);


        // disable retrain and change to new branch
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setRedoOver(true).build(), NodeGroup.DATABASE, false);
        System.out.println("Set new branch: " + newBranch);
        group.unicast(FromManagerProto.ProxyMsg.newBuilder().setBranch(newBranch).setRestrain(false).build(),
                      NodeGroup.PROXY,
                      false);
        System.out.println("Redo is over");
    }

    private void redoRequests(long parentCommit, short redoBranch, List<Long> roots) throws Exception {
        log.info(roots);
        graph.restoreCounters();
        for (Long root : roots) {
            List<Long> list = graph.getExecutionList(root, parentCommit);
            try {
                ExecList msg = FromManagerProto.ExecList.newBuilder()
                                                        .addAllRid(list)
                                                        .setBranch(redoBranch)
                                                        .setStart(false)
                                                        .build();
                group.broadcast(msg, NodeGroup.REDO, false);
            } catch (IOException e) {
                log.error(e);
            }
        }
        ExecList startMsg = FromManagerProto.ExecList.newBuilder().setBranch(redoBranch).setStart(true).build();
        group.broadcast(startMsg, NodeGroup.REDO, false);
        // wait for ack
        synchronized (ackWaiter) {
            ackWaiter.wait();
        }
    }

    public void newCommit(long commit) throws Exception {
        branches.addCommit(commit);
        ToDataNode msg = FromManagerProto.ToDataNode.newBuilder().setNewCommit(commit).build();
        group.broadcast(msg, NodeGroup.DATABASE, false);
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(commit));
        new NotifyEvent("Commit done: " + dateString, commit).start();
    }

    public void deleteBranch(Short branch) {
        // TODO Auto-generated method stub

    }


    /* -------------------- Graph -------------------- */
    public void showGraph() {
        graph.display();

    }

    public void saveGraph() throws IOException {
        FileOutputStream fout = new FileOutputStream(GRAPH_FILE);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(graph);
        oos.close();
    }

    public void loadGraph() throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(GRAPH_FILE);
        ObjectInputStream ois = new ObjectInputStream(fin);
        graph = (DependencyGraph) ois.readObject();
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

    public void resetDatabaseAccessLists() throws IOException {
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setResetDependencies(true).build(),
                        NodeGroup.DATABASE,
                        false);
    }

    public void cleanVoldemort() {
        CleanVoldemort.clean(group.getDatabaseNodes());
    }

    public void resetBranch() {
        branches = new BranchTree();
    }








}
