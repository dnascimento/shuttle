package pt.inesc.manager.graph;

import java.util.HashSet;

public class DependencyDouble extends
        Dependency {

    private static final long serialVersionUID = 1L;

    public DependencyDouble(long key) {
        super(key);
    }

    /** IDs which this entry depends from */
    private final HashSet<Long> before = new HashSet<Long>();

    public boolean addPrevious(Long from) {
        return before.add(from);

    }

    public HashSet<Long> getBefore() {
        return before;
    }


}
