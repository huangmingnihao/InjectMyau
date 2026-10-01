package myau.util.shader;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public abstract class Shader {
    private static final String vertex = "#version 120\n" +
            "void main(void) {\n" +
            "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";
    private final Map<String, Integer> uniformLocations;
    protected int programId;

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) {
            return shader;
        }
        System.out.println("[Myau] shader failed to compile: "
                + GL20.glGetShaderInfoLog(shader, GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH)));
        GL20.glDeleteShader(shader);
        return 0;
    }

    private void createProgram(String fragment) {
        int vertexShader = this.compileShader(vertex, GL20.GL_VERTEX_SHADER);
        int fragmentShader = this.compileShader(fragment, GL20.GL_FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            this.programId = 0;
            return;
        }
        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, vertexShader);
        GL20.glAttachShader(this.programId, fragmentShader);
        GL20.glLinkProgram(this.programId);
        if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) != 0) {
            this.onLink();
            return;
        }
        System.out.println("[Myau] shader failed to link: " + GL20.glGetProgramInfoLog(this.programId,
                GL20.glGetProgrami(this.programId, GL20.GL_INFO_LOG_LENGTH)));
        GL20.glDeleteProgram(this.programId);
        this.programId = 0;
    }

    public Shader(String string) {
        this.uniformLocations = new HashMap<>();
        this.createProgram(string);
    }

    public int getUniformLocationCached(String name) {
        Integer location = this.uniformLocations.get(name);
        return location == null ? -1 : location.intValue();
    }
    public boolean isValid() {
        return this.programId > 0;
    }
    public void setUniform(String name) {
        this.uniformLocations.put(name, GL20.glGetUniformLocation(this.programId, name));
    }
    public abstract void onLink();
    public abstract void onUse();
    public void use() {
        if (this.isValid()) {
            onUse();
        }
    }
    public void stop() {
        GL20.glUseProgram(0);
    }
}
