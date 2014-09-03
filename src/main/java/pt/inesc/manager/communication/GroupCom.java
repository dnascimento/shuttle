/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.branchTree.BranchNode;
import pt.inesc.proxy.Proxy;
import pt.inesc.redo.ReplayNode;
import undo.proto.FromManagerProto;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.AckMsg;

import com.google.protobuf.Message;

public class GroupCom {
    private final Logger log = LogManager.getLogger(GroupCom.class.getName());

    public enum NodeGroup {
        PROXY, DATABASE, REDO;
    }

    ArrayList<InetSocketAddress> databaseList = new ArrayList<InetSocketAddress>();
    ArrayList<InetSocketAddress> redoList = new ArrayList<InetSocketAddress>();
    ArrayList<InetSocketAddress> proxyList = new ArrayList<InetSocketAddress>();

    public GroupCom() {
        // Default instances:
        proxyList.add(new InetSocketAddress("localhost", Proxy.MY_PORT));
        redoList.add(new InetSocketAddress("localhost", ReplayNode.MY_PORT));
        databaseList.add(new InetSocketAddress("localhost", 11200));
    }

    /**
     * Retrieve node registry in group
     * 
     * @param host
     * @param port
     * @param group
     */
    public void newNode(String host, int port, NodeGroup group) {
        log.info("New node: " + host + ":" + port + " group: " + group);
        InetSocketAddress addr = new InetSocketAddress(host, port);
        if (!getGroup(group).contains(addr))
            getGroup(group).add(addr);
        else
            log.info("The node is known");
    }

    /**
     * Broadcast message
     * 
     * @throws IOException
     */
    public void broadcast(Message msg, NodeGroup group, boolean ack) throws IOException {
        // TODO may be optimized to use a socket pool or assync channels...
        for (InetSocketAddress addr : getGroup(group)) {
            sendMsg(msg, addr, ack);
        }
    }

    public void unicast(Message msg, NodeGroup group, boolean ack) throws IOException {
        ArrayList<InetSocketAddress> g = getGroup(group);
        if (g.isEmpty()) {
            throw new IOException("No nodes in group " + group);
        }
        // TODO optimize for node preference
        sendMsg(msg, g.get(0), ack);
    }

    private void sendMsg(Message msg, InetSocketAddress addr, boolean ack) throws IOException {
        Socket s = new Socket();
        s.connect(addr);
        msg.writeDelimitedTo(s.getOutputStream());
        s.getOutputStream().flush();
        if (ack) {
            AckMsg ackMsg = ToManagerProto.AckMsg.parseDelimitedFrom(s.getInputStream());
            if (ackMsg.getExceptionCount() > 0) {
                log.error(ackMsg.getExceptionList());
                // TODO ack processing better
            }
        }
        s.close();
    }

    private ArrayList<InetSocketAddress> getGroup(NodeGroup group) {
        switch (group) {
        case DATABASE:
            return databaseList;
        case PROXY:
            return proxyList;
        case REDO:
            return redoList;
        default:
            log.error("Unknown getGroup");
            return null;
        }
    }

    public String[] getDatabaseNodes() {
        ArrayList<InetSocketAddress> addressList = getGroup(NodeGroup.DATABASE);
        String[] result = new String[addressList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = addressList.get(i).getHostString();
        }
        return result;
    }

    public void sendNewRedoBranch(LinkedList<BranchNode> path) throws Exception {
        FromManagerProto.ToDataNode.Builder b = FromManagerProto.ToDataNode.newBuilder();
        for (BranchNode n : path) {
            b.addPathBranch(n.branch);
            b.addPathCommit(n.commit);
        }
        broadcast(b.build(), NodeGroup.DATABASE, true);
    }
}
