package com.euvcraft.lithmod.mc.multiblock.reflow;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thermal calibration console: one coupon slot, three +/- control pairs, a temperature graph and
 * the accept flow, arranged like the scanner diagnostic console inside the same opaque 320x238
 * window with the player inventory below the graph.
 *
 * <p>The screen is a pure view of the menu's synced {@code ContainerData} values: it never reads
 * the response seed, the controller or the machine JSON. The amber shaded envelope and the peak
 * band are visual guidance built only from the synced rule bounds (a fast-to-peak/hot profile and
 * a slow-to-peak/cool guide profile); the numerical band readouts show the exact synced bounds.
 * Gray is the nominal preview that the server recomputes after every +/- click; cyan is the last
 * server-paid test's measured curve and only appears while {@code CAL_TESTED} is set.</p>
 */
public final class ReflowCalibrationScreen extends AbstractContainerScreen<ReflowCalibrationMenu> {

    private static final int GRAPH_X = 15;
    private static final int GRAPH_Y = 36;
    private static final int GRAPH_W = 290;
    private static final int GRAPH_H = 48;

    /** Graph temperature window in centi-degrees: ambient 20.00 C up to a 320.00 C ceiling. */
    private static final int TEMP_MIN = 2_000;
    private static final int TEMP_SPAN = 30_000;

    private static final int PANEL_FILL = 0xFFD0D0D0;
    private static final int TEXT_COLOR = 0xFF303030;
    private static final int GRAPH_FILL = 0xFF152030;
    private static final int ENVELOPE_AMBER = 0x48FFC857;
    private static final int BAND_LINE = 0xB0FFB733;
    private static final int PREVIEW_GRAY = 0xFFC8D0DA;
    private static final int MEASURED_CYAN = 0xFF53D8FB;

    /**
     * Visual-guidance phase split of the normalized time axis for the shaded envelope. The real
     * phase boundaries depend on the (server-side) response, so these fixed fractions are only an
     * illustration of where a profile reaching the band corners would heat, hold and cool.
     */
    private static final double GUIDE_HEAT_END = 0.30;
    private static final double GUIDE_COOL_START = 0.65;

    private final List<Button> controlButtons = new ArrayList<>();
    private Button test;
    private Button accept;

    public ReflowCalibrationScreen(ReflowCalibrationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 238);
        this.inventoryLabelX = 8;
        this.inventoryLabelY = imageHeight - 94;
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        super.init();
        controlButtons.clear();
        pair(ReflowCalibrationMenu.BUTTON_HEATER_MINUS,
                ReflowCalibrationMenu.BUTTON_HEATER_PLUS, 88);
        pair(ReflowCalibrationMenu.BUTTON_CONVEYOR_MINUS,
                ReflowCalibrationMenu.BUTTON_CONVEYOR_PLUS, 104);
        pair(ReflowCalibrationMenu.BUTTON_AIRFLOW_MINUS,
                ReflowCalibrationMenu.BUTTON_AIRFLOW_PLUS, 120);
        test = addRenderableWidget(Button.builder(Component.literal("Test"),
                b -> send(ReflowCalibrationMenu.BUTTON_TEST))
                .bounds(leftPos + 8, topPos + 160, 60, 18).build());
        accept = addRenderableWidget(Button.builder(Component.literal("Accept"),
                b -> send(ReflowCalibrationMenu.BUTTON_ACCEPT))
                .bounds(leftPos + 8, topPos + 182, 60, 18).build());
    }

    /** The -/+ pair of one control row; both buttons share the row's enabled state. */
    private void pair(int minusId, int plusId, int y) {
        controlButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> send(minusId))
                .bounds(leftPos + 8, topPos + y, 18, 14).build()));
        controlButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(plusId))
                .bounds(leftPos + 28, topPos + y, 18, 14).build()));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        boolean formed = menu.formed();
        boolean locked = menu.value(ReflowCalibrationMenu.CAL_LOCKED) != 0;
        boolean controlsEnabled = formed && !locked;
        for (Button button : controlButtons) {
            button.active = controlsEnabled;
        }
        test.active = controlsEnabled
                && menu.value(ReflowCalibrationMenu.CAL_COUPON_OK) == 1
                && menu.value(ReflowCalibrationMenu.CAL_TEST_FE) > 0
                && menu.value(ReflowCalibrationMenu.CAL_ENERGY) >= menu.value(ReflowCalibrationMenu.CAL_TEST_FE);
        accept.active = menu.value(ReflowCalibrationMenu.CAL_ACCEPT_READY) == 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_FILL);
        drawSlotWells(graphics);
        drawGraph(graphics);
    }

    private void drawSlotWells(GuiGraphicsExtractor graphics) {
        for (var slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = leftPos + slot.x - 1;
            int sy = topPos + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF777777);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFA0A0A0);
        }
    }

    private void drawGraph(GuiGraphicsExtractor graphics) {
        int x = leftPos + GRAPH_X;
        int y = topPos + GRAPH_Y;
        graphics.fill(x, y, x + GRAPH_W, y + GRAPH_H, GRAPH_FILL);

        int minPeak = menu.value(ReflowCalibrationMenu.CAL_MIN_PEAK);
        int maxPeak = menu.value(ReflowCalibrationMenu.CAL_MAX_PEAK);
        if (maxPeak > minPeak && minPeak >= TEMP_MIN) {
            // Shaded target envelope between a fast/hot and a slow/cool guide profile, plus the
            // exact synced peak band drawn as two amber boundary lines. Both come only from the
            // synced rule bounds.
            for (int column = 0; column < GRAPH_W; column++) {
                double t = (double) column / (GRAPH_W - 1);
                int envelopeTop = yClamped((int) guideTemperature(t, maxPeak));
                int envelopeBottom = yClamped((int) guideTemperature(t, minPeak));
                graphics.fill(x + column, envelopeTop, x + column + 1, envelopeBottom + 1, ENVELOPE_AMBER);
            }
            int bandTop = yClamped(maxPeak);
            graphics.fill(x, bandTop, x + GRAPH_W, bandTop + 1, BAND_LINE);
            int bandBottom = yClamped(minPeak);
            graphics.fill(x, bandBottom, x + GRAPH_W, bandBottom + 1, BAND_LINE);
        }

        drawCurve(graphics, ReflowCalibrationMenu.CAL_PREVIEW_START, PREVIEW_GRAY);
        if (menu.tested()) {
            drawCurve(graphics, ReflowCalibrationMenu.CAL_MEASURED_START, MEASURED_CYAN);
        }
    }

    /** A straight guide profile reaching the given peak (centi-degrees), in centi-degrees. */
    private static double guideTemperature(double t, double peak) {
        if (t < GUIDE_HEAT_END) {
            return TEMP_MIN + (peak - TEMP_MIN) * (t / GUIDE_HEAT_END);
        }
        if (t < GUIDE_COOL_START) {
            return peak;
        }
        return TEMP_MIN + (peak - TEMP_MIN) * (1.0 - (t - GUIDE_COOL_START) / (1.0 - GUIDE_COOL_START));
    }

    /** Draws the 49 synced centi-degree samples column by column, clipped to the graph box. */
    private void drawCurve(GuiGraphicsExtractor graphics, int start, int color) {
        int x = leftPos + GRAPH_X;
        int y = topPos + GRAPH_Y;
        int previous = yClamped(menu.value(start));
        for (int n = 1; n < 49; n++) {
            int current = yClamped(menu.value(start + n));
            int px = x + (n - 1) * GRAPH_W / 48;
            int nx = x + n * GRAPH_W / 48;
            for (int xx = px; xx < nx && xx < x + GRAPH_W; xx++) {
                int yy = previous;
                if (nx > px) {
                    yy = previous + (current - previous) * (xx - px) / (nx - px);
                }
                yy = Math.clamp(yy, y, y + GRAPH_H - 1);
                graphics.fill(xx, yy, xx + 1, yy + 1, color);
            }
            previous = current;
        }
    }

    /** Maps centi-degrees to a graph row, monotonic and pre-clamped so curves stay inside the box. */
    private int yClamped(int centiDegrees) {
        int y = topPos + GRAPH_Y;
        int row = (GRAPH_H - 1)
                - Math.clamp(centiDegrees - TEMP_MIN, 0, TEMP_SPAN) * (GRAPH_H - 1) / TEMP_SPAN;
        return Math.clamp(y + row, y, y + GRAPH_H - 1);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int width) {
        graphics.text(font, font.plainSubstrByWidth(value, width), x, y, TEXT_COLOR, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        text(graphics, title.getString(), 8, 6, 300);
        text(graphics, "Ramp " + band(menu.value(29),menu.value(30)) + "  Peak " + centi(menu.value(21),menu.value(22)), 15,14,290);
        text(graphics, "Dwell " + band(menu.value(31),menu.value(32)) + "  Cooling " + band(menu.value(33),menu.value(34)),15,24,290);
        text(graphics, playerInventoryTitle.getString(), 79, 148, 160);

        text(graphics, "Heater " + menu.value(ReflowCalibrationMenu.CAL_HEATER), 52, 92, 110);
        text(graphics, "Conveyor " + menu.value(ReflowCalibrationMenu.CAL_CONVEYOR), 52, 108, 110);
        text(graphics, "Airflow " + menu.value(ReflowCalibrationMenu.CAL_AIRFLOW), 52, 124, 110);

        text(graphics, "R " + String.format(Locale.ROOT,"%.2f",menu.value(35)/100.0) + "  P " + String.format(Locale.ROOT,"%.2f C",menu.value(36)/100.0),170,92,140);
        text(graphics, "D " + String.format(Locale.ROOT,"%.2f",menu.value(37)/100.0) + "  C " + String.format(Locale.ROOT,"%.2f",menu.value(38)/100.0),170,104,140);
        text(graphics, qualityText(),170,116,105);
        text(graphics, "Coupon",278,118,40);
        text(graphics, "Test: " + menu.value(ReflowCalibrationMenu.CAL_TEST_FE) + " FE + " + menu.value(ReflowCalibrationMenu.CAL_COUPON_COUNT) + " coupon",8,137,226);
        text(graphics, "Cyan: paid test",244,160,70);
        text(graphics, "Gray: preview",244,172,70);
    }

    /** The exact synced rule bands; readouts always match the datapack JSON values. */
    private String bandText() {
        if (menu.value(ReflowCalibrationMenu.CAL_THERMAL_VALID) == 0) {
            return "Thermal rule bands: reflow_oven JSON missing or invalid";
        }
        return "Bands R " + band(menu.value(ReflowCalibrationMenu.CAL_MIN_RAMP),
                menu.value(ReflowCalibrationMenu.CAL_MAX_RAMP))
                + "  P " + centi(menu.value(ReflowCalibrationMenu.CAL_MIN_PEAK),
                menu.value(ReflowCalibrationMenu.CAL_MAX_PEAK))
                + "  D " + band(menu.value(ReflowCalibrationMenu.CAL_MIN_DWELL),
                menu.value(ReflowCalibrationMenu.CAL_MAX_DWELL))
                + "  C " + band(menu.value(ReflowCalibrationMenu.CAL_MIN_COOLING),
                menu.value(ReflowCalibrationMenu.CAL_MAX_COOLING));
    }

    private static String band(int minValue, int maxValue) {
        return String.format(Locale.ROOT, "%.2f-%.2f", minValue / 100.0, maxValue / 100.0);
    }

    private static String centi(int minValue, int maxValue) {
        return String.format(Locale.ROOT, "%.1f-%.1f C", minValue / 100.0, maxValue / 100.0);
    }

    private String qualityText() {
        if (!menu.tested()) {
            return "Not measured yet";
        }
        return "Q " + menu.value(ReflowCalibrationMenu.CAL_MEASURED_QUALITY)
                + " / min " + menu.value(ReflowCalibrationMenu.CAL_MIN_QUALITY);
    }

    private String problemText() {
        if (!menu.tested()) {
            return "Gray: nominal preview";
        }
        List<String> names = problemNames(menu.value(ReflowCalibrationMenu.CAL_PROBLEMS));
        return names.isEmpty() ? "In band: no problems" : "Issues: " + String.join(",", names);
    }

    /** Bit order mirrors the server problem mask: heating, peak, dwell, cooling. */
    private static List<String> problemNames(int mask) {
        List<String> names = new ArrayList<>();
        if ((mask & 1) != 0) {
            names.add("heating");
        }
        if ((mask & 2) != 0) {
            names.add("peak");
        }
        if ((mask & 4) != 0) {
            names.add("dwell");
        }
        if ((mask & 8) != 0) {
            names.add("cooling");
        }
        return names;
    }

    private String feedbackText() {
        if (!menu.tested()) {
            return "Gray: live nominal preview; a paid test draws the measured curve (cyan)";
        }
        return "Measured (cyan): R " + band(menu.value(ReflowCalibrationMenu.CAL_MEASURED_RAMP),
                menu.value(ReflowCalibrationMenu.CAL_MEASURED_RAMP))
                + "  P " + centi(menu.value(ReflowCalibrationMenu.CAL_MEASURED_PEAK),
                menu.value(ReflowCalibrationMenu.CAL_MEASURED_PEAK))
                + "  D " + band(menu.value(ReflowCalibrationMenu.CAL_MEASURED_DWELL),
                menu.value(ReflowCalibrationMenu.CAL_MEASURED_DWELL))
                + "  C " + band(menu.value(ReflowCalibrationMenu.CAL_MEASURED_COOLING),
                menu.value(ReflowCalibrationMenu.CAL_MEASURED_COOLING));
    }
}
