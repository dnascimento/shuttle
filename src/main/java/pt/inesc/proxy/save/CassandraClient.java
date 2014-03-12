package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraClient {
    static CassandraClient instance = new CassandraClient();
    private static final int CONCURRENCY = 20;
    private static final int MAX_CONNECTIONS = 10;
    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";
    private static final String TABLE_NAME = "requests";
    private Cluster cluster;
    private Session session;

    public CassandraClient() {
        init();
    }



    private void init() {
        String node = "localhost";
        PoolingOptions pools = new PoolingOptions();
        pools.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL,
                                                               CONCURRENCY);
        pools.setCoreConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setCoreConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);

        cluster = new Cluster.Builder().addContactPoints(String.valueOf(node))
                                       .withPoolingOptions(pools)
                                       .withSocketOptions(new SocketOptions().setTcpNoDelay(true))
                                       .build();
        session = cluster.connect("mykeyspace");

        Metadata metadata = cluster.getMetadata();
        System.out.println(String.format("Connected to cluster '%s' on %s.",
                                         metadata.getClusterName(),
                                         metadata.getAllHosts()));


    }


    public void putRequest(int id, ByteBuffer data) {
        putPackage(REQUEST, id, data);

    }

    public void putResponse(int id, ByteBuffer data) {
        putPackage(RESPONSE, id, data);
    }

    private void putPackage(String type, int id, ByteBuffer data) {
        Insert query = QueryBuilder.insertInto(TABLE_NAME)
                                   .value("id", id)
                                   .value(type, data);

        // ResultSetFuture resultSetFuture =
        session.executeAsync(query);
    }

    // /////////////////////////////////////////////////////////////

    public ByteBuffer getRequest(int id) {
        return getPackage(REQUEST, id);
    }


    public ByteBuffer getResponse(int id) {
        return getPackage(RESPONSE, id);
    }

    /**
     * Return the bytebuffer with the content of request
     * 
     * @param type
     * @param id
     * @return
     */
    private ByteBuffer getPackage(String type, int id) {
        String query = new String("select " + type + " from " + TABLE_NAME + " where id="
                + id + ";");

        ResultSet result = session.execute(query);
        for (Row row : result.all()) {
            return row.getBytes(type);
        }
        return null;
    }

    public void close() {
        cluster.close();
    }

    public static CassandraClient getInstance() {
        return instance;
    }

    public Session getSession() {
        return session;
    }
}
