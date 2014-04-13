package pt.inesc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;

public class CassandraTest {

    @Test
    public void Test() {
        long rid = 2;
        CassandraClient client = new CassandraClient();
        byte[] string = "darionascimento".getBytes();
        ByteBuffer data = ByteBuffer.wrap(string);
        Request pack = new Request(data, rid);
        client.putRequest(pack);
        ByteBuffer result = client.getRequest(rid);
        assertEquals(result.limit() - result.position(), data.capacity());
        boolean equals = true;

        while (result.hasRemaining()) {
            if (result.get() != data.get()) {
                equals = false;
                break;
            }
        }
        assertTrue(equals);
    }
}
