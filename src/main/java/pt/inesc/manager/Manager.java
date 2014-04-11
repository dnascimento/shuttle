package pt.inesc.manager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import pt.inesc.manager.graph.DependencyGraph;
import pt.inesc.redoNode.RedoScheduler;
import voldemort.undoTracker.proto.FromManagerProto;

public class Manager {
    private static final String GRAPH_STORE = "graph.obj";
    InetSocketAddress managerServicePort = new InetSocketAddress("localhost", 9090);
    DependencyGraph graph;
    RedoManager redoService;
    ServiceHandler serviceToDatabase;

    public static void main(String[] args) throws IOException {
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }

    public Manager() throws IOException {
        graph = loadGraph();
        serviceToDatabase = new ServiceHandler(this, managerServicePort);
        // TODO redo manager will be a separated thread due to nodes registry
        redoService = new RedoManager();
        serviceToDatabase.start();
    }

    public void resetGraph() {
        graph.reset();
    }

    private DependencyGraph loadGraph() {
        DependencyGraph graph;
        try {
            FileInputStream fin = new FileInputStream(GRAPH_STORE);
            ObjectInputStream ois = new ObjectInputStream(fin);
            graph = (DependencyGraph) ois.readObject();
            ois.close();
            return graph;
        } catch (FileNotFoundException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new DependencyGraph();

    }

    public void saveGraph() throws IOException {
        FileOutputStream fout = new FileOutputStream(GRAPH_STORE);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(graph);
        oos.close();
    }

    public void showGraph() {
        graph.display();
    }

    public List<Long> getRoots() {
        return graph.getRoots();
    }

    public void redoFromRoot(long[] roots) {
        List<Long> list = graph.getExecutionList(roots[0]);
        try {
            new RedoScheduler().newRequest(list);
            // TODO test with socket
            // redoService.executeList(list);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public DependencyGraph getGraph() {
        return graph;
    }

    public void setGraph(DependencyGraph g) {
        graph = g;
    }


    public void setNewSnapshotRID(long newRid) throws UnknownHostException, IOException {
        // TODO converter para assync e registo de mais dbs, receber os acks
        Socket s = new Socket("localhost", 9500);
        FromManagerProto.Snapshot.newBuilder().setSeasonId(newRid).build().writeTo(s.getOutputStream());
        s.close();
    }
}
