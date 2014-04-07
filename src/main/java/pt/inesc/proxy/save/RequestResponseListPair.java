package pt.inesc.proxy.save;

import java.util.LinkedList;

public class RequestResponseListPair {
    LinkedList<Request> requests;
    LinkedList<Response> responses;


    public RequestResponseListPair(LinkedList<Request> requests, LinkedList<Response> responses) {
        super();
        this.requests = requests;
        this.responses = responses;
    }


    public LinkedList<Request> getRequests() {
        return requests;
    }


    public LinkedList<Response> getResponses() {
        return responses;
    }


}
