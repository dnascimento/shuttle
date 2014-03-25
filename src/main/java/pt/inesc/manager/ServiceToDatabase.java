package pt.inesc.manager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import pt.inesc.manager.graph.DependencyGraph;
import voldemort.undoTracker.proto.OpProto;
import voldemort.undoTracker.proto.OpProto.TrackEntry;

// Retrieves the requests from database
public class ServiceToDatabase extends
        Thread {
    private final DependencyGraph graph;
    private final ServerSocket serverSocket;

    // Only for localtests
    public ServiceToDatabase(DependencyGraph graph) {
        super();
        this.graph = graph;
        serverSocket = null;
    }

    public ServiceToDatabase(DependencyGraph graph, InetSocketAddress databasePortAddress) throws IOException {
        super();
        this.graph = graph;
        serverSocket = new ServerSocket();
        serverSocket.bind(databasePortAddress);

    }

    @Override
    public void run() {
        // TODO Converter para uma socket pool com handlers e asnyc
        while (true) {
            try {
                System.out.println("Service Database listening...");
                Socket newSocket = serverSocket.accept();
                receive(newSocket);
            } catch (IOException e) {
                e.printStackTrace();
                // TODO logger
            }
        }
    }

    private void receive(Socket socket) throws IOException {
        System.out.println("New data from Database Nodes");
        InputStream stream = socket.getInputStream();
        OpProto.TrackList list = OpProto.TrackList.parseFrom(stream);
        newList(list);
    }

    public void newList(OpProto.TrackList list) {
        System.out.println(list);
        System.out.println("--------------");
        for (TrackEntry entry : list.getEntryList()) {
            graph.addDependencies(entry.getRid(), entry.getDependenciesList());
        }
    }
}
