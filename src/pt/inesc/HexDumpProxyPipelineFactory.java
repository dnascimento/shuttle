package pt.inesc;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class HexDumpProxyPipelineFactory
        implements ChannelPipelineFactory {


    private ClientSocketChannelFactory cf;
    private String remoteHost;
    private int remotePort;



    public HexDumpProxyPipelineFactory(ClientSocketChannelFactory cf,
            String remoteHost,
            int remotePort) {
        super();
        this.cf = cf;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }



    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline(); // Note the static import.
        p.addLast("handler", new HexDumpProxyInboundHandler(cf, remoteHost, remotePort));
        return p;
    }

}
