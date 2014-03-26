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
    private static final int CONCURRENCY = 20;
    private static final int MAX_CONNECTIONS = 10;
    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";
    private static final String TABLE_NAME = "requests";
    private static final String NODE = "localhost";
    private static final String KEYSPACE = "requestStore";
    private final Cluster cluster;
    private final Session session;

    public CassandraClient() {
        PoolingOptions pools = new PoolingOptions();
        pools.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL,
                                                               CONCURRENCY);
        pools.setCoreConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setCoreConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);

        cluster = new Cluster.Builder().addContactPoints(NODE)
                                       .withPoolingOptions(pools)
                                       .withSocketOptions(new SocketOptions().setTcpNoDelay(true))
                                       .build();
        session = cluster.connect(KEYSPACE);

        Metadata metadata = cluster.getMetadata();
        System.out.println(String.format("Connected to cluster '%s' on %s.",
                                         metadata.getClusterName(),
                                         metadata.getAllHosts()));


    }

    public void putRequest(long start, ByteBuffer data) {
        putPackage(REQUEST, start, data);

    }

    public void putResponse(long start, ByteBuffer data) {
        putPackage(RESPONSE, start, data);
    }

    private void putPackage(String type, long start, ByteBuffer data) {
        data.rewind();
        Insert query = QueryBuilder.insertInto(TABLE_NAME)
                                   .value("id", start)
                                   .value(type, data);

        // ResultSetFuture resultSetFuture =
        // TODO hipotese: monitorize if success or not adding a listener but it slows
        // down
        session.executeAsync(query);
    }

    // /////////////////////////////////////////////////////////////

    public ByteBuffer getRequest(long id) {
        return getPackage(REQUEST, id);
    }


    public ByteBuffer getResponse(long id) {
        return getPackage(RESPONSE, id);
    }

    /**
     * Return the bytebuffer with the content of request
     * 
     * @param type
     * @param id
     * @return
     */
    private ByteBuffer getPackage(String type, long id) {
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

    public Session getSession() {
        return session;
    }
}
