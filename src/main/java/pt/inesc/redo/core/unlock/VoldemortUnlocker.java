package pt.inesc.redo.core.unlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.StoreClientFactory;
import voldemort.client.protocol.RequestFormatType;
import voldemort.undoTracker.KeyAccess;
import voldemort.undoTracker.RUD;
import voldemort.utils.ByteArray;

public class VoldemortUnlocker {

    ConcurrentHashMap<String, StoreClient<ByteArray, Object>> cache;


    public VoldemortUnlocker() {
        cache = new ConcurrentHashMap<String, StoreClient<ByteArray, Object>>();
    }

    public StoreClient<ByteArray, Object> get(String storeName) {
        StoreClient<ByteArray, Object> s = cache.get(storeName);
        if (s == null) {
            newClient(storeName);
        }
        return cache.get(storeName);
    }

    private void newClient(String storeName) {
        StoreClientFactory factory = new SocketStoreClientFactory(
                new ClientConfig().setBootstrapUrls("tcp://localhost:6666")
                                  .setRequestFormatType(RequestFormatType.PROTOCOL_BUFFERS));
        StoreClient<ByteArray, Object> s = factory.getStoreClient(storeName);
        cache.putIfAbsent(storeName, s);
    }

    /**
     * @param accessedKeys Not empty set of keys to unlock
     */
    public void unlockKeys(Set<KeyAccess> unlockKeys, RUD rud) {
        ArrayList<KeyAccess> list = new ArrayList<KeyAccess>(unlockKeys);
        // sort by store
        Collections.sort(list);
        String lastStore = list.get(0).store;
        StoreClient<ByteArray, Object> client;
        ArrayList<ByteArray> keys = new ArrayList<ByteArray>();

        for (int i = 0; i < list.size(); i++) {
            KeyAccess k = list.get(i);
            if (k.store.equals(lastStore)) {
                keys.add(k.key);
            } else {
                client = get(lastStore);
                client.unlockKeys(keys, rud);
                keys.clear();
                lastStore = k.store;
                keys.add(k.key);
            }
        }
    }

}
