# Traffic Flow Simulator

This CSC 311 project uses the producer–consumer pattern to simulate a four-way intersection. Cars arrive from the north, east, south, and west. Each approach has one incoming lane and one outgoing lane. Cars continue straight through the intersection.

## Run in IntelliJ IDEA

1. Open the `CSC311_Example_Threads_2` folder in IntelliJ IDEA.
2. In **File → Project Structure → Project**, select JDK 21 or later if IntelliJ asks for a project SDK.
3. Open `src/traffic/TrafficSimulation.java` and click the green Run arrow next to `main()`.

## Controls

| Control | What it does |
|---|---|
| N/S green (s), E/W green (s) | Set the green duration for each pair of directions from 3 to 15 seconds. Changing either value starts a new run. |
| View | Switch between the 3D neighborhood and top-down views during a run. |
| Signal mode | Choose coordinated signals or unsynchronized signals. Changing mode starts a new run. |
| Traffic mix | Choose balanced arrivals, 70% north–south, or 70% east–west. Changing the mix starts a new run. |
| Cars/min | Set the total attempted arrival rate from 15 to 300 cars per minute. |
| Pause / Resume | Freeze or resume cars, new arrivals, and the signal timer. |
| Reset | Clear the intersection and counters while keeping the chosen timings, traffic mix, arrival rate, and pause setting. |

In coordinated mode, the signal gives green to north–south traffic and then to east–west traffic. After each green there are two seconds of amber and one second of all-red so cars already moving through the intersection can clear it. Cars waiting at the stop line enter only on green.

**Blocked arrivals** counts attempts that could not enter their assigned lane because it lacked space. **Crossed** counts cars released past the stop line. **Avg. stopped time** reports the mean stationary time of the cars already released.

## How the simulation works

| Class | Responsibility |
|---|---|
| `TrafficSimulation` | Starts an arrival worker and a traffic worker, and refreshes the Swing interface with snapshots. |
| `TrafficModel` | Creates one bounded FIFO `ArrayBlockingQueue` per approach. The arrival worker adds cars, and the traffic worker releases the lead car at a green signal. |
| `RoadPanel` | Draws the animated intersection, the four signals, and the stopped and crossed totals for each approach. |

## Demonstrate a collision

Select **Unsynchronized signals**. Each road follows its own timer, so both roads can have green at the same time. A collision is detected when the bodies of two perpendicular cars overlap. The app highlights the impact and freezes the run. Press **Reset** to try again, or choose **Coordinated signals** to compare. Try 300 cars/min with balanced traffic to see the collision sooner.

Cars are assigned different preferred speeds when they enter. A faster car slows behind a slower car in the same lane and keeps a safe following gap. Cars travel straight throughout the demonstration.

## Compare signal timings

Select **70% N/S**, set the slider to **225 cars/min**, and compare two 60-second runs. Start with **6 s north–south and 6 s east–west**. Then try **9 s north–south and 3 s east–west**. Both settings keep the same 18-second signal cycle. Compare **Avg. stopped time**, **Blocked arrivals**, and the per-approach crossing totals to see how the extra north–south green time affects both roads.
