package pt.inesc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;
import pt.inesc.replay.core.AssyncExecutionArray;

public class AsyncExecutionArrayTest {
    int N_REQUESTS = 150000;
    AssyncExecutionArray array;

    class CassandraClientMockup extends
            CassandraClient {

        @Override
        public void getRequests(ArrayList<Request> list, int fetchPosition, int limit) {
            for (int i = fetchPosition; i < limit; i++) {
                list.get(i).data = ByteBuffer.allocate(10);
            }
        }
    }

    @Test
    public void test() {
        List<Long> list = new ArrayList<Long>(N_REQUESTS);
        for (int i = 0; i < N_REQUESTS; i++) {
            list.add((long) i);
        }

        array = new AssyncExecutionArray(list, new CassandraClientMockup(), (short) 0);
        while (array.hasNext()) {
            Request r = array.next();
            org.junit.Assert.assertNotNull(r.data);
        }

    }
}
