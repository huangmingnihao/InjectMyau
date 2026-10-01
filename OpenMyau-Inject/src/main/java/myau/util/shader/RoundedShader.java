package myau.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class RoundedShader {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String FRAGMENT =
            "#version 120\n"
                    + "\n"
                    + "uniform vec2 location, rectSize;\n"
                    + "uniform vec4 color;\n"
                    + "uniform float radius;\n"
                    + "\n"
                    + "float roundSDF(vec2 p, vec2 b, float r) {\n"
                    + "    return length(max(abs(p) - b, 0.0)) - r;\n"
                    + "}\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 rectHalf = rectSize * .5;\n"
                    + "    float smoothedAlpha = (1.0 - smoothstep(0.0, 1.0,"
                    + " roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1., radius))) * color.a;\n"
                    + "    gl_FragColor = vec4(color.rgb, smoothedAlpha);\n"
                    + "}\n";
    private static final ShaderProgram PROGRAM = new ShaderProgram(FRAGMENT);
    private RoundedShader() {
    }
    public static boolean isReady() {
        return PROGRAM.isReady();
    }
    public static void drawRound(float x, float y, float width, float height, float radius, int color) {
        if (!PROGRAM.isReady()) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaleFactor = resolution.getScaleFactor();
        GlStateManager.enableBlend();

        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        PROGRAM.init();
        PROGRAM.setUniformf("location", x * scaleFactor,
                mc.displayHeight - height * scaleFactor - y * scaleFactor);
        PROGRAM.setUniformf("rectSize", width * scaleFactor, height * scaleFactor);
        PROGRAM.setUniformf("radius", radius * scaleFactor);
        PROGRAM.setUniformf("color",
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                (color >> 24 & 0xFF) / 255.0F);
        drawQuad(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F);
        PROGRAM.unload();

        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
    }
    private static void drawQuad(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
    }
}
