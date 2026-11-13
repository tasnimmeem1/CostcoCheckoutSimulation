# Costco Checkout Simulator

This CSC 311 lab uses the producer–consumer pattern to simulate a Costco checkout line. Customers finish shopping and add their IDs to a shared queue. Cashiers take IDs from the queue and serve customers.

## How to Run

1. Open the project in IntelliJ IDEA.
2. Open `src/ex2/CostcoSimulation.java`.
3. Click the green Run arrow next to `main()`.
4. Enter the number of customers, number of cashiers, queue capacity, shopping time in milliseconds, and checkout time in milliseconds.

The program prints each customer’s wait time, followed by the average and longest waits.

## Main Classes

| Class | Responsibility |
|---|---|
| `Customer` | Simulates shopping and adds the customer’s ID to the queue. |
| `CheckoutQueue` | Uses an `ArrayBlockingQueue` to hold customer IDs. Customers wait when the queue is full, and cashiers wait when it is empty. |
| `Cashier` | Takes customer IDs from the queue, records their wait times, and simulates checkout. |
| `CostcoSimulation` | Reads the settings, starts the threads, signals cashiers to stop, and prints the results. |

## Experiment Results

Each experiment used 20 customers, a shopping time of 100 ms, and a checkout time of 300 ms. Wait time starts when a customer finishes shopping and ends when a cashier takes their ID. It includes time spent waiting for space in the queue.

| Experiment | Customers | Cashiers | Queue capacity | Average wait (ms) | Longest wait (ms) |
|---|---:|---:|---:|---:|---:|
| 1. Understaffed | 20 | 1 | 5 | 2,949.36 | 5,906.90 |
| 2. Three cashiers | 20 | 3 | 5 | 888.55 | 1,872.00 |
| 3. Overstaffed | 20 | 10 | 5 | 153.83 | 310.79 |
| 4. Smaller queue | 20 | 3 | 1 | 895.73 | 1,882.92 |
| 5. Larger queue | 20 | 3 | 10 | 879.10 | 1,860.52 |

## Observations and Recommendation

Increasing staffing from one cashier to three reduced the average wait by about 70%. Ten cashiers reduced the wait further, but required seven additional cashiers. With three cashiers, the average waits for queue capacities of 1, 5, and 10 were within 17 ms of each other in these runs.

For the 20-customer rush we simulated, I would choose three cashiers. This setup reduced the average wait to under one second with fewer cashiers than the ten-cashier setup.

