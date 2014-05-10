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
        manager.showGraph();
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
        System.out.println("a) Cassandra");
        System.out.println("b) Voldemort");
        System.out.println("c) Database access lists");
        System.out.println("d) Manager graph");
        String line = s.nextLine();
        if (line.length() == 0)
            return;
        char[] args = line.toCharArray();
        switch (args[0]) {
        case 'a':
            new CassandraClient().truncatePackageTable();
            break;
        case 'b':
            manager.cleanVoldemort();
            break;
        case 'c':
            manager.resetDatabaseAccessLists();
            System.out.println("Cleaned");
            break;
        case 'd':
            manager.resetGraph();
            break;
        default:
            return;
        }
    }

    private void redo(Scanner s) throws Exception {
        boolean showed = false;
        short branch = 0;
        long commit = 0;
        while (true) {
            System.out.println("Select base branch and commit (enter 's' to visualize the tree):");
            String line = s.nextLine();
            if (line.isEmpty()) {
                if (showed) {
                    return;
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

        manager.redo(commit, branch);
    }
}
