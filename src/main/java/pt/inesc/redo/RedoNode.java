package pt.inesc.redo;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import pt.inesc.manager.Manager;
import pt.inesc.redo.core.RedoWorker;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ExecList;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.AckMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg.NodeGroup;


/**
 * Pool of channels ready to connect to Real Server and get the data Then return the data
 * to original thread and continue
 */
public class RedoNode extends
        Thread {
    public static final int MY_PORT = 11500;
    public static final InetSocketAddress TARGET_LOAD_BALANCER_ADDR = new InetSocketAddress("localhost", 8080);
    private final static Logger log = LogManager.getLogger(RedoNode.class.getName());
    protected ExecutorService threadPool = Executors.newFixedThreadPool(1);

    private ArrayList<RedoWorker> workers = new ArrayList<RedoWorker>();
    ServerSocket myServerSocket;

    public static void main(String[] args) throws IOException {
        DOMConfigurator.configure("log4j.xml");
        new RedoNode().start();
    }

    public RedoNode() throws IOException {
        try {
            myServerSocket = new ServerSocket(MY_PORT);
            registryToManger();
        } catch (BindException e) {
            log.error("Redo Node already running in same port...");
        }
    }



    @Override
    public void run() {
        log.info("Redo node is listening...");
        while (true) {
            Socket newSocket;
            try {
                newSocket = myServerSocket.accept();
                newConnection(newSocket);
            } catch (IOException e) {
                log.error(e);
            }
        }

    }






    private void registryToManger() {
        Socket s = new Socket();
        try {
            s.connect(Manager.MANAGER_ADDR);
            NodeRegistryMsg c = ToManagerProto.NodeRegistryMsg.newBuilder()
                                                              .setHostname("localhost")
                                                              .setPort(MY_PORT)
                                                              .setGroup(NodeGroup.REDO_NODE)
                                                              .build();
            ToManagerProto.MsgToManager.newBuilder().setNodeRegistry(c).build().writeTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }

    public void startOrder() {
        for (RedoWorker worker : workers) {
            threadPool.execute(worker);
        }
        workers = new ArrayList<RedoWorker>();
    }

    public void newRequest(List<Long> execList, short branch) throws IOException {
        workers.add(new RedoWorker(execList, TARGET_LOAD_BALANCER_ADDR, branch));
    }

    private void newConnection(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        FromManagerProto.ExecList list = ExecList.parseFrom(stream);
        List<Long> execList = list.getRidList();
        if (execList.size() != 0) {
            newRequest(execList, (short) list.getBranch());
        }
        if (list.getStart()) {
            startOrder();
        }
    }

    public static void sendAck() {
        Socket s = new Socket();
        try {
            s.connect(Manager.MANAGER_ADDR);
            AckMsg c = ToManagerProto.AckMsg.newBuilder().setHostname("localhost").setPort(MY_PORT).build();
            ToManagerProto.MsgToManager.newBuilder().setAck(c).build().writeTo(s.getOutputStream());

            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }

    }
}
