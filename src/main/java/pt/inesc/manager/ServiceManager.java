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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import pt.inesc.SharedProperties;
import pt.inesc.manager.communication.GroupCom.NodeGroup;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.MsgToManager;
import undo.proto.ToManagerProto.MsgToManager.TrackEntry;

// Retrieves the requests from database
public class ServiceManager extends
        Thread {
    private final Manager manager;
    private final ServerSocket serverSocket;
    private final Logger log = Logger.getLogger(ServiceManager.class.getName());
    protected ExecutorService threadPool = Executors.newFixedThreadPool(10);


    public ServiceManager(Manager manager) throws IOException {
        super();
        this.manager = manager;
        serverSocket = new ServerSocket();
        serverSocket.bind(SharedProperties.MANAGER_ADDRESS);
        log.setLevel(Level.DEBUG);
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        Thread.currentThread().setName("ServiceManager Main: " + threadName);

        log.info("Manager Service is listening...");
        while (true) {
            Socket s;
            try {
                s = serverSocket.accept();
                threadPool.execute(new ServiceManagerMsgHandler(s));
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    class ServiceManagerMsgHandler
            implements Runnable {
        Socket socket;

        public ServiceManagerMsgHandler(Socket s) {
            this.socket = s;
        }

        @Override
        public void run() {
            boolean keepAlive = false;
            do {
                ToManagerProto.MsgToManager proto;
                try {
                    proto = ToManagerProto.MsgToManager.parseDelimitedFrom(socket.getInputStream());
                    if (proto == null) {
                        keepAlive = true;
                        System.out.println("empty");
                        break;
                    }
                    // add dependencies (From Database nodes)
                    if (proto.hasTrackMsg()) {
                        log.info("New msg: dependencies");
                        MsgToManager.TrackMsg m1 = proto.getTrackMsg();
                        newList(m1.getEntryList());
                    }
                    // add start-end of each request (from proxy)
                    if (proto.hasStartEndMsg()) {
                        log.info("New msg: start-end");
                        MsgToManager.StartEndMsg m2 = proto.getStartEndMsg();
                        Iterator<Long> startEndList = m2.getDataList().iterator();
                        while (startEndList.hasNext()) {
                            manager.graph.addStartEnd(startEndList.next(), startEndList.next());
                        }
                        keepAlive = true;
                    }
                    //
                    if (proto.hasTrackMsgFromClient()) {
                        log.info("New msg: TrackMsg from Client Lib");
                        MsgToManager.TrackMsg m3 = proto.getTrackMsgFromClient();
                        clientDependencies(m3.getEntryList());
                    }

                    //
                    if (proto.hasNodeRegistry()) {
                        log.info("New msg: has Node Registry");
                        MsgToManager.NodeRegistryMsg msg = proto.getNodeRegistry();
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
                        MsgToManager.AckMsg m = proto.getAck();
                        System.out.println("Node " + m.getHostname() + ":" + m.getPort() + " done");
                        for (String e : m.getExceptionList()) {
                            System.err.println(e);
                        }
                        manager.ackWaiter.decrement();
                    }

                } catch (IOException e) {
                    log.error("Service Manager", e);
                }
            } while (keepAlive);
            try {
                socket.close();
                log.info("Socket closed");
            } catch (IOException e) {
                log.error("Service Manager", e);
            }
        }


        public void newList(List<TrackEntry> list) {
            log.debug(depListToString(list));
            for (TrackEntry entry : list) {
                manager.addDependencies(entry.getRid(), entry.getDependencyList());
            }
            log.debug("dep list is processed");
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



}
