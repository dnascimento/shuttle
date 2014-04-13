package pt.inesc.proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;
import undo.proto.FromManagerProto;
import undo.proto.FromManagerProto.ProxyMsg;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.NodeRegistryMsg.NodeGroup;

public class ServiceProxy extends
        Thread {
    private final ServerSocket serverSocket;
    private final Logger log = LogManager.getLogger(ServiceProxy.class.getName());
    private final Proxy proxy;


    public ServiceProxy(Proxy p) throws IOException {
        this.proxy = p;
        serverSocket = new ServerSocket(Proxy.MY_PORT);
        registryToManger();
    }

    @Override
    public void run() {
        log.info("Proxy Service listening...");
        // TODO Converter para asnyc
        while (true) {
            try {
                Socket newSocket = serverSocket.accept();
                receive(newSocket);
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
                                                              .setPort(Proxy.MY_PORT)
                                                              .setGroup(NodeGroup.PROXY)
                                                              .build();

            ToManagerProto.MsgToManager.newBuilder().setNodeRegistry(c).build().writeTo(s.getOutputStream());
            s.close();
        } catch (IOException e) {
            log.error("Manager not available");
        }
    }

    private void receive(Socket socket) throws IOException {
        log.debug("New service command");
        ProxyMsg msg = FromManagerProto.ProxyMsg.parseFrom(socket.getInputStream());
        if (msg.hasTimeTravel()) {
            // time travel
            proxy.timeTravel(msg.getTimeTravel());
        }
        if (msg.hasBranch() || msg.hasRestrain()) {
            short branch = (short) msg.getBranch();
            boolean restrain = msg.getRestrain();
            proxy.setBranchAndRestrain(branch, restrain);
        }
    }


}
