#version 150

uniform sampler2D Sampler0;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    // This sampler contains a completed world image, not an alpha-cutout material.
    // The current ocular stencil controls visibility; sky/weather alpha must not expose the main eye image.
    fragColor = vec4(texture(Sampler0, texCoord0).rgb, 1.0);
}
