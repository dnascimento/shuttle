package pt.inesc.manager.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

public class SortedMap<K, V>
        implements Iterable<V> {

    private final HashMap<K, V> map = new HashMap<K, V>();
    private final PriorityQueue<K> sortedQueue = new PriorityQueue<K>();

    public void put(K key, V value) {
        V existing = map.put(key, value);
        if (existing == null) {
            sortedQueue.add(key);
        }
    }

    public V get(K key) {
        return map.get(key);
    }

    @Override
    public Iterator<V> iterator() {
        return new SortedMapIterator(map, sortedQueue);
    }


    public class SortedMapIterator
            implements Iterator<V> {

        private final HashMap<K, V> map;
        private final Iterator<K> it;
        K currentKey;

        public SortedMapIterator(HashMap<K, V> map, PriorityQueue<K> sortedQueue) {
            this.map = map;
            it = sortedQueue.iterator();
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public V next() {
            currentKey = it.next();
            return map.get(currentKey);
        }

        @Override
        public void remove() {
            if (currentKey == null) {
                throw new IllegalStateException();
            }
            it.remove();
            map.remove(currentKey);
        }
    }


    public void clear() {
        map.clear();
        sortedQueue.clear();
    }

    public int size() {
        return map.size();
    }

}
