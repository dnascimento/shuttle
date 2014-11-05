package pt.inesc.voldemortClient;

import java.util.concurrent.TimeUnit;

import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.StoreClientFactory;
import voldemort.client.TimeoutConfig;
import voldemort.client.protocol.RequestFormatType;
import voldemort.undoTracker.SRD;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

import com.google.protobuf.Message;

/**
 * Lazy creation voldemort store
 * 
 * @author darionascimento
 */
public class VoldemortStore<K, V extends Message> {

    private StoreClient<K, V> store;
    private final String storeName;
    private final String bootstrapUrl;

    public VoldemortStore(String storeName, String bootstrapUrl) {
        this.storeName = storeName;
        this.bootstrapUrl = bootstrapUrl;
    }

    private void init() {
        StoreClientFactory factory = new SocketStoreClientFactory(
                new ClientConfig().setBootstrapUrls(bootstrapUrl)
                                  .setRequestFormatType(RequestFormatType.PROTOCOL_BUFFERS)
                                  .setEnableJmx(true)
                                  .setSocketKeepAlive(true)
                                  .setMaxBootstrapRetries(20)
                                  .setConnectionTimeout(200000000, TimeUnit.MILLISECONDS)
                                  .setFailureDetectorAsyncRecoveryInterval(2000000)
                                  .setFailureDetectorThresholdCountMinimum(2000000)
                                  .setFailureDetectorRequestLengthThreshold(10000)
                                  .setRoutingTimeout(2000000, TimeUnit.MILLISECONDS)
                                  // operation timeout
                                  .setTimeoutConfig(new TimeoutConfig(50000000))
                                  .setSocketTimeout(2000000, TimeUnit.MILLISECONDS));

        store = factory.getStoreClient(storeName);
    }

    public Version put(K key, V value, SRD srd) {
        if (store == null)
            init();
        return store.put(key, value, srd);
    }

    public Versioned<V> get(K key, SRD srd) {
        if (store == null) {
            init();
        }
        return store.get(key, srd);
    }

    public boolean delete(K key, SRD srd) {
        if (store == null) {
            init();
        }
        return store.delete(key, srd);
    }

    public Version put(K key, Versioned<V> versioned, SRD srd) {
        if (store == null) {
            init();
        }
        return store.put(key, versioned, srd);
    }
}
