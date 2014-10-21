package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class RequestResponsePair {
    public ByteBuffer request;
    public ByteBuffer response;
    public long start;
    public long end;

    public RequestResponsePair(ByteBuffer request, ByteBuffer response, long start, long end) {
        super();
        this.request = request;
        this.response = response;
        this.start = start;
        this.end = end;
    }
}
