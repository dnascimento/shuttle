/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.manager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import pt.inesc.manager.graph.Dependency;
import pt.inesc.manager.requests.RequestsModifier;
import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.replay.core.ReplayMode;


public class Interface extends
        Thread {
    private final Manager manager;

    public Interface(Manager manager) {
        super();
        this.manager = manager;
    }

    String[] voldemortStores = new String[] { "test", "questionStore", "answerStore", "commentStore", "index" };


    @Override
    public void run() {
        System.out.println("Shuttle - Undo Manager");
        Scanner s = new Scanner(System.in);
        String line;
        while (true) {
            try {
                System.out.println("-------------------------------");
                System.out.println("a) New Commit");
                System.out.println("b) Replay");
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
                    replay(s);
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
        System.out.println("b) Show dependency map");
        System.out.println("c) Create new branch");
        System.out.println("d) Change to branch");
        System.out.println("e) Show database stats");
        System.out.println("g) Measure cassandra");
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
            System.out.println(manager.graph);
            break;
        case 'c':
            Pair<Short, Long> pair = collectBranchAndCommit(s);
            if (pair == null)
                return;
            manager.newBranch(pair.v2, pair.v1);
            break;
        case 'd':
            System.out.println("Enter the branch number:");
            short branch = s.nextShort();
            manager.changeToBranch(branch);
            break;
        case 'e':
            manager.showDatabaseStats();
            break;
        case 'g':
            System.out.println(new CassandraClient().calculateSize());
            break;
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
        System.out.println("d) count");
        System.out.println("e) count dependencies");
        System.out.println("f) count clusters");
        System.out.println("g) graph size summary");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        char[] args = line.toCharArray();
        String fileName;
        switch (args[0]) {
        case 'a':
            System.out.println("Enter the filename: ");
            fileName = s.nextLine();
            manager.loadGraph(fileName);
            break;
        case 'b':
            System.out.println("Enter the filename: ");
            fileName = s.nextLine();
            manager.saveGraph(fileName);
            break;
        case 'c':
            manager.resetGraph();
            break;
        case 'd':
            System.out.println(manager.graph.countDependencies());
            break;
        case 'e':
            System.out.println("Enter the rids separated by space (or a .txt file)");
            String command = s.nextLine();
            if (command.contains(".txt")) {
                StringBuilder sb = new StringBuilder();
                BufferedReader in = new BufferedReader(new FileReader(command));
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
                in.close();
                command = sb.toString();
            }

            String[] ridString = command.split(" ");
            long[] rids = new long[ridString.length];
            for (int i = 0; i < rids.length; i++) {
                rids[i] = Long.parseLong(ridString[i]);
            }
            HashMap<Long, Dependency> result = manager.graph.countAffected(rids);
            System.out.println("Total: " + result.size());
            System.out.println(result);
            break;
        case 'f':
            List<List<Long>> clusters = manager.graph.replay(0, ReplayMode.allParallel, null).list;
            System.out.println(clusters.size() + " independent clusters");
            break;
        case 'g':
            System.out.println(manager.graph.getTotalByteSize());
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
        System.out.println("g) Set of Voldemort store");
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
            manager.cleanVoldemort(voldemortStores);
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
            break;
        case 'g':
            System.out.println("Enter store names (splitted by space): " + Arrays.toString(voldemortStores));
            String[] store = s.nextLine().split(" ");
            manager.cleanVoldemort(store);
            break;
        default:
            return;
        }
    }

    private void replay(Scanner s) throws Exception {
        Pair<Short, Long> pair = collectBranchAndCommit(s);
        if (pair == null)
            return;
        long commit = pair.v2;
        short branch = pair.v1;

        System.out.println("Enter the recovery mode: \n 0- all in serial \n 1- all in parallel \n 2- selective in serial \n 3 - selective in parallel, use 10 11 12 13 to see the execution list");
        int opt = Integer.parseInt(s.nextLine());
        boolean viewList = false;
        if (opt >= 10) {
            viewList = true;
            opt = opt % 10;
        }

        ArrayList<Long> attackSource = null;
        if (opt == 2 || opt == 3) {
            System.out.println("Enter the intrusion source requests (spaced)");
            String[] entries = s.nextLine().split(" ");
            attackSource = new ArrayList<Long>(entries.length);
            for (int i = 0; i < entries.length; i++) {
                attackSource.add(new Long(entries[i]));
            }
        }

        ReplayMode replayMode = ReplayMode.castFromInt(opt);
        if (viewList) {
            List<List<Long>> execLists = manager.graph.replay(commit, replayMode, attackSource).list;
            for (List<Long> l : execLists) {
                for (Long e : l) {
                    System.out.println(e);
                }
            }
        } else {
            manager.replay(commit, branch, replayMode, attackSource);
        }
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
