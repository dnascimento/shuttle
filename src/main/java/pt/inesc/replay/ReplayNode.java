/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.SharedProperties;
import pt.inesc.replay.core.ReplayMode;
import pt.inesc.replay.core.ReplayWorker;
import pt.inesc.undo.proto.FromManagerProto;
import pt.inesc.undo.proto.FromManagerProto.ExecList;
import pt.inesc.undo.proto.ToManagerProto;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager.AckMsg;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager.NodeRegistryMsg;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager.NodeRegistryMsg.NodeGroup;


/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to original thread and continue
 */
public class ReplayNode extends
        Thread {
    private static final Logger log = LogManager.getLogger(ReplayNode.class.getName());
    private static final int N_WORKERS = 30;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(N_WORKERS);
    private List<String> errors = new LinkedList<String>();
    private ArrayList<ReplayWorker> workers = new ArrayList<ReplayWorker>();
    private static long totalRequests = 0;
    ServerSocket myServerSocket;

    public static void main(String[] args) throws Exception {
        DOMConfigurator.configure("log4j.xml");
        new ReplayNode().start();
    }

    public ReplayNode() throws Exception {
        try {
            myServerSocket = new ServerSocket(SharedProperties.REPLAY_PORT);
            registryToManger();
        } catch (BindException e) {
            throw new Exception("Replay Node already running in same port...");
        }
    }



    @Override
    public void run() {
        log.info("Redo node is listening...");
        while (true) {
            Socket newSocket;
            try {
                newSocket = myServerSocket.accept();
                newConnection(newSocket);
            } catch (Exception e) {
                log.error(e);
                e.printStackTrace();
            }
        }

    }






    private void registryToManger() {
        Socket s = new Socket();
        try {
            s.connect(SharedProperties.MANAGER_ADDRESS);
            NodeRegistryMsg c = NodeRegistryMsg.newBuilder()
                                               .setHostname(SharedProperties.MY_HOST)
                                               .setPort(SharedProperties.REPLAY_PORT)
                                               .setGroup(NodeGroup.REDO_NODE)
                                               .build();
            ToManagerProto.MsgToManager.newBuilder().setNodeRegistry(c).build().writeDelimitedTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }

    public void startOrder() {
           long start = System.currentTimeMillis();
           System.out.println("Start:"+start);
           for (ReplayWorker worker : workers) {
               threadPool.execute(worker);
           }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errors.add("REDO FAIL: " + e.toString());
        }

        long end = System.currentTimeMillis();
        System.out.println("END:"+System.currentTimeMillis());
        long duration = end - start;
        log.info("All threads are done");
        log.info("Duration (ms) = " + duration);
        log.info("Total requests = " + totalRequests);
        log.info("Request rate = " + (((double) totalRequests) / duration * 1000) + " req/sec");
        sendAck();
        errors = new LinkedList<String>();
        workers = new ArrayList<ReplayWorker>();
        totalRequests = 0;
        threadPool = Executors.newFixedThreadPool(N_WORKERS);
    }

    public void newRequest(List<Long> execList, short branch, ReplayMode replayMode, String targetHost, int targetPort) throws Exception {
        workers.add(new ReplayWorker(execList, new InetSocketAddress(targetHost, targetPort), branch));
    }

    private void newConnection(Socket socket) throws Exception {
        InputStream stream = socket.getInputStream();
        FromManagerProto.ExecList request = ExecList.parseDelimitedFrom(stream);
        List<Long> execList = request.getRidList();
        if (execList.size() == 0) {
            log.warn("Retrieved an empty execution list");
        } else {
            ReplayMode replayMode = ReplayMode.valueOf(request.getReplayMode());
            newRequest(execList, (short) request.getBranch(), replayMode, request.getTargetHost(), request.getTargetPort());
        }
        if (request.getStart()) {
            startOrder();
        }
        socket.close();
    }

    public synchronized static void addErrors(List<String> errors, long totalRequests) {
        errors.addAll(errors);
        ReplayNode.totalRequests += totalRequests;
    }

    private void sendAck() {
        Socket s = new Socket();
        try {
            s.connect(SharedProperties.MANAGER_ADDRESS);
            AckMsg.Builder c = MsgToManager.AckMsg.newBuilder().setHostname(SharedProperties.MY_HOST).setPort(SharedProperties.REPLAY_PORT);
            c.addAllException(errors);
            ToManagerProto.MsgToManager.newBuilder().setAck(c).build().writeDelimitedTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }
}
