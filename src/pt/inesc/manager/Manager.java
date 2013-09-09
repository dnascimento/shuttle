package pt.inesc.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.TreeMap;

import pt.inesc.manager.redo.RedoBoss;

public class Manager {
    private static TreeMap<Integer, List<URLVersion>> snapshotList = new TreeMap<Integer, List<URLVersion>>();
    static BufferedReader terminal = new BufferedReader(new InputStreamReader(System.in));
    static SnapshotAPI snapAPI = new SQLSnapshoter();



    public static void main(String[] args) throws Exception {
        System.out.println("INESC Undo Manager");
        while (true) {

            System.out.println("-------------------------------");
            System.out.println("a) Do Snapshot");
            System.out.println("b) Recover from Snapshot");
            System.out.println("c) List Snapshots");

            char option = terminal.readLine().charAt(0);

            switch (option) {
            case 'a':
                doSnapshot();
                break;
            case 'b':
                recoverSnapshot();
                break;
            case 'c':
                listSnapshots();
                break;
            default:
                System.out.println("Invalid Option");
                break;
            }
        }
    }

    private static void doSnapshot() throws Exception {
        System.out.println("Enter snapshot ID: ");
        int id = Integer.parseInt(terminal.readLine());

        List<URLVersion> snap = snapAPI.shot(id);
        snapshotList.put(id, snap);
        System.out.println("Snapshot Done:");
        System.out.println(snap);
        System.out.println("---------------------------");
    }

    private static void recoverSnapshot() throws Exception {
        listSnapshots();
        System.out.println("Choose snapshot ID: ");
        int id = Integer.parseInt(terminal.readLine());
        // Check if belongs to snapshot list
        if (!snapshotList.keySet().contains(id)) {
            System.out.println("Invalid snapshot Id");
            return;
        }

        // Cut request files
        OldSnapCleaner cleaner = new OldSnapCleaner();
        cleaner.clean(id);

        snapAPI.load(snapshotList.get(id));
        // REDO
        RedoBoss boss = new RedoBoss();
        boss.run();

        System.out.println("Load Done");
    }




    private static void listSnapshots() {
        System.out.println("Snapshots ID's:");
        for (Integer id : snapshotList.keySet()) {
            System.out.println(id);
        }

    }
}
