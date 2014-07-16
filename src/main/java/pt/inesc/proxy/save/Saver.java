/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;
import undo.proto.ToManagerProto.MsgToManager;
import undo.proto.ToManagerProto.StartEndEntry;
import undo.proto.ToManagerProto.StartEndMsg;



public class Saver extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    private static Logger log = LogManager.getLogger(Saver.class.getName());

    CassandraClient cassandra;
    LinkedList<RequestResponseListPair> stack = new LinkedList<RequestResponseListPair>();

    public Saver() {
        cassandra = new CassandraClient();
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
            saveRequests(current.getRequests());
            saveResponses(current.getResponses());
        }
    }

    private void saveRequests(LinkedList<Request> requestsList) {
        while (!requestsList.isEmpty()) {
            Request req = requestsList.removeFirst();
            cassandra.putRequest(req);
        }

    }

    private void saveResponses(LinkedList<Response> responsesList) {
        LinkedList<Long> startEndList = new LinkedList<Long>();
        while (!responsesList.isEmpty()) {
            Response req = responsesList.removeFirst();
            cassandra.putResponse(req.start, req.data);
            startEndList.add(req.start);
            startEndList.add(req.end);
        }
        try {
            sendStartEndListToManager(startEndList);
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void sendStartEndListToManager(LinkedList<Long> startEndList) throws UnknownHostException, IOException {
        assert ((startEndList.size() % 2) == 0);
        StartEndMsg.Builder b = StartEndMsg.newBuilder();
        Iterator<Long> i = startEndList.iterator();
        while (i.hasNext()) {
            StartEndEntry e = StartEndEntry.newBuilder().setStart(i.next()).setEnd(i.next()).build();
            b.addMsg(e);
        }
        StartEndMsg m = b.build();
        MsgToManager msg = MsgToManager.newBuilder().setStartEndMsg(m).build();
        Socket s;
        try {
            s = new Socket();
            s.connect(Manager.MANAGER_ADDR);
            msg.writeDelimitedTo(s.getOutputStream());
            s.close();
        } catch (ConnectException e) {
            log.debug("Saver: Manager is off");
        }
    }
}
