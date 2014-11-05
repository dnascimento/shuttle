package pt.inesc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

public class GetIdTest {


    @Test
    public void testGetId() {
        ByteBuffer buffer = ByteBuffer.wrap("HTTP/1.1 200 OK \r\n Connection: keep-alive\r\nX-Powered-By: Undertow\r\nX-Powered-By: JSP/2.2\r\nServer: WildFly/8\r\nTransfer-Encoding: chunked\r\nContent-Type: text/html;charset=ISO-8859-1\r\nContent-Language: en-US\r\nId: 1414698331191001\r\nDate: Thu, 30 Oct 2014 19:46:48 GMT".getBytes());
        ArrayList<Long> ids = BufferTools.getIds(buffer);
        Assert.assertTrue(ids.contains(new Long(1414698331191001L)));
    }
}
