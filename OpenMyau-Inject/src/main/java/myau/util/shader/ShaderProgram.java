package myau.util.shader;

import myau.Myau;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public final class ShaderProgram {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String VERTEX =
            "#version 120\n"
                    + "\n"
                    + "void main() {\n"
                    + "    gl_TexCoord[0] = gl_MultiTexCoord0;\n"
                    + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
                    + "}\n";

    private final String fragment;
    private final Map<String, Integer> uniformLocations = new HashMap<String, Integer>();
    private int programId = 0;
    private boolean compiled = false;
    private boolean failed = false;
    public ShaderProgram(String fragment) {
        this.fragment = fragment;
    }
    public boolean isReady() {
        if (this.compiled) {
            return !this.failed;
        }
        this.compiled = true;
        try {
            int vertexId = compile(VERTEX, GL20.GL_VERTEX_SHADER);
            int fragmentId = compile(this.fragment, GL20.GL_FRAGMENT_SHADER);
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexId);
            GL20.glAttachShader(program, fragmentId);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                this.reportFailure("link failed: " + GL20.glGetProgramInfoLog(program, 4096));
                return false;
            }
            this.programId = program;
        } catch (Throwable throwable) {
            this.reportFailure(String.valueOf(throwable.getMessage()));
        }
        return !this.failed;
    }
    private void reportFailure(String detail) {
        this.failed = true;
        System.out.println("[Myau] shader unavailable -- " + detail);
        try {
            ChatUtil.sendFormatted(String.format("%s&cShader unavailable&r: %s", Myau.clientName,
                    detail.length() > 120 ? detail.substring(0, 120) : detail));
        } catch (Throwable ignored) {
        }
    }
    private static int compile(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
        }
        return shader;
    }
    public void init() {
        GL20.glUseProgram(this.programId);
    }
    public void unload() {
        GL20.glUseProgram(0);
    }
    public void setUniformf(String name, float... args) {
        int location = this.getUniformLocation(name);
        if (location == -1) {
            return;
        }
        switch (args.length) {
            case 1:
                GL20.glUniform1f(location, args[0]);
                break;
            case 2:
                GL20.glUniform2f(location, args[0], args[1]);
                break;
            case 3:
                GL20.glUniform3f(location, args[0], args[1], args[2]);
                break;
            case 4:
                GL20.glUniform4f(location, args[0], args[1], args[2], args[3]);
                break;
            default:
                break;
        }
    }

    public void setUniformi(String name, int... args) {
        int location = this.getUniformLocation(name);
        if (location == -1) {
            return;
        }
        if (args.length > 1) {
            GL20.glUniform2i(location, args[0], args[1]);
        } else {
            GL20.glUniform1i(location, args[0]);
        }
    }
    private int getUniformLocation(String name) {
        Integer cached = this.uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(this.programId, name);
        this.uniformLocations.put(name, location);
        return location;
    }

    public static void drawQuads() {
        ScaledResolution resolution = new ScaledResolution(mc);
        float width = (float) resolution.getScaledWidth_double();
        float height = (float) resolution.getScaledHeight_double();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(0.0F, height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(width, 0.0F);
        GL11.glEnd();
    }
}
