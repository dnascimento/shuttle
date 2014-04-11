package pt.inesc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import pt.inesc.manager.graph.DependencyGraph;


public class GraphTest {
    DependencyGraph graph;
    long[] rootArray;

    /** Graphs strategies */
    public void abcdIndependentCycle() {
        graph = new DependencyGraph();
        // List with 4 nodes: A:0-1; B:1-2; C:2-3; com A->B->C->D D->B
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies((long) 2, toList(1, 4));
        graph.addDependencies((long) 3, toList(2));
        graph.addDependencies((long) 4, toList(3));
        rootArray = new long[] { 1 };
    }

    private List<Long> toList(int... args) {
        List<Long> list = new ArrayList<Long>();
        for (int a : args) {
            list.add((long) a);
        }
        return list;
    }

    public void abcIndependentLine() {
        graph = new DependencyGraph();
        // List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies((long) 2, toList(1));
        graph.addDependencies((long) 3, toList(2));
        rootArray = new long[] { 1 };
    }

    public void parallelLines() {
        graph = new DependencyGraph();
        // 2x List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 4, 3, 6, 5, 7, 2, 5, 4, 7, 6, 8 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies((long) 3, toList(1));
        graph.addDependencies((long) 5, toList(3));
        graph.addDependencies((long) 4, toList(2));
        graph.addDependencies((long) 6, toList(4));
        rootArray = new long[] { 1, 2 };
    }


    // /////////// TESTS //////////////////

    @org.junit.Before
    public void initTest() {
        abcdIndependentCycle();
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
        display();
    }

    private void display() {
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
