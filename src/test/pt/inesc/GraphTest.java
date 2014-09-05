/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.GraphShuttle;
import pt.inesc.replay.core.ReplayMode;


public class GraphTest {
    GraphShuttle graph;


    @Test
    public void twoIndependentLines() throws Exception {
        GraphShuttle graph = new GraphShuttle();
        GraphPopulate.abcdSerie(graph);
        GraphPopulate.efghSerie(graph);
        // serial
        Assert.assertEquals("[[1, -1, 3, -1, 5, -1, 7, -1, 9, -1, 11, -1, 13, -1, 15]]", graph.replay(0, ReplayMode.allSerial, null)
                                                                                              .toString());
        // parallel
        Assert.assertEquals("[[1, -1, 3, -1, 5, -1, 7], [9, -1, 11, -1, 13, -1, 15]]", graph.replay(0, ReplayMode.allParallel, null)
                                                                                            .toString());

        // SELECTIVE
        // empty attack set
        Assert.assertEquals("[[]]", graph.replay(0, ReplayMode.selectiveSerial, new ArrayList<Long>()).toString());
        Assert.assertEquals("[]", graph.replay(0, ReplayMode.selectiveParallel, new ArrayList<Long>()).toString());

        // entry 7
        Assert.assertEquals("[[1, -1, 3, -1, 5, -1, 7]]", graph.replay(0, ReplayMode.selectiveSerial, Arrays.asList(7L)).toString());
        Assert.assertEquals("[[1, -1, 3, -1, 5, -1, 7]]", graph.replay(0, ReplayMode.selectiveParallel, Arrays.asList(7L)).toString());
    }



    @Test
    public void complexGraphWithCycles() throws Exception {
        GraphShuttle graph = new GraphShuttle();
        GraphPopulate.complexGraph(graph);

        // serial
        Assert.assertEquals("[[1, -1, 3, 4, 5, 9, 7, 10, 6, 8, 15, -1, 30, -1, 50, 55, 65, -1, 100]]",
                            graph.replay(0, ReplayMode.allSerial, null).toString());


        // parallel
        Assert.assertEquals("[[1, -1, 4, 3, 6, 8, 10, 5, -1, 55, 7, 50, 9, 65, 15, 30], [100]]",
                            graph.replay(0, ReplayMode.allParallel, null).toString());


        // one node affected
        Assert.assertEquals("[[65]]", graph.replay(60, ReplayMode.selectiveSerial, Arrays.asList(65L)).toString());
        Assert.assertEquals("[[65]]", graph.replay(60, ReplayMode.selectiveParallel, Arrays.asList(65L)).toString());

        // two affected
        Assert.assertEquals("[[55, 65]]", graph.replay(35, ReplayMode.selectiveSerial, Arrays.asList(55L)).toString());
        Assert.assertEquals("[[55, 65]]", graph.replay(35, ReplayMode.selectiveParallel, Arrays.asList(55L)).toString());

        // three affected
        Assert.assertEquals("[[55, -1, 100, 65]]", graph.replay(35, ReplayMode.selectiveSerial, Arrays.asList(55L, 100L)).toString());
        Assert.assertEquals("[[55, 65], [100]]", graph.replay(35, ReplayMode.selectiveParallel, Arrays.asList(55L, 100L)).toString());

        // TODO more tests
    }
}
