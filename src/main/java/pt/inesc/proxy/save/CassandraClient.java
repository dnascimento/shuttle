/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.proxy.save;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import voldemort.undoTracker.KeyAccess;
import voldemort.utils.ByteArray;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.io.BaseEncoding;

public class CassandraClient {
    private static final Logger log = LogManager.getLogger(CassandraClient.class.getName());

    // private static final int CONCURRENCY = 20;
    // private static final int MAX_CONNECTIONS = 10;
    private static final String NODE = "cassandra";

    private static final String TABLE_NAME = "requests";
    private static final String KEYSPACE = "requestStore";
    private static final String COL_ID = "id";
    private static final String COL_REQUEST = "request";
    private static final String COL_RESPONSE = "response";
    private static final String COL_KEYS = "keys";
    private static final String COL_END = "end";
    private static final String QUERY_REQUEST = new String("select " + COL_REQUEST + ", " + COL_END + " from " + TABLE_NAME + " where id=");
    private static final String QUERY_REQUESTS = new String("select " + COL_ID + ", " + COL_REQUEST + ", " + COL_END + " from "
            + TABLE_NAME + " where id in (");

    private static final String QUERY_RESPONSE = new String("select " + COL_RESPONSE + " from " + TABLE_NAME + " where id=");

    private static final String QUERY_KEYS = new String("select " + COL_KEYS + " from " + TABLE_NAME + " where id=");

    private static final String QUERY_LIST_REQUESTS = new String("select " + COL_ID + " from " + TABLE_NAME + ";");

    private static final String DELETE_REQUEST = "Update " + TABLE_NAME + " set " + COL_REQUEST + " = NULL, " + COL_RESPONSE
            + " = NULL where " + COL_ID + " = ";



    private final Cluster cluster;
    private Session session = null;

    public CassandraClient() {
        PoolingOptions pools = new PoolingOptions();
        // pools.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL,
        // CONCURRENCY);
        // pools.setCoreConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        // pools.setMaxConnectionsPerHost(HostDistance.LOCAL, MAX_CONNECTIONS);
        // pools.setCoreConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);
        // pools.setMaxConnectionsPerHost(HostDistance.REMOTE, MAX_CONNECTIONS);

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


    public void putRequestResponse(ByteBuffer request, ByteBuffer response, long start, long end) {
        request.rewind();
        response.rewind();

        Insert query = QueryBuilder.insertInto(TABLE_NAME)
                                   .value(COL_ID, start)
                                   .value(COL_REQUEST, request)
                                   .value(COL_RESPONSE, response)
                                   .value(COL_END, end);
        if (session != null) {
            session.execute(query);
        } else {
            System.err.println("session is null: cassandra client");
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



    // /////////////////////////////////////////////////////////////
    public void getRequests(ArrayList<Request> list, int fetchPosition, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_REQUESTS);
        int counter = 0;
        for (int i = fetchPosition; i < limit; i++) {
            long rid = list.get(i).rid;
            if (rid == -1) {
                continue;
            }
            if (counter > 0) {
                sb.append(", ");
            }
            sb.append(rid);
            counter++;
        }
        sb.append(");");
        if (counter == 0) {
            return;
        }
        String query = sb.toString();
        ResultSet result = session.execute(query);
        Iterator<Row> resultIterator = result.all().iterator();
        Row row = null;
        for (int i = fetchPosition; i < limit; i++) {
            Request r = list.get(i);
            if (r.rid == -1) {
                continue;
            }

            if (row == null) {
                row = resultIterator.next();
            }

            long cassandraRid = row.getLong(COL_ID);

            if (cassandraRid != r.rid) {
                log.error("Request " + r.rid + " not found");
                continue;
            }

            r.data = row.getBytes(COL_REQUEST);
            r.end = row.getLong(COL_END);
            if (resultIterator.hasNext()) {
                row = resultIterator.next();
            } else {
                row = null;
            }
        }
    }

    public Request getRequest(long rid) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_REQUEST);
        sb.append(rid);
        sb.append(";");
        ResultSet result = session.execute(sb.toString());
        for (Row row : result.all()) {
            ByteBuffer data = row.getBytes(COL_REQUEST);
            if (data == null) {
                return null;
            }
            long end = row.getLong(COL_END);
            return new Request(data, rid, end);
        }
        throw new Exception("Unknown request " + rid);
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


    public ArrayListMultimap<ByteArray, KeyAccess> getKeys(long id) {
        if (session == null)
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_KEYS);
        sb.append(id);
        sb.append(";");
        ResultSet result = session.execute(sb.toString());
        ArrayListMultimap<ByteArray, KeyAccess> r = ArrayListMultimap.create();
        Row row = result.one();
        if (row == null) {
            log.error("No registry of accessed keys for rid: " + id);
            return r;
        }

        List<String> l = row.getList(COL_KEYS, String.class);
        for (String s : l) {
            // key-store:times.store:times,key-store:times.store:times
            String[] splitted = s.split("-");
            ByteArray key = new ByteArray(BaseEncoding.base64().decode(splitted[0]));
            String[] storeTimes = splitted[1].split("\\.");

            // parse store:times pair
            for (String storeTime : storeTimes) {
                String[] entries = storeTime.split(":");
                Integer times = Integer.parseInt(entries[1]);
                KeyAccess access = new KeyAccess(entries[0], null, times);
                r.put(key, access);
            }
        }
        return r;
    }

    public void close() {
        cluster.close();
    }

    public Session getSession() {
        return session;
    }

    public void truncatePackageTable() {
        // truncate requests;
        String query = new String("truncate " + TABLE_NAME + ";");
        if (session != null) {
            session.execute(query);
            log.info(query + " executed");
        }
    }

    public List<Long> getRequestList() {
        ResultSet result = session.execute(QUERY_LIST_REQUESTS);
        List<Long> l = new ArrayList<Long>();
        for (Row row : result.all()) {
            l.add(row.getLong(COL_ID));
            return l;
        }
        return l;
    }

    public void deleteRequest(long reqId) {
        StringBuilder sb = new StringBuilder();
        sb.append(DELETE_REQUEST);
        sb.append(reqId);
        sb.append(";");
        session.execute(sb.toString());
    }


    public String calculateSize() {
        String query = "select * from " + TABLE_NAME + " LIMIT " + Integer.MAX_VALUE;
        ResultSet rs = session.execute(query);
        long totalKeys = 0;
        long totalIds = 0;
        long totalRequests = 0;
        long totalResponses = 0;
        long count = 0;

        Iterator<Row> iter = rs.iterator();
        while (iter.hasNext()) {
            if (rs.getAvailableWithoutFetching() == 100 && !rs.isFullyFetched())
                rs.fetchMoreResults();
            Row row = iter.next();
            count++;

            row.getLong(COL_ID);
            row.getLong(COL_END);
            totalIds += 16;

            ByteBuffer request = row.getBytes(COL_REQUEST);
            ByteBuffer response = row.getBytes(COL_RESPONSE);

            if (request != null) {
                totalRequests += request.limit() - request.position();
            }
            if (response != null) {
                totalResponses += response.limit() - response.position();
            } else {
                System.out.println("Null response");
            }

            List<String> keys = row.getList(COL_KEYS, String.class);
            for (String key : keys) {
                totalKeys += key.getBytes().length;
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Total cassandra size (bytes): " + (totalKeys + totalIds + totalRequests + totalResponses));
        sb.append("Keys: " + totalKeys);
        sb.append("Ids: " + totalIds);
        sb.append("Requests: " + totalRequests);
        sb.append("Response: " + totalResponses);
        sb.append("Number of rows: " + count);
        return sb.toString();
    }

    public static void main(String[] args) {
        CassandraClient c = new CassandraClient();
        System.out.println(c.calculateSize());
        c.close();
    }



}
