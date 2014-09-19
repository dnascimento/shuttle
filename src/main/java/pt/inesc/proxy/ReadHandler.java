package pt.inesc.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

public class ReadHandler
        implements CompletionHandler<Integer, AsynchronousSocketChannel> {

    private static final Logger log = Logger.getLogger(ReadHandler.class.getName());

    private static final long WAIT_FOR_AVAILABLE_WORKER = 1000;
    ByteBuffer buffer;
    LinkedBlockingDeque<ProxyWorker> workersList;

    private final DirectBufferPool buffers;

    public ReadHandler(ByteBuffer buffer, LinkedBlockingDeque<ProxyWorker> workersList, DirectBufferPool buffers) {
        this.buffer = buffer;
        this.workersList = workersList;
        this.buffers = buffers;
    }

    @Override
    public void completed(Integer result, AsynchronousSocketChannel ch) {
        if (result == -1) {
            closeChannel(ch);
            return;
        }
        // fetch a object to handle this request
        ProxyWorker worker = null;
        do {
            try {
                worker = workersList.poll(WAIT_FOR_AVAILABLE_WORKER, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.error(e);
            }
        } while (worker == null);

        // handle the request
        boolean keepAlive = worker.handle(ch, buffer);
        // return worker to list
        workersList.add(worker);

        if (keepAlive) {
            buffer.clear();
            ch.read(buffer, 100000, TimeUnit.MILLISECONDS, ch, this);
        } else {
            buffers.returnBufferSynchronized(buffer);
        }
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel ch) {
        closeChannel(ch);


    }

    private void closeChannel(AsynchronousSocketChannel ch) {
        try {
            ch.close();
        } catch (IOException e) {
            log.error(ch);
        }
    }
}
