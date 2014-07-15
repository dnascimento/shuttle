/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.apache.log4j.Logger;

import pt.inesc.manager.communication.GroupCom.NodeGroup;
import pt.inesc.manager.graph.DependencyGraph;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.StartEndEntry;
import undo.proto.ToManagerProto.StartEndMsg;
import undo.proto.ToManagerProto.TrackEntry;
import undo.proto.ToManagerProto.TrackMsg;

// Retrieves the requests from database
public class ServiceManager extends
        Thread {
    private final Manager manager;
    private final ServerSocket serverSocket;
    private final Logger log = Logger.getLogger(ServiceManager.class.getName());

    // Only for local tests
    public ServiceManager(DependencyGraph graph) throws IOException {
        super();
        manager = new Manager();
        manager.graph = graph;
        serverSocket = null;
    }

    public ServiceManager(Manager manager) throws IOException {
        super();
        this.manager = manager;
        serverSocket = new ServerSocket();
        serverSocket.bind(Manager.MANAGER_ADDR);
    }

    @Override
    public void run() {
        log.info("Manager Service is listening...");
        while (true) {
            Socket s = null;
            try {
                s = serverSocket.accept();
                receive(s);
            } catch (IOException e) {
                log.error(e);
            }
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
    }

    private void receive(Socket socket) throws IOException {
        ToManagerProto.MsgToManager proto = ToManagerProto.MsgToManager.parseDelimitedFrom(socket.getInputStream());
        if (proto == null)
            return;
        if (proto.hasTrackMsg()) {
            TrackMsg m1 = proto.getTrackMsg();
            newList(m1.getEntryList());
        }
        if (proto.hasStartEndMsg()) {
            StartEndMsg m2 = proto.getStartEndMsg();
            updateStartEnd(m2.getMsgList());
        }
        if (proto.hasTrackMsgFromClient()) {
            log.info("New msg: TrackMsg from Client Lib");
            TrackMsg m3 = proto.getTrackMsgFromClient();
            clientDependencies(m3.getEntryList());
        }
        if (proto.hasNodeRegistry()) {
            log.info("New msg: has Node Registry");
            NodeRegistryMsg msg = proto.getNodeRegistry();
            NodeGroup group;
            switch (msg.getGroup()) {
            case DB_NODE:
                group = NodeGroup.DATABASE;
                break;
            case PROXY:
                group = NodeGroup.PROXY;
                break;
            case REDO_NODE:
                group = NodeGroup.REDO;
                break;
            default:
                log.error("Unknown new node group");
                return;
            }
            manager.group.newNode(msg.getHostname(), msg.getPort(), group);
        }
        if (proto.hasAck()) {
            // TODO multiple redo nodes, o wait pode nao estar locked ainda
            synchronized (manager.ackWaiter) {
                manager.ackWaiter.notify();
            }
        }
    }



    private void updateStartEnd(List<StartEndEntry> entryList) {
        for (StartEndEntry entry : entryList) {
            manager.graph.updateStartEnd(entry.getStart(), entry.getEnd());
        }
    }

    public void newList(List<TrackEntry> list) {
        log.info(depListToString(list));
        for (TrackEntry entry : list) {
            manager.addDependencies(entry.getRid(), entry.getDependencyList());
        }
        log.info("dep list is processed");
    }


    private void clientDependencies(List<TrackEntry> list) {
        log.debug("------ Client Side dependency--------");
        log.debug(depListToString(list));
        log.debug("--------------");
        // TODO insert into graph and compare
    }

    /**
     * Display dependency list properly
     * 
     * @param list
     * @return
     */
    private String depListToString(List<TrackEntry> list) {
        log.debug("------Dep List (size: " + list.size() + ")--------");
        StringBuilder sb = new StringBuilder();
        for (TrackEntry entry : list) {
            sb.append("\n[");
            sb.append(entry.getRid());
            sb.append("<-");
            for (Long l : entry.getDependencyList()) {
                sb.append(l);
                sb.append(",");
            }
            sb.append("]\n");
        }
        sb.append("--------------");
        return sb.toString();
    }
}
