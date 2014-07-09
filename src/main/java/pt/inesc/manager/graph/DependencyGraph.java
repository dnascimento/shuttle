/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

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
    public HashMap<Long, Dependency> graph = new HashMap<Long, Dependency>();
    transient ShowGraph graphDisplayer;
    HashSet<Long> rootCandidates = new HashSet<Long>();
    private final HashMap<Long, Long> tmpStartEnd = new HashMap<Long, Long>();


    public void addDependencies(long key, Long... dependencies) {
        List<Long> list = new ArrayList<Long>(Arrays.asList(dependencies));
        addDependencies(key, list);
    }

    /**
     * For each StartID X map the list of StartID Y's which X reads from
     * 
     * @param list
     */
    public synchronized void addDependencies(Long key, List<Long> dependencies) {
        Dependency keyEntry = getEntry(key);
        if (keyEntry.end == 0) {
            Long endTmp = tmpStartEnd.remove(keyEntry.start);
            if (endTmp != null) {
                keyEntry.end = endTmp;
            }
        }
        Long[] possibleCicles = null;
        // Remove cycles
        if (keyEntry.hasAfter()) {
            possibleCicles = keyEntry.getArrayAfter();
        }
        // add dependencies
        for (Long depKey : dependencies) {
            Dependency depKeyEntry = getEntry(depKey);
            boolean isNew = depKeyEntry.addAfter(key);
            if (isNew) {
                keyEntry.countBefore++;
            }
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


    public synchronized void restoreCounters() {
        for (Dependency d : graph.values()) {
            d.countBeforeTmp = d.countBefore;
        }
    }

    /**
     * From a root key, extract the list of requests dependent from
     * 
     * @param rootKey (a key with counter = 0
     * @param baseCommit
     * @return
     */
    public synchronized List<Long> getExecutionList(long rootKey, long baseCommit) {
        Dependency entry = graph.get(rootKey);
        assert (entry != null); // TODO Handle exception: invalid root
        assert (entry.countBefore == 0);
        LinkedList<DependencyArray> executionList = new LinkedList<DependencyArray>();
        Dependency rootReq = graph.get(rootKey);
        DependencyArray current = new DependencyArray(rootReq);

        while (!current.isEmpty()) {
            executionList.add(current);
            DependencyArray next = new DependencyArray();
            expandEntries(current, next);
            current = next;
        }
        int totalSize = 0;
        for (DependencyArray a : executionList) {
            totalSize += a.size();
        }

        ArrayList<Long> execArray = new ArrayList<Long>(totalSize);
        for (DependencyArray d : executionList) {
            d.sort();
            for (Dependency dep : d) {
                if (dep.start >= baseCommit) {
                    execArray.add(dep.getKey());
                }
            }
            execArray.add(SEPARATOR);
        }
        return execArray;
    }


    /**
     * Load every request which have been executed concurrently with entry
     * 
     * @param entry
     * @param groupOfparallelRequest
     * @param readyHeap
     * @return
     */
    public void expandEntries(DependencyArray current, DependencyArray next) {
        DependencyArray currentExpanded = new DependencyArray();
        for (Dependency entry : current) {
            long previousEnd = entry.end;
            for (long key : entry.getAfter()) {
                Dependency req = graph.get(key);
                req.countBeforeTmp--;
                if (req.countBeforeTmp == 0) {
                    if (req.start < previousEnd) {
                        currentExpanded.add(req);
                        // executed with current request
                        expandEntry(req, currentExpanded, next);
                    } else {
                        next.add(req);
                    }
                }
            }
        }
        current.add(currentExpanded);
    }

    public void expandEntry(Dependency entry, DependencyArray current, DependencyArray next) {
        long previousEnd = entry.end;
        for (Long childKey : entry.getAfter()) {
            Dependency child = graph.get(childKey);
            child.countBeforeTmp--;
            if (child.countBeforeTmp == 0) {
                if (child.start < previousEnd) {
                    current.add(child);
                    expandEntry(child, current, next);
                    previousEnd = Math.max(previousEnd, child.end);
                } else {
                    next.add(child);
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
        Iterator<Long> it = rootEntry.getAfter().iterator();
        while (it.hasNext()) {
            Long next = it.next();
            Dependency nextNode = graph.get(next);
            if (searchCycleAux(nextNode, root)) {
                it.remove();
                nextNode.countBefore--;
            }
        }
    }

    private Boolean searchCycleAux(Dependency nextNode, Long rootStart) {
        for (long next : nextNode.getAfter()) {
            if (next == rootStart) {
                // found cycle
                if (nextNode.start > rootStart) {
                    nextNode.getAfter().remove(rootStart);
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


    public synchronized void reset() {
        graph.clear();
        rootCandidates.clear();
        if (graphDisplayer != null)
            graphDisplayer.reset();
    }

    /**
     * Retrieves one array with start|end|start|end...
     * 
     * @param array with start|end|start|end...
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

    public String showDepGraph() {
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

    public List<List<Long>> getExecutionList(long baseCommit) {
        restoreCounters();
        List<List<Long>> result = new LinkedList<List<Long>>();
        for (Long rootKey : getRoots()) {
            result.add(getExecutionList(rootKey, baseCommit));
        }
        return result;
    }
}
