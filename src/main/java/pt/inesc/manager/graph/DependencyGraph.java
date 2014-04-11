package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public class DependencyGraph
        implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final Long SEPARATOR = (long) -1;
    /**
     * Each dependency establish the elements which can only run after the key.
     */
    HashMap<Long, Dependency> graph = new HashMap<Long, Dependency>();
    transient ShowGraph graphDisplayer;
    HashSet<Long> rootCandidates = new HashSet<Long>();


    /**
     * For each StartID X map the list of StartID Y's which X reads from
     * 
     * @param list
     */
    public synchronized void addDependencies(Long key, List<Long> dependencies) {
        Dependency keyEntry = getEntry(key);
        Long[] possibleCicles = null;
        // Remove cycles
        if (keyEntry.hasAfter()) {
            possibleCicles = keyEntry.getArrayAfter();
        }
        // add dependencies
        for (Long depKey : dependencies) {
            Dependency depKeyEntry = getEntry(depKey);
            depKeyEntry.addAfter(key);
            keyEntry.countBefore++;
        }
        // add edges on graphDisplayer
        if (graphDisplayer != null) {
            graphDisplayer.addEdgeAndVertex(key, dependencies);
        }
        if (possibleCicles != null) {
            searchCycle(key, possibleCicles);
        }
    }

    /**
     * Get roots:
     * search for items which do not depend from other items
     */
    public synchronized List<Long> getRoots() {
        LinkedList<Long> roots = new LinkedList<Long>();
        for (Long l : graph.keySet()) {
            Dependency d = graph.get(l);
            if (d.countBefore == 0) {
                roots.add(l);
            }
        }
        return roots;
    }

    /**
     * From a root key, extract the list of requests dependent from
     * 
     * @param rootKey (a key with counter = 0
     * @return
     */
    public synchronized List<Long> getExecutionList(long rootKey) {
        Dependency entry = graph.get(rootKey);
        assert (entry != null); // TODO Handle exception: invalid root
        assert (entry.countBefore == 0);
        LinkedList<Long> executionList = new LinkedList<Long>();
        PriorityQueue<Dependency> readyHeap = new PriorityQueue<Dependency>();
        readyHeap.add(entry);
        while (!readyHeap.isEmpty()) {
            entry = readyHeap.remove();
            ArrayList<Dependency> parallelRequests = new ArrayList<Dependency>();
            expandEntry(entry, parallelRequests, readyHeap);
            Collections.sort(parallelRequests);
            for (Dependency dep : parallelRequests) {
                executionList.add(dep.getKey());
            }
            executionList.add(SEPARATOR);
        }
        return executionList;
    }

    /**
     * Load every request which have been executed concurrently with entry
     * 
     * @param entry
     * @param groupOfparallelRequest
     * @param readyHeap
     */
    public void expandEntry(
            Dependency entry,
                ArrayList<Dependency> groupOfparallelRequest,
                PriorityQueue<Dependency> readyHeap) {
        groupOfparallelRequest.add(entry);

        long previousEnd = entry.end;
        for (long key : entry.getAfter()) {
            Dependency req = graph.get(key);
            req.countBefore--;
            if (req.countBefore == 0) {
                if (req.start < previousEnd) {
                    expandEntry(req, groupOfparallelRequest, readyHeap); // recursion
                    previousEnd = req.end;
                } else {
                    readyHeap.add(req);
                }
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////
    /**
     * Search cycle using DSF algorithm (LIFO)
     * 
     * @param root
     * @param possibleCicles
     */
    private void searchCycle(Long root, Long[] possibleCiclesNexts) {
        Dependency rootEntry = graph.get(root);
        for (Long next : rootEntry.getAfter()) {
            Dependency nextNode = graph.get(next);
            if (searchCycleAux(nextNode, root)) {
                rootEntry.removeAfter(next);
                nextNode.countBefore--;
            }
        }
    }

    private Boolean searchCycleAux(Dependency nextNode, Long rootStart) {
        for (long next : nextNode.getAfter()) {
            if (next == rootStart) {
                // found cycle
                if (nextNode.start > rootStart) {
                    nextNode.removeAfter(rootStart);
                    getEntry(rootStart).countBefore--;
                    return false;
                } else {
                    return true;
                }
            }
            Dependency child = graph.get(next);
            if (child.end > rootStart)
                if (searchCycleAux(child, rootStart))
                    return true;
        }
        return false;
    }

    public Dependency getEntry(long key) {
        Dependency entry = graph.get(key);
        if (entry == null) {
            entry = new Dependency(key);
            graph.put(key, entry);
        }
        return entry;
    }

    public void display() {
        graphDisplayer = new ShowGraph(this.graph);
        graphDisplayer.start();
    }


    public void reset() {
        graph.clear();
        rootCandidates.clear();
        graphDisplayer.reset();
    }

    /**
     * Retrieves one array with start|end|start|end...
     * 
     * @param array with start|end|start|end...
     */
    public void updateStartEnd(long start, long end) {
        Dependency dep = getEntry(start);
        dep.start = start;
        dep.end = end;
    }



}
