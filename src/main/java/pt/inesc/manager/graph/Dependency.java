/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;

public class Dependency
        implements Comparable<Dependency>, Serializable {

    private static final long serialVersionUID = 1L;

    // To detect cycles
    /** preorder number **/
    int preorder;
    /** check strong componenet containing v **/
    boolean chk;
    /** to check if v is visited **/
    boolean visited;






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

    public Dependency(long key, Long... dependencies) {
        start = key;
        after.addAll(Arrays.asList(dependencies));
    }


    public Boolean hasAfter() {
        return !after.isEmpty();
    }

    public int getCountBefore() {
        return countBefore;
    }

    public HashSet<Long> cloneAfter() {
        HashSet<Long> clone = new HashSet<Long>();
        for (Long entry : after) {
            clone.add(entry);
        }
        return clone;
    }

    public HashSet<Long> getAfter() {
        return after;
    }

    public boolean addAfter(Long key) {
        return after.add(key);

    }

    public boolean isAfter(Long dep) {
        return after.contains(dep);
    }



    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Dependency other = (Dependency) obj;
        if (after == null) {
            if (other.after != null)
                return false;
        } else if (!after.equals(other.after))
            return false;
        if (countBefore != other.countBefore)
            return false;
        if (countBeforeTmp != other.countBeforeTmp)
            return false;
        if (end != other.end)
            return false;
        if (start != other.start)
            return false;
        return true;
    }

    @Override
    public int compareTo(Dependency o) {
        return (int) (this.start - o.start);
    }

    public Long getKey() {
        return start;
    }

    @Override
    public String toString() {
        return start + " : " + end;
    }
}
