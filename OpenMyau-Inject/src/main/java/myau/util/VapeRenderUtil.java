package myau.util;

import myau.access.AccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/** Scoped GL restoration, including Minecraft's cached state after item/font rendering. */
public final class VapeRenderUtil {
    private VapeRenderUtil() { }

    public static State save() { return new State(); }

    public static void overlay(Runnable draw) {
        Minecraft mc = Minecraft.getMinecraft();
        try (State ignored = save()) {
            if (OpenGlHelper.isFramebufferEnabled()) mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
            GL20.glUseProgram(0);
            ScaledResolution sr = new ScaledResolution(mc);
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
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0f);
            draw.run();
        }
    }

    public static final class State implements AutoCloseable {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture;
        private final int textureId;
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int framebuffer = OpenGlHelper.isFramebufferEnabled() ? RenderUtil.getBoundFramebuffer() : 0;
        private final boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        private final boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        private final boolean[] lights = new boolean[8];
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final boolean normalize = GL11.glIsEnabled(GL11.GL_NORMALIZE);
        private final boolean rescale = GL11.glIsEnabled(32826);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        private final float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        private final FloatBuffer color = BufferUtils.createFloatBuffer(4);

        private State() {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            textureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            for (int i = 0; i < lights.length; i++) lights[i] = GL11.glIsEnabled(GL11.GL_LIGHT0 + i);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
        }

        @Override
        public void close() {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GlStateManager.matrixMode(matrixMode);
            GL20.glUseProgram(program);
            if (OpenGlHelper.isFramebufferEnabled()) RenderUtil.bindFramebuffer(framebuffer);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(textureId);
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
            for (int i = 0; i < lights.length; i++) {
                if (lights[i]) GlStateManager.enableLight(i); else GlStateManager.disableLight(i);
            }
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (normalize) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            GlStateManager.depthMask(depthMask);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    public static void face(BlockPos pos, EnumFacing face, int color, boolean outline, boolean shade) {
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        double x = pos.getX() - AccessorRenderManager.getRenderPosX(rm);
        double y = pos.getY() - AccessorRenderManager.getRenderPosY(rm);
        double z = pos.getZ() - AccessorRenderManager.getRenderPosZ(rm);
        try (State ignored = save()) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderUtil.setColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            switch (face.getAxis()) {
                case X:
                    x += face == EnumFacing.EAST ? 1.002 : -0.002;
                    GL11.glVertex3d(x,y,z); GL11.glVertex3d(x,y+1,z);
                    GL11.glVertex3d(x,y+1,z+1); GL11.glVertex3d(x,y,z+1);
                    break;
                case Y:
                    y += face == EnumFacing.UP ? 1.002 : -0.002;
                    GL11.glVertex3d(x,y,z); GL11.glVertex3d(x+1,y,z);
                    GL11.glVertex3d(x+1,y,z+1); GL11.glVertex3d(x,y,z+1);
                    break;
                case Z:
                    z += face == EnumFacing.SOUTH ? 1.002 : -0.002;
                    GL11.glVertex3d(x,y,z); GL11.glVertex3d(x+1,y,z);
                    GL11.glVertex3d(x+1,y+1,z); GL11.glVertex3d(x,y+1,z);
            }
            GL11.glEnd();
        }
    }
}
