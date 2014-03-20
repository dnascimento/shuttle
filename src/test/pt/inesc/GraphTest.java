package pt.inesc;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import pt.inesc.manager.core.DependencyGraph;


public class GraphTest {
    DependencyGraph graph;
    long[] rootArray;

    /** Graphs strategies */
    public void abcdIndependentCycle() {
        graph = new DependencyGraph();
        // List with 4 nodes: A:0-1; B:1-2; C:2-3; com A->B->C->D D->B
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        graph.updateStartEnd(startEndArray);
        graph.addDependencies((long) 2, 1, 4);
        graph.addDependencies((long) 3, (long) 2);
        graph.addDependencies((long) 4, (long) 3);
        rootArray = new long[] { 1 };
    }

    public void abcIndependentLine() {
        graph = new DependencyGraph();
        // List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5 };
        graph.updateStartEnd(startEndArray);
        graph.addDependencies((long) 2, 1);
        graph.addDependencies((long) 3, 2);
        rootArray = new long[] { 1 };
    }

    public void parallelLines() {
        graph = new DependencyGraph();
        // 2x List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 4, 3, 6, 5, 7, 2, 5, 4, 7, 6, 8 };
        graph.updateStartEnd(startEndArray);
        graph.addDependencies((long) 3, 1);
        graph.addDependencies((long) 5, 3);
        graph.addDependencies((long) 4, 2);
        graph.addDependencies((long) 6, 4);
        rootArray = new long[] { 1, 2 };
    }


    // /////////// TESTS //////////////////

    @org.junit.Before
    public void initTest() {
        parallelLines();
    }

    @Test
    public void executionList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Exec list: \n");
        for (long root : rootArray) {
            List<Long> list = graph.getExecutionList(root);
            for (Long l : list) {
                sb.append(l);
                sb.append(" ");
            }
            sb.append("\n");
        }
        System.out.println(sb.toString());
    }

    // @Test
    public void display() {
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
