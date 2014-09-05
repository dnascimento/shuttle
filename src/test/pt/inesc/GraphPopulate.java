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

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.Dependency;
import pt.inesc.manager.graph.GraphShuttle;
import pt.inesc.manager.graph.SortedMap;


public class GraphPopulate {

    @Test
    public void testGraphStructure() throws IOException {
        abcdLinked(new GraphShuttle());
    }


    /**
     * List with 4 nodes: A:1-3; B:2-4; C:3-5; D:4->7;
     * Test if the list is correct stored on simpleDepGraph
     * B depends from A
     * C depends from B
     * D depends from C
     */
    public static void abcdLinked(GraphShuttle graph) throws IOException {
        long[] startEndArray = new long[] { 1, 3, 2, 4, 3, 5, 4, 7 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.addStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(2L, 1L);
        graph.addDependencies(3L, 2L);
        graph.addDependencies(4L, 3L);

        SortedMap<Long, Dependency> map = graph.map;
        Assert.assertEquals(4, map.size());

        assertEquals(0, map.get(1L).before.size());
        assertEquals(1, map.get(1L).after.size());
        assertTrue(map.get(1L).after.contains(2L));

        assertEquals(1, map.get(2L).before.size());
        assertEquals(1, map.get(2L).after.size());
        assertTrue(map.get(2L).after.contains(3L));
        assertTrue(map.get(2L).before.contains(1L));

        assertEquals(1, map.get(3L).before.size());
        assertEquals(1, map.get(3L).after.size());
        assertTrue(map.get(3L).after.contains(4L));

        assertEquals(1, map.get(4L).before.size());
        assertEquals(0, map.get(4L).after.size());
    }

    public static void abcdSerie(GraphShuttle graph) throws Exception {
        long[] startEndArray = new long[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.addStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(3L, 1L);
        graph.addDependencies(5L, 3L);
        graph.addDependencies(7L, 5L);
    }

    public static void efghSerie(GraphShuttle graph) throws Exception {
        long[] startEndArray = new long[] { 9, 10, 11, 12, 13, 14, 15, 16 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.addStartEnd(startEndArray[i++], startEndArray[i++]);
        }
        graph.addDependencies(11L, 9L);
        graph.addDependencies(13L, 11L);
        graph.addDependencies(15L, 13L);
    }


    /**
     * Check image GraphComplex in this folder
     * 
     * @param graph
     */
    public static void complexGraph(GraphShuttle graph) {
        long[] startEndArray = new long[] { 1, 2, 3, 10, 4, 10, 5, 10, 15, 20, 7, 18, 10, 18, 6, 15, 8, 12, 9, 15, 30, 40, 50, 60, 55, 70,
                65, 80, 100, 120 };
        int i = 0;
        while (i < startEndArray.length) {
            graph.addStartEnd(startEndArray[i++], startEndArray[i++]);
        }

        // WARNING: ARRAY WITH INVERSE DEPENDENCY ORDER, FOLLOWING THE ARROWS
        long[] dependencyArray = new long[] { 1, 3, 3, 4, 4, 5, 5, 3, 5, 15, 15, 7, 7, 5, 5, 7, 10, 7, 6, 8, 5, 30, 30, 50, 30, 55, 8, 9,
                55, 65, 6, 10 };
        i = 1;
        while (i < dependencyArray.length) {
            graph.addDependencies(dependencyArray[i], dependencyArray[i - 1]);
            i += 2;
        }

        graph.display();
    }

}
