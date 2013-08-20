package pt.inesc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UndoServer
        implements Runnable {
    private int clientPort;
    private String destinationHost;
    private int destinationPort;

    private Boolean isStopped = false;
    private ServerSocket serverSocket = null;

    private static Logger log = LogManager.getLogger("UndoServer");

    public static void main(String[] args) {
        log = LogManager.getLogger("UndoServer");
        UndoServer server = new UndoServer(9000, "localhost", 8080);
        server.run();
    }

    public UndoServer(int clientPort, String destinationHost, int destinationPort) {
        super();
        this.clientPort = clientPort;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
    }


    @Override
    public void run() {
        openServerSocket();
        while (!isStopped()) {
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if (isStopped()) {
                    System.out.println("Server Stopped.");
                    return;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            new Thread(new WorkerRunnable(clientSocket, destinationHost, destinationPort)).start();
        }
    }

    private synchronized boolean isStopped() {
        return isStopped;
    }




    public synchronized void stop() {
        isStopped = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing server", e);
        }
    }



    private void openServerSocket() {
        try {
            serverSocket = new ServerSocket(clientPort);
            log.info("Proxy started at port: " + clientPort);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open port " + clientPort, e);
        }
    }

}
