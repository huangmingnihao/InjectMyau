package myau.util.font;

import myau.util.shader.ShaderProgram;

public final class MsdfShader {
    private static final float DISTANCE_RANGE = 10.0F;
    private static final String FRAGMENT =
            "#version 120\n"
                    + "\n"
                    + "uniform sampler2D atlas;\n"
                    + "uniform vec2 atlasSize;\n"
                    + "uniform vec4 textColor;\n"
                    + "\n"
                    + "const float DISTANCE_RANGE = " + DISTANCE_RANGE + ";\n"
                    + "\n"
                    + "float median(vec3 v) {\n"
                    + "    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));\n"
                    + "}\n"
                    + "\n"
                    + "float screenPxRange(vec2 uv) {\n"
                    + "    vec2 unitRange = vec2(DISTANCE_RANGE) / atlasSize;\n"
                    + "    vec2 screenTexSize = vec2(1.0) / fwidth(uv);\n"
                    + "    return max(0.5 * dot(unitRange, screenTexSize), 1.0);\n"
                    + "}\n"
                    + "\n"
                    + "float coverage(vec2 uv, float range) {\n"
                    + "    float dist = median(texture2D(atlas, uv).rgb) - 0.5;\n"
                    + "    return clamp(dist * range + 0.5, 0.0, 1.0);\n"
                    + "}\n"
                    + "\n"
                    + "void main() {\n"
                    + "    vec2 uv = gl_TexCoord[0].xy;\n"
                    + "    float range = screenPxRange(uv);\n"
                    + "\n"
                    + "    vec2 dx = dFdx(uv);\n"
                    + "    vec2 dy = dFdy(uv);\n"
                    + "\n"
                    + "    vec2 o1 =  0.125 * dx + 0.375 * dy;\n"
                    + "    vec2 o2 = -0.375 * dx + 0.125 * dy;\n"
                    + "    vec2 o3 = -0.125 * dx - 0.375 * dy;\n"
                    + "    vec2 o4 =  0.375 * dx - 0.125 * dy;\n"
                    + "\n"
                    + "    float alpha = 0.25 * (coverage(uv + o1, range)\n"
                    + "                        + coverage(uv + o2, range)\n"
                    + "                        + coverage(uv + o3, range)\n"
                    + "                        + coverage(uv + o4, range));\n"
                    + "\n"
                    + "    if (alpha <= 0.0) {\n"
                    + "        discard;\n"
                    + "    }\n"
                    + "\n"
                    + "    gl_FragColor = vec4(textColor.rgb, textColor.a * alpha);\n"
                    + "}\n";
    private static ShaderProgram program;
    private MsdfShader() {
    }
    public static ShaderProgram get() {
        if (program == null) {
            program = new ShaderProgram(FRAGMENT);
        }
        return program.isReady() ? program : null;
    }
    public static boolean isSupported() {
        return get() != null;
    }
}
