/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

public class Dependency
        implements Comparable<Dependency>, Serializable {

    private static final long serialVersionUID = 1L;

    /** request start timestamp. It is also the ID */
    long start;

    /** request end timestamp */
    long end;


    int countBeforeTmp;

    /** How many requests must execute before this */
    int countBefore;

    /** IDs which this entry depends from */
    private final HashSet<Long> before = new HashSet<Long>();


    /** IDs dependent from entry */
    private final HashSet<Long> after = new HashSet<Long>();

    public Dependency(long key) {
        start = key;
    }

    public Boolean hasAfter() {
        return !after.isEmpty();
    }

    public Long[] getArrayAfter() {
        return after.toArray(new Long[0]);
    }

    public HashSet<Long> getAfter() {
        return after;
    }

    public boolean addAfter(Long key) {
        return after.add(key);

    }

    public boolean hasAfter(Long dep) {
        return after.contains(dep);
    }




    @Override
    public int compareTo(Dependency o) {
        return (int) (this.start - o.start);
    }

    public Long getKey() {
        return start;
    }

    public boolean addPrevious(List<Long> dependencies) {
        return before.addAll(dependencies);

    }

    public HashSet<Long> getBefore() {
        return before;
    }

    @Override
    public String toString() {
        return start + " : " + end;
    }
}
