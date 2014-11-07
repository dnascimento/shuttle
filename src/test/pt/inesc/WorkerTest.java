package pt.inesc;

import java.net.InetSocketAddress;
import java.util.Arrays;

import org.junit.Test;

import pt.inesc.replay.core.ReplayWorker;

public class WorkerTest {


    @Test
    public void workerTest() throws Exception {
        ReplayWorker worker = new ReplayWorker(Arrays.asList(1415374869864001L), new InetSocketAddress("localhost", 8080), (short) 1);
        worker.start();
        worker.join();
    }
}
