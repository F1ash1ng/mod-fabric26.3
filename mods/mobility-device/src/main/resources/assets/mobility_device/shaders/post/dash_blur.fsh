#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DashConfig {
    vec4 DashParameters; // strength, elapsed seconds, reserved, reserved
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 original = texture(InSampler, texCoord);
    vec2 radial = texCoord - vec2(0.5);
    // An ellipse relative to the viewport reaches all four edges, including ultrawide screens.
    float edge = smoothstep(0.30, 1.05, length(radial * 2.0));
    if (edge <= 0.0 || DashParameters.x <= 0.0) {
        fragColor = original;
        return;
    }

    float pulse = 0.96 + 0.04 * sin(DashParameters.y * 37.0);
    vec2 trail = radial * (0.12 * edge * DashParameters.x * pulse);
    vec2 halfTexel = 0.5 / InSize;
    vec3 blurred = original.rgb;
    // Sample only this frame: no history buffers or afterimages left after the jet ends.
    const int SAMPLES = 16;
    for (int i = 1; i < SAMPLES; i++) {
        float t = float(i) / float(SAMPLES - 1);
        vec2 uv = clamp(texCoord - trail * t, halfTexel, vec2(1.0) - halfTexel);
        blurred += texture(InSampler, uv).rgb;
    }
    fragColor = vec4(mix(original.rgb, blurred / float(SAMPLES), edge * 0.92), original.a);
}
