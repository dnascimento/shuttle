package pt.inesc;

import java.io.IOException;

import org.junit.Test;

import pt.inesc.manager.ServiceToDatabase;
import pt.inesc.manager.graph.DependencyGraph;
import voldemort.undoTracker.proto.OpProto;

public class ServiceToDatabaseTest {
    ServiceToDatabase service;
    DependencyGraph graph;

    @org.junit.Before
    public void init() throws IOException {
        graph = new DependencyGraph();
        service = new ServiceToDatabase(graph);
    }

    @Test
    public void test() {
        OpProto.TrackList.Builder builder = OpProto.TrackList.newBuilder();
        OpProto.TrackEntry entry1 = OpProto.TrackEntry.newBuilder()
                                                      .setRid(2)
                                                      .addDependencies(1)
                                                      .build();
        builder.addEntry(entry1);
        service.newList(builder.build());
        graph.display();
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
