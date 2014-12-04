package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import objectexplorer.MemoryMeasurer;
import objectexplorer.ObjectGraphMeasurer;
import objectexplorer.ObjectGraphMeasurer.Footprint;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.replay.core.ReplayMode;

public class GraphShuttle
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(GraphShuttle.class.getName());

    private transient ShowGraph graphDisplayer;

    public final SortedMap<Dependency> map;

    public GraphShuttle(int initSize) {
        map = new SortedMap<Dependency>(initSize);
    }

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
    public List<GraphShuttle> getSubTrees(long baseSnapshot) {
        resetVisited();
        List<GraphShuttle> trees = new LinkedList<GraphShuttle>();

        for (Dependency node : map) {
            if (!node.visited) {
                HashMap<Long, Dependency> subTree = new HashMap<Long, Dependency>();
                expandNode(node.start, ExpandMode.both, subTree, baseSnapshot);
                trees.add(new GraphShuttle(subTree));
            }
        }
        return trees;
    }



    /*
     * Get all accessible entries from the attackSource using only the nodes in the after
     * direction
     */
    public HashMap<Long, Dependency> expandForward(List<Long> attackSource, long baseSnapshot) {
        HashMap<Long, Dependency> allNodes = new HashMap<Long, Dependency>();
        for (Long key : attackSource) {
            expandNode(key, ExpandMode.forward, allNodes, baseSnapshot);
        }
        return allNodes;
    }

    /**
     * Return the roots of the expanding sets. Like backtracker paper
     * The snapshot defines that only request started after the snapshotID, belong to the
     * new snapshot. Requests started before the snapshot, can not depend from requests
     * started after the snapshot
     * 
     * @param taintedLeafs the leafs of all tainted requests
     * @param baseSnapshot before the snapshot, the data is known
     * @return the set of root requests needed to replay
     */
    public HashMap<Long, Dependency> expandBack(HashMap<Long, Dependency> tainted, long baseSnapshot) {
        HashMap<Long, Dependency> allNodes = new HashMap<Long, Dependency>();
        for (Entry<Long, Dependency> v : tainted.entrySet()) {
            expandNode(v.getKey(), ExpandMode.back, allNodes, baseSnapshot);
        }
        Iterator<Dependency> it = allNodes.values().iterator();
        while (it.hasNext()) {
            Dependency node = it.next();
            if (node.start < baseSnapshot) {
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
     * @param baseSnapshot
     */
    private void expandNode(Long root, ExpandMode mode, HashMap<Long, Dependency> result, long baseSnapshot) {
        Deque<Long> lifo = new ArrayDeque<Long>();
        lifo.add(root);

        while (!lifo.isEmpty()) {
            Long key = lifo.pop();
            if (result.containsKey(key)) {
                continue;
            }
            Dependency node = map.get(key);

            if (node == null) {
                LOGGER.error("[expandNode] Request not in graph: " + key);
                continue;
            }


            // visited
            result.put(key, node);

            // mark as part of a subtree
            if (mode == ExpandMode.both) {
                node.visited = true;
            }


            if (mode == ExpandMode.back || mode == ExpandMode.both) {
                for (Long back : node.before) {
                    if (!result.containsKey(back) && back > baseSnapshot) {
                        lifo.add(back);
                    }
                }
            }


            if (mode == ExpandMode.forward || mode == ExpandMode.both) {
                for (Long next : node.after) {
                    if (!result.containsKey(next) && next > baseSnapshot) {
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
            sb.append(printList(v.before));
            sb.append("-----");
            sb.append(printList(v.after));
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String printList(ArrayList<Long> before) {
        StringBuilder sb = new StringBuilder();
        for (Long v : before) {
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

    public synchronized ExecListWrapper replay(long baseSnapshot, ReplayMode mode, List<Long> attackSource) throws Exception {
        List<List<Long>> result = new ArrayList<List<Long>>(1);

        long latestRequest = getLatestRequest();

        switch (mode) {
        case allParallel:
            result = DepAlgorithms.replayParallel(baseSnapshot, this);
            break;
        case allSerial:
            result.add(DepAlgorithms.replaySerial(baseSnapshot, this));
            break;
        case selectiveParallel:
            result = DepAlgorithms.replaySelectiveParallel(baseSnapshot, attackSource, this);
        case selectiveSerial:
            result.add(DepAlgorithms.replaySelectiveSerial(baseSnapshot, attackSource, this));
            break;
        default:
            throw new UnsupportedOperationException("Unknown replay mode");
        }
        return new ExecListWrapper(result, latestRequest);

    }



    /**
     * For each rid, expand forward and collect the set of requests dependent from this
     * 
     * @param rids
     * @return
     */
    public HashMap<Long, Dependency> countAffected(long[] rids) {
        HashMap<Long, Dependency> result = new HashMap<Long, Dependency>();
        for (Long rid : rids) {
            expandNode(rid, ExpandMode.forward, result, 0L);
        }

        for (Long rid : rids) {
            result.remove(rid);
        }
        return result;
    }


    private long getLatestRequest() throws Exception {
        if (map.size() == 0) {
            throw new Exception("Graph is empty");
        }
        return map.getBiggestKey();
    }

    public long getMemorySize() {
        return MemoryMeasurer.measureBytes(map.getMap());
    }


    public String getTotalByteSize(boolean byteSize) {
        StringBuilder sb = new StringBuilder();
        map.deleteIterator();
        if (byteSize) {
            System.out.println("Get footprint");
            Footprint footPrint = ObjectGraphMeasurer.measure(map.getMap());
            System.out.println("\n \n \n \n /************** Graph Total Size ******************\\");
            System.out.println("Total: \n" + "    " + footPrint);
            System.out.println("Get memory usage");
            long memory = MemoryMeasurer.measureBytes(map.getMap());
            System.out.println("     memory" + memory + " bytes");
            System.out.println("------");
        }
        int before = 0;
        int after = 0;
        int count = 0;
        for (Dependency dep : map) {
            count++;
            System.out.println("Before bytes: " + MemoryMeasurer.measureBytes(dep.before));
            System.out.println("Before size: " + dep.before.size());

            System.out.println("After bytes: " + MemoryMeasurer.measureBytes(dep.after));
            System.out.println("After size: " + dep.after.size());

            System.out.println("Total size of item: " + MemoryMeasurer.measureBytes(dep));
            before += dep.before.size();
            after += dep.after.size();
        }



        System.out.println("Total Before: " + before + " \n");
        System.out.println("Total After: " + after + " \n");
        System.out.println("Total entries: " + count + "\n");
        System.out.println("/********************************\\ \n \n \n \n ");
        return sb.toString();
    }

    public String listAllEntries() {
        StringBuilder sb = new StringBuilder();
        for (Dependency dep : map) {
            sb.append(dep.start);
            sb.append(",");
        }
        return sb.toString();
    }

    public void deleteIterator() {
        map.deleteIterator();
    }

}
