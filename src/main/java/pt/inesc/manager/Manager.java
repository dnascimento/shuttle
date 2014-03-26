package pt.inesc.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import pt.inesc.manager.graph.DependencyGraph;

public class Manager {
    InetSocketAddress databasePortAddress = new InetSocketAddress("localhost", 9090);
    DependencyGraph graph;
    RedoManager redoService;
    ServiceToDatabase serviceToDatabase;

    public Manager() throws IOException {
        graph = new DependencyGraph();
        serviceToDatabase = new ServiceToDatabase(graph, databasePortAddress);
        redoService = new RedoManager();
        serviceToDatabase.start();
    }

    public void showGraph() {
        graph.refreshableDisplay();
    }

    public void redoFromRoot(long root) {
        List<Long> list = graph.getExecutionList(root);
        try {
            redoService.executeList(list);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}
