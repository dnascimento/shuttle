package pt.inesc.manager.branchTree;


import java.util.Iterator;
import java.util.LinkedList;

public class BranchTree {
    public static final long INIT_COMMIT = 0L;
    public static final short INIT_BRANCH = 0;
    public short currentBranch = INIT_BRANCH;




    /**
     * Track the most recent snapshot of each branch
     */
    LinkedList<BranchNode> branches;
    LinkedList<Long> commits;

    public BranchTree() {
        branches = new LinkedList<BranchNode>();
        commits = new LinkedList<Long>();
        branches.addLast(new BranchNode(INIT_COMMIT, INIT_BRANCH, null));
        commits.addLast(INIT_COMMIT);
    }

    /**
     * Track the path described by one of branches
     * 
     * @param commit
     * @param branch
     * @return
     * @throws Exception
     */
    public LinkedList<BranchNode> getPath(long commit, short branch) throws Exception {
        BranchNode c = null;
        for (BranchNode n : branches) {
            if (n.commit == commit && n.branch == branch) {
                c = n;
                break;
            }
        }
        if (c == null) {
            throw new Exception("Can not find the path of provided commit and branch");
        }
        LinkedList<BranchNode> path = new LinkedList<BranchNode>();
        while (c != null) {
            path.add(c);
            c = c.parent;
        }
        return path;
    }

    /**
     * Fork a commit/branch pair to a new branch
     * 
     * @param parentCommit
     * @param parentBranch
     * @param newCommit
     * @return the path of the new branch
     * @throws Exception
     */
    public short fork(long parentCommit, short parentBranch) throws Exception {
        currentBranch++;
        BranchNode c = null;
        for (BranchNode n : branches) {
            if (n.branch == parentBranch) {
                c = n;
                break;
            }
        }

        while (c != null && c.commit != parentCommit) {
            c = c.parent;
        }
        if (c == null) {
            throw new Exception("Can not find the required commit and branch to fork");
        }

        BranchNode newNode = new BranchNode(parentCommit, currentBranch, c);
        branches.addLast(newNode);
        return currentBranch;
    }

    /**
     * Add a new commit to the branch
     * 
     * @param commit
     * @param branch
     * @throws Exception
     */
    public void addCommit(long commit) throws Exception {
        BranchNode c = null;
        Iterator<BranchNode> it = branches.iterator();

        while (it.hasNext()) {
            c = it.next();
            if (c.branch == currentBranch) {
                break;
            }
        }

        if (c == null) {
            throw new Exception("Can not find the required branch to create a new snapshot");
        }
        it.remove();
        BranchNode newNode = new BranchNode(commit, currentBranch, c);
        branches.addLast(newNode);
        commits.addLast(commit);
    }

    @Override
    public String toString() {
        return "BranchTree [currentBranch=" + currentBranch + ", branches=" + branches + "]";
    }

    public String show() {
        int nBranches = branches.size();
        int nCommits = commits.size();
        char[][] table = new char[nCommits][nBranches];

        // expand the path of every branch
        for (BranchNode base : branches) {
            BranchNode k = base;
            do {
                int nCommit = commits.indexOf(k.commit);
                table[nCommit][base.branch] = 'x';
                k = k.parent;
                if (k == null || k.branch != base.branch) {
                    table[nCommit][base.branch] = 'o';
                    break;
                }
            } while (k != null);
        }
        // set the |
        for (int i = 0; i < nBranches; i++) {
            boolean set = false;
            for (int k = 0; k < nCommits; k++) {
                if (table[k][i] == 'o') {
                    set = true;
                }
                if (table[k][i] == '\u0000' && set) {
                    table[k][i] = '|';
                }
            }
        }

        // display
        StringBuilder sb = new StringBuilder();
        sb.append("               ");
        for (int k = 0; k < nCommits; k++) {
            sb.append(String.format("%02d", k));
        }
        sb.append("\n");
        for (int k = 0; k < nCommits; k++) {
            sb.append(String.format("%013d", commits.get(k)));
            sb.append(" :");
            for (int i = 0; i < nBranches; i++) {
                sb.append(table[k][i]);
                sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
