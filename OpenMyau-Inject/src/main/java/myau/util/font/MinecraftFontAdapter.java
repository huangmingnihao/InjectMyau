package myau.util.font;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public final class MinecraftFontAdapter implements RavenFontRenderer {
    private final FontRenderer fontRenderer;
    private final float scale;
    public MinecraftFontAdapter(FontRenderer fontRenderer, float scale) {
        this.fontRenderer = fontRenderer;
        this.scale = Math.max(0.5F, Math.min(2.0F, scale));
    }
    @Override
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        if (this.scale == 1.0F) {
            return this.fontRenderer.drawString(text, x, y, color, shadow);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(this.scale, this.scale, 1.0F);
        int width = this.fontRenderer.drawString(text, 0.0F, 0.0F, color, shadow);
        GlStateManager.popMatrix();
        return Math.round(width * this.scale);
    }
    @Override
    public int getStringWidth(String text) {
        return Math.round(this.fontRenderer.getStringWidth(text) * this.scale);
    }
    @Override
    public int getFontHeight() {
        return Math.round(this.fontRenderer.FONT_HEIGHT * this.scale);
    }
    @Override
    public int getLineHeight() {
        return Math.round(this.fontRenderer.FONT_HEIGHT * this.scale);
    }
    @Override
    public int getTextTopOffset() {
        return 0;
    }
    @Override
    public int getTextBottomOffset() {
        return Math.max(1, Math.round((this.fontRenderer.FONT_HEIGHT - 1.0F) * this.scale));
    }
    public float getScale() {
        return this.scale;
    }
}
