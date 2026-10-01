package myau.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

public final class BlurUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ShaderProgram BLUR_DOWN = new ShaderProgram(KawaseShaders.KAWASE_DOWN);
    private static final ShaderProgram BLUR_UP = new ShaderProgram(KawaseShaders.KAWASE_UP);
    private static final ShaderProgram BLOOM_DOWN = new ShaderProgram(KawaseShaders.KAWASE_DOWN_BLOOM);
    private static final ShaderProgram BLOOM_UP = new ShaderProgram(KawaseShaders.KAWASE_UP_BLOOM);
    private static final Chain BLUR = new Chain(BLUR_DOWN, BLUR_UP, 3.0, false, true);
    private static final Chain BLOOM = new Chain(BLOOM_DOWN, BLOOM_UP, 2.0, true, false);
    private static Framebuffer blurStencil = null;
    private static Framebuffer bloomStencil = null;
    private static boolean blurArmed = false;
    private static boolean bloomArmed = false;
    private BlurUtils() {
    }
    public static boolean isSupported() {
        return OpenGlHelperAccess.framebuffersEnabled();
    }
    public static boolean isReady() {
        return isSupported()
                && BLUR_DOWN.isReady() && BLUR_UP.isReady()
                && BLOOM_DOWN.isReady() && BLOOM_UP.isReady();
    }

    public static void prepareBlur() {
        blurArmed = begin(true);
    }

    public static void prepareBloom() {
        bloomArmed = begin(false);
    }
    public static void blurEnd(int passes, float radius) {
        if (!blurArmed) {
            return;
        }
        blurArmed = false;
        blurStencil.unbindFramebuffer();
        BLUR.render(blurStencil.framebufferTexture, passes, radius);
    }
    public static void bloomEnd(int passes, float radius) {
        if (!bloomArmed) {
            return;
        }
        bloomArmed = false;
        bloomStencil.unbindFramebuffer();
        BLOOM.render(bloomStencil.framebufferTexture, passes, radius);
    }
    private static boolean begin(boolean blur) {
        if (!isSupported()) {
            return false;
        }
        try {
            if (blur) {
                blurStencil = resize(blurStencil);
                blurStencil.framebufferClear();
                blurStencil.bindFramebuffer(false);
            } else {
                bloomStencil = resize(bloomStencil);
                bloomStencil.framebufferClear();
                bloomStencil.bindFramebuffer(false);
            }
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Framebuffer resize(Framebuffer framebuffer) {
        if (framebuffer == null
                || framebuffer.framebufferWidth != mc.displayWidth
                || framebuffer.framebufferHeight != mc.displayHeight) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            return new Framebuffer(mc.displayWidth, mc.displayHeight, false);
        }
        return framebuffer;
    }
    private static final class Chain {
        private final ShaderProgram down;
        private final ShaderProgram up;
        private final double scaleStep;
        private final boolean taperOffset;
        private final boolean restoreViewport;
        private final List<Framebuffer> buffers = new ArrayList<Framebuffer>();
        private int builtFor = -1;
        Chain(ShaderProgram down, ShaderProgram up, double scaleStep, boolean taperOffset,
              boolean restoreViewport) {
            this.down = down;
            this.up = up;
            this.scaleStep = scaleStep;
            this.taperOffset = taperOffset;
            this.restoreViewport = restoreViewport;
        }
        void render(int stencilTexture, int passes, float offset) {
            if (passes < 1 || !this.down.isReady() || !this.up.isReady()) {
                return;
            }
            try {
                this.build(passes);
                if (this.buffers.size() <= passes) {
                    return;
                }
                if (this.taperOffset) {
                    setAlphaLimit();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
                    GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                }
                int source = this.taperOffset ? stencilTexture : mc.getFramebuffer().framebufferTexture;
                this.pass(this.buffers.get(1), source, this.down, offset);
                for (int i = 1; i < passes; i++) {
                    this.pass(this.buffers.get(i + 1), this.buffers.get(i).framebufferTexture,
                            this.down, this.offsetAt(offset, i));
                }
                for (int i = passes; i > 1; i--) {
                    this.pass(this.buffers.get(i - 1), this.buffers.get(i).framebufferTexture,
                            this.up, this.offsetAt(offset, i - 1));
                }

                Framebuffer target = this.buffers.get(0);
                target.framebufferClear();
                target.bindFramebuffer(false);
                this.up.init();
                this.up.setUniformf("offset", offset, offset);
                this.up.setUniformi("inTexture", 0);
                this.up.setUniformi("check", 1);
                this.up.setUniformi("textureToCheck", 16);
                this.up.setUniformf("halfpixel", 1.0F / target.framebufferWidth, 1.0F / target.framebufferHeight);
                this.up.setUniformf("iResolution", target.framebufferWidth, target.framebufferHeight);
                GL13.glActiveTexture(GL13.GL_TEXTURE16);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, stencilTexture);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.buffers.get(1).framebufferTexture);
                ShaderProgram.drawQuads();
                this.up.unload();
                if (this.taperOffset) {
                    GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                }
                mc.getFramebuffer().bindFramebuffer(this.restoreViewport);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.framebufferTexture);
                setAlphaLimit();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                ShaderProgram.drawQuads();
                GlStateManager.bindTexture(0);
                if (this.taperOffset) {

                    setAlphaLimit();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else {
                    GlStateManager.disableBlend();
                }
            } catch (Throwable throwable) {
                try {
                    mc.getFramebuffer().bindFramebuffer(false);
                } catch (Throwable ignored) {
                }
            }
        }
        private float offsetAt(float offset, int level) {
            return this.taperOffset ? offset / (float) Math.pow(1.5, level) : offset;
        }
        private void pass(Framebuffer target, int texture, ShaderProgram shader, float offset) {
            target.framebufferClear();
            target.bindFramebuffer(false);
            shader.init();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            shader.setUniformf("offset", offset, offset);
            shader.setUniformi("inTexture", 0);
            shader.setUniformi("check", 0);
            shader.setUniformf("halfpixel", 1.0F / target.framebufferWidth, 1.0F / target.framebufferHeight);
            shader.setUniformf("iResolution", target.framebufferWidth, target.framebufferHeight);
            ShaderProgram.drawQuads();
            shader.unload();
        }
        private void build(int passes) {
            Framebuffer first = this.buffers.isEmpty() ? null : this.buffers.get(0);
            boolean sized = first != null
                    && first.framebufferWidth == mc.displayWidth
                    && first.framebufferHeight == mc.displayHeight;
            if (this.builtFor == passes && sized) {
                return;
            }
            for (Framebuffer framebuffer : this.buffers) {
                framebuffer.deleteFramebuffer();
            }
            this.buffers.clear();
            this.buffers.add(new Framebuffer(mc.displayWidth, mc.displayHeight, false));
            for (int i = 1; i <= passes; i++) {
                int width = Math.max(1, (int) (mc.displayWidth / Math.pow(this.scaleStep, i)));
                int height = Math.max(1, (int) (mc.displayHeight / Math.pow(this.scaleStep, i)));
                Framebuffer level = new Framebuffer(width, height, false);
                level.setFramebufferFilter(GL11.GL_LINEAR);
                GlStateManager.bindTexture(level.framebufferTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
                GlStateManager.bindTexture(0);
                this.buffers.add(level);
            }
            this.builtFor = passes;
        }
    }
    private static void setAlphaLimit() {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
    }
}
