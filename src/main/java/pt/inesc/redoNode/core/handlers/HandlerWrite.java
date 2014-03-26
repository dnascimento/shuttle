package pt.inesc.redoNode.core.handlers;

import java.nio.channels.CompletionHandler;

public class HandlerWrite
        implements CompletionHandler<Integer, ChannelPack> {

    public void completed(Integer bytesWritten, ChannelPack aux) {
        if (bytesWritten != aux.bytesToProcess) {
            // TODO logger
            System.out.println("error: socket didnt write everything");
        }
        aux.channel.read(aux.buffer, aux, new HandlerRead());
    }

    public void failed(Throwable exc, ChannelPack channel) {
        // TODO logger
        exc.printStackTrace();
    }




}
