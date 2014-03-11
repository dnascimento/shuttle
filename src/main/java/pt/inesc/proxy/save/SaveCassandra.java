package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class SaveCassandra extends
        SaveThread {
    CassandraClient cassandra;

    public SaveCassandra(SaveType type, int start, int end) {
        super(type, start, end);
        cassandra = CassandraClient.getInstance();
    }

    @Override
    public void save(int id, ByteBuffer pack) {
        cassandra.putRequest(id, pack);
    }

    @Override
    public void close() {

    }

    @Override
    public void init() {

    }

}
