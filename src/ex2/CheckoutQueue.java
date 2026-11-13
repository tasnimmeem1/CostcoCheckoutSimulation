package ex2;

import java.util.concurrent.ArrayBlockingQueue;

public class CheckoutQueue implements Buffer {
    private final ArrayBlockingQueue<Integer> customerIds;

    public CheckoutQueue(int capacity) {
        customerIds = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void blockingPut(int customerId) throws InterruptedException {
        customerIds.put(customerId);
    }

    @Override
    public int blockingGet() throws InterruptedException {
        return customerIds.take();
    }
}