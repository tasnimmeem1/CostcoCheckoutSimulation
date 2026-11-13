package ex2;

public class Cashier implements Runnable {
    public static final int END_OF_SHIFT = -1;

    private final int cashierId;
    private final Buffer checkoutQueue;
    private final long checkoutTimeMillis;
    private final long[] checkoutReadyTimes;
    private final long[] customerWaitNanos;

    public Cashier(int cashierId, Buffer checkoutQueue, long checkoutTimeMillis,
                   long[] checkoutReadyTimes, long[] customerWaitNanos) {
        this.cashierId = cashierId;
        this.checkoutQueue = checkoutQueue;
        this.checkoutTimeMillis = checkoutTimeMillis;
        this.checkoutReadyTimes = checkoutReadyTimes;
        this.customerWaitNanos = customerWaitNanos;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int customerId = checkoutQueue.blockingGet();

                if (customerId == END_OF_SHIFT) {
                    return;
                }

                int customerIndex = customerId - 1;
                long waitNanos = System.nanoTime() - checkoutReadyTimes[customerIndex];
                customerWaitNanos[customerIndex] = waitNanos;

                double waitMillis = waitNanos / 1_000_000.0;
                System.out.printf(
                        "Cashier %d is serving customer %d after a wait of %.2f ms.%n",
                        cashierId, customerId, waitMillis);

                Thread.sleep(checkoutTimeMillis);
                System.out.printf("Cashier %d finished serving customer %d.%n",
                        cashierId, customerId);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}