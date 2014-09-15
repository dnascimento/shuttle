/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;
import undo.proto.ToManagerProto.MsgToManager;
import undo.proto.ToManagerProto.StartEndMsg;
import undo.proto.ToManagerProto.StartEndMsg.Builder;



public class Saver extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    private static Logger log = LogManager.getLogger(Saver.class.getName());

    private static CassandraClient cassandra = new CassandraClient();
    LinkedList<RequestResponseListPair> stack = new LinkedList<RequestResponseListPair>();
    OutputStream streamToManager;

    public Saver() {
        @SuppressWarnings("resource")
        Socket socketToManager = new Socket();
        try {
            socketToManager.connect(Manager.MANAGER_ADDR);
            socketToManager.getOutputStream();
        } catch (IOException e) {
            log.error("Saver: manager not available", e);
            streamToManager = null;
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Saver-Thread");
        while (true) {
            synchronized (this) {
                try {
                    this.wait();
                    saving();
                } catch (InterruptedException e) {
                    log.error(e);
                }
            }
        }
    }

    /**
     * Add current requests to save list and notify the thread
     * 
     * @param requestsSave
     * @param responsesSave
     */
    public synchronized void save(RequestResponseListPair pair) {
        stack.addLast(pair);
        this.notify();
    }

    private synchronized RequestResponseListPair moveLists() {
        if (stack.isEmpty())
            return null;
        return stack.removeFirst();
    }

    /**
     * Process the pendent requests
     */
    private void saving() {
        RequestResponseListPair current;
        while ((current = moveLists()) != null) {
            LinkedList<Request> requestsList = current.getRequests();
            LinkedList<Response> responsesList = current.getResponses();
            StartEndMsg.Builder startEndMsg = StartEndMsg.newBuilder();


            if (responsesList.size() != requestsList.size()) {
                log.error("Different list sizes");
            }
            while (!requestsList.isEmpty()) {
                Request req = requestsList.removeFirst();
                Response res = responsesList.removeFirst();
                cassandra.putRequestResponse(req, res);
                startEndMsg.addData(res.start);
                startEndMsg.addData(res.end);
            }

            try {
                sendStartEndListToManager(startEndMsg);
            } catch (Exception e) {
                log.error(e);
            }

        }
    }


    private void sendStartEndListToManager(Builder startEndMsg) throws UnknownHostException, IOException {
        if (streamToManager != null) {
            System.out.println("Socket closed");
            return;
        }
        MsgToManager msg = MsgToManager.newBuilder().setStartEndMsg(startEndMsg).build();
        try {
            msg.writeDelimitedTo(streamToManager);
        } catch (ConnectException e) {
            log.debug("Saver: Manager is off");
        }
    }
}
