package pt.inesc.proxy.clientSide;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Proxy
        implements Runnable {
    private static Logger logger = LogManager.getLogger("Proxy");

    private final int localPort;
    private final String remoteHost;
    private final int remotePort;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(40);
    private ServerSocket serverSocket;
    private boolean isStopped = false;

    private Socket clientSocket;



    public Proxy(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }


    @Override
    public void run() {
        logger.info("Proxying *:" + localPort + " to " + remoteHost + ':' + remotePort
                + " ...");

        File requestsFolder = new File("./requests");
        requestsFolder.deleteOnExit();
        requestsFolder.mkdir();

        openServerSocket();
        while (!isStopped) {
            try {
                clientSocket = serverSocket.accept();
                threadPool.execute(new ProxyWorker(clientSocket, remoteHost, remotePort));
            } catch (IOException e) {
                if (isStopped) {
                    logger.info("Socket Stopped");
                    return;
                }
                logger.error("Error Accept Client: " + e.getMessage());
            }
        }
    }


    private void openServerSocket() {
        try {
            serverSocket = new ServerSocket(localPort);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }


    public static void main(String[] args) throws Exception {
        // Parse command line options.
        int localPort = 9000;
        String remoteHost = "localhost";
        int remotePort = 8080;

        new Proxy(localPort, remoteHost, remotePort).run();
    }
}
