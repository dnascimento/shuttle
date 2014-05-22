package pt.inesc.manager.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class DependencyArray
        implements Iterable<Dependency> {

    ArrayList<Dependency> array;

    public DependencyArray() {
        array = new ArrayList<Dependency>();
    }


    public DependencyArray(Dependency root) {
        this();
        array.add(root);
    }


    public void add(Dependency child) {
        array.add(child);
    }

    public Iterator<Dependency> iterator() {
        return array.iterator();
    }

    public boolean isEmpty() {
        return array.isEmpty();
    }

    public int size() {
        return array.size();
    }



    public void sort() {
        Collections.sort(array);
    }

}
