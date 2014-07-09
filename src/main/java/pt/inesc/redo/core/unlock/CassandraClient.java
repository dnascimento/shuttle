package pt.inesc.redo.core.unlock;

/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import voldemort.undoTracker.KeyAccess;
import voldemort.utils.ByteArray;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.io.BaseEncoding;

public class CassandraClient {
    private static final Logger log = LogManager.getLogger(CassandraClient.class.getName());

    private static final int CONCURRENCY = 20;
    private static final int MAX_CONNECTIONS = 10;
    private static final String TABLE_NAME = "requests";
    private static final String NODE = "localhost";
    private static final String KEYSPACE = "requestStore";
    private static final String COL_KEYS = "keys";

    private static final String QUERY_KEYS = new String("select " + COL_KEYS + " from " + TABLE_NAME + " where id=");


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
            log.info(String.format("Connected to cluster '%s' on %s.", metadata.getClusterName(), metadata.getAllHosts()));

        } catch (NoHostAvailableException e) {
            log.error("No Cassandra server available");
        }
    }






    // //////////////////////////////////////

    public void addKeys(Set<KeyAccess> accessedKeys, long id) {
        StringBuilder sb = new StringBuilder();
        sb.append("update ");
        sb.append(TABLE_NAME);
        sb.append(" set ");
        sb.append(COL_KEYS);
        sb.append(" = [");
        Iterator<KeyAccess> i = accessedKeys.iterator();
        while (i.hasNext()) {
            KeyAccess s = i.next();
            sb.append("'");
            sb.append(BaseEncoding.base64().encode(s.key.get()));
            sb.append(",");
            sb.append(s.store);
            sb.append("'");
            if (i.hasNext())
                sb.append(",");
        }
        sb.append("] where id=");
        sb.append(id);
        sb.append(";");
        log.info(sb.toString());
        session.execute(sb.toString());
    }

    public Set<KeyAccess> getKeys(long id) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_KEYS);
        sb.append(id);
        sb.append(";");
        ResultSet result = session.execute(sb.toString());
        for (Row row : result.all()) {
            List<String> l = row.getList(COL_KEYS, String.class);
            Set<KeyAccess> r = new HashSet<KeyAccess>();
            for (String s : l) {
                String[] splitted = s.split(",");
                KeyAccess access = new KeyAccess(new ByteArray(BaseEncoding.base64().decode(splitted[0])), splitted[1]);
                r.add(access);
            }
            return r;
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
