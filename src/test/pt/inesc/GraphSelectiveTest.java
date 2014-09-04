package pt.inesc;

import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.Assert;

import org.junit.Test;

import pt.inesc.manager.graph.SelectiveDepGraph;

public class GraphSelectiveTest {

    @Test
    public void twoIndependentLines() throws Exception {
        SelectiveDepGraph graph = new SelectiveDepGraph();
        GraphPopulate.abcdSerie(graph);
        GraphPopulate.efghSerie(graph);
        // all
        Assert.assertEquals("[[1, 3, 5, 7, -1], [10, 12, 14, 16, -1]]", graph.replayAllList(0).toString());
        // time
        Assert.assertEquals("[[1, 3, 5, 7, 10, 12, 14, 16]]", graph.replayTimeOrdered(0).toString());

        // empty attack set
        Assert.assertEquals("[]", graph.selectiveReplayList(0, new ArrayList<Long>()).toString());

        // entry 7
        Assert.assertEquals("[[1, 3, 5, 7, -1]]", graph.selectiveReplayList(0, Arrays.asList(7L)).toString());
    }
}
