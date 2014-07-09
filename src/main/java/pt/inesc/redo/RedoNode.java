/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.redo;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.manager.Manager;
import pt.inesc.redo.core.RedoWorker;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ExecList;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.AckMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg.NodeGroup;



/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to original thread and continue
 */
public class RedoNode extends
        Thread {
    public static final int MY_PORT = 11500;
    public static final InetSocketAddress TARGET_LOAD_BALANCER_ADDR = new InetSocketAddress("localhost", 8080);
    private final static Logger log = LogManager.getLogger(RedoNode.class.getName());
    private final static int N_WORKERS = 1;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(N_WORKERS);
    private List<String> errors = new LinkedList<String>();
    private ArrayList<RedoWorker> workers = new ArrayList<RedoWorker>();
    private static long totalRequests = 0;
    ServerSocket myServerSocket;

    public static void main(String[] args) throws Exception {
        DOMConfigurator.configure("log4j.xml");
        new RedoNode().start();
    }

    public RedoNode() throws Exception {
        try {
            myServerSocket = new ServerSocket(MY_PORT);
            registryToManger();
        } catch (BindException e) {
            throw new Exception("Redo Node already running in same port...");
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
            s.connect(Manager.MANAGER_ADDR);
            NodeRegistryMsg c = ToManagerProto.NodeRegistryMsg.newBuilder()
                                                              .setHostname("localhost")
                                                              .setPort(MY_PORT)
                                                              .setGroup(NodeGroup.REDO_NODE)
                                                              .build();
            ToManagerProto.MsgToManager.newBuilder().setNodeRegistry(c).build().writeDelimitedTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }

    public void startOrder() {
        Date startDate = new Date();
        for (RedoWorker worker : workers) {
            threadPool.execute(worker);
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errors.add("REDO FAIL: " + e.toString());
        }

        long duration = new Date().getTime() - startDate.getTime();
        log.info("All threads are done");
        log.info("Duration = " + duration);
        log.info("Total requests = " + totalRequests);
        log.info("Request rate = " + (((double) totalRequests) / duration * 1000) + " req/sec");
        sendAck();
        errors = new LinkedList<String>();
        workers = new ArrayList<RedoWorker>();
        threadPool = Executors.newFixedThreadPool(N_WORKERS);
    }

    public void newRequest(List<Long> execList, short branch) throws Exception {
        workers.add(new RedoWorker(execList, TARGET_LOAD_BALANCER_ADDR, branch));
    }

    private void newConnection(Socket socket) throws Exception {
        InputStream stream = socket.getInputStream();
        FromManagerProto.ExecList list = ExecList.parseDelimitedFrom(stream);
        List<Long> execList = list.getRidList();
        if (execList.size() != 0) {
            newRequest(execList, (short) list.getBranch());
        }
        if (list.getStart()) {
            startOrder();
        }
    }

    public synchronized static void addErrors(List<String> errors, long totalRequests) {
        errors.addAll(errors);
        RedoNode.totalRequests += totalRequests;
    }

    private void sendAck() {
        Socket s = new Socket();
        try {
            s.connect(Manager.MANAGER_ADDR);
            AckMsg.Builder c = ToManagerProto.AckMsg.newBuilder().setHostname("localhost").setPort(MY_PORT);
            c.addAllException(errors);
            ToManagerProto.MsgToManager.newBuilder().setAck(c).build().writeDelimitedTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }
}
