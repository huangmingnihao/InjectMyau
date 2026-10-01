package myau.ui.clickgui.components;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import myau.property.Property;
import myau.property.properties.ColorProperty;
import myau.ui.clickgui.GuiRender;
import net.minecraft.client.Minecraft;

public class ColorComponent extends Component {
    public static final float HEIGHT = 16.0F;
    public static final float EXPANDED_HEIGHT = 70.0F;
    private static final float HUE_HEIGHT = 8.0F;
    private static final float SB_HEIGHT = 6.0F;
    private static final int TRACK_COLOR = -12302777;
    private static final int PREVIEW_OUTLINE = -1;

    private final ColorProperty property;
    private final ModuleComponent module;
    private float offset;
    private boolean expanded;
    private boolean draggingHue;
    private boolean draggingSat;
    private boolean draggingBri;

    public ColorComponent(ColorProperty property, ModuleComponent module, float offset) {
        this.property = property;
        this.module = module;
        this.offset = offset;
    }

    public static boolean handles(Property<?> property) {
        return property instanceof ColorProperty;
    }

    private float[] hsb() {
        return Color.RGBtoHSB((this.property.getValue() >> 16) & 0xFF,
                (this.property.getValue() >> 8) & 0xFF,
                this.property.getValue() & 0xFF, null);
    }

    private void applyHsb(float h, float s, float b) {
        this.property.setValue(Color.getHSBColor(h, s, b).getRGB() & 0xFFFFFF);
    }

    @Override
    public void render() {
        float left = this.module.category.getX() + 4;
        float right = left + this.module.category.getWidth() - 8;
        float top = this.module.category.getY() + this.offset + 3;
        float bottom = top + 12;

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        String hex = String.format("#%06X", this.property.getValue() & 0xFFFFFF);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
                this.property.getName() + ": " + hex,
                left * 2, top * 2, -1);
        GL11.glPopMatrix();

        float previewLeft = right - 14;
        float previewRight = right;
        int color = this.property.getValue() | 0xFF000000;
        GuiRender.drawRoundedRect(previewLeft, top, previewRight, bottom, 3, color);
        GuiRender.drawOutline(previewLeft, top, previewRight, bottom, 1.0F, PREVIEW_OUTLINE);

        if (this.expanded) {
            float hueTop = bottom + 3;
            float hueBottom = hueTop + HUE_HEIGHT;
            drawHueBar(left, hueTop, right, hueBottom, hsb()[0]);

            float satTop = hueBottom + 3;
            float satBottom = satTop + SB_HEIGHT;
            float briTop = satBottom + 3;
            float briBottom = briTop + SB_HEIGHT;
            drawSatBar(left, satTop, right, satBottom, hsb()[1], hsb());
            drawBriBar(left, briTop, right, briBottom, hsb()[2], hsb());
        }
    }

    private void drawHueBar(float left, float top, float right, float bottom, float current) {
        GuiRender.drawRoundedRect(left, top, right, bottom, 3, TRACK_COLOR);
        float steps = 64.0F;
        for (int i = 0; i < steps; i++) {
            float x1 = left + (right - left) * (i / steps);
            float x2 = left + (right - left) * ((i + 1) / steps);
            int c = Color.getHSBColor(i / steps, 1.0F, 1.0F).getRGB() | 0xFF000000;
            GuiRender.drawRoundedRect(x1, top, x2, bottom, 0, c);
        }
        float knob = left + (right - left) * current;
        GuiRender.drawRoundedRect(knob - 1.5F, top - 1, knob + 1.5F, bottom + 1, 1.5F, -1);
    }

    private void drawSatBar(float left, float top, float right, float bottom, float current, float[] hsb) {
        int sat0 = Color.getHSBColor(hsb[0], 0.0F, hsb[2]).getRGB() | 0xFF000000;
        int sat1 = Color.getHSBColor(hsb[0], 1.0F, hsb[2]).getRGB() | 0xFF000000;
        GuiRender.drawRoundedGradientRect(left, top, right, bottom, 3, sat0, sat1);
        float knob = left + (right - left) * current;
        GuiRender.drawRoundedRect(knob - 1.5F, top - 1, knob + 1.5F, bottom + 1, 1.5F, -1);
    }

    private void drawBriBar(float left, float top, float right, float bottom, float current, float[] hsb) {
        int bri0 = Color.getHSBColor(hsb[0], hsb[1], 0.0F).getRGB() | 0xFF000000;
        int bri1 = Color.getHSBColor(hsb[0], hsb[1], 1.0F).getRGB() | 0xFF000000;
        GuiRender.drawRoundedGradientRect(left, top, right, bottom, 3, bri0, bri1);
        float knob = left + (right - left) * current;
        GuiRender.drawRoundedRect(knob - 1.5F, top - 1, knob + 1.5F, bottom + 1, 1.5F, -1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        if (this.draggingHue || this.draggingSat || this.draggingBri) {
            float left = this.module.category.getX() + 4;
            float right = left + this.module.category.getWidth() - 8;
            float span = right - left;
            float clamped = Math.max(0.0F, Math.min(1.0F, (mouseX - left) / span));
            float[] hsb = hsb();
            if (this.draggingHue) {
                applyHsb(clamped, hsb[1], hsb[2]);
            } else if (this.draggingSat) {
                applyHsb(hsb[0], clamped, hsb[2]);
            } else if (this.draggingBri) {
                applyHsb(hsb[0], hsb[1], clamped);
            }
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!this.module.isOpened() || !this.module.isVisible(this)) {
            return false;
        }
        float left = this.module.category.getX() + 4;
        float right = left + this.module.category.getWidth() - 8;
        float top = this.module.category.getY() + this.offset + 3;
        float bottom = top + 12;

        if (button == 0 && mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom) {
            this.expanded = !this.expanded;
            return true;
        }
        if (this.expanded && button == 0) {
            float hueTop = bottom + 3;
            float hueBottom = hueTop + HUE_HEIGHT;
            float satTop = hueBottom + 3;
            float satBottom = satTop + SB_HEIGHT;
            float briTop = satBottom + 3;
            float briBottom = briTop + SB_HEIGHT;

            if (mouseX >= left && mouseX <= right) {
                if (mouseY >= hueTop && mouseY <= hueBottom) {
                    this.draggingHue = true;
                    return true;
                }
                if (mouseY >= satTop && mouseY <= satBottom) {
                    this.draggingSat = true;
                    return true;
                }
                if (mouseY >= briTop && mouseY <= briBottom) {
                    this.draggingBri = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        this.draggingHue = false;
        this.draggingSat = false;
        this.draggingBri = false;
    }

    @Override
    public void onGuiClosed() {
        this.draggingHue = false;
        this.draggingSat = false;
        this.draggingBri = false;
        this.expanded = false;
    }

    @Override
    public void updateHeight(float offset) {
        this.offset = offset;
    }

    @Override
    public float getOffset() {
        return this.offset;
    }

    @Override
    public float getHeightF() {
        return this.expanded ? EXPANDED_HEIGHT : HEIGHT;
    }

    @Override
    public boolean isBaseVisible() {
        return this.property.isVisible();
    }
}
