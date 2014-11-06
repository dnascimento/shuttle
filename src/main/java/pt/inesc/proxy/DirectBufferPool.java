package pt.inesc.proxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Stack;

public class DirectBufferPool {
    final Stack<ByteBuffer> buffers;
    int BUFFER_SIZE;

    private static final int VOTE_PERCENTAGE = 2;

    /*
     * if after DECREASE renews, there were no request to increase, try to decrease
     * DECREASE_PERCENTAGE. It deletes the memory of big responses
     */
    private static final int DECREASE = 2000;
    private static final int DECREASE_PERCENTAGE = 10;

    // add TOLERANCE bytes to the requested size
    private static final int TOLERANCE = 50;
    private final String label;


    private int voteIncrease = 0;
    private int maxIncrease = 0;
    private int nonIncreased = 0;

    // point the current available buffer
    private final int CAPACITY;

    public DirectBufferPool(String label, int CAPACITY, int BUFFER_SIZE) {
        buffers = new Stack<ByteBuffer>();
        this.label = label;
        this.BUFFER_SIZE = BUFFER_SIZE;
        this.CAPACITY = CAPACITY;
        refill();
    }

    private void refill() {
        // review the buffer size
        if (voteIncrease > buffers.size() / VOTE_PERCENTAGE) {
            BUFFER_SIZE = maxIncrease;
            System.out.println(label + ": size increased: " + BUFFER_SIZE);
            voteIncrease = 0;
        } else {
            if (++nonIncreased == DECREASE) {
                BUFFER_SIZE = BUFFER_SIZE / DECREASE_PERCENTAGE;
                System.out.println(label + ": size decrease: " + BUFFER_SIZE);
                nonIncreased = 0;
            }
        }

        // fullfill
        while (buffers.size() < CAPACITY) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
            buffers.push(buffer);
        }
    }

    public synchronized ByteBuffer pop() {
        if (buffers.isEmpty()) {
            refill();
        }
        return buffers.pop();
    }

    /**
     * Request buffers require a minimum capacity. if not matched, create a new buffer and
     * vote to change
     * 
     * @param minCapacity
     * @return
     */
    public synchronized ByteBuffer pop(int minBufferSize) {
        if (buffers.isEmpty()) {
            refill();
        }
        ByteBuffer b = buffers.pop();
        if (b.capacity() < minBufferSize) {
            voteBufferSize(minBufferSize);
            System.out.println(label + ": buffer is too small, discarding the poped");
            return ByteBuffer.allocateDirect(minBufferSize).order(ByteOrder.BIG_ENDIAN);
        }
        return b;
    }




    public synchronized void returnBuffer(LinkedList<ByteBuffer> cleanBuffersRequests) {
        // System.out.println(label + "return buffer");
        for (ByteBuffer b : cleanBuffersRequests) {
            buffers.push(b);
        }
    }


    public synchronized void returnBuffer(ByteBuffer buffer) {
        // System.out.println(label + "return buffer");
        buffers.push(buffer);

    }

    public synchronized void voteBufferSize(int capacity) {
        voteIncrease++;
        maxIncrease = Math.max(maxIncrease, capacity + TOLERANCE);
    }




}
