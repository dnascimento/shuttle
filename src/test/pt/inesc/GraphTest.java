/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.DepGraph;
import pt.inesc.manager.graph.SimpleDepGraph;


public class GraphTest {
    DepGraph graph;

    @Test
    public void twoIndependentLines() throws Exception {
        DepGraph graph = new SimpleDepGraph();
        GraphPopulate.abcdSerie(graph);
        GraphPopulate.efghSerie(graph);
        List<List<Long>> all = graph.replayAllList(0);
        Assert.assertEquals("[[1, 3, 5, 7, -1], [10, 12, 14, 16, -1]]", all.toString());
        List<List<Long>> time = graph.replayTimeOrdered(0);
        Assert.assertEquals("[[1, 3, 5, 7, 10, 12, 14, 16]]", time.toString());
    }


    @Test
    public void complexGraphWithCycles() throws Exception {
        DepGraph graph = new SimpleDepGraph();
        GraphPopulate.complexGraph(graph);
        // List<List<Dependency>> cycles = graph.removeCycles();
        //
        // List<List<Long>> all = graph.replayAllList(0);
        // Assert.assertEquals("[[1, 3, 5, 7, -1], [10, 12, 14, 16, -1]]",
        // all.toString());
        List<List<Long>> time = graph.replayTimeOrdered(0);
        Assert.assertEquals("[[1, 3, 5, 7, 10, 12, 14, 16]]", time.toString());
    }




}
