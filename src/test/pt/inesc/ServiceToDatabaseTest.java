package pt.inesc;

import java.io.IOException;

import org.junit.Test;

import pt.inesc.manager.ServiceHandler;
import pt.inesc.manager.graph.DependencyGraph;
import voldemort.undoTracker.proto.ToManagerProto.TrackEntry;
import voldemort.undoTracker.proto.ToManagerProto.TrackMsg;

public class ServiceToDatabaseTest {
    ServiceHandler service;
    DependencyGraph graph;

    @org.junit.Before
    public void init() throws IOException {
        graph = new DependencyGraph();
        service = new ServiceHandler(graph);
    }

    @Test
    public void test() {
        TrackMsg.Builder builder = TrackMsg.newBuilder();
        TrackEntry entry1 = TrackEntry.newBuilder().setRid(2).addDependency(1).build();
        builder.addEntry(entry1);
        service.newList(builder.build().getEntryList());
        graph.display();
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
