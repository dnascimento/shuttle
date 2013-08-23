package pt.inesc.proxy.real;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;



/**
 * Init the Proxy Server. Create the pipes.
 */
public class RealInit extends
        ChannelInitializer<SocketChannel> {

    Channel inboundChannel;

    public RealInit(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    // Creates a new instance of handler per-channel
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new HttpRequestEncoder(),
                              new HttpObjectAggregator(1048576),
                              new HttpResponseDecoder(),
                              new RealHandler(inboundChannel));
    }
}
