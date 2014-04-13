package pt.inesc.redo.core.handlers;

import java.nio.channels.CompletionHandler;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class HandlerWrite
        implements CompletionHandler<Integer, ChannelPack> {
    private static Logger logger = LogManager.getLogger(HandlerWrite.class.getName());

    public void completed(Integer bytesWritten, ChannelPack aux) {
        if (bytesWritten != aux.bytesToProcess) {
            logger.error("The socket did not write everything");
        }
        aux.channel.read(aux.buffer, aux, new HandlerRead());
    }

    public void failed(Throwable exc, ChannelPack channel) {
        logger.error(exc);
    }




}
