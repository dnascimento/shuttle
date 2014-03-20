package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class Request {
    public ByteBuffer data;
    public long start;

    public Request(ByteBuffer data, long start) {
        super();
        this.data = data;
        this.start = start;
    }
}
