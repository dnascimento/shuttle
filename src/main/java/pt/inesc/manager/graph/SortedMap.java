package pt.inesc.manager.graph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class SortedMap<V>
        implements Iterable<V>, Serializable {

    private static final long serialVersionUID = 1L;

    private final HashMap<Long, V> map = new HashMap<Long, V>();
    private transient SortedMapIterator currentIterator = null;
    private int sizeOfLastKeySetIterated = -1;

    public void put(Long key, V value) {
        map.put(key, value);
    }

    public V get(Long key) {
        return map.get(key);
    }

    public SortedMapIterator getIterator() {
        if (currentIterator != null && sizeOfLastKeySetIterated == map.size()) {
            currentIterator.reset();
            return currentIterator;
        }
        currentIterator = new SortedMapIterator(map);
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

    public class SortedMapIterator
            implements Iterator<V> {

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
            Arrays.sort(sortedKeys);
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


}
