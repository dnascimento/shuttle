package pt.inesc.manager;

import java.util.ArrayList;
import java.util.Scanner;

import pt.inesc.proxy.save.CassandraClient;


public class Interface extends
        Thread {
    private final Manager manager;

    public Interface(Manager manager) {
        super();
        this.manager = manager;
    }



    @Override
    public void run() {
        System.out.println("INESC Undo Manager");
        manager.showGraph();
        @SuppressWarnings("resource")
        Scanner s = new Scanner(System.in);
        while (true) {
            try {
                System.out.println("-------------------------------");
                System.out.println("a) Schedule a Snapshot:");
                System.out.println("b) REDO");
                System.out.println("c) Clean Cassandra");
                System.out.println("d) Clean Voldemort");
                System.out.println("e) Clean database nodes dependencies");
                System.out.println("f) Redo from root");
                System.out.println("g) load graph");
                System.out.println("h) save graph");
                System.out.println("i) reset graph");
                System.out.println("j) Set branch and restrain");
                System.out.println("k) Proxy time travel:");
                System.out.println("l) Set database branch");
                String line = s.nextLine();
                if (line.length() == 0)
                    continue;
                char[] args = line.toCharArray();
                switch (args[0]) {
                case 'a':
                    System.out.println("Enter snapshot instant (time (secounds) from now):");
                    long delay = s.nextLong();
                    manager.setNewSnapshotInstant(System.currentTimeMillis() + (delay * 1000));
                    break;
                case 'b':
                    System.out.println("Enter the branch ID (last was: " + manager.lastBranch + "):");
                    short b1 = s.nextShort();
                    manager.redoProcedure(b1);
                case 'c':
                    cleanCassandra();
                    break;
                case 'd':
                    manager.cleanVoldemort();
                    break;
                case 'e':
                    manager.resetDataNodesDependenices();
                    break;
                case 'f':
                    System.out.println(manager.getRoots());
                    System.out.println("Enter the branch ID (last was: " + manager.lastBranch + "):");
                    short b = s.nextShort();
                    System.out.println("Enter the root: (enter to all roots, multi separated by comma)");
                    String rootArgs = s.nextLine();
                    String[] tokens = rootArgs.split(",");
                    ArrayList<Long> roots = new ArrayList<Long>();
                    for (String t : tokens) {
                        roots.add(Long.parseLong(t));
                    }
                    manager.redoFromRoot(roots, b);
                    break;
                case 'g':
                    manager.loadGraph();
                    break;
                case 'h':
                    manager.saveGraph();
                    break;
                case 'i':
                    manager.resetGraph();
                    break;
                case 'j':
                    System.out.println("Enter the branch number and restrain: <number> <t/f>");
                    short branch = s.nextShort();
                    char boo = s.next().toCharArray()[0];
                    manager.setProxyBranchAndRestrain(branch, (boo == 't'));
                    break;
                case 'k':
                    System.out.println("Enter time travel instant (negative to past, positive to future) (secounds):");
                    long travel = s.nextLong();
                    manager.proxyTimeTravel(travel * 1000);
                    break;
                case 'l':
                    System.out.println("Enter the branch ID (last was: " + manager.lastBranch + "):");
                    short b2 = s.nextShort();
                    manager.setNewBranchInDatabaseNodes(b2);
                default:
                    System.out.println("Invalid Option");
                    break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void cleanCassandra() {
        new CassandraClient().truncatePackageTable();
    }



}
