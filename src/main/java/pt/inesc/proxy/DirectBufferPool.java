package pt.inesc.proxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

public class DirectBufferPool {
    final ArrayDeque<ByteBuffer> buffers;
    final int CAPACITY;
    int BUFFER_SIZE;

    private static final int VOTE_PERCENTAGE = 2;

    /*
     * if after DECREASE renews, there were no request to increase, try to decrease
     * DECREASE_PERCENTAGE. It deletes the memory of big responses
     */
    private static final int DECREASE = 200;
    private static final int DECREASE_PERCENTAGE = 10;

    // add TOLERANCE bytes to the requested size
    private static final int TOLERANCE = 50;


    private int voteIncrease = 0;
    private int maxIncrease = 0;
    private int nonIncreased = 0;

    public DirectBufferPool(int CAPACITY, int BUFFER_SIZE) {
        buffers = new ArrayDeque<ByteBuffer>(CAPACITY);
        refill();
        this.CAPACITY = CAPACITY;
        this.BUFFER_SIZE = BUFFER_SIZE;
        refill();
    }

    void refill() {
        if (voteIncrease > CAPACITY / VOTE_PERCENTAGE) {
            BUFFER_SIZE = maxIncrease;
        } else {
            if (++nonIncreased == DECREASE)
                BUFFER_SIZE = BUFFER_SIZE / DECREASE_PERCENTAGE;
        }
        for (int i = 0; i < CAPACITY; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
            buffers.push(buffer);
        }

    }

    public ByteBuffer pop() {
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
    public ByteBuffer pop(int minCapacity) {
        if (buffers.isEmpty()) {
            refill();
        }
        ByteBuffer b = buffers.pop();
        if (BUFFER_SIZE < minCapacity) {
            voteBufferSize(minCapacity);
            return ByteBuffer.allocateDirect(minCapacity).order(ByteOrder.BIG_ENDIAN);
        }
        return b;
    }

    public void returnBuffer(ByteBuffer buffer) {
        buffers.push(buffer);
    }

    public void voteBufferSize(int capacity) {
        voteIncrease++;
        maxIncrease = Math.max(maxIncrease, capacity + TOLERANCE);
    }


}
