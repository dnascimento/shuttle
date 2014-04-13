package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraClient {
    private static final Logger log = LogManager.getLogger(CassandraClient.class.getName());

    private static final int CONCURRENCY = 20;
    private static final int MAX_CONNECTIONS = 10;
    private static final String TABLE_NAME = "requests";
    private static final String NODE = "localhost";
    private static final String KEYSPACE = "requestStore";
    private static final String COL_ID = "id";
    private static final String COL_REQUEST = "request";
    private static final String COL_RESPONSE = "response";
    private static final String QUERY_REQUEST = new String("select " + COL_REQUEST + " from " + TABLE_NAME
            + " where id=");
    private static final Object QUERY_RESPONSE = new String("select " + COL_RESPONSE + " from " + TABLE_NAME
            + " where id=");

    private final Cluster cluster;
    private Session session = null;

    public CassandraClient() {
        PoolingOptions pools = new PoolingOptions();
        pools.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL, CONCURRENCY);
        pools.setCoreConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        pools.setCoreConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);
        pools.setMaxConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);

        cluster = new Cluster.Builder().addContactPoints(NODE)
                                       .withPoolingOptions(pools)
                                       .withSocketOptions(new SocketOptions().setTcpNoDelay(true))
                                       .build();
        try {
            session = cluster.connect(KEYSPACE);
            Metadata metadata = cluster.getMetadata();
            System.out.println(String.format("Connected to cluster '%s' on %s.",
                                             metadata.getClusterName(),
                                             metadata.getAllHosts()));

        } catch (NoHostAvailableException e) {
            log.error("No Cassandra server available");
        }
    }

    /**
     * This PUT is Synchronous
     * 
     * @param pack
     */
    public void putRequest(Request pack) {
        pack.data.rewind();
        Insert query = QueryBuilder.insertInto(TABLE_NAME).value(COL_ID, pack.rid).value(COL_REQUEST, pack.data);

        if (session != null) {
            session.execute(query);
        }
    }

    /**
     * Put response is Synchronous
     * 
     * @param rid
     * @param data
     */
    public void putResponse(long rid, ByteBuffer data) {
        data.rewind();
        Insert query = QueryBuilder.insertInto(TABLE_NAME).value(COL_ID, rid).value(COL_RESPONSE, data);

        if (session != null) {
            session.execute(query);
        }
    }



    // /////////////////////////////////////////////////////////////

    public ByteBuffer getRequest(long id) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_REQUEST);
        sb.append(id);
        sb.append(";");
        ResultSet result = session.execute(sb.toString());
        for (Row row : result.all()) {
            return row.getBytes(COL_REQUEST);
        }
        return null;
    }


    public ByteBuffer getResponse(long id) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_RESPONSE);
        sb.append(id);
        sb.append(";");
        ResultSet result = session.execute(sb.toString());
        for (Row row : result.all()) {
            return row.getBytes(COL_RESPONSE);
        }
        return null;
    }


    public void close() {
        cluster.close();
    }

    public Session getSession() {
        return session;
    }

    public void truncatePackageTable() {
        String query = new String("truncate " + TABLE_NAME + ";");
        session.execute(query);
        log.info(query + " executed");
    }
}
