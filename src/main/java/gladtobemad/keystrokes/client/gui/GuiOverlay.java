package gladtobemad.keystrokes.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.event.InputEvent.MouseInputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.gui.GuiUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

public class GuiOverlay {

    private Minecraft mc;
    private int screen_height = 0, screen_width = 0;

    private final double cell_size_width_ratio = 0.04;
    private final double cell_gap_ratio = 0.05;
    private final double font_height_adjustment = (7.0 / 9.0);
    private final int cell_color_idle = 0x80000000;
    private final int cell_color_pressed = 0xC0FFFFFF;
    private final int label_color_pressed = 0xFF00CCFF;
    private final int label_color_idle = 0xFFFFFFFF;

    private int cell_size, cell_gap;
    private double font_size_scale;

    // 0 - W, 1 - A, 2 - S, 3 - D, 4 - LMB, 5 - RMB, 6 - Space
    private final int num_keys = 7;
    private int[] cell_x = new int[num_keys];
    private int[] cell_y = new int[num_keys];
    private int[] cell_w = new int[num_keys];
    private int[] cell_h = new int[num_keys];

    // 0 - 6 Same as above, 7 - CPS for LMB, 8 - CPS for RMB
    private final int num_labels = num_keys + 2;
    private String[] label_string = new String[num_labels];
    private int[] label_x = new int[num_labels];
    private int[] label_y = new int[num_labels];
    private double[] label_scale_ratio = new double[num_labels];

    private int cps_left, cps_right;
    private Set<Long> left_clicks = new HashSet<>();
    private Set<Long> right_clicks = new HashSet<>();

    private final int cooldown_ticks = 5;
    private int[] key_cooldown = new int[num_labels];
    private final KeyBinding[] keybinds = new KeyBinding[num_labels];

    public GuiOverlay(Minecraft minecraft) {
        mc = minecraft;
        keybinds[0] = mc.gameSettings.keyBindForward;
        keybinds[1] = mc.gameSettings.keyBindLeft;
        keybinds[2] = mc.gameSettings.keyBindBack;
        keybinds[3] = mc.gameSettings.keyBindRight;
        keybinds[4] = mc.gameSettings.keyBindAttack;
        keybinds[5] = mc.gameSettings.keyBindUseItem;
        keybinds[6] = mc.gameSettings.keyBindJump;
        updateKeybindLabels();
    }

    // Update the coordinates if the screen size was changed
    private void updateScreenSize(int w, int h) {
        if (screen_height != h || screen_width != w) {
            screen_height = h;
            screen_width = w;
            calcGUICoordinates();
        }
    }

    // Tick the cooldowns of the keys
    private void updateKeyCooldown() {
        for (int i = 0; i < num_keys; i++) {
            if (keybinds[i].isKeyDown()) {
                key_cooldown[i] = cooldown_ticks;
            } else if (key_cooldown[i] > 0) {
                key_cooldown[i]--;
            }
        }
    }

    // Method for mixing two colors for a smooth transition
    private static int mixColors(int col1, int col2, int weight1, int totalWeight) {
        if (weight1 == 0) return col2;
        if (weight1 == totalWeight) return col1;

        int a1 = (0xFF000000 & col1) >> 24;
        int r1 = (0x00FF0000 & col1) >> 16;
        int g1 = (0x0000FF00 & col1) >> 8;
        int b1 = (0x000000FF & col1);
        int a2 = (0xFF000000 & col2) >> 24;
        int r2 = (0x00FF0000 & col2) >> 16;
        int g2 = (0x0000FF00 & col2) >> 8;
        int b2 = (0x000000FF & col2);
        int a3 = (a1 * weight1 + a2 * (totalWeight - weight1)) / totalWeight;
        int r3 = (r1 * weight1 + r2 * (totalWeight - weight1)) / totalWeight;
        int g3 = (g1 * weight1 + g2 * (totalWeight - weight1)) / totalWeight;
        int b3 = (b1 * weight1 + b2 * (totalWeight - weight1)) / totalWeight;

        return (a3 << 24) + (r3 << 16) + (g3 << 8) + b3;
    }

    // Calculate all the coordinate/size values to use during rendering
    private void calcGUICoordinates() {
        cell_size = (int) Math.round(((double) screen_width) * cell_size_width_ratio);
        cell_gap = (int) Math.round(((double) cell_size) * cell_gap_ratio);
        font_size_scale = ((double) cell_size) / ((double) mc.fontRenderer.FONT_HEIGHT);

        int corner_x = cell_size/4;
        int corner_y = cell_size/4;

        cell_x[0] = corner_x + cell_size + cell_gap;
        cell_y[0] = corner_y;
        cell_w[0] = cell_size;
        cell_h[0] = cell_size;

        cell_x[1] = corner_x;
        cell_y[1] = corner_y + cell_size + cell_gap;
        cell_w[1] = cell_size;
        cell_h[1] = cell_size;

        cell_x[2] = corner_x + cell_size + cell_gap;
        cell_y[2] = corner_y + cell_size + cell_gap;
        cell_w[2] = cell_size;
        cell_h[2] = cell_size;

        cell_x[3] = corner_x + cell_size*2 + cell_gap*2;
        cell_y[3] = corner_y + cell_size + cell_gap;
        cell_w[3] = cell_size;
        cell_h[3] = cell_size;

        cell_x[4] = corner_x;
        cell_y[4] = corner_y + cell_size*2 + cell_gap*2;
        cell_w[4] = (cell_size * 3 + cell_gap) / 2;
        cell_h[4] = cell_size;

        cell_x[5] = corner_x + cell_size*3 + cell_gap*2 - ((cell_size * 3 + cell_gap) / 2);
        cell_y[5] = corner_y + cell_size*2 + cell_gap*2;
        cell_w[5] = (cell_size * 3 + cell_gap) / 2;
        cell_h[5] = cell_size;

        cell_x[6] = corner_x;
        cell_y[6] = corner_y + cell_size*3 + cell_gap*3;
        cell_w[6] = cell_size*3 + cell_gap*2;
        cell_h[6] = cell_size / 2;

        label_scale_ratio[0] = 0.5 * font_size_scale;
        label_scale_ratio[1] = 0.5 * font_size_scale;
        label_scale_ratio[2] = 0.5 * font_size_scale;
        label_scale_ratio[3] = 0.5 * font_size_scale;
        label_scale_ratio[4] = 0.4 * font_size_scale;
        label_scale_ratio[5] = 0.4 * font_size_scale;
        label_scale_ratio[6] = 0.25 * font_size_scale;
        label_scale_ratio[7] = 0.25 * font_size_scale;
        label_scale_ratio[8] = 0.25 * font_size_scale;

        for (int i = 0; i < 4; i++) {
            double label_height = ((double) mc.fontRenderer.FONT_HEIGHT) * (label_scale_ratio[i]) * font_height_adjustment;
            double label_width = ((double) mc.fontRenderer.getStringWidth(label_string[i])) * (label_scale_ratio[i]);
            label_x[i] = (int) (cell_x[i] + (cell_w[i] - label_width) / 2.0);
            label_y[i] = (int) (cell_y[i] + (cell_h[i] - label_height) / 2.0);
        }

        for (int i = 4; i < 6; i++) {
            double label_width = ((double) mc.fontRenderer.getStringWidth(label_string[i])) * (label_scale_ratio[i]);
            label_x[i] = (int) (cell_x[i] + (cell_w[i] - label_width) / 2.0);
            label_y[i] = (int) (cell_y[i] + ((double) cell_size) * 0.2);
        }

        for (int i = 6; i < 7; i++) {
            double label_height = ((double) mc.fontRenderer.FONT_HEIGHT) * (label_scale_ratio[i]) * font_height_adjustment;
            double label_width = ((double) mc.fontRenderer.getStringWidth(label_string[i])) * (label_scale_ratio[i]);
            label_x[i] = (int) (cell_x[i] + (cell_w[i] - label_width) / 2.0);
            label_y[i] = (int) (cell_y[i] + (cell_h[i] - label_height) / 2.0);
        }

        for (int i = 7; i < 9; i++) {
            double label_width = ((double) mc.fontRenderer.getStringWidth(label_string[i])) * (label_scale_ratio[i]);
            label_x[i] = (int) (cell_x[i-3] + (cell_w[i-3] - label_width) / 2.0);
            label_y[i] = (int) (cell_y[i-3] + ((double) cell_size) * 0.6);
        }
    }

    // Update the key labels with new keybinds
    private void updateKeybindLabels() {
        for (int i = 0; i < num_keys; i++) {
            label_string[i] = getNiceKeyLabel(keybinds[i]);
        }
        calcGUICoordinates();
    }

    // Update the CPS labels with the new cps values
    private void updateCPSLabels() {
        calculateCPS();
        label_string[num_keys] = cps_left + " CPS";
        label_string[num_keys + 1] = cps_right + " CPS";
    }

    // Calculate the clicks per second values
    private void calculateCPS() {
        cps_left = 0;
        cps_right = 0;
        long now = System.currentTimeMillis();

        Set<Long> left_new = new HashSet<>();
        for (Long time : left_clicks) {
            if (now - time <= 1000) {
                cps_left++;
                left_new.add(time);
            }
        }
        left_clicks = left_new;

        Set<Long> right_new = new HashSet<>();
        for (Long time : right_clicks) {
            if (now - time <= 1000) {
                cps_right++;
                right_new.add(time);
            }
        }
        right_clicks = right_new;
    }

    // Method for getting nice key labels
    private static String getNiceKeyLabel(KeyBinding key) {
        int keyCode = key.getKey().getKeyCode();
        String result;
        switch (keyCode) {
            case 0: result = "LMB"; break;
            case 1: result = "RMB"; break;
            case 2: result = "MMB"; break;
            case 32: result = "-------"; break;
            default: result = key.getLocalizedName().toUpperCase();
            if (result.length() > 3) result = result.substring(0,3);
        }
        return result;
    }

    // Update Keybinds after every tick
    @SubscribeEvent
    public void checkKeybinds(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        updateKeybindLabels();
    }

    // Keep track of mouse clicks
    @SubscribeEvent
    public void onClick(InputEvent event) {
        if (event instanceof KeyInputEvent) {
            KeyInputEvent keyEvent = (KeyInputEvent) event;
            if (keyEvent.getAction() != GLFW.GLFW_PRESS) return;
            if (keyEvent.getKey() == keybinds[4].getKey().getKeyCode()) {
                left_clicks.add(System.currentTimeMillis());
            } else if (keyEvent.getKey() == keybinds[5].getKey().getKeyCode()) {
                right_clicks.add(System.currentTimeMillis());
            }
        } else if (event instanceof MouseInputEvent) {
            MouseInputEvent mouseEvent = (MouseInputEvent) event;
            if (mouseEvent.getAction() != GLFW.GLFW_PRESS) return;
            if (mouseEvent.getButton() == keybinds[4].getKey().getKeyCode()) {
                left_clicks.add(System.currentTimeMillis());
            } else if (mouseEvent.getButton() == keybinds[5].getKey().getKeyCode()) {
                right_clicks.add(System.currentTimeMillis());
            }
        }
    }

    // Render HUD
    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        int width = event.getWindow().getScaledWidth();
        int height = event.getWindow().getScaledHeight();
        updateScreenSize(width, height);
        updateKeyCooldown();
        updateCPSLabels();

        // Draw Rectangles
        for (int i = 0; i < num_keys; i++) {
            int cell_color = mixColors(cell_color_pressed, cell_color_idle, key_cooldown[i], cooldown_ticks);
            GuiUtils.drawGradientRect(0, cell_x[i], cell_y[i], cell_x[i] + cell_w[i], cell_y[i] + cell_h[i], cell_color, cell_color);
        }

        // Draw Labels
        for (int i = 0; i < num_labels; i++) {
            GL11.glPushMatrix();
            int cd = i < num_keys ? key_cooldown[i] : key_cooldown[i-3];
            int label_color = mixColors(label_color_pressed, label_color_idle, cd, cooldown_ticks);
            double scale = label_scale_ratio[i];
            GL11.glTranslated(label_x[i], label_y[i], 0);
            GL11.glScaled(scale, scale, 1);
            GL11.glTranslated(1, 0, 0);
            mc.fontRenderer.drawString(label_string[i], 0, 0, label_color);
            GL11.glPopMatrix();
        }
    }
}
