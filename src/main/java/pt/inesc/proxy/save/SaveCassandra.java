package pt.inesc.proxy.save;

import java.nio.ByteBuffer;

public class SaveCassandra extends
        SaveThread {
    CassandraClient cassandra;
    final SaveType type;

    public SaveCassandra(SaveType type, int start, int end) {
        super(type, start, end);
        this.type = type;
        cassandra = CassandraClient.getInstance();
    }

    @Override
    public void save(int id, ByteBuffer pack) {
        if (type.equals(SaveType.Request)) {
            cassandra.putRequest(id, pack);
        } else {
            cassandra.putResponse(id, pack);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void init() {

    }

}
