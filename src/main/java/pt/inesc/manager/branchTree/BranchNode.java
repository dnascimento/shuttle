package pt.inesc.manager.branchTree;


public class BranchNode
        implements Comparable<BranchNode> {
    public final long snapshot;
    public final short branch;
    public final BranchNode parent;

    public BranchNode(long snapshot, short branch, BranchNode parent) {
        super();
        this.snapshot = snapshot;
        this.branch = branch;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "[b:" + branch + ", c:" + snapshot + ", p" + parent + "]";
    }

    public int compareTo(BranchNode o) {
        if (snapshot != o.snapshot) {
            return (int) (snapshot - o.snapshot);
        }
        return branch - o.branch;
    }

}
