package pt.inesc;

import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

import pt.inesc.proxy.save.CassandraClient;

public class CassandraTest {

    @Test
    public void Test() {
        CassandraClient client = new CassandraClient();
        byte[] data = "darionascimento".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        client.putRequest(2, buffer);
        buffer = client.getRequest(2);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result, 0, result.length);
        assertTrue(Arrays.equals(data, result));
    }
}
