/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

import org.apache.http.HttpRequest;

public class Request {
    public ByteBuffer data;
    public long rid;
    public long end;
    public HttpRequest request;


    public Request(ByteBuffer data, long rid) {
        super();
        this.data = data;
        this.rid = rid;
    }

    public Request(ByteBuffer data, long rid, long end) {
        super();
        this.data = data;
        this.rid = rid;
        this.end = end;
    }



    public Request() {

    }

    public Request(long rid) {
        this.rid = rid;
    }




}
