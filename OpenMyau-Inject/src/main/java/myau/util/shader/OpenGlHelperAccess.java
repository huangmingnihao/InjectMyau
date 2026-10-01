package myau.util.shader;

import net.minecraft.client.renderer.OpenGlHelper;

public final class OpenGlHelperAccess {
    private OpenGlHelperAccess() {
    }
    public static boolean framebuffersEnabled() {
        try {
            return OpenGlHelper.isFramebufferEnabled() && OpenGlHelper.areShadersSupported();
        } catch (Throwable throwable) {
            return false;
        }
    }
}
