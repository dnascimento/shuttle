/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class DepGraphDoubleLinked extends
        DepGraph {

    private static final long serialVersionUID = 1L;

    @Override
    public synchronized void addDependencies(Long key, List<Long> dependencies) {
        super.addDependencies(key, dependencies);
        getEntry(key).addPrevious(dependencies);
    }

    /**
     * Based on a source set of requests, taint (forward) the requests which are affected
     * by them.
     * 
     * @param source
     * @param baseRid
     * @return
     */
    public Set<Long> expandAffected(List<Long> source) {
        HashSet<Long> affected = new HashSet<Long>();
        while (!source.isEmpty()) {
            Long entry = source.remove(0);
            if (affected.contains(entry)) {
                continue;
            }
            source.addAll(graph.get(entry).getAfter());
            affected.add(entry);
        }
        return affected;
    }

    /**
     * Based on a set of requests, find which are needed to replay
     * 
     * @param source
     * @param baseSnapshot
     * @return
     */
    public Set<Long> expandAll(List<Long> source, long baseSnapshot) {
        HashSet<Long> dependent = new HashSet<Long>();
        while (!source.isEmpty()) {
            Long entry = source.remove(0);
            if (dependent.contains(entry) || entry < baseSnapshot) {
                continue;
            }
            source.addAll(graph.get(entry).getAfter());
            source.addAll(graph.get(entry).getBefore());
            dependent.add(entry);
        }
        return dependent;
    }
}
