package myau.util.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import myau.util.RenderUtil;
import myau.util.shader.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * Blurs only the HUD's screen region, then composites its entire silhouette once.
 * The snapshot is taken before drawing the HUD, so neither letters nor old HUD frames feed back.
 */
public final class GlassHudRenderer {
    public static final int MAX_ROWS = 64;
    private static final String BLUR_FRAGMENT =
            "#version 120\n"
            + "uniform sampler2D scene;\n"
            + "uniform vec2 uvMax, halfTexel, direction;\n"
            + "vec3 sampleAt(vec2 p) { return texture2D(scene, clamp(p, halfTexel, uvMax-halfTexel)).rgb; }\n"
            + "void main() {\n"
            + " vec2 uv=gl_TexCoord[0].xy*uvMax;\n"
            + " vec3 c=sampleAt(uv)*0.2270270270;\n"
            + " c+=(sampleAt(uv+direction*1.3846153846)+sampleAt(uv-direction*1.3846153846))*0.3162162162;\n"
            + " c+=(sampleAt(uv+direction*3.2307692308)+sampleAt(uv-direction*3.2307692308))*0.0702702703;\n"
            + " gl_FragColor=vec4(c,1.0);\n"
            + "}\n";
    private static final String COMPOSITE_FRAGMENT =
            "#version 120\n"
            + "uniform sampler2D scene;\n"
            + "uniform int rowCount, hasScene;\n"
            + "uniform vec4 rows[64];\n"
            + "uniform float opacity[64];\n"
            + "uniform vec2 guiSize, pixelScale, viewportOrigin, captureOrigin, captureSize, sceneUvMax;\n"
            + "uniform vec2 panelY, sceneHalfTexel;\n"
            + "uniform vec3 topColor, bottomColor;\n"
            + "uniform float rounding, backgroundOpacity, glowStrength, glowRadius;\n"
            + "float distanceToBox(vec2 p, vec4 b, float r) {\n"
            + " vec2 h=max((b.zw-b.xy)*0.5,vec2(0.001));\n"
            + " r=min(r,min(h.x,h.y));\n"
            + " vec2 q=abs(p-(b.xy+b.zw)*0.5)-h+r;\n"
            + " return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;\n"
            + "}\n"
            + "void main() {\n"
            + " vec2 p=(gl_FragCoord.xy-viewportOrigin)/pixelScale; p.y=guiSize.y-p.y;\n"
            + " float aa=0.65/min(pixelScale.x,pixelScale.y);\n"
            + " float mask=0.0, halo=0.0;\n"
            + " float sigma=max(glowRadius*0.6,0.1);\n"
            + " for(int i=0;i<64;i++) {\n"
            + "  if(i>=rowCount) break;\n"
            + "  float d=distanceToBox(p,rows[i],rounding);\n"
            + "  mask=max(mask,(1.0-smoothstep(-aa,aa,d))*opacity[i]);\n"
            + "  halo=max(halo,exp(-pow(max(d,0.0)/sigma,2.0)*0.5)*opacity[i]);\n"
            + "  if(i+1<rowCount) {\n"
            + "   vec4 a=rows[i], b=rows[i+1];\n"
            + "   float l=max(a.x,b.x), r=min(a.z,b.z);\n"
            + "   if(b.y<=a.w+0.25 && r>l) {\n"
            + "    float y=(a.w+b.y)*0.5;\n"
            + "    float h=min(min(a.w-a.y,b.w-b.y)*0.5,rounding+1.0);\n"
            + "    float bridge=distanceToBox(p,vec4(l,y-h,r,y+h),0.0);\n"
            + "    float o=min(opacity[i],opacity[i+1]);\n"
            + "    mask=max(mask,(1.0-smoothstep(-aa,aa,bridge))*o);\n"
            + "    halo=max(halo,exp(-pow(max(bridge,0.0)/sigma,2.0)*0.5)*o);\n"
            + "   }\n"
            + "  }\n"
            + " }\n"
            + " float t=clamp((p.y-panelY.x)/max(panelY.y-panelY.x,1.0),0.0,1.0);\n"
            + " vec3 accent=mix(topColor,bottomColor,t);\n"
            + " vec3 fill=vec3(0.018,0.020,0.027)+accent*0.055;\n"
            + " vec3 body=fill;\n"
            + " float bodyAlpha=mask*backgroundOpacity;\n"
            + " if(hasScene==1) {\n"
            + "  vec2 uv=clamp((gl_FragCoord.xy-captureOrigin)/captureSize,0.0,1.0)*sceneUvMax;\n"
            + "  uv=clamp(uv,sceneHalfTexel,sceneUvMax-sceneHalfTexel);\n"
            + "  body=mix(texture2D(scene,uv).rgb,fill,backgroundOpacity);\n"
            + "  bodyAlpha=mask;\n"
            + " }\n"
            + " float haloAlpha=halo*(1.0-mask)*glowStrength*0.52;\n"
            + " vec3 haloColor=accent*0.30;\n"
            + " float a=bodyAlpha+haloAlpha*(1.0-bodyAlpha);\n"
            + " vec3 c=(body*bodyAlpha+haloColor*haloAlpha*(1.0-bodyAlpha))/max(a,0.0001);\n"
            + " gl_FragColor=vec4(c,a);\n"
            + "}\n";

    private final ShaderProgram blur = new ShaderProgram(BLUR_FRAGMENT);
    private final ShaderProgram composite = new ShaderProgram(COMPOSITE_FRAGMENT);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private int sceneTexture, pingTexture, resultTexture, pingFbo, resultFbo;
    private int capacityWidth, capacityHeight, viewportWidth, viewportHeight;
    private boolean failed;
    private boolean reported;

    public void draw(List<float[]> input, int topColor, int bottomColor, float backgroundOpacity,
                     float rounding, float blurRadius, float glowStrength, float glowRadius) {
        if (input.isEmpty()) return;
        List<float[]> rows = new ArrayList<>(input);
        rows.sort(Comparator.comparingDouble(row -> row[2]));
        if (rows.size() > MAX_ROWS) rows = rows.subList(0, MAX_ROWS);
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        float guiWidth = (float) sr.getScaledWidth_double();
        float guiHeight = (float) sr.getScaledHeight_double();
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int vx = viewport.get(0), vy = viewport.get(1), vw = viewport.get(2), vh = viewport.get(3);
        if (vw <= 0 || vh <= 0) return;
        float sx = vw / guiWidth, sy = vh / guiHeight;
        float left = guiWidth, top = guiHeight, right = 0, bottom = 0;
        for (float[] row : rows) {
            left = Math.min(left, row[0]); right = Math.max(right, row[1]);
            top = Math.min(top, row[2]); bottom = Math.max(bottom, row[3]);
        }
        float margin = Math.max(blurRadius * 2.0F, glowRadius * 2.5F) + 2;
        float x0 = Math.max(0, left - margin), x1 = Math.min(guiWidth, right + margin);
        float y0 = Math.max(0, top - margin), y1 = Math.min(guiHeight, bottom + margin);
        if (x1 <= x0 || y1 <= y0) return;
        int cx = vx + Math.max(0, (int) Math.floor(x0 * sx));
        int cy = vy + Math.max(0, vh - (int) Math.ceil(y1 * sy));
        int cw = Math.min(vx + vw - cx, (int) Math.ceil(x1 * sx) + vx - cx);
        int ch = Math.min(vy + vh - cy, (int) Math.ceil((guiHeight - y0) * sy) + vy - cy);
        State state = new State();
        try {
            state.prepare();
            if (failed || !composite.isReady()) {
                fallback(rows, backgroundOpacity);
                return;
            }
            boolean captured = blurRadius > 0.0F && OpenGlHelper.isFramebufferEnabled() && blur.isReady();
            int halfWidth = Math.max(1, (cw + 1) / 2), halfHeight = Math.max(1, (ch + 1) / 2);
            if (captured) {
                ensureBuffers(cw, ch, vw, vh);
                RenderUtil.bindFramebuffer(state.framebuffer);
                GlStateManager.bindTexture(sceneTexture);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, cx, cy, cw, ch);
                GlStateManager.disableBlend();
                blurPass(pingFbo, sceneTexture, halfWidth, halfHeight,
                        cw / (float) capacityWidth, ch / (float) capacityHeight,
                        capacityWidth, capacityHeight, blurRadius * sx / (3 * capacityWidth), 0,
                        guiWidth, guiHeight);
                blurPass(resultFbo, pingTexture, halfWidth, halfHeight,
                        halfWidth / (float) (capacityWidth / 2), halfHeight / (float) (capacityHeight / 2),
                        capacityWidth / 2, capacityHeight / 2, 0, blurRadius * sy / (3 * (capacityHeight / 2)),
                        guiWidth, guiHeight);
            }
            RenderUtil.bindFramebuffer(state.framebuffer);
            GlStateManager.viewport(vx, vy, vw, vh);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            composite.init();
            composite.setUniformi("scene", 0);
            composite.setUniformi("hasScene", captured ? 1 : 0);
            composite.setUniformi("rowCount", rows.size());
            composite.setUniformf("guiSize", guiWidth, guiHeight);
            composite.setUniformf("pixelScale", sx, sy);
            composite.setUniformf("viewportOrigin", vx, vy);
            composite.setUniformf("captureOrigin", cx, cy);
            composite.setUniformf("captureSize", cw, ch);
            composite.setUniformf("sceneUvMax", captured ? halfWidth / (float) (capacityWidth / 2) : 1,
                    captured ? halfHeight / (float) (capacityHeight / 2) : 1);
            composite.setUniformf("sceneHalfTexel", captured ? 1.0F / capacityWidth : 0,
                    captured ? 1.0F / capacityHeight : 0);
            composite.setUniformf("panelY", top, bottom);
            color(composite, "topColor", topColor);
            color(composite, "bottomColor", bottomColor);
            composite.setUniformf("rounding", rounding);
            composite.setUniformf("backgroundOpacity", backgroundOpacity);
            composite.setUniformf("glowStrength", glowStrength);
            composite.setUniformf("glowRadius", glowRadius);
            for (int i = 0; i < rows.size(); i++) {
                float[] row = rows.get(i);
                composite.setUniformf("rows[" + i + "]", row[0], row[2], row[1], row[3]);
                composite.setUniformf("opacity[" + i + "]", row[4]);
            }
            GlStateManager.bindTexture(captured ? resultTexture : 0);
            quad(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            if (!reported) {
                reported = true;
                System.err.println("[Myau] HUD glass rendering failed; using an unblurred background.");
                error.printStackTrace();
            }
            RenderUtil.bindFramebuffer(state.framebuffer);
            GlStateManager.viewport(vx, vy, vw, vh);
            fallback(rows, backgroundOpacity);
        } finally {
            state.restore();
        }
    }

    private void blurPass(int target, int source, int width, int height, float maxU, float maxV,
                          int textureWidth, int textureHeight, float dx, float dy, float guiWidth, float guiHeight) {
        RenderUtil.bindFramebuffer(target);
        GlStateManager.viewport(0, 0, width, height);
        blur.init();
        blur.setUniformi("scene", 0);
        blur.setUniformf("uvMax", maxU, maxV);
        blur.setUniformf("halfTexel", 0.5F / textureWidth, 0.5F / textureHeight);
        blur.setUniformf("direction", dx, dy);
        GlStateManager.bindTexture(source);
        quad(0, 0, guiWidth, guiHeight);
    }

    private void ensureBuffers(int width, int height, int vw, int vh) {
        if (capacityWidth >= width && capacityHeight >= height && viewportWidth == vw && viewportHeight == vh) return;
        int newWidth = capacityWidth, newHeight = capacityHeight;
        if (viewportWidth != vw || viewportHeight != vh) newWidth = newHeight = 0;
        newWidth = Math.max(32, Math.max(newWidth, powerOfTwo(width)));
        newHeight = Math.max(32, Math.max(newHeight, powerOfTwo(height)));
        release();
        capacityWidth = newWidth;
        capacityHeight = newHeight;
        viewportWidth = vw;
        viewportHeight = vh;
        sceneTexture = texture(newWidth, newHeight);
        pingTexture = texture(newWidth / 2, newHeight / 2);
        resultTexture = texture(newWidth / 2, newHeight / 2);
        pingFbo = framebuffer(pingTexture);
        resultFbo = framebuffer(resultTexture);
    }

    public void release() {
        if (sceneTexture != 0) GlStateManager.deleteTexture(sceneTexture);
        if (pingTexture != 0) GlStateManager.deleteTexture(pingTexture);
        if (resultTexture != 0) GlStateManager.deleteTexture(resultTexture);
        if (pingFbo != 0) OpenGlHelper.glDeleteFramebuffers(pingFbo);
        if (resultFbo != 0) OpenGlHelper.glDeleteFramebuffers(resultFbo);
        sceneTexture = pingTexture = resultTexture = pingFbo = resultFbo = 0;
        capacityWidth = capacityHeight = 0;
    }

    private static int powerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }

    private static int texture(int width, int height) {
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return id;
    }

    private static int framebuffer(int texture) {
        int fbo = OpenGlHelper.glGenFramebuffers();
        RenderUtil.bindFramebuffer(fbo);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            OpenGlHelper.glDeleteFramebuffers(fbo);
            throw new IllegalStateException("Incomplete HUD blur framebuffer");
        }
        return fbo;
    }

    private static void color(ShaderProgram program, String name, int rgb) {
        program.setUniformf(name, (rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F);
    }

    private static void quad(float left, float top, float right, float bottom) {
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(left, top, 0).tex(0, 1).endVertex();
        buffer.pos(left, bottom, 0).tex(0, 0).endVertex();
        buffer.pos(right, bottom, 0).tex(1, 0).endVertex();
        buffer.pos(right, top, 0).tex(1, 1).endVertex();
        Tessellator.getInstance().draw();
    }

    private static void fallback(List<float[]> rows, float opacity) {
        GL20.glUseProgram(0);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (float[] row : rows) {
            int alpha = Math.round(opacity * row[4] * 255);
            buffer.pos(row[0], row[3], 0).color(10, 10, 14, alpha).endVertex();
            buffer.pos(row[1], row[3], 0).color(10, 10, 14, alpha).endVertex();
            buffer.pos(row[1], row[2], 0).color(10, 10, 14, alpha).endVertex();
            buffer.pos(row[0], row[2], 0).color(10, 10, 14, alpha).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    /** The caller owns matrices/depth/lighting. This renderer restores everything it changes. */
    private static final class State {
        final int framebuffer = OpenGlHelper.isFramebufferEnabled() ? RenderUtil.getBoundFramebuffer() : 0;
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        int texture;
        boolean textured;
        State() {
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }
        void prepare() {
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        }
        void restore() {
            GL20.glUseProgram(program);
            RenderUtil.bindFramebuffer(framebuffer);
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.bindTexture(texture);
            if (textured) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(activeUnit);
        }
    }
}
