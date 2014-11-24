package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;

public class SortedMap<V>
        implements Iterable<V>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(Manager.class.getName());

    private final HashMap<Long, V> map;
    private transient SortedMapIterator<V> currentIterator = null;
    private int sizeOfLastKeySetIterated = -1;

    public SortedMap() {
        map = new HashMap<Long, V>();
    }

    public SortedMap(int initSize) {
        map = new HashMap<Long, V>(initSize);
    }

    public void put(Long key, V value) {
        map.put(key, value);
    }

    public V get(Long key) {
        return map.get(key);
    }

    public SortedMapIterator<V> getIterator() {
        if (currentIterator != null && sizeOfLastKeySetIterated == map.size()) {
            currentIterator.reset();
            return currentIterator;
        }
        currentIterator = new SortedMapIterator<V>(map);
        sizeOfLastKeySetIterated = map.size();
        return currentIterator;
    }



    public long getBiggestKey() {
        return getIterator().getBiggestKey();
    }




    @Override
    public Iterator<V> iterator() {
        return getIterator();
    }


    public void clear() {
        map.clear();
        currentIterator = null;
        sizeOfLastKeySetIterated = 0;
    }

    public int size() {
        return map.size();
    }


    public HashMap<Long, V> getMap() {
        return map;
    }

    public void deleteIterator() {
        currentIterator = null;
    }
}
