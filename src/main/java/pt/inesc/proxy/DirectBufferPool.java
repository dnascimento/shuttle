package pt.inesc.proxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class DirectBufferPool {
    final ArrayList<ByteBuffer> buffers;
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

    // point the current available buffer
    private int index = -1;
    private final int CAPACITY;

    public DirectBufferPool(int CAPACITY, int BUFFER_SIZE) {
        buffers = new ArrayList<ByteBuffer>(CAPACITY);
        this.BUFFER_SIZE = BUFFER_SIZE;
        this.CAPACITY = CAPACITY;
        refill();
    }

    void refill() {
        System.out.println("refill");
        // review the buffer size
        if (voteIncrease > buffers.size() / VOTE_PERCENTAGE) {
            BUFFER_SIZE = maxIncrease;
            System.out.println("size increased: " + BUFFER_SIZE);
            voteIncrease = 0;
        } else {
            if (++nonIncreased == DECREASE) {
                BUFFER_SIZE = BUFFER_SIZE / DECREASE_PERCENTAGE;
                System.out.println("size decrease: " + BUFFER_SIZE);
                nonIncreased = 0;
            }
        }

        // fullfill
        while (index < CAPACITY) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
            buffers.add(++index, buffer);
        }
        index--;
    }

    public synchronized ByteBuffer popSynchronized() {
        return pop();
    }

    public ByteBuffer pop() {
        if (index <= -1) {
            refill();
        }
        return buffers.get(index--);
    }

    /**
     * Request buffers require a minimum capacity. if not matched, create a new buffer and
     * vote to change
     * 
     * @param minCapacity
     * @return
     */
    public ByteBuffer pop(int minBufferSize) {
        if (index == -1) {
            refill();
        }
        ByteBuffer b = buffers.get(index--);
        if (BUFFER_SIZE < minBufferSize) {
            voteBufferSize(minBufferSize);
            System.out.println("buffer is too small");
            return ByteBuffer.allocateDirect(minBufferSize).order(ByteOrder.BIG_ENDIAN);
        }
        return b;
    }

    public synchronized void returnBufferSynchronized(ByteBuffer buffer) {
        returnBuffer(buffer);
    }


    public void returnBuffer(ByteBuffer buffer) {
        if (index == (CAPACITY - 1)) {
            // buffer is full
            return;
        }
        buffers.add(++index, buffer);
    }

    public void voteBufferSize(int capacity) {
        voteIncrease++;
        maxIncrease = Math.max(maxIncrease, capacity + TOLERANCE);
    }




}
