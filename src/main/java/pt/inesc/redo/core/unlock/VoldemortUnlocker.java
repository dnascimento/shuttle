package pt.inesc.redo.core.unlock;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.StoreClientFactory;
import voldemort.client.protocol.RequestFormatType;
import voldemort.undoTracker.KeyAccess;
import voldemort.undoTracker.RUD;
import voldemort.utils.ByteArray;

import com.google.common.collect.ArrayListMultimap;

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
                new ClientConfig().setBootstrapUrls("tcp://localhost:6666").setRequestFormatType(RequestFormatType.PROTOCOL_BUFFERS));
        StoreClient<ByteArray, Object> s = factory.getStoreClient(storeName);
        cache.putIfAbsent(storeName, s);
    }

    /**
     * @param accessedKeys Not empty set of keys to unlock
     */
    public void unlockKeys(ArrayListMultimap<ByteArray, KeyAccess> unlockedKeys, RUD rud) {
        ArrayListMultimap<String, ByteArray> perStore = invertToPerStore(unlockedKeys);
        StoreClient<ByteArray, Object> client;

        for (String store : perStore.keySet()) {
            List<ByteArray> accesses = perStore.get(store);
            client = get(store);
            client.unlockKeys(accesses, rud);
        }
    }



    /**
     * Group per store
     * 
     * @param unlockedKeys
     * @return
     */
    private ArrayListMultimap<String, ByteArray> invertToPerStore(ArrayListMultimap<ByteArray, KeyAccess> unlockedKeys) {
        ArrayListMultimap<String, ByteArray> perStore = ArrayListMultimap.create();
        for (ByteArray key : unlockedKeys.keySet()) {
            for (KeyAccess access : unlockedKeys.get(key)) {
                access.setKey(key);
                perStore.put(access.store, access.key);
            }
        }
        return perStore;
    }
}
