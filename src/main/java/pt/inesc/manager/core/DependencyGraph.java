package pt.inesc.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
     * @param dependenciesMap
     */
    public void addDependencies(Map<Long, List<Long>> dependenciesMap) {
        for (Entry<Long, List<Long>> keyDepPair : dependenciesMap.entrySet()) {
            Dependency keyEntry = getEntry(keyDepPair.getKey());
            // Remove cycles
            if (keyEntry.hasAfter()) {
                searchCycle(keyDepPair.getKey(), keyDepPair.getValue());
            }
            // add dependencies
            for (Long depKey : keyDepPair.getValue()) {
                Dependency depKeyEntry = getEntry(depKey);
                depKeyEntry.addAfter(keyDepPair.getKey());
            }
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
        assert entry.countBefore == 0;
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

    public void expandEntry(
            Dependency entry,
                ArrayList<Dependency> groupOfparallelRequest,
                PriorityQueue<Dependency> readyHeap) {
        groupOfparallelRequest.add(entry);

        long previousEnd = entry.end;
        Iterator<Long> iterator = entry.getAfterIterator();
        while (iterator.hasNext()) {
            long key = iterator.next();
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
     * Search cycle using BSF algorithm (FIFO)
     * 
     * @param key
     * @param dependencies
     */
    private void searchCycle(Long key, List<Long> dependencies) {
        LinkedList<Long> queue = new LinkedList<Long>();
        Dependency sourceEntry = graph.get(key);
        queue.addLast(key);
        while (!queue.isEmpty() && !dependencies.isEmpty()) {
            long k = queue.getFirst();
            Dependency kEntry = graph.get(k);
            if (!kEntry.hasAfter())
                continue;
            for (Long dep : dependencies) {
                if (kEntry.hasAfter(dep)) {
                    dependencies.remove(dep);
                }
            }
            Iterator<Long> iterator = kEntry.getAfterIterator();
            while (iterator.hasNext()) {
                long next = iterator.next();
                Dependency child = graph.get(next);
                if (child.end > sourceEntry.start) {
                    queue.add(next);
                }
            }
        }
    }

    public Dependency getEntry(long key) {
        Dependency entry = graph.get(key);
        if (entry == null) {
            entry = new Dependency();
            graph.put(key, entry);
        }
        return entry;
    }

    public void display() {
        ShowGraph graphDisplayer = new ShowGraph(this.graph);
        graphDisplayer.display();
    }
}
