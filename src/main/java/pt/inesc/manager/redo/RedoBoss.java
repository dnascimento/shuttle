package pt.inesc.manager.redo;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to origal thread and continue
 */
public class RedoBoss
        implements Runnable {

    protected ExecutorService threadPool = Executors.newFixedThreadPool(10);


    public void run() {
        try {
            threadPool.execute(new RedoWorker(0, 90000, "localhost", 8080));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        RedoBoss boss = new RedoBoss();
        boss.run();
    }

}
