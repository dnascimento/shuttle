/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;



public class SelectiveDepGraph extends
        DepGraph {
    private static final long serialVersionUID = 1L;

    @Override
    public void addNode(Long from, Long to) {
        DependencyDouble fromEntry = (DependencyDouble) getOrCreateNode(from);
        boolean isNew = fromEntry.addAfter(to);
        if (isNew) {
            DependencyDouble toNode = (DependencyDouble) getOrCreateNode(to);
            toNode.addPrevious(from);
            toNode.countBefore++;
        }

    }

    @Override
    public Dependency getOrCreateNode(Long key) {
        if (!graph.containsKey(key)) {
            graph.put(key, new DependencyDouble(key));
        }
        return graph.get(key);
    }



    @Override
    public List<List<Long>> selectiveReplayList(long baseCommit, List<Long> attackSource) throws Exception {
        restoreCounters();

        // expand the set to get which requests are tainted
        List<Long> tainted = expandForward(this, attackSource);
        // backtrack to get the requests to replay: return the roots.
        List<Long> toReplayRoots = expandBack(tainted, baseCommit);

        // set toReplayRoots to zero before
        for (Long root : toReplayRoots) {
            Dependency dep = getNode(root);
            dep.countBeforeTmp = 0;
        }
        // create the exec list of the subgraph (given the toReplay)
        return GraphUtils.getExecutionList(baseCommit, toReplayRoots, this);
    }

    /**
     * Get the requests affected by the attackSourceSet.
     * 
     * @param selectiveDepGraph
     * @param attackSource
     * @return The set of leaf requests, the ones which do not have a following requests
     */
    private List<Long> expandForward(SelectiveDepGraph selectiveDepGraph, List<Long> attackSource) {
        // all items affected by the source attack
        HashSet<Long> tainted = new HashSet<Long>();
        // all items which do not have a following dependency requests
        LinkedList<Long> leafs = new LinkedList<Long>();
        // LIFO queue to do a DSF on graph
        LinkedList<Long> queue = new LinkedList<Long>();

        queue.addAll(attackSource);
        tainted.addAll(attackSource);

        while (!queue.isEmpty()) {
            Long key = queue.removeFirst();
            Dependency dep = getNode(key);
            if (dep.getAfter().isEmpty()) {
                leafs.add(key);
            } else {
                for (Long after : dep.getAfter()) {
                    if (!tainted.contains(after)) {
                        queue.addFirst(after);
                        tainted.add(after);
                    }
                }
            }
        }
        return leafs;
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
    private List<Long> expandBack(List<Long> taintedLeafs, long baseCommit) {
        // all accessed items
        HashSet<Long> toReplay = new HashSet<Long>();
        // all items which do not have a following dependency requests
        LinkedList<Long> roots = new LinkedList<Long>();
        // LIFO queue to do a DSF on graph from the leafs
        LinkedList<Long> queue = new LinkedList<Long>();

        queue.addAll(taintedLeafs);
        toReplay.addAll(taintedLeafs);

        while (!queue.isEmpty()) {
            Long key = queue.removeFirst();
            DependencyDouble dep = (DependencyDouble) getNode(key);
            boolean isRoot = true;

            // root if all before elements are previous to the baseCommit
            for (Long before : dep.getBefore()) {
                DependencyDouble depBefore = (DependencyDouble) getNode(before);
                // ignore keys before the commit
                if (depBefore.start >= baseCommit) {
                    isRoot = false;
                    queue.add(before);
                }
            }
            if (isRoot) {
                roots.add(key);
            }
        }
        return roots;
    }

}
