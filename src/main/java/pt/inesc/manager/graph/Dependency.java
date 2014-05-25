/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.HashSet;

public class Dependency
        implements Comparable<Dependency>, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /** request start timestamp. It is also the ID */
    long start;

    /** request end timestamp */
    long end;


    int countBeforeTmp;

    /** How many requests must execute before this */
    int countBefore;

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
    public String toString() {
        return start + " : " + end;
    }


    public int compareTo(Dependency o) {
        return (int) (this.start - o.start);
    }

    public Long getKey() {
        return start;
    }


}
