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
import java.util.HashMap;

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.DepGraph;
import pt.inesc.manager.graph.Dependency;
import pt.inesc.manager.graph.SelectiveDepGraph;
import pt.inesc.manager.graph.SimpleDepGraph;


public class GraphPopulate {

    @Test
    public void testGraphStructure() throws IOException {
        abcdLinked(new SimpleDepGraph());
        abcdLinked(new SelectiveDepGraph());

    }


    /**
     * List with 4 nodes: A:1-3; B:2-4; C:3-5; D:4->7;
     * Test if the list is correct stored on simpleDepGraph
     * B depends from A
     * C depends from B
     * D depends from C
     */
    public static void abcdLinked(DepGraph graph) throws IOException {
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

    public static void abcdSerie(DepGraph graph) throws Exception {
        long[] startEndArray = new long[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(3L, 1L);
        graph.addDependencies(5L, 3L);
        graph.addDependencies(7L, 5L);
    }

    public static void efghSerie(DepGraph graph) throws Exception {
        long[] startEndArray = new long[] { 10, 11, 12, 13, 14, 15, 16, 17 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.updateStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(12L, 10L);
        graph.addDependencies(14L, 12L);
        graph.addDependencies(16L, 14L);
    }

}
