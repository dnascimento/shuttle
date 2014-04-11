package pt.inesc.manager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.graph.DependencyGraph;
import voldemort.undoTracker.proto.ToManagerProto;
import voldemort.undoTracker.proto.ToManagerProto.StartEndEntry;
import voldemort.undoTracker.proto.ToManagerProto.StartEndMsg;
import voldemort.undoTracker.proto.ToManagerProto.TrackEntry;
import voldemort.undoTracker.proto.ToManagerProto.TrackMsg;

// Retrieves the requests from database
public class ServiceHandler extends
        Thread {
    private final Manager manager;
    private final ServerSocket serverSocket;
    private final Logger log = LogManager.getLogger("ServiceHandler");

    // Only for local tests
    public ServiceHandler(DependencyGraph graph) throws IOException {
        super();
        manager = new Manager();
        manager.setGraph(graph);
        serverSocket = null;
    }

    public ServiceHandler(Manager manager, InetSocketAddress managerServicePort) throws IOException {
        super();
        this.manager = manager;
        serverSocket = new ServerSocket();
        serverSocket.bind(managerServicePort);
    }

    @Override
    public void run() {
        // TODO Converter para asnyc
        while (true) {
            try {
                log.info("Service Database listening...");
                Socket newSocket = serverSocket.accept();
                receive(newSocket);
            } catch (IOException e) {
                e.printStackTrace();
                // TODO logger
            }
        }
    }

    private void receive(Socket socket) throws IOException {
        log.info("New data from Database Nodes");
        ToManagerProto.MsgToManager proto = ToManagerProto.MsgToManager.parseFrom(socket.getInputStream());
        if (proto.getTrackMsg() != null) {
            TrackMsg m1 = proto.getTrackMsg();
            newList(m1.getEntryList());
        }
        if (proto.getStartEndMsg() != null) {
            StartEndMsg m2 = proto.getStartEndMsg();
            updateStartEnd(m2.getMsgList());
        }
        if (proto.getTrackMsgFromClient() != null) {
            TrackMsg m3 = proto.getTrackMsgFromClient();
            clientDependencies(m3.getEntryList());
        }
    }



    private void updateStartEnd(List<StartEndEntry> entryList) {
        for (StartEndEntry entry : entryList) {
            manager.getGraph().updateStartEnd(entry.getStart(), entry.getEnd());
        }

    }

    public void newList(List<TrackEntry> list) {
        log.info(list);
        log.info("--------------");
        for (TrackEntry entry : list) {
            manager.getGraph().addDependencies(entry.getRid(), entry.getDependencyList());
        }
    }

    private void clientDependencies(List<TrackEntry> list) {
        log.info("------ Client Side dependency--------");
        log.info(list);
        log.info("--------------");
        // TODO insert into graph and compare
    }


}
