/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.SharedProperties;
import pt.inesc.proxy.DirectBufferPool;
import undo.proto.ToManagerProto.MsgToManager;



public class Saver extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    private static Logger log = LogManager.getLogger(Saver.class.getName());

    private static CassandraClient cassandra = new CassandraClient();
    LinkedList<RequestResponseListPair> stack = new LinkedList<RequestResponseListPair>();
    OutputStream streamToManager;
    private final LinkedList<ByteBuffer> cleanBuffersRequests = new LinkedList<ByteBuffer>();
    private final LinkedList<ByteBuffer> cleanBuffersResponses = new LinkedList<ByteBuffer>();


    public Saver() {
        @SuppressWarnings("resource")
        Socket socketToManager = new Socket();
        try {
            socketToManager.connect(SharedProperties.MANAGER_ADDRESS);
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
     * @param responseBuffers
     * @param requestBuffers
     * @param requestsSave
     * @param responsesSave
     */
    public synchronized void save(RequestResponseListPair pair, DirectBufferPool requestBuffers, DirectBufferPool responseBuffers) {
        stack.addLast(pair);

        // collect the clean buffers (which have been sent)
        for (ByteBuffer b : cleanBuffersRequests) {
            requestBuffers.returnBuffer(b);
        }
        for (ByteBuffer b : cleanBuffersResponses) {
            responseBuffers.returnBuffer(b);
        }

        cleanBuffersRequests.clear();
        cleanBuffersResponses.clear();

        this.notify();
    }

    private synchronized RequestResponseListPair moveLists() {
        if (stack.isEmpty())
            return null;
        return stack.removeFirst();
    }

    private synchronized void setCleanBuffers(LinkedList<ByteBuffer> request, LinkedList<ByteBuffer> response) {
        cleanBuffersRequests.addAll(request);
        cleanBuffersResponses.addAll(response);
    }

    /**
     * Process the pendent requests
     */
    private void saving() {
        LinkedList<ByteBuffer> buffersRequests = new LinkedList<ByteBuffer>();
        LinkedList<ByteBuffer> buffersResponses = new LinkedList<ByteBuffer>();


        RequestResponseListPair current;
        while ((current = moveLists()) != null) {
            LinkedList<Request> requestsList = current.getRequests();
            LinkedList<Response> responsesList = current.getResponses();
            MsgToManager.StartEndMsg.Builder startEndMsg = MsgToManager.StartEndMsg.newBuilder();


            if (responsesList.size() != requestsList.size()) {
                log.error("Different list sizes");
            }
            while (!requestsList.isEmpty()) {
                Request req = requestsList.removeFirst();
                Response res = responsesList.removeFirst();
                cassandra.putRequestResponse(req, res);
                startEndMsg.addData(res.start);
                startEndMsg.addData(res.end);
                req.data.clear();
                buffersRequests.add(req.data);
                res.data.clear();
                buffersResponses.add(res.data);
            }
            sendStartEndListToManager(startEndMsg.build());
        }
        setCleanBuffers(buffersRequests, buffersResponses);
    }


    private void sendStartEndListToManager(MsgToManager.StartEndMsg startEndMsg) {
        if (streamToManager == null) {
            return;
        }
        MsgToManager msg = MsgToManager.newBuilder().setStartEndMsg(startEndMsg).build();
        try {
            msg.writeDelimitedTo(streamToManager);
        } catch (IOException e) {
            log.error(e);
            streamToManager = null;
            log.debug("Saver: Manager is off");
        }
    }
}
