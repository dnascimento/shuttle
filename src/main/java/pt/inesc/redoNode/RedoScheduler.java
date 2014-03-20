package pt.inesc.redoNode;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to origal thread and continue
 */
public class RedoScheduler
        implements Runnable {

    protected ExecutorService threadPool = Executors.newFixedThreadPool(1);

    public void run() {
        try {
            long[] requestsToExecute = new long[] { 1, 2, 3 }; // TODO fix
            threadPool.execute(new RedoWorker(requestsToExecute, "localhost", 8080));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        RedoScheduler boss = new RedoScheduler();
        boss.run();
    }

}
