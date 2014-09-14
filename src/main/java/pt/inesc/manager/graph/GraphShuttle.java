package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.replay.core.ReplayMode;

public class GraphShuttle
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(GraphShuttle.class.getName());

    private transient ShowGraph graphDisplayer;

    public final SortedMap<Dependency> map;

    public GraphShuttle() {
        map = new SortedMap<Dependency>();
    }

    /**
     * Create a new map with a known set of dependencies
     * 
     * @param initValues
     */
    GraphShuttle(HashMap<Long, Dependency> initValues) {
        this();
        for (Entry<Long, Dependency> v : initValues.entrySet()) {
            map.put(v.getKey(), v.getValue());
        }
    }



    /**
     * Retrieves the start and end of a dependency
     * 
     * @param start
     * @param end
     */
    public synchronized void addStartEnd(long start, long end) {
        // checkIfExists with end
        Dependency entry = getOrCreate(start);
        entry.end = end;
    }

    /**
     * The request with Key key must execute after the requests in the list dependencies
     * 
     * @param key
     * @param dependencies
     */
    public synchronized void addDependencies(long key, List<Long> dependencies) {
        getOrCreate(key).addBefore(dependencies);

        for (Long k : dependencies) {
            getOrCreate(k).addAfter(key);
        }

        // add edges on graphDisplayer
        if (graphDisplayer != null) {
            graphDisplayer.addEdgeAndVertex(key, dependencies);
        }
    }

    public void addDependencies(long key, long dependsFrom) {
        addDependencies(key, Arrays.asList(dependsFrom));
    }


    private Dependency getOrCreate(long key) {
        Dependency entry = map.get(key);
        if (entry == null) {
            entry = new Dependency(key, 0L);
            map.put(key, entry);
        }
        return entry;
    }

    public Iterator<Dependency> iterator() {
        return map.iterator();
    }

    /**
     * Split the current graph in multiple smaller and independent maps using BFS
     * 
     * @return a list of independent GraphShuttle
     */
    public List<GraphShuttle> getSubTrees(long baseCommit) {
        resetVisited();
        List<GraphShuttle> trees = new LinkedList<GraphShuttle>();

        for (Dependency node : map) {
            if (!node.visited) {
                HashMap<Long, Dependency> subTree = new HashMap<Long, Dependency>();
                expandNode(node.start, ExpandMode.both, subTree, baseCommit);
                trees.add(new GraphShuttle(subTree));
            }
        }
        return trees;
    }



    /*
     * Get all accessible entries from the attackSource using only the nodes in the after
     * direction
     */
    public HashMap<Long, Dependency> expandForward(List<Long> attackSource, long baseCommit) {
        HashMap<Long, Dependency> allNodes = new HashMap<Long, Dependency>();
        for (Long key : attackSource) {
            expandNode(key, ExpandMode.forward, allNodes, baseCommit);
        }
        return allNodes;
    }

    /**
     * Return the roots of the expanding sets. Like backtracker paper
     * The snapshot defines that only request started after the commitID, belong to the
     * new snapshot. Requests started before the snapshot, can not depend from requests
     * started after the snapshot
     * 
     * @param taintedLeafs the leafs of all tainted requests
     * @param baseCommit before the commit, the data is known
     * @return the set of root requests needed to replay
     */
    public HashMap<Long, Dependency> expandBack(HashMap<Long, Dependency> tainted, long baseCommit) {
        HashMap<Long, Dependency> allNodes = new HashMap<Long, Dependency>();
        for (Entry<Long, Dependency> v : tainted.entrySet()) {
            expandNode(v.getKey(), ExpandMode.back, allNodes, baseCommit);
        }
        Iterator<Dependency> it = allNodes.values().iterator();
        while (it.hasNext()) {
            Dependency node = it.next();
            if (node.start < baseCommit) {
                it.remove();
            }
        }
        return allNodes;
    }


    /**
     * Expands the root node using DFS. It expands using the forward, backward or both
     * directions. The result is stored in the result set, which may not be empty
     * 
     * @param root the source of the DFS algorithm
     * @param mode the directions of the graph to explore
     * @param result where the result is stored
     * @param baseCommit
     */
    private void expandNode(Long root, ExpandMode mode, HashMap<Long, Dependency> result, long baseCommit) {
        Deque<Long> lifo = new ArrayDeque<Long>();
        lifo.add(root);

        while (!lifo.isEmpty()) {
            Long key = lifo.pop();
            if (result.containsKey(key)) {
                continue;
            }
            Dependency node = map.get(key);

            // visited
            result.put(key, node);

            // mark as part of a subtree
            if (mode == ExpandMode.both) {
                node.visited = true;
            }


            if (mode == ExpandMode.back || mode == ExpandMode.both) {
                for (Long back : node.before) {
                    if (!result.containsKey(back) && back > baseCommit) {
                        lifo.add(back);
                    }
                }
            }

            if (mode == ExpandMode.forward || mode == ExpandMode.both) {
                for (Long next : node.after) {
                    if (!result.containsKey(next) && next > baseCommit) {
                        lifo.add(next);
                    }
                }
            }
        }
    }

    private void resetVisited() {
        for (Dependency node : map) {
            node.visited = false;
        }
    }

    // ------------- Display -------------
    public synchronized void reset() {
        map.clear();
        if (graphDisplayer != null)
            graphDisplayer.reset();
    }

    public void display() {
        graphDisplayer = new ShowGraph(map);
        graphDisplayer.start();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("start:end -- before -- after\n");
        for (Dependency v : map) {
            sb.append(v);
            sb.append(";");
            sb.append(printSet(v.before));
            sb.append("-----");
            sb.append(printSet(v.after));
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String printSet(HashSet<Long> set) {
        StringBuilder sb = new StringBuilder();
        for (Long v : set) {
            sb.append(v);
            sb.append(",");
        }
        return sb.toString();
    }

    enum ExpandMode {
        forward, back, both;
    }


    public synchronized int countDependencies() {
        Iterator<Dependency> it = map.iterator();
        int counterBefore = 0;
        int counterAfter = 0;

        while (it.hasNext()) {
            Dependency dep = it.next();
            counterBefore += dep.before.size();
            counterAfter += dep.after.size();
        }

        if (counterBefore != counterAfter) {
            System.err.println(counterBefore + " before, while " + counterAfter + " after");
        }
        return counterBefore;
    }

    public synchronized List<List<Long>> replay(long baseCommit, ReplayMode mode, List<Long> attackSource) {
        List<List<Long>> result = new ArrayList<List<Long>>(1);

        switch (mode) {
        case allParallel:
            result = DepAlgorithms.replayParallel(baseCommit, this);
            return result;
        case allSerial:
            result.add(DepAlgorithms.replaySerial(baseCommit, this));
            return result;

        case selectiveParallel:
            return DepAlgorithms.replaySelectiveParallel(baseCommit, attackSource, this);
        case selectiveSerial:
            result.add(DepAlgorithms.replaySelectiveSerial(baseCommit, attackSource, this));
            return result;
        default:
            throw new UnsupportedOperationException("Unknown replay mode");
        }

    }
}
