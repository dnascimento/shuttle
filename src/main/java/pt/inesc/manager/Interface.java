/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import pt.inesc.manager.requests.RequestsModifier;
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
        System.out.println("Shuttle - Undo Manager");
        Scanner s = new Scanner(System.in);
        String line;
        while (true) {
            try {
                System.out.println("-------------------------------");
                System.out.println("a) New Commit");
                System.out.println("b) Redo");
                System.out.println("c) Clean");
                System.out.println("d) Branches");
                System.out.println("e) Graph");
                System.out.println("f) Advanced");
                System.out.println("g) Requests");
                System.out.println("h) Show graph");
                do {
                    line = s.nextLine();
                } while (line.length() == 0);
                char[] args = line.toCharArray();
                switch (args[0]) {
                case 'a':
                    commit(s);
                    break;
                case 'b':
                    redo(s);
                    break;
                case 'c':
                    clean(s);
                    break;
                case 'd':
                    branches(s);
                    break;
                case 'e':
                    graph(s);
                    break;
                case 'f':
                    advanced(s);
                    break;
                case 'g':
                    requests(s);
                    break;
                case 'h':
                    manager.showGraph();
                    break;
                default:
                    System.out.println("Invalid Option");
                    break;
                }
            } catch (NumberFormatException e1) {
                System.err.println("Invalid arguments");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void advanced(Scanner s) throws Exception {
        System.out.println("a) Timetravel proxy");
        System.out.println("b) Show graph roots");
        System.out.println("d) Show dependency map");
        System.out.println("e) Create new branch");
        System.out.println("f) Change to branch");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        char[] args = line.toCharArray();
        switch (args[0]) {
        case 'a':
            System.out.println("Enter instant (time (secounds) from now (negative to travel to past):");
            long delay = Long.parseLong(s.nextLine());
            long instant = System.currentTimeMillis() + (delay * 1000);
            manager.proxyTimeTravel(instant);
            String dateString = new SimpleDateFormat("H:m:S").format(new Date(instant));
            System.out.println("Traveling to: " + dateString);
            break;
        case 'b':
            System.out.println(manager.graph.getRoots());
            break;
        case 'd':
            System.out.println(manager.graph);
            break;
        case 'e':
            Pair<Short, Long> pair = collectBranchAndCommit(s);
            if (pair == null)
                return;
            manager.newBranch(pair.v2, pair.v1);
        case 'f':
            System.out.println("Enter the branch number:");
            short branch = s.nextShort();
            manager.changeToBranch(branch);
        default:
            return;
        }

    }




    private void commit(Scanner s) throws Exception {
        System.out.println("Enter commit instant (time (secounds) from now):");
        long delay = Long.parseLong(s.nextLine());
        long instant = System.currentTimeMillis() + (delay * 1000);
        manager.newCommit(instant);
        String dateString = new SimpleDateFormat("H:m:S").format(new Date(instant));
        System.out.println("Commit scheduled to: " + dateString);
    }


    private void graph(Scanner s) throws Exception {
        System.out.println("a) load");
        System.out.println("b) save");
        System.out.println("c) reset");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        char[] args = line.toCharArray();
        switch (args[0]) {
        case 'a':
            manager.loadGraph();
            break;
        case 'b':
            manager.saveGraph();
            break;
        case 'c':
            manager.resetGraph();
            break;
        default:
            return;
        }

    }



    private void branches(Scanner s) throws IOException {
        System.out.println(manager.branches.show());
        System.out.println("\n");
        System.out.println("Delete some? (enter to continue)");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        Short branch = Short.parseShort(line);
        manager.deleteBranch(branch);
    }



    private void clean(Scanner s) throws IOException {
        System.out.println("a) ALL");
        System.out.println("b) Cassandra");
        System.out.println("c) Voldemort");
        System.out.println("d) Database access lists");
        System.out.println("e) Manager graph");
        System.out.println("f) Reset branches");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        char[] args = line.toCharArray();
        boolean all = false;
        switch (args[0]) {
        case 'a':
            all = true;
            if (!all)
                break;
        case 'b':
            new CassandraClient().truncatePackageTable();
            if (!all)
                break;
        case 'c':
            manager.cleanVoldemort();
            if (!all)
                break;
        case 'd':
            manager.resetDatabaseAccessLists();
            System.out.println("Cleaned");
            if (!all)
                break;
        case 'e':
            manager.resetGraph();
            if (!all)
                break;
        case 'f':
            manager.resetBranch();
            if (!all)
                break;
        default:
            return;
        }
    }

    private void redo(Scanner s) throws Exception {
        Pair<Short, Long> pair = collectBranchAndCommit(s);
        if (pair == null)
            return;
        manager.replay(pair.v2, pair.v1, null);
    }

    private Pair<Short, Long> collectBranchAndCommit(Scanner s) throws Exception {
        boolean showed = false;
        Short branch = 0;
        Long commit = 0L;
        while (true) {
            System.out.println("Select base branch and commit (press enter to visualize the tree):");
            String line = s.nextLine();
            if (line.isEmpty()) {
                if (showed) {
                    return null;
                } else {
                    System.out.println(manager.branches.show());
                    showed = true;
                    continue;
                }
            }
            String[] args = line.split(" ");
            if (args.length != 2)
                throw new Exception("invalid arguments");
            branch = Short.parseShort(args[0]);
            commit = Long.parseLong(args[1]);
            break;
        }
        return new Pair<Short, Long>(branch, commit);
    }

    private void requests(Scanner s) throws Exception {
        System.out.println("a) list");
        System.out.println("b) print");
        System.out.println("c) edit");
        System.out.println("d) delete");
        String line = s.nextLine();
        if (line.length() == 0)
            return;

        RequestsModifier rm = new RequestsModifier();
        long reqId;
        char[] args = line.toCharArray();
        switch (args[0]) {
        case 'a':
            System.out.println(rm.listRequests());
            break;
        case 'b':
            System.out.println("Enter request id:");
            reqId = Long.parseLong(s.nextLine());
            System.out.println(rm.showRequest(reqId));
            break;
        case 'c':
            System.out.println("Edit request id:");
            reqId = Long.parseLong(s.nextLine());
            rm.editRequest(reqId);
            break;
        case 'd':
            System.out.println("Enter request id:");
            reqId = Long.parseLong(s.nextLine());
            rm.deleteRequest(reqId);
            break;
        default:
            return;
        }

    }

    class Pair<T1, T2> {
        T1 v1;
        T2 v2;

        public Pair(T1 v1, T2 v2) {
            super();
            this.v1 = v1;
            this.v2 = v2;
        }
    }
}
