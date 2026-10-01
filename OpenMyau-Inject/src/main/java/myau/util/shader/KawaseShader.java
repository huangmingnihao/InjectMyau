package myau.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public final class KawaseShader {
    private static final String VERTEX =
            "#version 120\n"
                    + "\n"
                    + "void main() {\n"
                    + "    gl_TexCoord[0] = gl_MultiTexCoord0;\n"
                    + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
                    + "}\n";
    static final String DOWN =
            "#version 120\n"
                    + "\n"
                    + "uniform sampler2D inTexture;\n"
                    + "uniform vec2 offset, halfpixel, iResolution;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
                    + "\n"
                    + "    vec4 sum = texture2D(inTexture, uv) * 4.0;\n"
                    + "    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n"
                    + "    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n"
                    + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n"
                    + "    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n"
                    + "\n"
                    + "    gl_FragColor = vec4(sum.rgb * 0.125, 1.0);\n"
                    + "}\n";
    static final String UP =
            "#version 120\n"
                    + "\n"
                    + "uniform sampler2D inTexture, textureToCheck;\n"
                    + "uniform vec2 halfpixel, offset, iResolution;\n"
                    + "uniform int check;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
                    + "\n"
                    + "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n"
                    + "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n"
                    + "    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n"
                    + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n"
                    + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n"
                    + "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n"
                    + "    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n"
                    + "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n"
                    + "\n"
                    + "    vec4 average = sum / 12.0;\n"
                    + "    gl_FragColor = vec4(average.rgb, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n"
                    + "}\n";
    static final String DOWN_BLOOM =
            "#version 120\n"
                    + "\n"
                    + "uniform sampler2D inTexture;\n"
                    + "uniform vec2 offset, halfpixel, iResolution;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
                    + "\n"
                    + "    vec4 sum = texture2D(inTexture, uv);\n"
                    + "    sum.rgb *= sum.a;\n"
                    + "    sum *= 4.0;\n"
                    + "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n"
                    + "    smp1.rgb *= smp1.a;\n"
                    + "    sum += smp1;\n"
                    + "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n"
                    + "    smp2.rgb *= smp2.a;\n"
                    + "    sum += smp2;\n"
                    + "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n"
                    + "    smp3.rgb *= smp3.a;\n"
                    + "    sum += smp3;\n"
                    + "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n"
                    + "    smp4.rgb *= smp4.a;\n"
                    + "    sum += smp4;\n"
                    + "    vec4 result = sum / 8.0;\n"
                    + "    gl_FragColor = vec4(result.rgb / result.a, result.a);\n"
                    + "}\n";
    static final String UP_BLOOM =
            "#version 120\n"
                    + "\n"
                    + "uniform sampler2D inTexture, textureToCheck;\n"
                    + "uniform vec2 halfpixel, offset, iResolution;\n"
                    + "uniform int check;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = gl_FragCoord.xy / iResolution;\n"
                    + "\n"
                    + "    vec2 offset1 = vec2(-halfpixel.x, 0.0) * offset;\n"
                    + "    vec2 offset2 = vec2(-halfpixel.x, halfpixel.y) * offset;\n"
                    + "    vec2 offset3 = vec2(0.0, halfpixel.y * 2.0) * offset;\n"
                    + "    vec2 offset4 = vec2(halfpixel.x, halfpixel.y) * offset;\n"
                    + "    vec2 offset5 = vec2(halfpixel.x * 2.0, 0.0) * offset;\n"
                    + "    vec2 offset6 = vec2(halfpixel.x, -halfpixel.y) * offset;\n"
                    + "    vec2 offset7 = vec2(0.0, -halfpixel.y * 2.0) * offset;\n"
                    + "    vec2 offset8 = vec2(-halfpixel.x, -halfpixel.y) * offset;\n"
                    + "\n"
                    + "    vec4 sum = texture2D(inTexture, uv + offset1);\n"
                    + "    sum.rgb *= sum.a;\n"
                    + "    vec4 smpl1 = texture2D(inTexture, uv + offset2);\n"
                    + "    smpl1.rgb *= smpl1.a;\n"
                    + "    sum += smpl1 * 2.0;\n"
                    + "    vec4 smp2 = texture2D(inTexture, uv + offset3);\n"
                    + "    smp2.rgb *= smp2.a;\n"
                    + "    sum += smp2;\n"
                    + "    vec4 smp3 = texture2D(inTexture, uv + offset4);\n"
                    + "    smp3.rgb *= smp3.a;\n"
                    + "    sum += smp3 * 2.0;\n"
                    + "    vec4 smp4 = texture2D(inTexture, uv + offset5);\n"
                    + "    smp4.rgb *= smp4.a;\n"
                    + "    sum += smp4;\n"
                    + "    vec4 smp5 = texture2D(inTexture, uv + offset6);\n"
                    + "    smp5.rgb *= smp5.a;\n"
                    + "    sum += smp5 * 2.0;\n"
                    + "    vec4 smp6 = texture2D(inTexture, uv + offset7);\n"
                    + "    smp6.rgb *= smp6.a;\n"
                    + "    sum += smp6;\n"
                    + "    vec4 smp7 = texture2D(inTexture, uv + offset8);\n"
                    + "    smp7.rgb *= smp7.a;\n"
                    + "    sum += smp7 * 2.0;\n"
                    + "\n"
                    + "    vec4 result = sum / 12.0;\n"
                    + "    float checkAlpha = texture2D(textureToCheck, gl_TexCoord[0].st).a;\n"
                    + "    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, result.a * (1.0 - checkAlpha), float(check)));\n"
                    + "}\n";
    private final String fragment;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();
    private int program = -1;
    private boolean failed;
    KawaseShader(String fragment) {
        this.fragment = fragment;
    }
    boolean compiles() {
        if (this.failed) {
            return false;
        }
        if (this.program == -1) {
            this.program = link();
            if (this.program == 0) {
                this.failed = true;
                return false;
            }
        }
        return true;
    }
    boolean bind() {
        if (!this.compiles()) {
            return false;
        }
        GL20.glUseProgram(this.program);
        return true;
    }
    void unbind() {
        GL20.glUseProgram(0);
    }
    private int link() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int frag = compile(GL20.GL_FRAGMENT_SHADER, this.fragment);
        if (vertex == 0 || frag == 0) {
            return 0;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, frag);
        GL20.glLinkProgram(id);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(frag);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.out.println("[Myau] kawase shader failed to link: "
                    + GL20.glGetProgramInfoLog(id, 1024));
            GL20.glDeleteProgram(id);
            return 0;
        }
        return id;
    }
    private static int compile(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.out.println("[Myau] kawase shader failed to compile: "
                    + GL20.glGetShaderInfoLog(id, 1024));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }
    private int location(String name) {
        Integer cached = this.uniforms.get(name);
        if (cached != null) {
            return cached;
        }
        int found = GL20.glGetUniformLocation(this.program, name);
        this.uniforms.put(name, found);
        return found;
    }
    void setInt(String name, int value) {
        int where = this.location(name);
        if (where != -1) {
            GL20.glUniform1i(where, value);
        }
    }
    void setVec2(String name, float x, float y) {
        int where = this.location(name);
        if (where != -1) {
            GL20.glUniform2f(where, x, y);
        }
    }
    static void drawFullScreen() {
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
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
