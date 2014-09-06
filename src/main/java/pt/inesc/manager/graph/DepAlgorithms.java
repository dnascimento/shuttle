package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class which defines all the algorithms used to generate the dependency graph.
 * This class is data structure independent
 * 
 * @author darionascimento
 */
public final class DepAlgorithms {

    public static List<Long> replaySerial(long baseCommit, GraphShuttle map) {
        // the requests are sorted by time
        long biggestEndExecuting = -1;
        List<Long> execList = new ArrayList<Long>();

        Iterator<Dependency> it = map.iterator();

        while (it.hasNext()) {
            Dependency node = it.next();
            if (node.start <= biggestEndExecuting || biggestEndExecuting == -1) {
                execList.add(node.start);
            } else {
                // wait for the previous to exec
                execList.add(-1L);
                execList.add(node.start);
            }
            biggestEndExecuting = Math.max(biggestEndExecuting, node.end);
        }

        // sort execLis
        // sortExecList(execList);


        return execList;
    }



    /**
     * Separate the main tree into multiple subtrees and then invoke the
     * replay series to each subtree
     * 
     * @param baseCommit
     * @param baseGraph
     * @return
     */
    public static List<List<Long>> replayParallel(long baseCommit, GraphShuttle baseGraph) {
        // use the DFS to separate the entries
        List<GraphShuttle> listOfTrees = baseGraph.getSubTrees(baseCommit);

        // invoke the startEnd to each subtree
        List<List<Long>> execLists = new ArrayList<List<Long>>(listOfTrees.size());
        for (GraphShuttle subTree : listOfTrees) {
            execLists.add(replaySerial(baseCommit, subTree));
        }
        return execLists;
    }



    /**
     * Replay only the requests affected by attackSource in a serial order
     * 
     * @param baseCommit
     * @param attackSource
     * @param baseGraph
     * @return
     */
    public static List<Long> replaySelectiveSerial(long baseCommit, List<Long> attackSource, GraphShuttle baseGraph) {
        GraphShuttle subGraph = replaySelective(baseCommit, attackSource, baseGraph);
        return replaySerial(baseCommit, subGraph);
    }

    public static List<List<Long>> replaySelectiveParallel(long baseCommit, List<Long> attackSource, GraphShuttle baseGraph) {
        GraphShuttle subGraph = replaySelective(baseCommit, attackSource, baseGraph);
        return replayParallel(baseCommit, subGraph);
    }



    private static GraphShuttle replaySelective(long baseCommit, List<Long> attackSource, GraphShuttle baseGraph) {
        // expand the set to get which requests are tainted
        HashMap<Long, Dependency> tainted = baseGraph.expandForward(attackSource, baseCommit);

        // backtrack to get the requests to replay: return the roots.
        HashMap<Long, Dependency> allNodes = baseGraph.expandBack(tainted, baseCommit);

        return new GraphShuttle(allNodes);
    }


}
