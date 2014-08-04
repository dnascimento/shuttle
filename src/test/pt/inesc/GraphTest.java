/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import pt.inesc.manager.graph.DepGraphDoubleLinked;


public class GraphTest {
    DepGraphDoubleLinked graph;
    long[] rootArray;

    /** Graphs strategies */
    public void abcdIndependentCycle() {
        graph = new DepGraphDoubleLinked();
        // List with 4 nodes: A:0-1; B:1-2; C:2-3; com A->B->C->D D->B
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L, 4L);
        graph.addDependencies(3L, 2L);
        graph.addDependencies(4L, 3L);
        rootArray = new long[] { 1 };
    }



    public void abcIndependentLine() {
        graph = new DepGraphDoubleLinked();
        // List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L);
        graph.addDependencies(3L, 2L);
        rootArray = new long[] { 1 };
    }

    public void parallelLines() {
        graph = new DepGraphDoubleLinked();
        // 2x List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 4, 3, 6, 5, 7, 2, 5, 4, 7, 6, 8 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(3L, 1L);
        graph.addDependencies(5L, 3L);
        graph.addDependencies(4L, 2L);
        graph.addDependencies(6L, 4L);
        rootArray = new long[] { 1, 2 };
    }


    // /////////// TESTS //////////////////

    @org.junit.Before
    public void initTest() {
        abcdIndependentCycle();
    }

    @Test
    public void startBeforeEnd() throws Exception {
        graph = new DepGraphDoubleLinked();
        graph.addDependencies(1L);
        graph.addDependencies(3L, 1L);
        graph.addDependencies(7L, 1L);
        graph.updateStartEnd(1L, 2L);
        graph.updateStartEnd(3L, 10L);
        graph.updateStartEnd(7L, 9L);
        graph.restoreCounters();
        assertEquals(Arrays.asList(1L, -1L, 3L, 7L, -1L), graph.getExecutionList(1L, 0));

        // the request 1 executes until 5, so request 3 executes in parallel.
        graph.updateStartEnd(1L, 5L);
        graph.restoreCounters();
        assertEquals(Arrays.asList(1L, 3L, -1L, 7L, -1L), graph.getExecutionList(1L, 0));

        // new requests: 4-17 and 9-20
        graph.addDependencies(4L, 3L);
        graph.addDependencies(9L, 3L);
        graph.updateStartEnd(4L, 17L);
        graph.updateStartEnd(9L, 19L);
        graph.restoreCounters();
        assertEquals(Arrays.asList(1L, 3L, 4L, 9L, -1L, 7L, -1L), graph.getExecutionList(1L, 0));

        graph.addDependencies(9L, 7L);
        graph.restoreCounters();
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L, 7L, -1L, 9L, -1L), graph.getExecutionList(1L, 0));

        // add new root
        graph.addDependencies(20L);
        List<List<Long>> execList = graph.getExecutionList(0);
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L, 7L, -1L, 9L, -1L), execList.get(0));
        assertEquals(Arrays.asList(20L, -1L), execList.get(1));

        // add link between 2 roots
        graph.addDependencies(7L, 20L);
        execList = graph.getExecutionList(0);
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L), execList.get(0));
        assertEquals(Arrays.asList(20L, -1L, 7L, -1L, 9L, -1L), execList.get(1));

    }
}
