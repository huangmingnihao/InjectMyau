package myau.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

public final class BlurUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final KawaseShader BLUR_DOWN = new KawaseShader(KawaseShader.DOWN);
    private static final KawaseShader BLUR_UP = new KawaseShader(KawaseShader.UP);
    private static final KawaseShader BLOOM_DOWN = new KawaseShader(KawaseShader.DOWN_BLOOM);
    private static final KawaseShader BLOOM_UP = new KawaseShader(KawaseShader.UP_BLOOM);
    private static Framebuffer blurMask = new Framebuffer(1, 1, false);
    private static Framebuffer bloomMask = new Framebuffer(1, 1, false);
    private static boolean blurCapturing;
    private static boolean bloomCapturing;
    private static final Chain BLUR_CHAIN = new Chain(3.0);
    private static final Chain BLOOM_CHAIN = new Chain(2.0);
    private BlurUtil() {
    }
    public static boolean isSupported() {
        return BLUR_DOWN.compiles() && BLUR_UP.compiles()
                && BLOOM_DOWN.compiles() && BLOOM_UP.compiles();
    }
    public static void prepareBlur() {
        if (!isSupported()) {
            return;
        }
        blurMask = resize(blurMask);
        blurMask.framebufferClear();
        blurMask.bindFramebuffer(false);
        blurCapturing = true;
    }
    public static void prepareBloom() {
        if (!isSupported()) {
            return;
        }
        bloomMask = resize(bloomMask);
        bloomMask.framebufferClear();
        bloomMask.bindFramebuffer(false);
        bloomCapturing = true;
    }

    public static void blurEnd(int passes, float radius) {
        if (!blurCapturing) {
            return;
        }
        blurCapturing = false;
        blurMask.unbindFramebuffer();
        BLUR_CHAIN.run(BLUR_DOWN, BLUR_UP, mc.getFramebuffer().framebufferTexture,
                blurMask.framebufferTexture, passes, radius, false);
    }
    public static void bloomEnd(int passes, float radius) {
        if (!bloomCapturing) {
            return;
        }
        bloomCapturing = false;
        bloomMask.unbindFramebuffer();
        BLOOM_CHAIN.run(BLOOM_DOWN, BLOOM_UP, bloomMask.framebufferTexture,
                bloomMask.framebufferTexture, passes, radius, true);
    }
    private static Framebuffer resize(Framebuffer framebuffer) {
        if (framebuffer != null
                && framebuffer.framebufferWidth == mc.displayWidth
                && framebuffer.framebufferHeight == mc.displayHeight) {
            return framebuffer;
        }
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
        return new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    private static final class Chain {
        private final double step;
        private final List<Framebuffer> rungs = new ArrayList<Framebuffer>();
        private int builtFor = -1;
        private int builtWidth = -1;
        private int builtHeight = -1;
        private Chain(double step) {
            this.step = step;
        }
        private void build(int passes) {
            for (Framebuffer rung : this.rungs) {
                rung.deleteFramebuffer();
            }
            this.rungs.clear();
            this.rungs.add(new Framebuffer(mc.displayWidth, mc.displayHeight, false));
            for (int level = 1; level <= passes; level++) {
                double divisor = Math.pow(this.step, level);
                Framebuffer rung = new Framebuffer(
                        Math.max(1, (int) (mc.displayWidth / divisor)),
                        Math.max(1, (int) (mc.displayHeight / divisor)),
                        false);
                rung.setFramebufferFilter(GL11.GL_LINEAR);

                GlStateManager.bindTexture(rung.framebufferTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
                GlStateManager.bindTexture(0);
                this.rungs.add(rung);
            }
            this.builtFor = passes;
            this.builtWidth = mc.displayWidth;
            this.builtHeight = mc.displayHeight;
        }

        private void run(KawaseShader down, KawaseShader up, int source, int mask,
                         int passes, float radius, boolean additive) {
            passes = Math.max(1, passes);
            if (this.builtFor != passes || this.builtWidth != mc.displayWidth
                    || this.builtHeight != mc.displayHeight) {
                this.build(passes);
            }
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.enableBlend();
            if (additive) {
                GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
                GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            }
            pass(down, this.rungs.get(1), source, radius);
            for (int level = 1; level < passes; level++) {
                float offset = additive ? radius / (float) Math.pow(1.5, level) : radius;
                pass(down, this.rungs.get(level + 1), this.rungs.get(level).framebufferTexture, offset);
            }
            for (int level = passes; level > 1; level--) {
                float offset = additive ? radius / (float) Math.pow(1.5, level - 1) : radius;
                pass(up, this.rungs.get(level - 1), this.rungs.get(level).framebufferTexture, offset);
            }
            Framebuffer result = this.rungs.get(0);
            result.framebufferClear();
            result.bindFramebuffer(false);
            if (!up.bind()) {
                return;
            }
            up.setVec2("offset", radius, radius);
            up.setInt("inTexture", 0);
            up.setInt("check", 1);
            up.setInt("textureToCheck", 16);
            up.setVec2("halfpixel", 1.0F / result.framebufferWidth, 1.0F / result.framebufferHeight);
            up.setVec2("iResolution", result.framebufferWidth, result.framebufferHeight);
            GL13.glActiveTexture(GL13.GL_TEXTURE16);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mask);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.rungs.get(1).framebufferTexture);
            KawaseShader.drawFullScreen();
            up.unbind();
            if (additive) {
                GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            }
            mc.getFramebuffer().bindFramebuffer(!additive);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, result.framebufferTexture);
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            KawaseShader.drawFullScreen();
            GlStateManager.bindTexture(0);
            if (!additive) {
                GlStateManager.disableBlend();
            }
        }
        private void pass(KawaseShader shader, Framebuffer into, int texture, float radius) {
            into.framebufferClear();
            into.bindFramebuffer(false);
            if (!shader.bind()) {
                return;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            shader.setVec2("offset", radius, radius);
            shader.setInt("inTexture", 0);
            shader.setInt("check", 0);
            shader.setVec2("halfpixel", 1.0F / into.framebufferWidth, 1.0F / into.framebufferHeight);
            shader.setVec2("iResolution", into.framebufferWidth, into.framebufferHeight);
            KawaseShader.drawFullScreen();
            shader.unbind();
        }
    }
}
