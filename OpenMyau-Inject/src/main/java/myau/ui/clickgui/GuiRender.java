package myau.ui.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public final class GuiRender {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static double renderScale = 1.0;
    private GuiRender() {
    }
    public static void setRenderScale(double scale) {
        renderScale = scale;
    }
    public static double getRenderScale() {
        return renderScale;
    }

    public static void drawRoundedRect(float x, float y, float x2, float y2, float radius, int color) {
        if (x2 <= x) {
            return;
        }
        float width = x2 - x;
        if (width < 3.0F) {
            radius = Math.min(radius, width / 2.0F);
        }
        x *= 2.0F;
        y *= 2.0F;
        x2 *= 2.0F;
        y2 *= 2.0F;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_POLYGON);
        colour(color);
        arc(x + radius, y + radius, radius, 0, 90, -1.0, -1.0, 1);
        arc(x + radius, y2 - radius, radius, 90, 180, -1.0, -1.0, 1);
        if (x2 - x >= 4.5F) {
            arc(x2 - radius, y2 - radius, radius, 0, 90, 1.0, 1.0, 1);
            arc(x2 - radius, y + radius, radius, 90, 180, 1.0, 1.0, 1);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
    public static void drawRoundedGradientRect(float x, float y, float x2, float y2,
                                               float radius, int left, int right) {
        if (x2 <= x) {
            return;
        }
        float width = x2 - x;
        if (width < 3.0F) {
            radius = Math.min(radius, width / 2.0F);
        }
        x *= 2.0F;
        y *= 2.0F;
        x2 *= 2.0F;
        y2 *= 2.0F;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_POLYGON);
        colour(left);
        arc(x + radius, y + radius, radius, 0, 90, -1.0, -1.0, 1);
        arc(x + radius, y2 - radius, radius, 90, 180, -1.0, -1.0, 1);
        if (x2 - x >= 4.5F) {
            colour(right);
            arc(x2 - radius, y2 - radius, radius, 0, 90, 1.0, 1.0, 1);
            arc(x2 - radius, y + radius, radius, 90, 180, 1.0, 1.0, 1);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
    public static void drawRoundedGradientOutlinedRect(float x, float y, float x2, float y2,
                                                       float radius, int fill, int leftEdge, int rightEdge) {
        x *= 2.0F;
        y *= 2.0F;
        x2 *= 2.0F;
        y2 *= 2.0F;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_POLYGON);
        colour(fill);
        arc(x + radius, y + radius, radius, 0, 90, -1.0, -1.0, 1);
        arc(x + radius, y2 - radius, radius, 90, 180, -1.0, -1.0, 1);
        arc(x2 - radius, y2 - radius, radius, 0, 90, 1.0, 1.0, 1);
        arc(x2 - radius, y + radius, radius, 90, 180, 1.0, 1.0, 1);
        GL11.glEnd();
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(2.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        if (leftEdge != 0) {
            colour(leftEdge);
        }
        arc(x + radius, y + radius, radius, 0, 90, -1.0, -1.0, 1);
        arc(x + radius, y2 - radius, radius, 90, 180, -1.0, -1.0, 1);
        if (rightEdge != 0) {
            colour(rightEdge);
        }
        arc(x2 - radius, y2 - radius, radius, 0, 90, 1.0, 1.0, 1);
        arc(x2 - radius, y + radius, radius, 90, 180, 1.0, 1.0, 1);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glLineWidth(1.0F);
        GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
    private static void arc(double centreX, double centreY, double radius,
                            int fromDegrees, int toDegrees, double signX, double signY, int step) {
        for (int degrees = fromDegrees; degrees <= toDegrees; degrees += step) {
            double radians = degrees * 0.017453292F;
            GL11.glVertex2d(centreX + Math.sin(radians) * radius * signX,
                    centreY + Math.cos(radians) * radius * signY);
        }
    }
    public static void drawOutline(float x, float y, float x2, float y2, float lineWidth, int color) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glPushMatrix();
        colour(color);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y2);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y);
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawHorizontalGradientRect(float left, float top, float right, float bottom,
                                                  int leftColor, int rightColor) {
        gradient(left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }
    public static void drawVerticalGradientRect(float left, float top, float right, float bottom,
                                                int topColor, int bottomColor) {
        gradient(left, top, right, bottom, topColor, bottomColor, topColor, bottomColor);
    }
    private static void gradient(float left, float top, float right, float bottom,
                                 int topLeft, int bottomLeft, int topRight, int bottomRight) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(renderer, left, bottom, bottomLeft);
        vertex(renderer, right, bottom, bottomRight);
        vertex(renderer, right, top, topRight);
        vertex(renderer, left, top, topLeft);
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
    private static void vertex(WorldRenderer renderer, float x, float y, int color) {
        renderer.pos(x, y, 0.0)
                .color((color >> 16 & 0xFF) / 255.0F,
                        (color >> 8 & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F,
                        (color >> 24 & 0xFF) / 255.0F)
                .endVertex();
    }

    private static final int SCISSOR_DEPTH = 4;
    private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);
    private static final int[][] scissorStack = new int[SCISSOR_DEPTH][5];
    private static int scissorDepth;
    public static void scissorPush(double x, double y, double width, double height) {
        double guiScale = renderScale;
        x *= guiScale;
        y *= guiScale;
        width *= guiScale;
        height *= guiScale;

        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        double screenHeight = resolution.getScaledHeight();
        int left = (int) Math.floor(x * scale);
        int right = (int) Math.ceil((x + width) * scale);
        int boxWidth = Math.max(0, right - left);
        int bottom = (int) Math.floor((screenHeight - (y + height)) * scale);
        int top = (int) Math.ceil((screenHeight - y) * scale);
        int boxHeight = Math.max(0, top - bottom);
        if (scissorDepth >= SCISSOR_DEPTH) {
            return;
        }
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] saved = scissorStack[scissorDepth++];
        if (wasEnabled) {
            SCISSOR_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
            saved[0] = 1;
            saved[1] = SCISSOR_BOX.get(0);
            saved[2] = SCISSOR_BOX.get(1);
            saved[3] = SCISSOR_BOX.get(2);
            saved[4] = SCISSOR_BOX.get(3);
            int intersectX = Math.max(saved[1], left);
            int intersectY = Math.max(saved[2], bottom);
            int intersectW = Math.max(0, Math.min(saved[1] + saved[3], left + boxWidth) - intersectX);
            int intersectH = Math.max(0, Math.min(saved[2] + saved[4], bottom + boxHeight) - intersectY);
            GL11.glScissor(intersectX, intersectY, intersectW, intersectH);
        } else {
            saved[0] = 0;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left, bottom, boxWidth, boxHeight);
        }
    }
    public static void scissorPop() {
        if (scissorDepth <= 0) {
            return;
        }
        int[] saved = scissorStack[--scissorDepth];
        if (saved[0] == 1) {
            GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    public static void resetScissors() {
        scissorDepth = 0;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    public static int setAlpha(int rgb, double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            alpha = 0.5;
        }
        return (int) (alpha * 255.0) << 24 | rgb & 0xFFFFFF;
    }
    private static void colour(int argb) {
        GL11.glColor4f((argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                (argb >> 24 & 0xFF) / 255.0F);
    }
}
