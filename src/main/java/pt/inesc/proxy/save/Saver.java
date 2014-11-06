/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.SharedProperties;
import pt.inesc.proxy.DirectBufferPool;
import pt.inesc.proxy.ProxyWorker;
import pt.inesc.undo.proto.ToManagerProto.MsgToManager;



public class Saver extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    /**
     * At least, every TIMEOUT_PERIOD milliseconds, fetch the dependencies.
     */
    private static final long TIMEOUT_PERIOD = 10000;

    private static Logger log = LogManager.getLogger(Saver.class.getName());

    private static CassandraClient cassandra = new CassandraClient();
    LinkedList<ArrayList<RequestResponsePair>> stack = new LinkedList<ArrayList<RequestResponsePair>>();
    private final LinkedList<ByteBuffer> cleanBuffersRequests = new LinkedList<ByteBuffer>();
    private final LinkedList<ByteBuffer> cleanBuffersResponses = new LinkedList<ByteBuffer>();

    private boolean TIMEOUT = true;
    private final ProxyWorker worker;


    public Saver(ProxyWorker worker) {
        this.worker = worker;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Saver-Thread");
        while (true) {
            synchronized (this) {
                try {
                    TIMEOUT = true;
                    this.wait(TIMEOUT_PERIOD);
                    if (TIMEOUT) {
                        worker.flushData();
                    }
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
    public synchronized void save(ArrayList<RequestResponsePair> previous, DirectBufferPool requestBuffers, DirectBufferPool responseBuffers) {
        stack.addLast(previous);

        // collect the clean buffers (which have been sent)
        requestBuffers.returnBuffer(cleanBuffersRequests);

        responseBuffers.returnBuffer(cleanBuffersResponses);


        cleanBuffersRequests.clear();
        cleanBuffersResponses.clear();
        TIMEOUT = false;
        this.notify();
    }

    private synchronized ArrayList<RequestResponsePair> moveLists() {
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


        ArrayList<RequestResponsePair> current;
        while ((current = moveLists()) != null) {
            MsgToManager.StartEndMsg.Builder startEndMsg = MsgToManager.StartEndMsg.newBuilder();
            for (RequestResponsePair e : current) {
                cassandra.putRequestResponse(e.request, e.response, e.start, e.end);
                startEndMsg.addData(e.start);
                startEndMsg.addData(e.end);
                e.request.clear();

                buffersRequests.add(e.request);
                buffersResponses.add(e.response);
            }
            sendStartEndListToManager(startEndMsg.build());
        }
        setCleanBuffers(buffersRequests, buffersResponses);
    }

    private void sendStartEndListToManager(MsgToManager.StartEndMsg startEndMsg) {
        MsgToManager msg = MsgToManager.newBuilder().setStartEndMsg(startEndMsg).build();

        Socket socketToManager = new Socket();
        try {
            socketToManager.connect(SharedProperties.MANAGER_ADDRESS);

            msg.writeDelimitedTo(socketToManager.getOutputStream());
            socketToManager.close();
            System.out.println("Dependencies sent");
        } catch (IOException e) {
            log.error("Saver: manager not available", e);
        }
    }
}
