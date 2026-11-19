package traffic;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TrafficSimulation {
    static final Color INK = new Color(0x13282B);
    static final Color MUTED = new Color(0x50666A);
    static final Color PAPER = new Color(0xEFF4F2);
    private final JFrame frame = new JFrame("Traffic Flow Simulator | Four-Way Intersection");
    private final JSpinner northSouth = new JSpinner(new SpinnerNumberModel(6, 3, 15, 1));
    private final JSpinner eastWest = new JSpinner(new SpinnerNumberModel(6, 3, 15, 1));
    private final JComboBox<String> trafficMix = new JComboBox<>(
            new String[]{"Balanced", "70% N/S", "70% E/W"});
    private final JComboBox<String> signalMode = new JComboBox<>(
            new String[]{"Coordinated signals", "Unsynchronized signals"});
    private final JSlider density = new JSlider(15, 300, 100);
    private final JButton pause = new JButton("Pause");
    private final RoadPanel road = new RoadPanel();
    private final JLabel rateLabel = new JLabel();
    private final JLabel[] values = new JLabel[5];
    private final Timer refresh = new Timer(20, event -> refresh());
    private final Object control = new Object();
    private final ScheduledExecutorService workers = Executors.newScheduledThreadPool(2);
    private TrafficModel model = new TrafficModel(6, 6, 50);
    private boolean running = true;
    private int carsPerMinute = 100;

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                refresh.stop();
                workers.shutdownNow();
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(PAPER);
        content.setBorder(new EmptyBorder(22, 26, 22, 26));
        JPanel header = new JPanel(new BorderLayout(10, 12));
        header.setOpaque(false);
        JLabel title = label("Four-Way Traffic Simulator", 30, INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        controls.setOpaque(false);
        controls.add(label("N/S green (s)", 13, INK));
        controls.add(northSouth);
        controls.add(label("E/W green (s)", 13, INK));
        controls.add(eastWest);
        controls.add(trafficMix);
        northSouth.addChangeListener(event -> reset());
        eastWest.addChangeListener(event -> reset());
        trafficMix.addActionListener(event -> reset());
        density.setOpaque(false);
        density.setPreferredSize(new Dimension(105, 28));
        density.getAccessibleContext().setAccessibleName("Arrival rate in cars per minute");
        density.addChangeListener(event -> {
            synchronized (control) {
                carsPerMinute = density.getValue();
            }
            updateRateLabel();
        });
        updateRateLabel();
        controls.add(density);
        controls.add(rateLabel);
        pause.addActionListener(event -> {
            synchronized (control) {
                running = !running;
                pause.setText(running ? "Pause" : "Resume");
            }
        });
        controls.add(pause);
        JButton reset = new JButton("Reset");
        reset.addActionListener(event -> reset());
        controls.add(reset);
        JPanel settings = new JPanel(new GridLayout(2, 1));
        settings.setOpaque(false);
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modeRow.setOpaque(false);
        modeRow.add(signalMode);
        JComboBox<String> view = new JComboBox<>(new String[]{"3D neighborhood", "Top-down"});
        view.addActionListener(event -> road.setElevated(view.getSelectedIndex() == 0));
        modeRow.add(view);
        signalMode.addActionListener(event -> reset());
        settings.add(controls);
        settings.add(modeRow);
        header.add(settings, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);
        content.add(road, BorderLayout.CENTER);

        String[] captions = {"Entered road", "Stopped", "Crossed", "Blocked arrivals", "Avg. stopped time"};
        JPanel statistics = new JPanel(new GridLayout(1, captions.length, 10, 0));
        statistics.setOpaque(false);
        for (int i = 0; i < captions.length; i++) {
            JPanel card = new JPanel(new BorderLayout(0, 3));
            card.setBackground(Color.WHITE);
            card.setBorder(new EmptyBorder(10, 12, 10, 12));
            card.add(label(captions[i], 12, MUTED), BorderLayout.NORTH);
            values[i] = label("0", 23, INK);
            values[i].setFont(values[i].getFont().deriveFont(Font.BOLD));
            card.add(values[i], BorderLayout.CENTER);
            statistics.add(card);
        }
        content.add(statistics, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.setMinimumSize(new Dimension(940, 600));
        frame.setSize(1060, 730);
        frame.setLocationRelativeTo(null);
        refresh();
        frame.setVisible(true);

        workers.scheduleAtFixedRate(() -> {
            synchronized (control) {
                if (running) {
                    model.produce(carsPerMinute);
                }
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        workers.scheduleAtFixedRate(() -> {
            synchronized (control) {
                if (running) {
                    model.advance();
                }
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        refresh.start();
    }

    private void reset() {
        synchronized (control) {
            int northSouthShare = switch (trafficMix.getSelectedIndex()) {
                case 1 -> 70;
                case 2 -> 30;
                default -> 50;
            };
            model = new TrafficModel((Integer) northSouth.getValue(),
                    (Integer) eastWest.getValue(), northSouthShare, signalMode.getSelectedIndex() == 0);
        }
        refresh();
    }

    private void refresh() {
        TrafficModel.Snapshot snapshot;
        synchronized (control) {
            snapshot = model.snapshot();
        }
        pause.setEnabled(snapshot.collision() == null);
        road.display(snapshot);
        values[0].setText(Integer.toString(snapshot.admitted()));
        values[1].setText(Integer.toString(snapshot.waiting()));
        values[2].setText(Integer.toString(snapshot.crossed()));
        values[3].setText(Integer.toString(snapshot.blocked()));
        values[4].setText(String.format(Locale.US, "%.1f s", snapshot.averageWait()));
    }

    private void updateRateLabel() {
        rateLabel.setText(String.format("%3d cars/min", density.getValue()));
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont((float) size));
        label.setForeground(color);
        return label;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TrafficSimulation().show());
    }
}
