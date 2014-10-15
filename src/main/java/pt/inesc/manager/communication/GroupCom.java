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
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.SharedProperties;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager;

import com.google.protobuf.Message;

public class GroupCom {
    private final Logger log = LogManager.getLogger(GroupCom.class.getName());

    public enum NodeGroup {
        PROXY, DATABASE, REDO, APPLICATION_SERVER;
    }

    ArrayList<InetSocketAddress> databaseList = new ArrayList<InetSocketAddress>();
    ArrayList<InetSocketAddress> replayInstancesList = new ArrayList<InetSocketAddress>();
    ArrayList<InetSocketAddress> proxyList = new ArrayList<InetSocketAddress>();
    ArrayList<InetSocketAddress> applicationServers = new ArrayList<InetSocketAddress>();


    public GroupCom() {
        // Default instances:
        applicationServers.add(SharedProperties.LOAD_BALANCER_ADDRESS);
        proxyList.add(new InetSocketAddress("localhost", SharedProperties.PROXY_PORT));
        replayInstancesList.add(new InetSocketAddress("localhost", SharedProperties.REPLAY_PORT));
        databaseList.add(new InetSocketAddress("localhost", SharedProperties.DATABASE_PORT));
    }

    /**
     * Retrieve node registry in group
     * 
     * @param host
     * @param port
     * @param group
     */
    public void newNode(String host, int port, NodeGroup type) {
        log.info("New node: " + host + ":" + port + " group: " + type);
        InetSocketAddress addr = new InetSocketAddress(host, port);

        List<InetSocketAddress> group = getGroup(type);
        if (group.contains(addr)) {
            log.info("The node is known");
            return;
        }
        // remove the localhost predefinition
        Iterator<InetSocketAddress> it = group.iterator();
        while (it.hasNext()) {
            if (it.next().getHostString().equals("localhost"))
                it.remove();
        }
        group.add(addr);
    }

    /**
     * Broadcast message
     * 
     * @throws IOException
     */
    public void broadcast(Message msg, NodeGroup group) throws Exception {
        // TODO may be optimized to use a socket pool or assync channels...
        for (InetSocketAddress addr : getGroup(group)) {
            send(msg, addr);
        }
    }


    /**
     * Broadcast message
     * 
     * @throws IOException
     */
    public void broadcastWithAck(Message msg, NodeGroup group) throws Exception {
        // TODO may be optimized to use a socket pool or assync channels...
        for (InetSocketAddress addr : getGroup(group)) {
            MsgToManager.AckMsg ack = sendWithResponse(msg, addr).getAck();
            for (String s : ack.getExceptionList()) {
                System.err.println(s);
            }
        }
    }

    public MsgToManager sendWithResponse(Message msg, NodeGroup group) throws Exception {
        ArrayList<InetSocketAddress> g = getGroup(group);
        if (g.isEmpty()) {
            throw new IOException("No nodes in group " + group);
        }
        return sendWithResponse(msg, g.get(0));
    }

    public void send(Message msg, NodeGroup group) throws Exception {
        ArrayList<InetSocketAddress> g = getGroup(group);
        if (g.isEmpty()) {
            throw new IOException("No nodes in group " + group);
        }
        send(msg, g.get(0));
    }

    public void send(Message msg, InetSocketAddress addr) throws Exception {
        Socket s = new Socket();
        s.connect(addr);
        msg.writeDelimitedTo(s.getOutputStream());
        s.getOutputStream().flush();
        s.close();
    }


    public MsgToManager sendWithResponse(Message msg, InetSocketAddress addr) throws Exception {
        Socket s = new Socket();
        s.connect(addr);
        msg.writeDelimitedTo(s.getOutputStream());
        s.getOutputStream().flush();
        MsgToManager response = MsgToManager.parseDelimitedFrom(s.getInputStream());
        s.close();
        return response;

    }







    private ArrayList<InetSocketAddress> getGroup(NodeGroup group) {
        switch (group) {
        case DATABASE:
            return databaseList;
        case PROXY:
            return proxyList;
        case REDO:
            return replayInstancesList;
        case APPLICATION_SERVER:
            return applicationServers;
        default:
            throw new RuntimeException("Unknown getGroup: " + group);
        }
    }


    public int countReplayNodes() {
        return replayInstancesList.size();
    }


    public String[] getDatabaseNodes() {
        ArrayList<InetSocketAddress> addressList = getGroup(NodeGroup.DATABASE);
        String[] result = new String[addressList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = addressList.get(i).getHostString();
        }
        return result;
    }

    public List<InetSocketAddress> getReplayInstances() {
        return replayInstancesList;
    }
}
