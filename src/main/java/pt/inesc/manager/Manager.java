package pt.inesc.manager;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.manager.graph.DependencyGraph;
import pt.inesc.manager.groupCom.GroupCom;
import pt.inesc.manager.groupCom.GroupCom.NodeGroup;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ExecList;
import undo.proto.FromManagerProto.ProxyMsg;
import undo.proto.FromManagerProto.ToDataNode;
import voldemort.client.ClientConfig;
import voldemort.client.protocol.admin.AdminClient;
import voldemort.client.protocol.admin.AdminClientConfig;
import voldemort.cluster.Node;

import com.google.common.collect.Lists;

public class Manager {
    private final Logger log = LogManager.getLogger(Manager.class.getName());

    public static final InetSocketAddress MANAGER_ADDR = new InetSocketAddress("localhost", 11000);
    public short lastBranch = 0;
    public GroupCom group = new GroupCom();

    public Object ackWaiter = new Object();
    private static final String GRAPH_FILE = "graph.obj";
    private static DependencyGraph graph;
    private static ServiceManager service;


    public static void main(String[] args) throws IOException {
        DOMConfigurator.configure("log4j.xml");
        Manager manager = new Manager();
        Interface menu = new Interface(manager);
        menu.start();
    }

    public Manager() throws IOException {
        graph = new DependencyGraph();
        service = new ServiceManager(this);
        service.start();
    }

    public void resetGraph() {
        graph.reset();
    }

    public void loadGraph() throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(GRAPH_FILE);
        ObjectInputStream ois = new ObjectInputStream(fin);
        graph = (DependencyGraph) ois.readObject();
        ois.close();
    }

    public void saveGraph() throws IOException {
        FileOutputStream fout = new FileOutputStream(GRAPH_FILE);
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

    public void redoFromRoot(List<Long> roots, short branch) throws IOException, InterruptedException {
        for (Long root : roots) {
            List<Long> list = graph.getExecutionList(root);
            try {
                ExecList msg = FromManagerProto.ExecList.newBuilder()
                                                        .addAllRid(list)
                                                        .setBranch(branch)
                                                        .setStart(false)
                                                        .build();
                group.broadcast(msg, NodeGroup.REDO);
            } catch (IOException e) {
                log.error(e);
            }
        }
        ExecList startMsg = FromManagerProto.ExecList.newBuilder().setBranch(branch).setStart(true).build();
        group.broadcast(startMsg, NodeGroup.REDO);
        // wait for ack
        synchronized (ackWaiter) {
            ackWaiter.wait();
        }
    }

    public DependencyGraph getGraph() {
        return graph;
    }

    public void setGraph(DependencyGraph g) {
        graph = g;
    }


    public void setNewSnapshotInstant(long newRid) throws UnknownHostException, IOException {
        // TODO usar sync e receber os acks
        ToDataNode msg = FromManagerProto.ToDataNode.newBuilder().setSeasonId(newRid).build();
        group.broadcast(msg, NodeGroup.DATABASE);
    }

    /**
     * If branch == -1, keep the same
     * 
     * @param branch
     * @param restrain
     * @throws IOException
     */
    public void setProxyBranchAndRestrain(short branch, boolean restrain) throws IOException {
        ProxyMsg msg = FromManagerProto.ProxyMsg.newBuilder().setBranch(branch).setRestrain(restrain).build();
        group.unicast(msg, NodeGroup.PROXY);
    }

    public void proxyTimeTravel(long time) throws IOException {
        ProxyMsg msg = FromManagerProto.ProxyMsg.newBuilder().setTimeTravel(time).build();
        group.unicast(msg, NodeGroup.PROXY);
    }


    /**
     * Performs the REDO
     * 
     * @param branch: Branch to store the redo
     * @throws IOException
     * @throws InterruptedException
     */
    public void redoProcedure(short branch) throws IOException, InterruptedException {
        // enable restrain
        setProxyBranchAndRestrain((short) -1, true);
        // get requests to send
        List<Long> roots = graph.getRoots();
        if (roots.size() == 0) {
            log.error("No roots available");
            return;
        }
        // send to redo
        redoFromRoot(roots, branch);
        // disable retrain
        setProxyBranchAndRestrain(branch, false);
        setNewBranchInDatabaseNodes(branch);
    }

    void resetDataNodesDependenices() throws IOException {
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setResetDependencies(true).build(), NodeGroup.DATABASE);
    }

    void setNewBranchInDatabaseNodes(short branch) throws IOException {
        group.broadcast(FromManagerProto.ToDataNode.newBuilder().setUnlockNewBranch(branch).build(), NodeGroup.DATABASE);
    }

    void cleanVoldemort() {
        // TODO multiple nodes
        String voldemortUrl = "tcp://localhost:6666";
        List<String> voldemortStores;

        voldemortStores = new ArrayList<String>(Arrays.asList("test",
                                                              "questionStore",
                                                              "answerStore",
                                                              "commentStore",
                                                              "index"));

        AdminClient adminClient = new AdminClient(voldemortUrl, new AdminClientConfig(), new ClientConfig());

        List<Integer> nodeIds = Lists.newArrayList();
        for (Node node : adminClient.getAdminClientCluster().getNodes()) {
            nodeIds.add(node.getId());
        }
        for (String storeName : voldemortStores) {
            for (Integer currentNodeId : nodeIds) {
                log.info("Truncating " + storeName + " on node " + currentNodeId);
                adminClient.storeMntOps.truncate(currentNodeId, storeName);
            }
        }
    }
}
