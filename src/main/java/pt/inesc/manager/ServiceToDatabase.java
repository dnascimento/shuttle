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
    private final Manager manager;
    private final ServerSocket serverSocket;

    // Only for local tests
    public ServiceToDatabase(DependencyGraph graph) throws IOException {
        super();
        manager = new Manager();
        manager.setGraph(graph);
        serverSocket = null;
    }

    public ServiceToDatabase(Manager manager, InetSocketAddress databasePortAddress) throws IOException {
        super();
        this.manager = manager;
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
            manager.getGraph().addDependencies(entry.getRid(), entry.getDependenciesList());
        }
    }
}
