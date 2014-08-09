/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.DepGraph;
import pt.inesc.manager.graph.Dependency;
import pt.inesc.manager.graph.DependencyDouble;
import pt.inesc.manager.graph.SelectiveDepGraph;
import pt.inesc.manager.graph.SimpleDepGraph;


public class GraphTest {
    DepGraph graph;
    long[] rootArray;

    /**
     * List with 4 nodes: A:1-3; B:2-4; C:3-5; D:4->7;
     * Test if the list is correct stored on simpleDepGraph
     * B depends from A
     * C depends from B
     * D depends from C
     * 
     * @throws IOException
     */
    @Test
    public void abcdIndependentCycle() throws IOException {
        graph = new SimpleDepGraph();
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L);
        graph.addDependencies(3L, 2L);
        graph.addDependencies(4L, 3L);

        HashMap<Long, Dependency> map = graph.getMap();
        Assert.assertEquals(4, map.size());

        assertEquals(0, map.get(1L).getCountBefore());
        assertEquals(1, map.get(1L).getAfter().size());
        assertTrue(map.get(1L).hasAfter(2L));

        assertEquals(1, map.get(2L).getCountBefore());
        assertEquals(1, map.get(2L).getAfter().size());
        assertTrue(map.get(2L).hasAfter(3L));

        assertEquals(1, map.get(3L).getCountBefore());
        assertEquals(1, map.get(3L).getAfter().size());
        assertTrue(map.get(3L).hasAfter(4L));

        assertEquals(1, map.get(4L).getCountBefore());
        assertEquals(0, map.get(4L).getAfter().size());
    }


    /**
     * List with 4 nodes: A:1-3; B:2-4; C:3-5; D:4->7;
     * Test if the list is correct stored on simpleDepGraph
     * B depends from A
     * C depends from B
     * D depends from C
     * 
     * @throws IOException
     */
    @Test
    public void abcdIndependentCycleDouble() throws IOException {
        graph = new SelectiveDepGraph();
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L);
        graph.addDependencies(3L, 2L);
        graph.addDependencies(4L, 3L);

        HashMap<Long, Dependency> map = graph.getMap();
        Assert.assertEquals(4, map.size());

        assertEquals(0, map.get(1L).getCountBefore());
        assertEquals(0, ((DependencyDouble) map.get(1L)).getBefore().size());
        assertEquals(1, map.get(1L).getAfter().size());
        assertTrue(map.get(1L).hasAfter(2L));

        assertEquals(1, map.get(2L).getCountBefore());
        assertEquals(1, ((DependencyDouble) map.get(2L)).getBefore().size());
        assertEquals(1, map.get(2L).getAfter().size());
        assertTrue(map.get(2L).hasAfter(3L));

        assertEquals(1, map.get(3L).getCountBefore());
        assertEquals(1, ((DependencyDouble) map.get(3L)).getBefore().size());
        assertEquals(1, map.get(3L).getAfter().size());
        assertTrue(map.get(3L).hasAfter(4L));

        assertEquals(1, map.get(4L).getCountBefore());
        assertEquals(1, ((DependencyDouble) map.get(4L)).getBefore().size());
        assertEquals(0, map.get(4L).getAfter().size());
    }



    // /////////////////////////////////////////////
    @Test
    public void abcIndependentLine() throws Exception {
        graph = new SelectiveDepGraph();
        // List with 3 nodes: A:0-1; B:1-2; C:2-3; com A->B->C
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L);
        graph.addDependencies(3L, 2L);
        ArrayList<List<Long>> execList = new ArrayList<List<Long>>();
        execList.add(Arrays.asList(1L, -1L, 2L, 3L, -1L));
        assertEquals(execList, graph.replayAllList(0));
    }

    @Test
    public void parallelLines() throws Exception {
        graph = new SelectiveDepGraph();
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

        assertEquals(Arrays.asList(1L, -1L, 3L, 5L, -1L), graph.replayAllList(0).get(0));
        assertEquals(Arrays.asList(2L, -1L, 4L, 6L, -1L), graph.replayAllList(0).get(1));


    }

    // /////////// Testing replays //////////////////
    @Test
    public void startBeforeEnd() throws Exception {
        graph = new SelectiveDepGraph();
        graph.addDependencies(1L);
        graph.addDependencies(3L, 1L);
        graph.addDependencies(7L, 1L);
        graph.updateStartEnd(1L, 2L);
        graph.updateStartEnd(3L, 10L);
        graph.updateStartEnd(7L, 9L);
        ArrayList<List<Long>> execList = new ArrayList<List<Long>>();
        execList.add(Arrays.asList(1L, -1L, 3L, 7L, -1L));
        assertEquals(execList, graph.replayAllList(0));

        // the request 1 executes until 5, so request 3 executes in parallel.
        graph.updateStartEnd(1L, 5L);
        execList.clear();
        execList.add(Arrays.asList(1L, 3L, -1L, 7L, -1L));
        assertEquals(execList, graph.replayAllList(0));

        // new requests: 4-17 and 9-20
        graph.addDependencies(4L, 3L);
        graph.addDependencies(9L, 3L);
        graph.updateStartEnd(4L, 17L);
        graph.updateStartEnd(9L, 19L);
        execList.clear();

        assertEquals(Arrays.asList(1L, 3L, 4L, 9L, -1L, 7L, -1L), graph.replayAllList(0).get(0));

        graph.addDependencies(9L, 7L);
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L, 7L, -1L, 9L, -1L), graph.replayAllList(0).get(0));

        // add new root
        graph.addDependencies(20L);
        List<List<Long>> execListReplayAll = graph.replayAllList(0);
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L, 7L, -1L, 9L, -1L), execListReplayAll.get(0));
        assertEquals(Arrays.asList(20L, -1L), execListReplayAll.get(1));

        // add link between 2 roots
        graph.addDependencies(7L, 20L);
        execListReplayAll = graph.replayAllList(0);
        assertEquals(Arrays.asList(1L, 3L, 4L, -1L), execListReplayAll.get(0));
        assertEquals(Arrays.asList(20L, -1L, 7L, -1L, 9L, -1L), execListReplayAll.get(1));
    }


    @Test
    public void selectiveReplay() throws Exception {
        graph = new SelectiveDepGraph();
        // 1->3->4
        // ..5
        // 2->7->6
        graph.addDependencies(3L, 1L);
        graph.addDependencies(4L, 3L);
        graph.addDependencies(4L, 5L);
        graph.addDependencies(6L, 5L);
        graph.addDependencies(6L, 7L);
        graph.addDependencies(7L, 2L);

        ArrayList<Long> sources = new ArrayList<Long>();
        sources.add(3L);
        List<List<Long>> replayAll = graph.replayAllList(0);
        Assert.assertEquals(Arrays.asList(1L, -1L, 3L, -1L), replayAll.get(0));
        Assert.assertEquals(Arrays.asList(2L, -1L, 7L, -1L), replayAll.get(1));
        Assert.assertEquals(Arrays.asList(5l, -1l, 4l, 6l, -1l), replayAll.get(2));

        List<List<Long>> toReplay = graph.selectiveReplayList(0, sources);
        Assert.assertEquals(Arrays.asList(5L, -1L), toReplay.get(0));
        Assert.assertEquals(Arrays.asList(1L, -1L, 3L, -1L, 4L, -1L), toReplay.get(1));

    }
}
