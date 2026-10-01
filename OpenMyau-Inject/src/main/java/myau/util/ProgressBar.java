package myau.util;

import myau.Myau;
import myau.module.modules.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

import java.awt.Color;

public final class ProgressBar {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float BASE_WIDTH = 200.0F;
    private static final float BASE_HEIGHT = 6.0F;
    private static final float BELOW_CENTRE = 15.0F;
    private static final Color TRACK = new Color(0, 0, 0, 110);
    private static final double ENTRY_SCALE = 0.95;
    private static final double EXIT_SCALE = 1.1;
    private static final double FADE_RATE = 10.0;

    private static final Animation fill = new Animation(Animation.LINEAR, 50L);

    private static final Animation presence = new Animation(Animation.EASE_OUT_EXPO, 900L);
    private static boolean pushed;
    private static boolean idle = true;
    private static float target;
    private static float scale = 1.0F;
    private ProgressBar() {
    }
    public static void push(float progress) {
        push(progress, 1.0F);
    }
    public static void push(float progress, float barScale) {
        float clamped = MathHelper.clamp_float(progress, 0.0F, 1.0F);
        float clampedScale = MathHelper.clamp_float(barScale, 0.5F, 1.0F);
        boolean fresh = idle || scale != clampedScale;
        target = clamped;
        scale = clampedScale;
        pushed = true;
        idle = false;
        if (fresh) {
            presence.jumpTo(ENTRY_SCALE);
            fill.jumpTo(0.0);
        }
    }

    public static void tick() {
        pushed = false;
    }
    public static void reset() {
        pushed = false;
        idle = true;
        target = 0.0F;
        fill.jumpTo(0.0);
        presence.jumpTo(ENTRY_SCALE);
    }
    public static void render() {
        if (idle || mc.thePlayer == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        presence.to(pushed ? 1.0 : EXIT_SCALE);
        double grown = presence.getValue();

        double opacity = 1.0 - FADE_RATE * Math.abs(1.0 - grown);
        fill.to(target);
        float width = BASE_WIDTH * scale;
        float height = BASE_HEIGHT * scale;
        float x = resolution.getScaledWidth() * 0.5F - width * 0.5F;
        float y = resolution.getScaledHeight() * 0.5F + BELOW_CENTRE;
        int trackAlpha = alpha(TRACK.getAlpha() * opacity);
        int fillAlpha = alpha(255.0 * opacity);
        if (trackAlpha > 0 || fillAlpha > 0) {
            Color left = accent(0.0);
            Color right = accent(50.0);
            GlStateManager.pushMatrix();
            GlStateManager.translate((x + width * 0.5F) * (1.0 - grown),
                    (y + height * 0.5F) * (1.0 - grown), 0.0);
            GlStateManager.scale(grown, grown, 1.0);
            RenderUtil.enableRenderState();
            RenderUtil.drawRect(x, y, x + width, y + height, withAlpha(TRACK, trackAlpha));
            float filled = (float) (width * fill.getValue());
            if (filled > 0.0F) {
                RenderUtil.drawGradientRect(x, y, x + filled, y + height,
                        withAlpha(left, fillAlpha), withAlpha(right, fillAlpha));
            }
            RenderUtil.disableRenderState();
            GlStateManager.popMatrix();
        }
        if (!pushed && presence.isFinished()) {
            fill.jumpTo(0.0);
            idle = true;
        }
    }
    private static Color accent(double y) {
        Theme theme = (Theme) Myau.moduleManager.modules.get(Theme.class);
        return theme == null ? Color.WHITE : theme.getColor(0.0, y);
    }
    private static int alpha(double value) {
        return (int) MathHelper.clamp_double(value, 0.0, 255.0);
    }

    private static int withAlpha(Color color, int alpha) {
        return (alpha << 24) | (color.getRGB() & 0xFFFFFF);
    }
}
