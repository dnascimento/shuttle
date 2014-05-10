/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

package pt.inesc;

import java.io.IOException;

import org.junit.Test;

import pt.inesc.manager.graph.ShowGraph;

public class GraphRefresh {

    @Test
    public void test() throws InterruptedException, IOException {
        ShowGraph graph = new ShowGraph();
        graph.start();
        graph.addEdgeAndVertex("dario", "maike");
        Thread.sleep(5000);
        System.out.println("update");
        graph.addEdgeAndVertex("andre", "ze");
        System.in.read();
    }
}
