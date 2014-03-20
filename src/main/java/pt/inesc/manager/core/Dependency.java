package pt.inesc.manager.core;

import java.util.HashSet;

public class Dependency
        implements Comparable<Dependency> {
    /** request start timestamp. It is also the ID */
    long start;

    /** request end timestamp */
    long end;

    /** How many requests must execute before this */
    int countBefore;

    /** IDs dependent from entry */
    private final HashSet<Long> after = new HashSet<Long>();

    public Boolean hasAfter() {
        return !after.isEmpty();
    }

    public HashSet<Long> getAfter() {
        return after;
    }

    public void addAfter(Long key) {
        after.add(key);

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

    public void removeAfter(Long key) {
        after.remove(key);
    }
}
