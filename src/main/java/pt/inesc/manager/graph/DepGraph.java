package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public abstract class DepGraph
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(DepGraph.class.getName());


    /**
     * Class which displays this graph
     */
    private transient ShowGraph graphDisplayer;

    /**
     * Temporary storage of the start-end of each request because the end may arrive
     * before the start
     */
    final HashMap<Long, Long> tmpStartEnd = new HashMap<Long, Long>();


    /**
     * Each dependency establish the elements which can only run after the key.
     */
    final HashMap<Long, Dependency> graph = new HashMap<Long, Dependency>();




    public abstract void addNode(Long from, Long to);

    public abstract void removeNode(Long from, Long to);


    public List<List<Long>> replayAllList(long baseCommit) throws Exception {
        restoreCounters();
        return GraphUtils.getExecutionListSortedByDependencies(baseCommit, getRoots(), this);
    }

    /**
     * Create an execution list where requests are ordered by their start-end. A
     * request
     * can start only after its previous ends.
     */

    public List<List<Long>> replayTimeOrdered(long baseCommit) {
        restoreCounters();
        List<List<Long>> result = new LinkedList<List<Long>>();
        ArrayList<Long> list = new ArrayList<Long>(graph.keySet());

        // sort the list by time, the replay instance coordinates the start-end.
        long tsStart = new Date().getTime();
        Collections.sort(list);
        long tsEnd = new Date().getTime();
        LOGGER.info("Execution list sorted in " + (tsEnd - tsStart) + " ms");

        return result;
    }


    public abstract List<List<Long>> selectiveReplayList(long baseCommit, List<Long> attackSource) throws Exception;



    public void addDependencies(Long key, Long... dependencies) {
        addDependencies(key, Arrays.asList(dependencies));
    }

    /**
     * The request with Key key must execute after the requests in the list dependencies
     * 
     * @param key
     * @param dependencies
     */
    public synchronized void addDependencies(Long key, List<Long> dependencies) {
        // get the end-time
        Dependency keyEntry = getOrCreateNode(key);
        if (keyEntry.end == 0) {
            Long endTmp = tmpStartEnd.remove(keyEntry.start);
            if (endTmp != null) {
                keyEntry.end = endTmp;
            }
        }

        // Copy the after list to detect cycles later
        // HashSet<Long> possibleCicles = keyEntry.cloneAfter();

        // add dependencies
        for (Long depKey : dependencies) {
            addNode(depKey, key);
        }

        // add edges on graphDisplayer
        if (graphDisplayer != null) {
            graphDisplayer.addEdgeAndVertex(key, dependencies);
        }
        // if (possibleCicles.size() == 0) {
        GraphUtils.searchCycle(key, this);
        // }
    }



    /**
     * Retrieves the start and end of a dependency
     * 
     * @param start
     * @param end
     */
    public synchronized void updateStartEnd(long start, long end) {
        Dependency dep = graph.get(start);
        if (dep == null) {
            tmpStartEnd.put(start, end);
        } else {
            dep.start = start;
            dep.end = end;
        }
    }


    public Dependency getNode(Long key) {
        return graph.get(key);
    }

    public Dependency getOrCreateNode(Long key) {
        if (!graph.containsKey(key)) {
            graph.put(key, new Dependency(key));
        }
        return graph.get(key);
    }

    /**
     * Get roots:
     * search for items which do not depend from other items
     */
    public synchronized ArrayList<Long> getRoots() {
        ArrayList<Long> roots = new ArrayList<Long>();
        for (Long l : graph.keySet()) {
            Dependency d = graph.get(l);
            if (d.countBefore == 0) {
                roots.add(l);
            }
        }
        return roots;
    }


    /**
     * Before iterating the graph, the countBefore must be reseted.
     */
    public synchronized void restoreCounters() {
        for (Dependency d : graph.values()) {
            d.countBeforeTmp = d.countBefore;
        }
    }

    // ------------- Display -------------
    public synchronized void reset() {
        graph.clear();
        if (graphDisplayer != null)
            graphDisplayer.reset();
    }

    public void display() {
        graphDisplayer = new ShowGraph(graph);
        graphDisplayer.start();
    }

    /**
     * Used by tests to assert the state
     * 
     * @return all dependencies
     */
    public HashMap<Long, Dependency> getMap() {
        return graph;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Key:start:end;before; dependencies");
        for (Entry<Long, Dependency> e : graph.entrySet()) {
            sb.append(e.getKey());
            sb.append(":");
            sb.append(e.getValue().start);
            sb.append(":");
            sb.append(e.getValue().end);
            sb.append(";");
            sb.append(e.getValue().countBefore);
            sb.append("->");
            sb.append(e.getValue().getAfter());
            sb.append("\n");
        }
        return sb.toString();
    }



}
