#version 150
uniform sampler2D DiffuseSampler;
uniform float Mode;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 src = texture(DiffuseSampler,texCoord);
    float y = dot(src.rgb,vec3(.2126,.7152,.0722));
    vec3 result;
    if (Mode < .5) {
        // Crush ambient moonlight, while bright light sources remain useful.
        result = src.rgb * mix(.16,.92,smoothstep(.22,.78,y));
    } else if (Mode < 1.5) {
        float v = clamp(pow(y,.78)*1.18,0.,1.);
        result = v * vec3(.39,1.,.36);
    } else {
        float v = mix(.085,.58,pow(y,.9));
        // Fullbright white entity surfaces retain a bright signature.
        v = mix(v,.98,smoothstep(.73,.98,y));
        result = vec3(v);
    }
    fragColor=vec4(result,src.a);
}
