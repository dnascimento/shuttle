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
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /** request start timestamp. It is also the ID */
    public long start;

    /** request end timestamp */
    public long end;

    /** IDs which this entry depends from */
    public final HashSet<Long> before = new HashSet<Long>();

    /** IDs dependent from entry */
    public final HashSet<Long> after = new HashSet<Long>();

    public boolean visited;

    public Dependency(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public void addBefore(List<Long> dependencies) {
        before.addAll(dependencies);
    }

    /**
     * Add a new link to a request that will execute after
     * 
     * @param key
     * @return true if is a new requests
     */
    public boolean addAfter(Long key) {
        return after.add(key);
    }



    @Override
    public String toString() {
        return start + " : " + end;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((after == null) ? 0 : after.hashCode());
        result = prime * result + ((before == null) ? 0 : before.hashCode());
        result = prime * result + (int) (end ^ (end >>> 32));
        result = prime * result + (int) (start ^ (start >>> 32));
        return result;
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
        if (before == null) {
            if (other.before != null)
                return false;
        } else if (!before.equals(other.before))
            return false;
        if (end != other.end)
            return false;
        if (start != other.start)
            return false;
        return true;
    }
}
