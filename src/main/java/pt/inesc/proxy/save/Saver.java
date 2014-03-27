package pt.inesc.proxy.save;

import java.util.LinkedList;


public class Saver {
    public enum SaveType {
        Request, Response
    }

    CassandraClient cassandra;
    SaveFile file;
    LinkedList<Request> requestsSave;
    LinkedList<Response> responsesSave;

    public Saver(LinkedList<Request> requestsSave, LinkedList<Response> responsesSave) {
        System.out.println("New save worker");
        cassandra = new CassandraClient();
        file = new SaveFile(); // DEBUG
        this.requestsSave = requestsSave;
        this.responsesSave = responsesSave;
    }

    public void save() {
        file.openChannels();
        System.out.println("saving requests...");
        saveRequests(requestsSave);

        System.out.println("saving responses...");
        saveResponses(responsesSave);

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
