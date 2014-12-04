package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;

public class SortedMapIterator<V>
        implements Iterator<V>, Serializable {
    private static final Logger LOGGER = LogManager.getLogger(Manager.class.getName());

    private static final long serialVersionUID = 1L;
    private final HashMap<Long, V> map;
    private final long[] sortedKeys;
    int pointer;

    public SortedMapIterator(HashMap<Long, V> map) {
        this.map = map;
        Set<Long> keys = map.keySet();
        sortedKeys = new long[keys.size()];
        int i = 0;
        for (Long key : keys) {
            sortedKeys[i++] = key;
        }
        if (sortedKeys.length > 1) {
            LOGGER.info("Will sort the keys");
            Arrays.sort(sortedKeys);
            LOGGER.info("Keys sorted");
        }
    }


    public V getBiggestValue() {
        return map.get(sortedKeys[sortedKeys.length - 1]);
    }

    public Long getBiggestKey() {
        return sortedKeys[sortedKeys.length - 1];
    }

    @Override
    public boolean hasNext() {
        return pointer != sortedKeys.length;
    }

    @Override
    public V next() {
        long key = sortedKeys[pointer++];
        return map.get(key);
    }

    @Override
    public void remove() {
        if (pointer == sortedKeys.length) {
            throw new IllegalStateException();
        }
        long key = sortedKeys[pointer++];
        map.remove(key);
    }

    public void reset() {
        pointer = 0;
    }
}
