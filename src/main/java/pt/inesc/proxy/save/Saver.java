package pt.inesc.proxy.save;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedList;

import voldemort.undoTracker.proto.ToManagerProto.MsgToManager;
import voldemort.undoTracker.proto.ToManagerProto.StartEndEntry;
import voldemort.undoTracker.proto.ToManagerProto.StartEndMsg;


public class Saver extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    CassandraClient cassandra;
    SaveFile file;
    LinkedList<RequestResponseListPair> stack = new LinkedList<RequestResponseListPair>();

    public Saver() {
        System.out.println("New save worker");
        cassandra = new CassandraClient();
        // file = new SaveFile(); // DEBUG
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                saving();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
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
     * 
     * @throws InterruptedException
     */
    private void saving() throws InterruptedException {
        RequestResponseListPair current;
        while ((current = moveLists()) != null) {
            sleep(500);
            // file.openChannels();
            saveRequests(current.getRequests());

            saveResponses(current.getResponses());

            // file.closeChannels();
        }
    }

    private void saveRequests(LinkedList<Request> requestsList) {
        while (!requestsList.isEmpty()) {
            Request req = requestsList.removeFirst();
            req.data.rewind();
            cassandra.putRequest(req.start, req.data);
            // file.putRequest(req.start, req.data);
        }

    }

    private void saveResponses(LinkedList<Response> responsesList) {
        LinkedList<Long> startEndList = new LinkedList<Long>();
        while (!responsesList.isEmpty()) {
            Response req = responsesList.removeFirst();
            cassandra.putResponse(req.start, req.data);
            // file.putResponse(req.start, req.data);
            startEndList.add(req.start);
            startEndList.add(req.end);
        }
        try {
            sendStartEndListToManager(startEndList);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
        // TODO send by socket to manager
        Socket s = new Socket("localhost", 9800);
        msg.writeTo(s.getOutputStream());
        s.close();
    }



}
