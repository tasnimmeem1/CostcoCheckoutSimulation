package ex2;

import java.util.Scanner;

public class CostcoSimulation {
    public static void main(String[] args) throws InterruptedException {
        try (Scanner input = new Scanner(System.in)) {
            int customerCount = readPositiveInt(input, "Number of customers");
            int cashierCount = readPositiveInt(input, "Number of cashiers");
            int queueCapacity = readPositiveInt(input, "Checkout queue capacity");
            int shoppingMillis = readPositiveInt(input, "Shopping time in milliseconds");
            int checkoutMillis = readPositiveInt(input, "Checkout time in milliseconds");

            CheckoutQueue queue = new CheckoutQueue(queueCapacity);
            Thread[] cashiers = new Thread[cashierCount];
            Thread[] customers = new Thread[customerCount];
            long[] checkoutReadyTimes = new long[customerCount];
            long[] customerWaitNanos = new long[customerCount];

            for (int i = 0; i < cashiers.length; i++) {
                cashiers[i] = new Thread(new Cashier(
                        i + 1, queue, checkoutMillis, checkoutReadyTimes, customerWaitNanos));
                cashiers[i].start();
            }

            for (int i = 0; i < customers.length; i++) {
                customers[i] = new Thread(new Customer(
                        i + 1, queue, shoppingMillis, checkoutReadyTimes));
                customers[i].start();
            }

            for (Thread customer : customers) {
                customer.join();
            }

            for (int i = 0; i < cashiers.length; i++) {
                queue.blockingPut(Cashier.END_OF_SHIFT);
            }

            for (Thread cashier : cashiers) {
                cashier.join();
            }

            double totalWaitMillis = 0;
            double longestWaitMillis = 0;

            for (long waitNanos : customerWaitNanos) {
                double waitMillis = waitNanos / 1_000_000.0;
                totalWaitMillis += waitMillis;
                longestWaitMillis = Math.max(longestWaitMillis, waitMillis);
            }

            System.out.println("Simulation complete. All customers were served.");
            System.out.printf("Average customer wait: %.2f ms%n",
                    totalWaitMillis / customerCount);
            System.out.printf("Longest customer wait: %.2f ms%n", longestWaitMillis);
        }
    }

    private static int readPositiveInt(Scanner input, String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String value = input.nextLine().trim();

            try {
                int number = Integer.parseInt(value);
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException exception) {
                // Ask again if the input is not a valid integer.
            }

            System.out.println("Please enter a positive whole number.");
        }
    }
}