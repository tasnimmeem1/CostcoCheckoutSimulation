package traffic;

import javax.swing.JPanel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

final class RoadPanel extends JPanel {
    private static final double GROUND_TILT = 0.78;
    private static final Color PARK = new Color(0x173B36);
    private static final Color ASPHALT = new Color(0x29363E);
    private static final Color CURB = new Color(0x91A6A2);
    private static final Color TEXT = new Color(0xD5E5E1);
    private static final Color[] PALETTE = {
        new Color(0x43D0AD), new Color(0xF4BB56), new Color(0x8EAAFA),
        new Color(0xF27E78), new Color(0xD3B6FD), new Color(0xDFE9E9),
        new Color(0x63BEED)
    };
    private TrafficModel.Snapshot snapshot;
    private boolean elevated = true;

    void setElevated(boolean elevated) {
        this.elevated = elevated;
        repaint();
    }

    void display(TrafficModel.Snapshot snapshot) {
        this.snapshot = snapshot;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (snapshot == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PARK);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            int sceneWidth = getWidth() - 248;
            double centerX = 232 + sceneWidth / 2.0;
            double centerY = getHeight() / 2.0 + 9;
            double scale = elevated
                    ? Math.min((sceneWidth - 30) / 400.0, (getHeight() - 32) / 322.0)
                    : Math.min((sceneWidth - 20) / 400.0, (getHeight() - 28) / 400.0);
            Graphics2D map = (Graphics2D) g.create();
            try {
                map.clipRect(231, 8, getWidth() - 241, getHeight() - 16);
                map.translate(centerX, centerY);
                map.scale(scale, scale);
                if (elevated) {
                    map.scale(1, GROUND_TILT);
                }
                map.translate(-200, -200);
                drawIntersection(map);
                if (snapshot.collision() != null) {
                    drawCollision(map, snapshot.collision());
                }
            } finally {
                map.dispose();
            }
            drawOverview(g);
        } finally {
            g.dispose();
        }
    }

    private void drawOverview(Graphics2D g) {
        g.setColor(new Color(0x102C2C));
        g.fillRoundRect(18, 18, 207, getHeight() - 36, 18, 18);
        g.setColor(TEXT);
        g.setFont(getFont().deriveFont(Font.BOLD, 16f));
        g.drawString("Signal cycle", 34, 48);
        g.setFont(getFont().deriveFont(13f));
        if (snapshot.collision() == null) {
            g.drawString(String.format(Locale.US, "Next change in %.1f s", snapshot.remaining()), 34, 73);
        } else {
            g.setColor(new Color(0xFF817A));
            g.drawString("Collision detected. Press Reset.", 28, 73);
        }
        drawSignalLabel(g, "North–south", snapshot.phase().northSouth(), 106);
        drawSignalLabel(g, "East–west", snapshot.phase().eastWest(), 135);

        g.setColor(new Color(0x365452));
        g.drawLine(34, 155, 208, 155);
        g.setColor(TEXT);
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("FROM", 34, 179);
        g.drawString("STOPPED", 101, 179);
        g.drawString("CROSSED", 160, 179);
        g.setFont(getFont().deriveFont(13f));
        int y = 209;
        for (TrafficModel.LaneView lane : snapshot.lanes()) {
            String name = lane.approach().name().toLowerCase(Locale.ROOT);
            g.drawString(Character.toUpperCase(name.charAt(0)) + name.substring(1), 34, y);
            g.drawString(Integer.toString(lane.waiting()), 120, y);
            g.drawString(Integer.toString(lane.crossed()), 180, y);
            y += 28;
        }
    }

    private void drawCollision(Graphics2D map, TrafficModel.Collision collision) {
        int x = (int) (collision.x() * 400);
        int y = (int) (collision.y() * 400);
        map.setColor(new Color(0xFF817A));
        map.setStroke(new BasicStroke(2));
        map.drawOval(x - 17, y - 17, 34, 34);
    }

    private void drawSignalLabel(Graphics2D g, String axis, TrafficModel.Signal signal, int y) {
        g.setColor(signalColor(signal));
        g.fillOval(34, y - 10, 11, 11);
        g.setColor(TEXT);
        g.drawString(axis, 53, y);
        g.drawString(signal.label, 151, y);
    }

    private void drawIntersection(Graphics2D map) {
        drawTerrain(map);
        map.setColor(ASPHALT);
        map.fillRect(152, 0, 96, 400);
        map.fillRect(0, 152, 400, 96);
        for (TrafficModel.LaneView lane : snapshot.lanes()) {
            Graphics2D arm = (Graphics2D) map.create();
            try {
                arm.rotate(lane.approach().ordinal() * Math.PI / 2, 200, 200);
                drawApproach(arm, lane);
            } finally {
                arm.dispose();
            }
        }
        var cars = new ArrayList<SceneCar>();
        for (TrafficModel.LaneView lane : snapshot.lanes()) {
            for (TrafficModel.Vehicle car : lane.cars()) {
                cars.add(new SceneCar(lane.approach(), car));
            }
        }
        if (elevated) {
            cars.sort(Comparator.comparingDouble(SceneCar::depth));
        }
        for (SceneCar entry : cars) {
            Graphics2D arm = (Graphics2D) map.create();
            try {
                int direction = entry.approach().ordinal();
                arm.rotate(direction * Math.PI / 2, 200, 200);
                drawCar(arm, 176, (int) (entry.car().position() * 400), entry.car(), direction);
            } finally {
                arm.dispose();
            }
        }
    }

    private record SceneCar(TrafficModel.Approach approach, TrafficModel.Vehicle car) {
        double depth() {
            double travel = car.position() * 400;
            return switch (approach) {
                case NORTH -> travel;
                case EAST -> 176;
                case SOUTH -> 400 - travel;
                case WEST -> 224;
            };
        }
    }

    private void drawTerrain(Graphics2D map) {
        map.setColor(new Color(0x102927));
        map.fillRect(3, 5, 400, 400);
        map.setColor(new Color(0xA8AC9E));
        map.fillRect(0, 0, 400, 400);
        for (int x : new int[]{0, 248}) {
            for (int y : new int[]{0, 248}) {
                map.setColor(new Color(0x788D60));
                map.fillRect(x + 7, y + 7, 138, 138);
                map.setColor(new Color(0xA49D87));
                map.fillRect(x + 51, y + 78, 18, 74);
                map.setColor(new Color(0x647A4F));
                map.drawRect(x + 12, y + 12, 127, 127);
                Color roof = x == y ? new Color(0x884B3E) : new Color(0x4B5A67);
                drawHouse(map, x + 24, y + 80, roof);
                drawTree(map, x + 116, y + 69, 16);
                drawTree(map, x + 103, y + 116, 12);
            }
        }
    }

    private void drawHouse(Graphics2D map, int x, int y, Color roof) {
        Graphics2D house = (Graphics2D) map.create();
        try {
            house.translate(x, y);
            if (elevated) {
                house.scale(1, 1 / GROUND_TILT);
            }
            house.setColor(new Color(0x5B6B48));
            house.fillRoundRect(4, -25, 78, 31, 6, 6);
            if (elevated) {
                house.setColor(new Color(0xE4CDB0));
                house.fillRect(4, -26, 67, 26);
                house.setColor(new Color(0xB99F84));
                house.fillRect(64, -26, 7, 26);
                house.setColor(new Color(0x715245));
                house.fillRect(31, -14, 13, 14);
                house.setColor(new Color(0x526D75));
                house.fillRect(12, -17, 13, 9);
                house.fillRect(50, -17, 12, 9);
                house.setColor(new Color(0xE9E2CC));
                house.drawLine(18, -17, 18, -9);
                house.drawLine(56, -17, 56, -9);
            }
            int eave = elevated ? -24 : 0;
            house.setColor(roof);
            house.fillPolygon(new int[]{0, 9, 68, 76}, new int[]{eave, eave - 24, eave - 24, eave}, 4);
            house.setColor(roof.darker());
            house.fillPolygon(new int[]{0, 38, 76}, new int[]{eave, eave - 19, eave}, 3);
            house.setColor(roof.brighter());
            house.drawLine(9, eave - 24, 68, eave - 24);
            house.setColor(new Color(0x9C7565));
            house.fillRect(55, eave - 25, 8, 11);
        } finally {
            house.dispose();
        }
    }

    private void drawTree(Graphics2D map, int x, int y, int radius) {
        Graphics2D tree = (Graphics2D) map.create();
        try {
            tree.translate(x, y);
            if (elevated) {
                tree.scale(1, 1 / GROUND_TILT);
            }
            tree.setColor(new Color(0x5E734B));
            tree.fillOval(-radius + 5, -5, 2 * radius, 11);
            int crown = elevated ? -radius - 11 : 0;
            tree.setColor(new Color(0x6A4C38));
            tree.fillRect(-2, crown, 4, -crown + 2);
            tree.setColor(new Color(0x355D3D));
            tree.fillOval(-radius, crown - radius, 2 * radius, 2 * radius);
            tree.setColor(new Color(0x507747));
            tree.fillOval(-radius + 1, crown - radius - 3, radius + 7, radius + 7);
            tree.setColor(new Color(0x678954));
            tree.fillOval(-radius + 3, crown - radius - 1, radius, radius);
        } finally {
            tree.dispose();
        }
    }

    private static void lift(Graphics2D g, double height, int direction) {
        double shift = -height / GROUND_TILT;
        switch (direction) {
            case 0 -> g.translate(0, shift);
            case 1 -> g.translate(shift, 0);
            case 2 -> g.translate(0, -shift);
            case 3 -> g.translate(-shift, 0);
            default -> throw new IllegalArgumentException("Unknown approach");
        }
    }

    private void drawApproach(Graphics2D g, TrafficModel.LaneView lane) {
        g.setColor(CURB);
        g.fillRect(151, 0, 2, 152);
        g.fillRect(247, 0, 2, 152);
        g.setColor(new Color(0xD3AC63));
        g.fillRect(198, 0, 2, 140);
        g.fillRect(202, 0, 2, 140);
        int stopLine = (int) (TrafficModel.STOP * 400) + 10;
        g.setColor(TEXT);
        g.fillRect(154, stopLine, 44, 2);
        for (int x = 156; x <= 238; x += 9) {
            g.fillRect(x, 152, 5, 9);
        }
        drawSignalHead(g, lane, stopLine);

        g.setColor(new Color(0x71898D));
        g.drawLine(176, 76, 176, 90);
        g.drawLine(176, 90, 171, 85);
        g.drawLine(176, 90, 181, 85);
        g.drawLine(224, 76, 224, 90);
        g.drawLine(224, 76, 219, 81);
        g.drawLine(224, 76, 229, 81);

        // Undo rotation around the label so the direction letter stays upright.
        g.rotate(-lane.approach().ordinal() * Math.PI / 2, 133, 32);
        g.setColor(TEXT);
        g.setFont(getFont().deriveFont(Font.BOLD, 15f));
        g.drawString(lane.approach().name().substring(0, 1), 128, 37);
    }

    private void drawSignalHead(Graphics2D arm, TrafficModel.LaneView lane, int stopLine) {
        Graphics2D signal = (Graphics2D) arm.create();
        try {
            signal.translate(144, stopLine - 6);
            signal.rotate(-lane.approach().ordinal() * Math.PI / 2);
            if (elevated) {
                signal.scale(1, 1 / GROUND_TILT);
            }
            signal.setColor(new Color(0x525D5F));
            signal.fillRect(-2, -18, 4, 21);
            signal.setColor(new Color(0x161D1F));
            signal.fillRoundRect(-7, -38, 14, 28, 4, 4);
            TrafficModel.Signal active = snapshot.phase().forApproach(lane.approach());
            TrafficModel.Signal[] lights = {
                    TrafficModel.Signal.RED, TrafficModel.Signal.AMBER, TrafficModel.Signal.GREEN
            };
            for (int index = 0; index < lights.length; index++) {
                Color color = signalColor(lights[index]);
                signal.setColor(lights[index] == active ? color : color.darker().darker().darker());
                signal.fillOval(-3, -35 + index * 8, 6, 6);
            }
        } finally {
            signal.dispose();
        }
    }

    private static Color signalColor(TrafficModel.Signal signal) {
        return switch (signal) {
            case GREEN -> new Color(0x55DBA1);
            case AMBER -> new Color(0xFFC567);
            case RED -> new Color(0xFF817A);
        };
    }

    private void drawCar(Graphics2D g, int x, int y, TrafficModel.Vehicle car, int direction) {
        g.setColor(new Color(0x111B1E));
        g.fillRoundRect(x - 7, y - 8, 16, 19, 5, 5);
        Color paint = PALETTE[car.color()];
        if (elevated) {
            for (int level = 0; level < 6; level++) {
                g.setColor(paint.darker());
                g.fillRoundRect(x - 7, y - 9, 14, 18, 5, 5);
                lift(g, 1, direction);
            }
        }
        g.setColor(paint);
        g.fillRoundRect(x - 7, y - 9, 14, 18, 5, 5);
        if (elevated) {
            g.setColor(paint.darker());
            g.fillRoundRect(x - 5, y - 5, 10, 11, 3, 3);
            lift(g, 3, direction);
        }
        g.setColor(new Color(0x253C46));
        g.fillRoundRect(x - 5, y - 4, 10, 9, 3, 3);
        g.setColor(new Color(0x8BA9AD));
        g.fillRect(x - 4, y + 3, 8, 2);
        g.setColor(car.stopped() ? new Color(0xFF352A) : new Color(0x9C444A));
        g.fillRect(x - 5, y - 8, 3, 2);
        g.fillRect(x + 2, y - 8, 3, 2);
        g.setColor(new Color(0xFFF3CE));
        g.fillRect(x - 5, y + 7, 3, 2);
        g.fillRect(x + 2, y + 7, 3, 2);
    }
}
