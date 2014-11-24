package pt.inesc;

import java.net.InetSocketAddress;
import java.util.Arrays;

import org.junit.Test;

import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.replay.core.ReplayWorker;

public class WorkerTest {


    @Test
    public void workerTest() throws Exception {
        CassandraClient cassandra = new CassandraClient();
        // TODO this cassandra client shall be a stub and be used to test constant
        ReplayWorker worker = new ReplayWorker(Arrays.asList(1415374869864001L), new InetSocketAddress("localhost", 8080), (short) 1,
                cassandra);
        worker.start();
        worker.join();
    }
}
