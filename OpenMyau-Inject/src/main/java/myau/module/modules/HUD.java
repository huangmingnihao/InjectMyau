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
import org.lwjgl.opengl.GL20;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

import myau.Myau;
import myau.access.AccessorGuiChat;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.Render2DEvent;
import myau.events.Render2DFrameEvent;
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
import myau.util.HudAnimation;
import myau.util.font.Fonts;
import myau.util.font.RavenFontRenderer;
import myau.util.render.GlassHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    private List<Entry> activeEntries = new ArrayList<>();
    private long lastAnimationMs = 0L;
    private long lastGlassFrameNs;
    private final GlassHudRenderer glassRenderer = new GlassHudRenderer();
    private final IntBuffer frameViewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer frameColor = BufferUtils.createFloatBuffer(16);

    private static final float ANIMATION_MS = 100.0F;

    private static final double LERP_RATE = 0.015;

    private static final float MAX_FRAME_MS = 200.0F;

    private static final float BASE_PADDING = 2.0F;
    private static final float BASE_OUTLINE = 1.0F;
    private static final int OUTLINE_NONE = 0;
    private static final int OUTLINE_FULL = 1;
    private static final int OUTLINE_SIDE = 2;
    private static final int COLOR_THEME = 6;
    private static final int STYLE_CLASSIC = 0;
    private static final int STYLE_GLOW = 1;
    public final ModeProperty colorMode = new ModeProperty(
            "color", 4, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123", "THEME"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 100,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100,
            () -> this.colorMode.getValue() != COLOR_THEME);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", 0xB53BD2, () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", 0xEDB5EB, () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 0, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 0, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 75);
    public final IntProperty backgroundRounding = new IntProperty("background-rounding", 2, 0, 16);
    public final ModeProperty arraylistOutline = new ModeProperty("arraylist-outline", OUTLINE_SIDE,
            new String[]{"NONE", "FULL", "SIDE"});
    // Reference-video style: blurred stepped plate with flowing violet highlights.
    public final ModeProperty arraylistStyle = new ModeProperty("arraylist-style", STYLE_GLOW,
            new String[]{"CLASSIC", "GLOW"});
    public final BooleanProperty videoPreset = new BooleanProperty("video-preset", false,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final BooleanProperty spacedNames = new BooleanProperty("spaced-names", true,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final BooleanProperty shine = new BooleanProperty("shine", true,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final FloatProperty shineSpeed = new FloatProperty("shine-speed", 1.0F, 0.25F, 3.0F,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW && this.shine.getValue());
    public final BooleanProperty glow = new BooleanProperty("glow", true,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final PercentProperty glowStrength = new PercentProperty("glow-strength", 55,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW && this.glow.getValue());
    public final BooleanProperty backgroundBlur = new BooleanProperty("background-blur", true,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final FloatProperty blurRadius = new FloatProperty("blur-radius", 6.0F, 1.0F, 16.0F,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW && this.backgroundBlur.getValue());
    public final FloatProperty glowRadius = new FloatProperty("glow-radius", 7.0F, 1.0F, 20.0F,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW && this.glow.getValue());
    public final IntProperty animationDuration = new IntProperty("animation-ms", 280, 0, 800,
            () -> this.arraylistStyle.getValue() == STYLE_GLOW);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final ModeProperty arraylistFont = new ModeProperty("arraylist-font", 1,
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
    @Override
    public void verifyValue(String name) {
        if (!"video-preset".equals(name) || !videoPreset.getValue()) return;
        arraylistStyle.setValue(STYLE_GLOW);
        colorMode.setValue(4);
        custom1.setValue(0xB53BD2);
        custom2.setValue(0xEDB5EB);
        colorSpeed.setValue(1.0F);
        colorSaturation.setValue(100);
        colorBrightness.setValue(100);
        posX.setValue(1);
        posY.setValue(0);
        offsetX.setValue(0);
        offsetY.setValue(0);
        scale.setValue(1.0F);
        background.setValue(75);
        backgroundRounding.setValue(2);
        arraylistOutline.setValue(OUTLINE_SIDE);
        arraylistFont.setValue(1);
        spacedNames.setValue(true);
        suffixes.setValue(true);
        lowerCase.setValue(false);
        removeSpaces.setValue(false);
        glow.setValue(true);
        glowStrength.setValue(55);
        glowRadius.setValue(7.0F);
        shine.setValue(true);
        shineSpeed.setValue(1.0F);
        shadow.setValue(true);
        backgroundBlur.setValue(true);
        blurRadius.setValue(6.0F);
        animationDuration.setValue(280);
        videoPreset.setValue(false); // Momentary preset action; subsequent edits remain user-controlled.
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
        if (this.arraylistStyle.getValue() == STYLE_GLOW) {
            return; // Animated HUDs sample module states every frame, independently of cached overlays.
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
     * Slinky 风格的圆角连接背景：相邻行通过四分之一圆平滑衔接，半透明背景互不重叠；
     * 整块背景的四个外角（首行两顶角、末行两底角）同样做圆角，呈完整圆角卡片。
     *
     * @param corner 0=左上(π→3π/2) 1=右上(3π/2→2π) 2=右下(0→π/2) 3=左下(π/2→π)
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

    /** 把 from 颜色按比例 t 向 to 颜色插值，保留 from 的 alpha。 */
    private static int mixColor(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int alpha = (from >>> 24) & 0xFF;
        int red = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int green = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** 自上而下循环扫过的高光带：行中心越靠近亮带中心返回值越接近 1，用于文字扫光。 */
    private float shineFactor(float rowCenterY, float panelTop, float panelBottom, long now) {
        float span = Math.max(1.0F, panelBottom - panelTop);
        float speed = Math.max(0.1F, this.shineSpeed.getValue());
        long period = (long) Math.max(1.0F, 2600.0F / speed);
        float progress = (float) (now % period) / period;
        float band = span * 0.32F;
        float bandCenter = panelTop - band + progress * (span + band * 2.0F);
        float distance = Math.abs(rowCenterY - bandCenter);
        if (distance >= band) {
            return 0.0F;
        }
        float x = 1.0F - distance / band;
        return x * x * (3.0F - 2.0F * x);
    }

    /**
     * 按 Slinky 规则填充整块背景形状；expand>0 时各边向外扩，用于叠加光晕层，
     * 因此光晕轮廓与背景完全同形（该圆的地方圆、该平的地方平）。
     * accent!=0 且 tintTop>0 时，颜色按行从顶部主题色向底部基础色渐变，做出渐变发光底板。
     */
    private void fillConnectedShape(List<float[]> rows, float expand, int color, int accent, float tintTop) {
        boolean anchoredLeft = this.posX.getValue() == 0;
        float requestedRadius = (float) this.backgroundRounding.getValue();
        int count = rows.size();
        for (int i = 0; i < count; i++) {
            float[] row = rows.get(i);
            int rowColor = color;
            if (accent != 0 && tintTop > 0.0F) {
                float tint = count <= 1 ? tintTop : tintTop * (1.0F - i / (float) (count - 1));
                rowColor = mixColor(color, (color & 0xFF000000) | (accent & 0x00FFFFFF), tint);
            }
            int red = (rowColor >> 16) & 0xFF;
            int green = (rowColor >> 8) & 0xFF;
            int blue = rowColor & 0xFF;
            int alpha = (rowColor >> 24) & 0xFF;
            float left = row[0] - expand;
            float right = row[1] + expand;
            float top = row[2] - expand;
            float bottom = row[3] + expand;
            float height = bottom - top;
            float width = right - left;
            float radius = Math.min(requestedRadius + expand, height);
            if (i + 1 < count) {
                float[] next = rows.get(i + 1);
                // 外扩量在相邻行差值中抵消，圆角半径因此与背景保持一致
                radius = Math.min(radius, anchoredLeft
                        ? Math.max(0.0F, row[1] - next[1])
                        : Math.max(0.0F, next[0] - row[0]));
            } else {
                radius = Math.min(radius, width);
            }
            if (radius <= 0.0F) {
                RenderUtil.drawRect(left, top, right, bottom, rowColor);
                continue;
            }
            // 中段全宽矩形（顶部直角贴边，底部让出衔接圆角）
            if (bottom - radius > top) {
                RenderUtil.drawRect(left, top, right, bottom - radius, rowColor);
            }
            // 底部条带：只让出衔接侧
            float bandLeft = anchoredLeft ? left : left + radius;
            float bandRight = anchoredLeft ? right - radius : right;
            RenderUtil.drawRect(bandLeft, bottom - radius, bandRight, bottom, rowColor);
            // 衔接圆角：左对齐在右下，右对齐在左下
            if (anchoredLeft) {
                fillCornerFan(right - radius, bottom - radius, radius, 2, red, green, blue, alpha);
            } else {
                fillCornerFan(left + radius, bottom - radius, radius, 3, red, green, blue, alpha);
            }
        }
    }

    /**
     * Slinky 风格的圆角连接背景：每行只在衔接侧（左对齐为右下、右对齐为左下）做一个圆角，
     * 相邻行通过四分之一圆平滑衔接，半透明背景互不重叠；贴屏幕边缘的角全部保持直角，避免漏空隙。
     */
    private void drawConnectedBackground(List<float[]> rows) {
        if (rows.isEmpty() || this.background.getValue() <= 0) {
            return;
        }
        int color = new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB();
        RenderUtil.enableRenderState();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_FLAT);
        this.fillConnectedShape(rows, 0.0F, color, 0, 0.0F);
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
        // 先收集所有可见行的 box，统一绘制圆角连接背景
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
    private long renderGlassArrayList(long now) {
        long nanoTime = System.nanoTime();
        float elapsed = lastGlassFrameNs == 0L ? 16.0F
                : Math.max(0.0F, Math.min(100.0F, (nanoTime - lastGlassFrameNs) / 1000000.0F));
        lastGlassFrameNs = nanoTime;
        RavenFontRenderer font = getArraylistFont();
        float duration = animationDuration.getValue();
        for (Module module : Myau.moduleManager.modules.values()) {
            if (!module.isHidden()) entries.computeIfAbsent(module, Entry::new);
        }
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : entries.values()) {
            entry.visibility = HudAnimation.visibility(entry.visibility, shows(entry.module), elapsed, duration);
            if (entry.visibility <= 0.0F) {
                entry.glassPositioned = false;
                entry.previousText = "";
                continue;
            }
            String rawName = entry.module.getName();
            if (spacedNames.getValue()) {
                rawName = "InvManager".equals(rawName) ? "Inventory Manager"
                        : rawName.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
            }
            String name = formatEntryText(rawName);
            String[] suffix = entry.module.getSuffix();
            String nextText = suffixes.getValue() && suffix.length > 0
                    ? name + " \u00a77" + formatEntryText(String.join(" ", suffix)) : name;
            if (!nextText.equals(entry.displayText)) {
                entry.previousText = entry.displayText;
                entry.previousWidth = font.getStringWidth(entry.previousText);
                entry.displayText = nextText;
                entry.textBlend = entry.previousText.isEmpty() ? 1.0F : 0.0F;
            }
            entry.width = font.getStringWidth(entry.displayText);
            entry.textBlend = HudAnimation.visibility(entry.textBlend, true, elapsed, Math.min(duration, 180.0F));
            visible.add(entry);
        }
        visible.sort(Comparator.comparingDouble(entry -> -entry.width));
        ScaledResolution sr = new ScaledResolution(mc);
        float padding = 3.0F * scale.getValue();
        float lineHeight = Math.max(1, font.getTextBottomOffset() - font.getTextTopOffset());
        float spacing = lineHeight + 4.0F * scale.getValue();
        boolean left = posX.getValue() == 0;
        float edge = left ? offsetX.getValue() : sr.getScaledWidth() - offsetX.getValue();
        float rowOffset = 0.0F;
        List<float[]> rows = new ArrayList<>();
        List<Entry> drawn = new ArrayList<>();
        for (Entry entry : visible) {
            float targetY = posY.getValue() == 0 ? offsetY.getValue() + rowOffset
                    : sr.getScaledHeight() - offsetY.getValue() - spacing - rowOffset;
            if (!entry.glassPositioned) {
                entry.glassPositioned = true;
                entry.glassY = targetY;
                entry.glassWidth = entry.width;
            }
            entry.glassY = HudAnimation.approach(entry.glassY, targetY, elapsed, duration);
            entry.glassWidth = HudAnimation.approach(entry.glassWidth, entry.width, elapsed, duration);
            float width = entry.glassWidth + padding * 2.0F + outlineSpace();
            float slide = (width + offsetX.getValue() + 2.0F) * (1.0F - entry.visibility);
            float boxLeft = left ? edge - slide : edge - width + slide;
            float boxRight = boxLeft + width;
            if (rows.size() < GlassHudRenderer.MAX_ROWS
                    && entry.glassY + spacing > 0 && entry.glassY < sr.getScaledHeight()) {
                rows.add(new float[]{boxLeft, boxRight, entry.glassY, entry.glassY + spacing, entry.visibility});
                drawn.add(entry);
            }
            rowOffset += spacing * entry.visibility;
        }
        if (rows.isEmpty()) return 0;
        float panelTop = Float.MAX_VALUE;
        float panelBottom = -Float.MAX_VALUE;
        for (float[] box : rows) {
            panelTop = Math.min(panelTop, box[2]);
            panelBottom = Math.max(panelBottom, box[3]);
        }
        glassRenderer.draw(rows, getColor(now, 0).getRGB(), getColor(now, Math.max(1, rows.size() - 1)).getRGB(),
                background.getValue() / 100.0F, backgroundRounding.getValue() * scale.getValue(),
                backgroundBlur.getValue() ? blurRadius.getValue() * scale.getValue() : 0.0F,
                glow.getValue() ? glowStrength.getValue() / 100.0F : 0.0F,
                glowRadius.getValue() * scale.getValue());
        for (int i = 0; i < drawn.size(); i++) {
            Entry entry = drawn.get(i);
            float[] box = rows.get(i);
            int color = getColor(now, i).getRGB();
            if (shine.getValue()) {
                float factor = shineFactor((box[2] + box[3]) * 0.5F, panelTop, panelBottom, now);
                color = mixColor(color, 0xFFF8DEF6, factor * 0.35F);
            }
            if (arraylistOutline.getValue() != OUTLINE_NONE) {
                float thickness = Math.max(0.5F, scale.getValue() * 0.65F);
                float sideX = left ? box[0] : box[1] - thickness;
                hudRect(sideX, box[2], sideX + thickness, box[3], alpha(color, entry.visibility * 0.8F));
                if (arraylistOutline.getValue() == OUTLINE_FULL) {
                    float otherX = left ? box[1] - thickness : box[0];
                    hudRect(otherX, box[2], otherX + thickness, box[3], alpha(color, entry.visibility * 0.8F));
                }
            }
            float textY = box[2] + (spacing - lineHeight) * 0.5F - font.getTextTopOffset();
            float inset = padding + outlineSpace();
            if (entry.textBlend < 0.999F && !entry.previousText.isEmpty()) {
                float oldX = HudAnimation.textX(left, box[0], box[1], inset, entry.previousWidth);
                drawGlassText(font, entry.previousText, oldX, textY - entry.textBlend * scale.getValue(),
                        color, entry.visibility * (1.0F - entry.textBlend));
            }
            float textX = HudAnimation.textX(left, box[0], box[1], inset, entry.width);
            drawGlassText(font, entry.displayText, textX, textY + (1.0F - entry.textBlend) * scale.getValue(),
                    color, entry.visibility * entry.textBlend);
        }
        return rows.size();
    }

    private void drawGlassText(RavenFontRenderer font, String text, float x, float y, int color, float opacity) {
        if (opacity * 255.0F < 4.0F) return; // Vanilla treats near-zero alpha as opaque.
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        font.drawString(text, x, y, alpha(color, opacity), shadow.getValue());
    }

    private static int alpha(int color, float opacity) {
        return (Math.max(0, Math.min(255, Math.round(opacity * 255.0F))) << 24) | (color & 0xFFFFFF);
    }

    private static void hudRect(float left, float top, float right, float bottom, int color) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, bottom, 0).color(r, g, b, a).endVertex();
        buffer.pos(right, bottom, 0).color(r, g, b, a).endVertex();
        buffer.pos(right, top, 0).color(r, g, b, a).endVertex();
        buffer.pos(left, top, 0).color(r, g, b, a).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
    }

    @EventTarget(Priority.LOW)
    public void onRenderFrame(Render2DFrameEvent event) {
        if (arraylistStyle.getValue() != STYLE_GLOW || !isEnabled() || mc.thePlayer == null
                || mc.theWorld == null || mc.gameSettings.showDebugInfo || mc.gameSettings.hideGUI
                || mc.skipRenderWorld || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))) {
            lastGlassFrameNs = 0L;
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        boolean fbo = OpenGlHelper.isFramebufferEnabled();
        int boundFbo = fbo ? RenderUtil.getBoundFramebuffer() : 0;
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean texturing = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int srcRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        int textureEnvironment = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE);
        frameViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, frameViewport);
        frameColor.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, frameColor);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            if (fbo) mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
            GL20.glUseProgram(0);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, 1000, 3000);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0, 0, -2000);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            renderGlassArrayList(System.currentTimeMillis());
        } finally {
            GL20.glUseProgram(program);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrixMode);
            if (fbo) RenderUtil.bindFramebuffer(boundFbo);
            GlStateManager.viewport(frameViewport.get(0), frameViewport.get(1), frameViewport.get(2), frameViewport.get(3));
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(depthMask);
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            if (alphaTest) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.bindTexture(texture);
            if (texturing) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, textureEnvironment);
            GlStateManager.resetColor();
            GlStateManager.color(frameColor.get(0), frameColor.get(1), frameColor.get(2), frameColor.get(3));
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    @Override
    public void onDisabled() {
        lastGlassFrameNs = 0L;
        lastAnimationMs = 0L;
        entries.clear();
        activeEntries.clear();
        glassRenderer.release();
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

            long offset = this.arraylistStyle.getValue() == STYLE_CLASSIC ? this.renderArrayList(l) : 0L;
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
        float visibility;
        float glassY;
        float glassWidth;
        boolean glassPositioned;
        String previousText = "";
        float previousWidth;
        float textBlend = 1.0F;
        Entry(Module module) {
            this.module = module;
        }
    }
}
