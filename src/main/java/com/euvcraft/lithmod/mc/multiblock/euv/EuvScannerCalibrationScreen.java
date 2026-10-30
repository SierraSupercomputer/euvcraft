package com.euvcraft.lithmod.mc.multiblock.euv;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** Calibration screen for EUV scanner (calibration panel). Shows movable spot/focus and six controls + accept. */
public final class EuvScannerCalibrationScreen extends AbstractContainerScreen<EuvScannerCalibrationMenu> {

    private final List<Button> controls = new ArrayList<>();
    private Button accept;

    public EuvScannerCalibrationScreen(EuvScannerCalibrationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 280, 228);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        controls.clear();

        String[] labels = {"X −", "X +", "Y −", "Y +", "Focus −", "Focus +"};
        for (int i = 0; i < 6; i++) {
            final int id = i;
            controls.add(addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send(id))
                    .bounds(leftPos + 164 + (i % 2) * 54, topPos + 42 + (i / 2) * 31, 51, 20).build()));
        }
        accept = addRenderableWidget(Button.builder(Component.translatable("button.lithmod.scanner.accept_alignment"), b -> send(6))
                .bounds(leftPos + 164, topPos + 147, 105, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean unlocked = menu.value(0) != 0 && menu.value(10) == 0;
        for (var b : controls) b.active = unlocked;
        if (accept != null) accept.active = unlocked && menu.value(2) >= menu.value(11);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFD0D0D0);

        if (menu.value(0) != 0) { // formed
            int x = leftPos + 12, y = topPos + 32;
            g.fill(x, y, x + 140, y + 140, 0xFF152030);
            g.fill(x + 69, y + 8, x + 71, y + 132, 0xFF526273);
            g.fill(x + 8, y + 69, x + 132, y + 71, 0xFF526273);
            // Target center crosshair
            g.fill(x + 65, y + 65, x + 75, y + 66, 0xFF55CC99);
            g.fill(x + 65, y + 74, x + 75, y + 75, 0xFF55CC99);
            g.fill(x + 65, y + 65, x + 66, y + 75, 0xFF55CC99);
            g.fill(x + 74, y + 65, x + 75, y + 75, 0xFF55CC99);
            // Movable spot based on spotX/spotY (values 4,5), focus error radius from value 6
            int sx = x + 70 + Math.clamp(menu.value(4) * 2, -62, 62);
            int sy = y + 70 + Math.clamp(menu.value(5) * 2, -62, 62);
            int radius = 2 + Math.min(9, menu.value(6));
            g.fill(sx - radius, sy - radius, sx + radius + 1, sy + radius + 1, 0xFFFFCC44);
        }
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int width) {
        g.text(font, font.plainSubstrByWidth(s, width), x, y, 0xFF303030, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        text(g, Component.translatable("block.lithmod.euv_scanner_calibration_panel").getString(), 8, 7, 264);
        if (menu.value(0) == 0) {
            text(g, "Unformed: check the 5x5x7 structure", 12, 32, 256);
            return;
        }
        text(g, "Center the spot; reduce focus error to zero.", 12, 20, 256);
        text(g, "X: " + menu.value(7) + "    Y: " + menu.value(8) + "    Focus: " + menu.value(9), 12, 179, 256);
        text(g, "Spot: " + menu.value(4) + ", " + menu.value(5) + "  Focus error: " + menu.value(6), 12, 190, 256);
        text(g, "Quality " + menu.value(2) + "/100  Yield ×" + menu.value(3) / 1000.0 + "  Minimum " + menu.value(11), 12, 201, 256);
        text(g, menu.value(10) != 0 ? "Locked while a lot is active"
                : menu.value(1) != 0 ? "Accepted—beam alignment saved"
                : "Adjust, then accept alignment", 12, 212, 256);
    }
}
