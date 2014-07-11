/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ProxyMsg;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg.NodeGroup;


public class ServiceProxy extends
        Thread {
    private final ServerSocket serverSocket;
    private final Logger log = Logger.getLogger(ServiceProxy.class.getName());
    private final Proxy proxy;


    public ServiceProxy(Proxy p) throws IOException {
        this.proxy = p;
        serverSocket = new ServerSocket(Proxy.MY_PORT);
        registryToManger();
    }

    @Override
    public void run() {
        log.info("Proxy Service listening...");
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


    private void registryToManger() {
        Socket s = new Socket();
        try {
            s.connect(Manager.MANAGER_ADDR);
            NodeRegistryMsg c = ToManagerProto.NodeRegistryMsg.newBuilder()
                                                              .setHostname("localhost")
                                                              .setPort(Proxy.MY_PORT)
                                                              .setGroup(NodeGroup.PROXY)
                                                              .build();

            ToManagerProto.MsgToManager.newBuilder().setNodeRegistry(c).build().writeDelimitedTo(s.getOutputStream());
            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }

    private void receive(Socket socket) throws IOException {
        log.info("New service command");
        ProxyMsg msg = FromManagerProto.ProxyMsg.parseDelimitedFrom(socket.getInputStream());
        if (msg.hasCommit()) {
            short branch = (short) msg.getBranch();
            long commit = msg.getCommit();
            proxy.reset(branch, commit);
            return;
        }

        if (msg.hasTimeTravel()) {
            // time travel
            proxy.timeTravel(msg.getTimeTravel());
        }
        if (msg.hasBranch() || msg.hasRestrain()) {
            short branch = (short) msg.getBranch();
            boolean restrain = msg.getRestrain();
            proxy.setBranchAndRestrain(branch, restrain);
        }
    }


}
