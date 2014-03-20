package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class Response {
    public ByteBuffer data;
    public long start;
    public long end;

    public Response(ByteBuffer data, long start, long end) {
        super();
        this.data = data;
        this.start = start;
        this.end = end;
    }

}
