package pt.inesc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.net.httpserver.HttpServer;

public class UndoServer {
    private int clientSidePort;
    private String destinationHost;
    private int destinationPort;

    private static Logger log = LogManager.getLogger("UndoServer");

    public static void main(String[] args) throws IOException {
        log = LogManager.getLogger("UndoServer");
        UndoServer server = new UndoServer(9000, "localhost", 8080);
        server.run();
    }

    public UndoServer(int clientPort, String destinationHost, int destinationPort) {
        super();
        clientSidePort = clientPort;
        this.destinationHost = destinationHost;
        this.destinationPort = destinationPort;
    }


    public void run() throws IOException {
        // Create socket to real server
        InetAddress inteAddress;
        inteAddress = InetAddress.getByName(destinationHost);
        SocketAddress destinationAddress = new InetSocketAddress(inteAddress,
                destinationPort);

        HttpServer server = HttpServer.create(new InetSocketAddress("", clientSidePort),
                                              clientSidePort);

        server.createContext("/", new UndoHandler(destinationAddress));
        server.setExecutor(null); // creates a default executor
        server.start();
    }

}
