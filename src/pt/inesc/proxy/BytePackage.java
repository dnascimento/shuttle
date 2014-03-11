package pt.inesc.proxy;

import java.nio.ByteBuffer;
import java.util.LinkedList;


public class BytePackage {
    LinkedList<ByteBuffer> bytes;

    public BytePackage() {
        bytes = new LinkedList<ByteBuffer>();
    }



    public LinkedList<ByteBuffer> getBytesList() {
        return bytes;
    }

    public void add(ByteBuffer buffer) {
        bytes.add(buffer);
    }
}
