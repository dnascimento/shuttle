package pt.inesc.redo.core.handlers;

import java.util.Comparator;
import java.util.PriorityQueue;



public class BiggestEndList {

    class ReverseLongOrder
            implements Comparator<Long> {

        @Override
        public int compare(Long o1, Long o2) {
            return (int) (o2 - o1);
        }

    }

    ReverseLongOrder reverseOrder = new ReverseLongOrder();
    private final PriorityQueue<Long> endList = new PriorityQueue<Long>(20, reverseOrder);

    public boolean isEmpty() {
        return endList.isEmpty();
    }

    /**
     * @param end
     * @return true if the end value is the biggest value in the list
     */
    public boolean wasTheBiggest(long end) {
        long biggest = endList.peek();
        endList.remove(end);
        return (end == biggest);
    }

    public long getBiggest() {
        return endList.peek();
    }

    public void addEnd(long end) {
        endList.add(end);
    }
}
