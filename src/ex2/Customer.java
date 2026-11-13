package ex2;

public class Customer implements Runnable {
    private final int customerId;
    private final Buffer checkoutQueue;
    private final long shoppingTimeMillis;
    private final long[] checkoutReadyTimes;

    public Customer(int customerId, Buffer checkoutQueue, long shoppingTimeMillis,
                    long[] checkoutReadyTimes) {
        this.customerId = customerId;
        this.checkoutQueue = checkoutQueue;
        this.shoppingTimeMillis = shoppingTimeMillis;
        this.checkoutReadyTimes = checkoutReadyTimes;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(shoppingTimeMillis);
            checkoutReadyTimes[customerId - 1] = System.nanoTime();
            checkoutQueue.blockingPut(customerId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}