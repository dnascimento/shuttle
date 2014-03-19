package pt.inesc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import pt.inesc.manager.core.DependencyGraph;


public class GraphTest {

    @Test
    public void line() {
        DependencyGraph graph = new DependencyGraph();
        // Quero testar uma lista de 3 nos: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 0, 1, 1, 2, 2, 3 };
        graph.updateStartEnd(startEndArray);

        HashMap<Long, List<Long>> dependenciesMap = new HashMap<Long, List<Long>>();
        ArrayList<Long> dep = new ArrayList<Long>();
        dep.add((long) 0);
        dependenciesMap.put((long) 1, dep);
        dep = new ArrayList<Long>();
        dep.add((long) 1);
        dependenciesMap.put((long) 2, dep);
        graph.addDependencies(dependenciesMap);

        graph.display();
        System.out.println("Press enter to exit");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // @Test
    // public void simpleGraph() {
    // HashMap<String, String> hashGraph = new HashMap<String, String>();
    // hashGraph.put("a", "b");
    // hashGraph.put("b", "c");
    // hashGraph.put("c", "d");
    // hashGraph.put("d", "a");
    // // Training
    // Graph<String, String> g = new SparseMultigraph<String, String>();
    // for (Entry<String, String> entry : hashGraph.entrySet()) {
    // g.addVertex(entry.getKey());
    // g.addVertex(entry.getValue());
    // g.addEdge(entry.getKey() + entry.getValue(),
    // entry.getKey(),
    // entry.getValue(),
    // EdgeType.DIRECTED);
    // }
    //
    // ShowGraph show = new ShowGraph(g);
    // show.display();
    // System.out.println("Press enter to exit");
    // try {
    // System.in.read();
    // } catch (IOException e) {
    // e.printStackTrace();
    // }
    // }
    //
}
