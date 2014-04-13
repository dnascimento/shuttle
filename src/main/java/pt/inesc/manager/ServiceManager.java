package pt.inesc.manager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.graph.DependencyGraph;
import pt.inesc.manager.groupCom.GroupCom.NodeGroup;
import undo.proto.ToManagerProto;
import undo.proto.ToManagerProto.NodeRegistryMsg;
import undo.proto.ToManagerProto.StartEndEntry;
import undo.proto.ToManagerProto.StartEndMsg;
import undo.proto.ToManagerProto.TrackEntry;
import undo.proto.ToManagerProto.TrackMsg;

// Retrieves the requests from database
public class ServiceManager extends
        Thread {
    private final Manager manager;
    private final ServerSocket serverSocket;
    private final Logger log = LogManager.getLogger(ServiceManager.class.getName());

    // Only for local tests
    public ServiceManager(DependencyGraph graph) throws IOException {
        super();
        manager = new Manager();
        manager.setGraph(graph);
        serverSocket = null;
    }

    public ServiceManager(Manager manager) throws IOException {
        super();
        this.manager = manager;
        serverSocket = new ServerSocket();
        serverSocket.bind(Manager.MANAGER_ADDR);
    }

    @Override
    public void run() {
        log.info("Manager Service is listening...");
        while (true) {
            try {
                Socket newSocket = serverSocket.accept();
                receive(newSocket);
            } catch (IOException e) {
                log.error(e);
            }
        }
    }

    private void receive(Socket socket) throws IOException {
        ToManagerProto.MsgToManager proto = ToManagerProto.MsgToManager.parseFrom(socket.getInputStream());
        if (proto.hasTrackMsg()) {
            log.info("New msg: TrackMsg");
            TrackMsg m1 = proto.getTrackMsg();
            newList(m1.getEntryList());
        }
        if (proto.hasStartEndMsg()) {
            StartEndMsg m2 = proto.getStartEndMsg();
            updateStartEnd(m2.getMsgList());
        }
        if (proto.hasTrackMsgFromClient()) {
            log.info("New msg: TrackMsg from Client Lib");
            TrackMsg m3 = proto.getTrackMsgFromClient();
            clientDependencies(m3.getEntryList());
        }
        if (proto.hasNodeRegistry()) {
            log.info("New msg: has Node Registry");
            NodeRegistryMsg msg = proto.getNodeRegistry();
            NodeGroup group;
            switch (msg.getGroup()) {
            case DB_NODE:
                group = NodeGroup.DATABASE;
                break;
            case PROXY:
                group = NodeGroup.PROXY;
                break;
            case REDO_NODE:
                group = NodeGroup.REDO;
                break;
            default:
                log.error("Unknown new node group");
                return;
            }
            manager.group.newNode(msg.getHostname(), msg.getPort(), group);
        }
        if (proto.hasAck()) {
            // TODO multiple redo nodes, o wait pode nao estar locked ainda
            synchronized (manager.ackWaiter) {
                manager.ackWaiter.notify();
            }
        }
    }



    private void updateStartEnd(List<StartEndEntry> entryList) {
        // TODO
        // for (StartEndEntry entry : entryList) {
        // manager.getGraph().updateStartEnd(entry.getStart(), entry.getEnd());
        // }
    }

    public void newList(List<TrackEntry> list) {
        log.info("------Dep List (size: " + list.size() + ")--------");
        log.info(list);
        log.info("--------------");
        for (TrackEntry entry : list) {
            manager.getGraph().addDependencies(entry.getRid(), entry.getDependencyList());
        }
    }

    private void clientDependencies(List<TrackEntry> list) {
        log.info("------ Client Side dependency--------");
        log.info(depListToString(list));
        log.info("--------------");
        // TODO insert into graph and compare
    }

    /**
     * Display dependency list properly
     * 
     * @param list
     * @return
     */
    private String depListToString(List<TrackEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (TrackEntry entry : list) {
            sb.append("[");
            sb.append(entry.getRid());
            sb.append("<-");
            for (Long l : entry.getDependencyList()) {
                sb.append(l);
                sb.append(",");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }
}
