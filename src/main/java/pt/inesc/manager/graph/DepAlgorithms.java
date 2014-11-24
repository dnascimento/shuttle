package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * Utility class which defines all the algorithms used to generate the dependency graph.
 * This class is data structure independent
 * 
 * @author darionascimento
 */
public final class DepAlgorithms {
    private static final Logger LOGGER = LogManager.getLogger(DepAlgorithms.class.getName());

    public static List<Long> replaySerial(long baseSnapshot, GraphShuttle map) {
        // the requests are sorted by time
        long biggestEndExecuting = -1;
        List<Long> execList = new ArrayList<Long>();

        Iterator<Dependency> it = map.iterator();

        while (it.hasNext()) {
            Dependency node = it.next();
            // filter the requests previous to the snapshot
            if (node.start < baseSnapshot) {
                continue;
            }
            if (node.start >= biggestEndExecuting) {
                // start a new list
                execList.add(-1L);
            }
            execList.add(node.start);
            biggestEndExecuting = Math.max(biggestEndExecuting, node.end);
        }

        return execList;
    }



    /**
     * Separate the main tree into multiple subtrees and then invoke the
     * replay series to each subtree
     * 
     * @param baseSnapshot
     * @param baseGraph
     * @return
     */
    public static List<List<Long>> replayParallel(long baseSnapshot, GraphShuttle baseGraph) {
        // use the DFS to separate the entries
        LOGGER.warn("Get subtrees");
        List<GraphShuttle> listOfTrees = baseGraph.getSubTrees(baseSnapshot);
        LOGGER.warn("Get subtrees - done ");
        LOGGER.warn("Invoke startEnd on each subtree");

        // invoke the startEnd to each subtree
        List<List<Long>> execLists = new ArrayList<List<Long>>(listOfTrees.size());
        for (GraphShuttle subTree : listOfTrees) {
            execLists.add(replaySerial(baseSnapshot, subTree));
        }
        LOGGER.warn("Invoke startEnd on each subtree - done");
        return execLists;
    }



    /**
     * Replay only the requests affected by attackSource in a serial order
     * 
     * @param baseSnapshot
     * @param attackSource
     * @param baseGraph
     * @return
     */
    public static List<Long> replaySelectiveSerial(long baseSnapshot, List<Long> attackSource, GraphShuttle baseGraph) {
        GraphShuttle subGraph = replaySelective(baseSnapshot, attackSource, baseGraph);
        return replaySerial(baseSnapshot, subGraph);
    }

    public static List<List<Long>> replaySelectiveParallel(long baseSnapshot, List<Long> attackSource, GraphShuttle baseGraph) {
        GraphShuttle subGraph = replaySelective(baseSnapshot, attackSource, baseGraph);
        return replayParallel(baseSnapshot, subGraph);
    }



    private static GraphShuttle replaySelective(long baseSnapshot, List<Long> attackSource, GraphShuttle baseGraph) {
        // expand the set to get which requests are tainted
        LOGGER.warn("Expand forward");
        HashMap<Long, Dependency> tainted = baseGraph.expandForward(attackSource, baseSnapshot);
        LOGGER.warn("Expand forward - done");

        LOGGER.warn("Expand back");
        // backtrack to get the requests to replay: return the roots.
        HashMap<Long, Dependency> allNodes = baseGraph.expandBack(tainted, baseSnapshot);
        LOGGER.warn("Expand back - done");

        for (Long a : attackSource) {
            allNodes.remove(a);
        }

        return new GraphShuttle(allNodes);
    }


}
