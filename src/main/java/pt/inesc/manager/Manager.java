package pt.inesc.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import pt.inesc.manager.graph.DependencyGraph;
import pt.inesc.redoNode.RedoScheduler;

public class Manager {
    InetSocketAddress databasePortAddress = new InetSocketAddress("localhost", 9090);
    DependencyGraph graph;
    RedoManager redoService;
    ServiceToDatabase serviceToDatabase;

    public static void main(String[] args) throws IOException {
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }

    public Manager() throws IOException {
        graph = new DependencyGraph();
        serviceToDatabase = new ServiceToDatabase(graph, databasePortAddress);
        // TODO redo manager will be a separated thread due to nodes registry
        redoService = new RedoManager();
        serviceToDatabase.start();
    }

    public void showGraph() {
        graph.display();
    }

    public List<Long> getRoots() {
        return graph.getRoots();
    }

    public void redoFromRoot(long root) {
        List<Long> list = graph.getExecutionList(root);
        try {
            new RedoScheduler().newRequest(list);
            // TODO test with socket
            // redoService.executeList(list);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}
