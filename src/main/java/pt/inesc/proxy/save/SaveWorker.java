package pt.inesc.proxy.save;

import java.util.LinkedList;


public class SaveWorker extends
        Thread {
    public enum SaveType {
        Request, Response
    }

    private static final int FLUSH_PERIODICITY = 6000;

    LinkedList<Request> requests = new LinkedList<Request>();
    LinkedList<Response> responses = new LinkedList<Response>();
    CassandraClient cassandra;
    SaveFile file;



    public SaveWorker(LinkedList<Request> requests, LinkedList<Response> responses) {
        System.out.println("New save worker");
        this.requests = requests;
        this.responses = responses;
        cassandra = new CassandraClient();
        file = new SaveFile(); // DEBUG
    }

    @Override
    public void run() {
        while (true) {
            try {
                sleep(FLUSH_PERIODICITY);
                save();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



    private void save() {
        file.openChannels();
        // disconnect
        // TODO: there is inconsistency due to concurrence?
        LinkedList<Request> requestsSave = requests;
        LinkedList<Response> responsesSave = responses;


        if (requestsSave.size() != 0) {
            System.out.println("saving requests...");
            requests = new LinkedList<Request>();
            saveRequests(requestsSave);
        }
        if (responsesSave.size() != 0) {
            System.out.println("saving responses...");
            responses = new LinkedList<Response>();
            saveResponses(responsesSave);
        }
        file.closeChannels();
    }



    private void saveRequests(LinkedList<Request> requestsList) {
        while (!requestsList.isEmpty()) {
            Request req = requestsList.removeFirst();
            req.data.rewind();
            cassandra.putRequest(req.start, req.data);
            file.putRequest(req.start, req.data);
        }

    }

    private void saveResponses(LinkedList<Response> responsesList) {
        LinkedList<Long> startEndList = new LinkedList<Long>();
        while (!responsesList.isEmpty()) {
            Response req = responsesList.removeFirst();
            cassandra.putResponse(req.start, req.data);
            file.putResponse(req.start, req.data);
            startEndList.add(req.start);
            startEndList.add(req.end);
        }
        sendStartEndListToManager(startEndList);
    }

    private void sendStartEndListToManager(LinkedList<Long> startEndList) {
        // TODO Auto-generated method stub

    }



}
