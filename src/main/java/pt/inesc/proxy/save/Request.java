package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class Request {
    public ByteBuffer data;
    public long rid;



    public Request(ByteBuffer data, long rid) {
        super();
        this.data = data;
        this.rid = rid;
    }




    public Request() {

    }




}
