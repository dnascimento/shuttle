package pt.inesc.proxy.clientSide;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;



/**
 * Init the Proxy Server. Create the pipes.
 */
public class ClientInit extends
        ChannelInitializer<SocketChannel> {

    private final InetSocketAddress remoteHost;

    public ClientInit(String remoteHost, int remotePort) throws UnknownHostException {
        this.remoteHost = new InetSocketAddress(InetAddress.getByName(remoteHost),
                remotePort);
    }

    // TODO Testar manter a instancia

    // Creates a new instance of handler per-channel
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new HttpRequestDecoder(),
                              new HttpObjectAggregator(1048576),
                              new HttpResponseEncoder(),
                              new ProxyHandler(remoteHost));
    }
}
