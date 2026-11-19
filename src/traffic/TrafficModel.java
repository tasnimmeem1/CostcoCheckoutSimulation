package traffic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

final class TrafficModel {
    static final double STOP = 0.34;
    static final double GAP = 0.046;
    static final int CAPACITY = 8;
    static final double STEP_SECONDS = 0.02;

    enum Signal {
        GREEN("Green"), AMBER("Amber"), RED("Red");
        final String label;

        Signal(String label) {
            this.label = label;
        }
    }

    enum Approach {
        NORTH, EAST, SOUTH, WEST;

        boolean northSouth() {
            return this == NORTH || this == SOUTH;
        }
    }

    record Phase(Signal northSouth, Signal eastWest, double duration) {
        Signal forApproach(Approach approach) {
            return approach.northSouth() ? northSouth : eastWest;
        }
    }

    record Vehicle(double position, int color, boolean stopped) {}
    record Collision(double x, double y, Approach first, Approach second) {}
    private record Point(double x, double y) {}
    record LaneView(Approach approach, List<Vehicle> cars, int waiting, int crossed) {}
    record Snapshot(List<LaneView> lanes, Phase phase, double remaining,
                    int admitted, int crossed, int waiting, int blocked, double averageWait, Collision collision) {}

    private static final class Car {
        final int color;
        final double speed;
        double position;
        double wait;
        boolean stopped;

        Car(int color, double speed) {
            this.color = color;
            this.speed = speed;
        }
    }

    private static final class Lane {
        final Approach direction;
        final ArrayBlockingQueue<Car> approach = new ArrayBlockingQueue<>(CAPACITY);
        final List<Car> departing = new ArrayList<>();
        int crossed;

        Lane(Approach direction) {
            this.direction = direction;
        }
    }

    private final List<Lane> lanes = new ArrayList<>();
    private final Random random = new Random(311);
    private long ticks;
    private int admitted;
    private int crossed;
    private int blocked;
    private final List<Phase> phases;
    private final long cycleTicks;
    private final int northSouthShare;
    private double totalWait;
    private double nextArrival;
    private final boolean synchronizedSignals;
    private final int northSouthGreen;
    private final int eastWestGreen;
    private Collision collision;

    TrafficModel(int northSouthGreen, int eastWestGreen, int northSouthShare) {
        this(northSouthGreen, eastWestGreen, northSouthShare, true);
    }

    TrafficModel(int northSouthGreen, int eastWestGreen, int northSouthShare,
                 boolean synchronizedSignals) {
        this.northSouthShare = northSouthShare;
        this.northSouthGreen = northSouthGreen;
        this.eastWestGreen = eastWestGreen;
        this.synchronizedSignals = synchronizedSignals;
        for (Approach approach : Approach.values()) {
            lanes.add(new Lane(approach));
        }
        phases = List.of(
                new Phase(Signal.GREEN, Signal.RED, northSouthGreen),
                new Phase(Signal.AMBER, Signal.RED, 2),
                new Phase(Signal.RED, Signal.RED, 1),
                new Phase(Signal.RED, Signal.GREEN, eastWestGreen),
                new Phase(Signal.RED, Signal.AMBER, 2),
                new Phase(Signal.RED, Signal.RED, 1));
        cycleTicks = ticksFor(northSouthGreen + eastWestGreen + 6);
    }

    synchronized void produce(int carsPerMinute) {
        if (collision != null || seconds() < nextArrival) {
            return;
        }
        nextArrival = seconds() + 60.0 / carsPerMinute;
        boolean northSouth = random.nextInt(100) < northSouthShare;
        int index = (northSouth ? 0 : 1) + 2 * random.nextInt(2);
        Lane lane = lanes.get(index);
        Car last = null;
        for (Car car : lane.approach) {
            last = car;
        }
        if ((last == null || last.position >= GAP)
                && lane.approach.offer(new Car(random.nextInt(7), random.nextDouble(0.19, 0.40)))) {
            admitted++;
        } else {
            blocked++;
        }
    }

    synchronized void advance() {
        if (collision != null) {
            return;
        }
        ticks++;
        Phase phase = currentPhase().phase();
        for (Lane lane : lanes) {
            double departureLimit = Double.POSITIVE_INFINITY;
            for (Car car : lane.departing) {
                car.position = Math.min(car.position + car.speed * STEP_SECONDS, departureLimit);
                departureLimit = car.position - GAP;
            }
            lane.departing.removeIf(car -> car.position > 1.05);
            Car lastDeparting = lane.departing.isEmpty()
                    ? null : lane.departing.get(lane.departing.size() - 1);
            double limit = STOP;
            if (lastDeparting != null) {
                limit = Math.min(limit, lastDeparting.position - GAP);
            }
            for (Car car : lane.approach) {
                double previous = car.position;
                car.position = Math.min(previous + car.speed * STEP_SECONDS, limit);
                car.stopped = car.position <= previous + 0.000001;
                if (car.stopped) {
                    car.wait += STEP_SECONDS;
                }
                limit = car.position - GAP;
            }

            Car lead = lane.approach.peek();
            if (phase.forApproach(lane.direction) == Signal.GREEN && lead != null && lead.position >= STOP) {
                lane.departing.add(lane.approach.remove());
                lead.stopped = false;
                lane.crossed++;
                crossed++;
                totalWait += lead.wait;
            }
        }
        if (!synchronizedSignals) {
            detectCollision();
        }
    }

    private void detectCollision() {
        for (int vertical = 0; vertical < 4; vertical += 2) {
            Lane northSouth = lanes.get(vertical);
            for (Car first : northSouth.departing) {
                Point a = center(northSouth.direction, first.position);
                for (int horizontal = 1; horizontal < 4; horizontal += 2) {
                    Lane eastWest = lanes.get(horizontal);
                    for (Car second : eastWest.departing) {
                        Point b = center(eastWest.direction, second.position);
                        // A vertical car is 14 by 18 pixels. A horizontal car swaps those dimensions.
                        if (Math.abs(a.x() - b.x()) < 16.0 / 400
                                && Math.abs(a.y() - b.y()) < 16.0 / 400) {
                            collision = new Collision((a.x() + b.x()) / 2, (a.y() + b.y()) / 2,
                                    northSouth.direction, eastWest.direction);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static Point center(Approach approach, double position) {
        return switch (approach) {
            case NORTH -> new Point(0.44, position);
            case EAST -> new Point(1 - position, 0.44);
            case SOUTH -> new Point(0.56, 1 - position);
            case WEST -> new Point(position, 0.56);
        };
    }

    synchronized Snapshot snapshot() {
        List<LaneView> views = new ArrayList<>();
        int waiting = 0;
        for (Lane lane : lanes) {
            List<Vehicle> cars = new ArrayList<>();
            int laneWaiting = 0;
            for (Car car : lane.approach) {
                cars.add(new Vehicle(car.position, car.color, car.stopped));
                if (car.stopped) {
                    laneWaiting++;
                }
            }
            for (Car car : lane.departing) {
                cars.add(new Vehicle(car.position, car.color, false));
            }
            views.add(new LaneView(lane.direction, List.copyOf(cars), laneWaiting, lane.crossed));
            waiting += laneWaiting;
        }
        ActivePhase active = currentPhase();
        return new Snapshot(List.copyOf(views), active.phase(), active.remaining(),
                admitted, crossed, waiting, blocked, crossed == 0 ? 0 : totalWait / crossed, collision);
    }

    private double seconds() {
        return ticks * STEP_SECONDS;
    }

    private record ActivePhase(Phase phase, double remaining) {}

    private ActivePhase currentPhase() {
        if (!synchronizedSignals) {
            AxisPhase ns = independentPhase(northSouthGreen);
            AxisPhase ew = independentPhase(eastWestGreen);
            double remaining = Math.min(ns.remaining(), ew.remaining());
            return new ActivePhase(new Phase(ns.signal(), ew.signal(), remaining), remaining);
        }
        long elapsed = ticks % cycleTicks;
        for (Phase phase : phases) {
            long duration = ticksFor(phase.duration());
            if (elapsed < duration) {
                return new ActivePhase(phase, (duration - elapsed) * STEP_SECONDS);
            }
            elapsed -= duration;
        }
        throw new IllegalStateException("Signal phases do not match the cycle length");
    }

    private record AxisPhase(Signal signal, double remaining) {}

    private AxisPhase independentPhase(int green) {
        long elapsed = ticks % ticksFor(green + 8);
        long greenEnd = ticksFor(green);
        long amberEnd = ticksFor(green + 2);
        if (elapsed < greenEnd) {
            return new AxisPhase(Signal.GREEN, (greenEnd - elapsed) * STEP_SECONDS);
        }
        if (elapsed < amberEnd) {
            return new AxisPhase(Signal.AMBER, (amberEnd - elapsed) * STEP_SECONDS);
        }
        return new AxisPhase(Signal.RED, (ticksFor(green + 8) - elapsed) * STEP_SECONDS);
    }

    private static long ticksFor(double seconds) {
        return Math.round(seconds / STEP_SECONDS);
    }
}
