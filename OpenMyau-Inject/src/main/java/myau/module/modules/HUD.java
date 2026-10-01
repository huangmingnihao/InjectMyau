package myau.module.modules;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.lwjgl.opengl.GL11;

import myau.Myau;
import myau.access.AccessorGuiChat;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.font.Fonts;
import myau.util.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    private List<Entry> activeEntries = new ArrayList<>();
    private long lastAnimationMs = 0L;

    private static final float ANIMATION_MS = 100.0F;

    private static final double LERP_RATE = 0.015;

    private static final float MAX_FRAME_MS = 200.0F;

    private static final float BASE_PADDING = 2.0F;
    private static final float BASE_OUTLINE = 1.0F;
    private static final int OUTLINE_NONE = 0;
    private static final int OUTLINE_FULL = 1;
    private static final int OUTLINE_SIDE = 2;
    private static final int COLOR_THEME = 6;
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123", "THEME"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 43);
    public final IntProperty backgroundRounding = new IntProperty("background-rounding", 3, 0, 16);
    public final ModeProperty arraylistOutline = new ModeProperty("arraylist-outline", OUTLINE_SIDE,
            new String[]{"NONE", "FULL", "SIDE"});
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final ModeProperty arraylistFont = new ModeProperty("arraylist-font", 0,
            new String[]{"VANILLA", "SF-BOLD", "SF-REGULAR", "SF-UI", "PRODUCT-SANS",
                    "MSDF-SF", "MSDF-SF-BOLD", "MSDF-PRODUCT-SANS",
                    "MSDF-GOOGLE-SANS", "MSDF-GOOGLE-SANS-BOLD", "MSDF-TAHOMA",
                    "MSDF-TAHOMA-BOLD", "MSDF-VERDANA"});
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty removeSpaces = new BooleanProperty("remove-spaces", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    private static boolean shows(Module module) {
        return module.isEnabled() && !module.isHidden();
    }
    private String formatEntryText(String raw) {
        String text = this.lowerCase.getValue() ? raw.toLowerCase(Locale.ROOT) : raw;
        return this.removeSpaces.getValue() ? text.replace(" ", "") : text;
    }
    private float renderScale() {
        return this.scale.getValue();
    }
    private RavenFontRenderer getArraylistFont() {
        return Fonts.arraylist(this.arraylistFont.getValue(), this.scale.getValue());
    }
    private float hudPixels(float base) {
        return Math.max(1.0F, Math.round(base * this.scale.getValue()));
    }
    private float horizontalPadding() {
        return this.hudPixels(BASE_PADDING);
    }
    private float topPadding() {
        return this.hudPixels(BASE_PADDING);
    }
    private float outlineThickness() {
        return this.hudPixels(BASE_OUTLINE);
    }
    private float arraylistStringWidth(String string) {
        return this.getArraylistFont().getStringWidth(string);
    }
    private float arraylistLineHeight() {
        RavenFontRenderer font = this.getArraylistFont();
        return Math.max(1, font.getTextBottomOffset() - font.getTextTopOffset());
    }
    private float arraylistTextY(float rowY) {
        return rowY - this.getArraylistFont().getTextTopOffset();
    }
    private void arraylistDraw(String text, float x, float y, int color) {
        int opaque = (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
        this.getArraylistFont().drawString(text, x, y, opaque, this.shadow.getValue());
    }
    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }
    public HUD() {
        super("HUD", true, true);
    }
    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }
    public Color getStaticColor() {
        int mode = this.colorMode.getValue();
        if (mode == COLOR_THEME) {
            Theme theme = (Theme) Myau.moduleManager.modules.get(Theme.class);
            return theme == null ? Color.WHITE : theme.getTheme().getPrimary();
        }
        if (mode == 3 || mode == 4 || mode == 5) {
            return new Color(this.custom1.getValue());
        }
        return this.getColor(0L);
    }
    public Color getColor(long time, long offset) {
        if (this.colorMode.getValue() == COLOR_THEME) {
            Theme theme = (Theme) Myau.moduleManager.modules.get(Theme.class);
            if (theme != null) {
                return theme.getColor(0.0, offset * 11.0);
            }
        }
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        for (Module module : Myau.moduleManager.modules.values()) {
            if (!module.isHidden()) {
                this.entries.computeIfAbsent(module, Entry::new);
            }
        }
        for (Entry entry : this.entries.values()) {
            if (!shows(entry.module) && entry.animationTime == 0.0F) {
                continue;
            }
            String name = this.formatEntryText(entry.module.getName());
            String[] suffix = entry.module.getSuffix();
            boolean hasTag = this.suffixes.getValue() && suffix.length > 0;

            entry.displayText = hasTag
                    ? name + " \u00a77" + this.formatEntryText(String.join(" ", suffix))
                    : name;
            entry.width = this.arraylistStringWidth(entry.displayText);
        }
        this.activeEntries = this.entries
                .values()
                .stream()
                .filter(entry -> shows(entry.module) || entry.animationTime > 0.0F)
                .sorted(Comparator.comparingDouble(entry -> -entry.width))
                .collect(Collectors.toList());
    }

    private float outlineSpace() {
        return this.arraylistOutline.getValue() == OUTLINE_NONE ? 0.0F : this.outlineThickness();
    }

    private double entryX(float width, boolean leaving, float screenWidth, float edgeX) {

        if (this.posX.getValue() == 0) {
            float inset = edgeX + this.outlineSpace() + this.horizontalPadding();
            return leaving ? inset - width * 2.0F : inset;
        }
        float inset = edgeX + this.outlineSpace() + this.horizontalPadding();
        return leaving ? screenWidth - inset + width : screenWidth - inset - width;
    }
    private void drawArrayListOutline(float boxLeft, float boxRight, float boxTop, float boxBottom,
                                      int color, boolean firstRow, float prevLeft, float prevRight) {
        int mode = this.arraylistOutline.getValue();
        if (mode == OUTLINE_NONE) {
            return;
        }
        float thickness = this.outlineThickness();
        float outerLeft = boxLeft - thickness;
        float outerRight = boxRight + thickness;
        boolean anchoredLeft = this.posX.getValue() == 0;
        if (anchoredLeft) {
            RenderUtil.drawRect(outerLeft, boxTop, boxLeft, boxBottom, color);
        } else {
            RenderUtil.drawRect(boxRight, boxTop, outerRight, boxBottom, color);
        }
        if (mode != OUTLINE_FULL) {
            return;
        }
        if (anchoredLeft) {
            RenderUtil.drawRect(boxRight, boxTop, outerRight, boxBottom, color);
        } else {
            RenderUtil.drawRect(outerLeft, boxTop, boxLeft, boxBottom, color);
        }
        float bandTop = this.posY.getValue() == 0 ? boxTop - thickness : boxBottom;
        float bandBottom = bandTop + thickness;
        if (firstRow) {
            RenderUtil.drawRect(outerLeft, bandTop, outerRight, bandBottom, color);
            return;
        }
        if (prevLeft < outerLeft) {
            RenderUtil.drawRect(prevLeft, bandTop, outerLeft, bandBottom, color);
        }
        if (prevRight > outerRight) {
            RenderUtil.drawRect(outerRight, bandTop, prevRight, bandBottom, color);
        }
    }

    /**
     *
     */
    private void fillCornerFan(float centerX, float centerY, float radius, int corner,
                               int red, int green, int blue, int alpha) {
        if (radius <= 0.0F) {
            return;
        }
        double startAngle;
        switch (corner) {
            case 0:
                startAngle = Math.PI;
                break;
            case 1:
                startAngle = Math.PI * 1.5;
                break;
            case 2:
                startAngle = 0.0;
                break;
            default:
                startAngle = Math.PI * 0.5;
                break;
        }
        GL11.glColor4f(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        int segments = 64;
        for (int p = 0; p <= segments; p++) {
            double angle = startAngle + (Math.PI / 2.0) * p / segments;
            GL11.glVertex2f(
                    centerX + (float) (Math.cos(angle) * radius),
                    centerY + (float) (Math.sin(angle) * radius)
            );
        }
        GL11.glEnd();
    }

    /**
     */
    private void drawConnectedBackground(List<float[]> rows) {
        if (rows.isEmpty() || this.background.getValue() <= 0) {
            return;
        }
        int color = new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB();
        float requestedRadius = (float) this.backgroundRounding.getValue();
        boolean anchoredLeft = this.posX.getValue() == 0;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = (color >> 24) & 0xFF;

        RenderUtil.enableRenderState();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_FLAT);

        for (int i = 0; i < rows.size(); i++) {
            float[] row = rows.get(i);
            float left = row[0];
            float right = row[1];
            float top = row[2];
            float bottom = row[3];
            float height = bottom - top;
            float width = right - left;
            boolean isLast = i + 1 == rows.size();

            float radius = Math.min(requestedRadius, height);
            if (!isLast) {
                float[] next = rows.get(i + 1);
                if (anchoredLeft) {
                    radius = Math.min(radius, Math.max(0.0F, right - next[1]));
                } else {
                    radius = Math.min(radius, Math.max(0.0F, next[0] - left));
                }
            } else {
                radius = Math.min(radius, width);
            }

            if (radius <= 0.0F) {
                RenderUtil.drawRect(left, top, right, bottom, color);
                continue;
            }

            if (bottom - radius > top) {
                RenderUtil.drawRect(left, top, right, bottom - radius, color);
            }
            float bandLeft = anchoredLeft ? left : left + radius;
            float bandRight = anchoredLeft ? right - radius : right;
            RenderUtil.drawRect(bandLeft, bottom - radius, bandRight, bottom, color);

            if (anchoredLeft) {
                fillCornerFan(right - radius, bottom - radius, radius, 2,
                        red, green, blue, alpha);
            } else {
                fillCornerFan(left + radius, bottom - radius, radius, 3,
                        red, green, blue, alpha);
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        RenderUtil.disableRenderState();
    }

    private long renderArrayList(long now) {
        float topPadding = this.topPadding();
        float spacing = this.arraylistLineHeight() + topPadding;
        ScaledResolution resolution = new ScaledResolution(mc);
        float screenWidth = resolution.getScaledWidth();
        float screenHeight = resolution.getScaledHeight();
        float edgeX = this.offsetX.getValue();
        float edgeY = this.offsetY.getValue();
        float elapsed = this.lastAnimationMs == 0L ? 0.0F : Math.min(now - this.lastAnimationMs, MAX_FRAME_MS);
        this.lastAnimationMs = now;
        for (Entry entry : this.entries.values()) {
            if (shows(entry.module)) {
                entry.animationTime = Math.min(entry.animationTime + elapsed / ANIMATION_MS, 10.0F);
            } else {
                entry.animationTime = Math.max(entry.animationTime - elapsed / ANIMATION_MS, 0.0F);
            }
        }
        float row = 0.0F;
        for (Entry entry : this.activeEntries) {
            if (entry.animationTime == 0.0F) {
                continue;
            }
            float width = entry.width;
            boolean leaving = !shows(entry.module) && entry.animationTime < 10.0F;
            entry.targetX = this.entryX(width, leaving, screenWidth, edgeX);
            entry.targetY = this.posY.getValue() == 0
                    ? edgeY + topPadding + row
                    : screenHeight - edgeY - spacing + topPadding - row;
            if (!leaving) {
                row += spacing;
            }
            if (!entry.positioned) {
                entry.positioned = true;
                entry.x = this.entryX(width, true, screenWidth, edgeX);
                entry.y = entry.targetY;
            }
            if (Math.abs(entry.x - entry.targetX) <= 0.5
                    && Math.abs(entry.y - entry.targetY) <= 0.5
                    && (entry.animationTime == 0.0F || entry.animationTime == 10.0F)) {
                entry.x = entry.targetX;
                entry.y = entry.targetY;
            } else {
                entry.x += LERP_RATE * elapsed * (entry.targetX - entry.x);
                entry.y += LERP_RATE * elapsed * (entry.targetY - entry.y);
            }
        }

        GlStateManager.disableDepth();
        long index = 0L;
        float horizontalPadding = this.horizontalPadding();
        float thickness = this.outlineThickness();
        boolean firstRow = true;
        float prevLeft = 0.0F;
        float prevRight = 0.0F;
        float lastBoxTop = 0.0F;
        float lastBoxBottom = 0.0F;
        int lastColor = 0;
        List<float[]> backgroundRows = new ArrayList<>();
        for (Entry entry : this.activeEntries) {
            if (entry.animationTime == 0.0F) {
                continue;
            }
            float x = (float) entry.x;
            float y = (float) entry.y;
            float boxLeft = x - horizontalPadding;
            float boxRight = x + entry.width + horizontalPadding;
            float boxTop = y - topPadding;
            float boxBottom = boxTop + spacing;
            backgroundRows.add(new float[]{boxLeft, boxRight, boxTop, boxBottom});
        }
        this.drawConnectedBackground(backgroundRows);

        int rowIdx = 0;
        for (Entry entry : this.activeEntries) {
            if (entry.animationTime == 0.0F) {
                continue;
            }
            float[] box = backgroundRows.get(rowIdx++);
            float boxLeft = box[0];
            float boxRight = box[1];
            float boxTop = box[2];
            float boxBottom = box[3];
            int color = this.getColor(now, index).getRGB();
            RenderUtil.enableRenderState();
            this.drawArrayListOutline(boxLeft, boxRight, boxTop, boxBottom, color, firstRow, prevLeft, prevRight);
            RenderUtil.disableRenderState();
            float x = (float) entry.x;
            float y = (float) entry.y;
            this.arraylistDraw(entry.displayText, x, this.arraylistTextY(y), color);
            firstRow = false;
            prevLeft = boxLeft - thickness;
            prevRight = boxRight + thickness;
            lastBoxTop = boxTop;
            lastBoxBottom = boxBottom;
            lastColor = color;
            index++;
        }
        if (!firstRow && this.arraylistOutline.getValue() == OUTLINE_FULL) {
            float capTop = this.posY.getValue() == 0 ? lastBoxBottom : lastBoxTop - thickness;
            RenderUtil.enableRenderState();
            RenderUtil.drawRect(prevLeft, capTop, prevRight, capTop + thickness, lastColor);
            RenderUtil.disableRenderState();
        }
        return index;
    }
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = AccessorGuiChat.getInputField((GuiChat) mc.currentScreen).getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            long l = System.currentTimeMillis();

            long offset = this.renderArrayList(l);
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Myau.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.pushMatrix();
                        GlStateManager.scale(this.renderScale(), this.renderScale(), 1.0F);
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        mc.fontRendererObj
                                .drawString(
                                        String.valueOf(movementPacketSize),
                                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.renderScale()
                                                - (float) mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                                        (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.renderScale(),
                                        this.getColor(l, offset).getRGB() & 16777215 | -1090519040,
                                        this.shadow.getValue()
                                );
                        GlStateManager.disableBlend();
                        GlStateManager.popMatrix();
                    }
                }
            }
            GlStateManager.enableDepth();
        }
    }
    private static final class Entry {
        final Module module;
        float animationTime;
        boolean positioned;
        double x;
        double y;
        double targetX;
        double targetY;
        float width;
        String displayText = "";
        Entry(Module module) {
            this.module = module;
        }
    }
}
