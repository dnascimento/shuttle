package pt.inesc.manager.groupCom;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.proxy.Proxy;
import pt.inesc.redoNode.RedoNode;

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
        redoList.add(new InetSocketAddress("localhost", RedoNode.MY_PORT));
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
        try {
            if (!getGroup(group).contains(addr))
                getGroup(group).add(addr);
            else
                log.info("The node is known");
        } catch (IOException e) {
            log.error(e);
        }
    }

    /**
     * Broadcast message
     * 
     * @throws IOException
     */
    public void broadcast(Message msg, NodeGroup group) throws IOException {
        // TODO may be optimized to use a socket pool or assync channels...
        for (InetSocketAddress addr : getGroup(group)) {
            sendMsg(msg, addr);
        }
    }

    public void unicast(Message msg, NodeGroup group) throws IOException {
        ArrayList<InetSocketAddress> g = getGroup(group);
        if (g.isEmpty()) {
            throw new IOException("No nodes in group " + group);
        }
        // TODO optimize for node preference
        sendMsg(msg, g.get(0));
    }

    private void sendMsg(Message msg, InetSocketAddress addr) throws IOException {
        Socket s = new Socket();
        s.connect(addr);
        msg.writeTo(s.getOutputStream());
        s.close();
    }

    private ArrayList<InetSocketAddress> getGroup(NodeGroup group) throws IOException {
        switch (group) {
        case DATABASE:
            return databaseList;
        case PROXY:
            return proxyList;
        case REDO:
            return redoList;
        default:
            throw new IOException("Unknown group");
        }
    }
}
