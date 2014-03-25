package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public class DependencyGraph {
    private static final Long SEPARATOR = (long) -1;
    HashMap<Long, Dependency> graph = new HashMap<Long, Dependency>();

    /**
     * Retrieves one array with start|end|start|end...
     * 
     * @param array with start|end|start|end...
     */
    public void updateStartEnd(long[] startEndArray) {
        assert (startEndArray.length % 2 == 0);
        for (int i = 0; i < startEndArray.length; i++) {
            Dependency dep = getEntry(startEndArray[i]);
            dep.start = startEndArray[i];
            dep.end = startEndArray[++i];
        }
    }

    /**
     * For each StartID X map the list of StartID Y's which X reads from
     * 
     * @param list
     */
    public void addDependencies(Long key, List<Long> dependencies) {
        Dependency keyEntry = getEntry(key);
        Long[] possibleCicles = null;
        // Remove cycles
        if (keyEntry.hasAfter()) {
            possibleCicles = keyEntry.getAfter().toArray(new Long[0]);
        }
        // add dependencies
        for (Long depKey : dependencies) {
            Dependency depKeyEntry = getEntry(depKey);
            depKeyEntry.addAfter(key);
            keyEntry.countBefore++;
        }
        if (possibleCicles != null) {
            searchCycle(key, possibleCicles);
        }
    }

    /**
     * From a root key, extract the list of requests dependent from
     * 
     * @param rootKey (a key with counter = 0
     * @return
     */
    public List<Long> getExecutionList(long rootKey) {
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
        ShowGraph graphDisplayer = new ShowGraph(this.graph);
        graphDisplayer.display();
    }

    public void refreshableDisplay() {
        new ShowGraph(this.graph).start();
    }
}
