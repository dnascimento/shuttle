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
            long[] requestsToExecute = new long[] { 1395828275566L, -1, 1395828275566L,
                    -1 }; // TODO
            // TODO Fix to socket interface
            threadPool.execute(new RedoWorker(requestsToExecute, "localhost", 8080));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        RedoScheduler boss = new RedoScheduler();
        boss.run();
    }

}
