package pt.inesc.manager.requests;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import pt.inesc.BufferTools;
import pt.inesc.proxy.save.CassandraClient;
import pt.inesc.proxy.save.Request;

public class RequestsModifier {


    public String listRequests() {
        CassandraClient client = new CassandraClient();
        List<Long> l = client.getRequestList();
        StringBuilder sb = new StringBuilder();
        for (Long s : l) {
            sb.append(s);
            sb.append(", ");
        }
        client.close();
        return sb.toString();
    }

    public String showRequest(long reqId) {
        CassandraClient client = new CassandraClient();
        ByteBuffer buffer = client.getRequest(reqId);
        client.close();
        if (buffer == null) {
            return "Request not found";
        }
        return BufferTools.printContent(buffer, buffer.position(), buffer.limit());
    }

    public void editRequest(long reqId) throws Exception {
        CassandraClient client = new CassandraClient();
        ByteBuffer buffer = client.getRequest(reqId);
        File desktop = new File(System.getProperty("user.home") + "/Desktop");
        File tmp = new File(desktop, "request.txt");
        tmp.createNewFile();
        FileOutputStream os = new FileOutputStream(tmp);
        os.write(buffer.array(), buffer.position(), buffer.limit() - buffer.position());
        System.out.println("Check your desktop");
        System.in.read();
        FileInputStream is = new FileInputStream(tmp);
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] bf = new byte[(int) tmp.length()];
        int bytesRead = 0;
        bytesRead = bis.read(bf);
        System.out.println("Reading the new request...");
        bis.close();
        os.close();
        tmp.delete();
        if (bytesRead != tmp.length()) {
            throw new Exception("Buffer issues");
        }
        buffer = ByteBuffer.wrap(bf);
        buffer.rewind();
        Request pack = new Request(buffer, reqId);
        client.putRequest(pack);
        client.close();
    }


    public void deleteRequest(long reqId) {
        CassandraClient client = new CassandraClient();
        client.deleteRequest(reqId);
        client.close();
    }
}
