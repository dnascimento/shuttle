package pt.inesc.proxy.clientSide;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;



/**
 * Init the Proxy Server. Create the pipes.
 */
public class ClientInit extends
        ChannelInitializer<SocketChannel> {

    private final String remoteHost;
    private final int remotePort;

    public ClientInit(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        // p.addLast("codec", new HttpServerCodec());
        p.addLast("proxy", new ProxyHandler(remoteHost, remotePort));
        // p.addLast(new HTTPHandler(remoteHost, remotePort));
    }
}
