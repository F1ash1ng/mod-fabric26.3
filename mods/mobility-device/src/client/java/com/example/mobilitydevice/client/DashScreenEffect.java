package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.example.mobilitydevice.ability.DashMotion;
import com.example.mobilitydevice.mixin.PostChainAccessor;
import com.example.mobilitydevice.mixin.PostPassAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/** World-only radial blur; the shader manager owns the chain and its GPU resources. */
public final class DashScreenEffect {
    private static final Identifier EFFECT = MobilityDeviceMod.id("dash_blur");
    private static final String UNIFORM = "DashConfig";

    private DashScreenEffect() {}

    public static void render(Minecraft client, RenderTarget target,
                              GraphicsResourceAllocator allocator, float progress) {
        if (progress < 0) return;
        PostChain chain = client.getShaderManager().getPostChain(EFFECT, Set.of(PostChain.MAIN_TARGET_ID));
        if (chain == null) return;

        // Follow horizontal distance so the blur ends with the movement, regardless of flight time.
        float fade = Math.clamp((float) ((DashMotion.TOTAL_DISTANCE - progress) / 3.0), 0.0f, 1.0f);
        float strength = (progress < 2.0f ? 1.0f : 0.82f) * fade * fade * (3.0f - 2.0f * fade);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(16);
            data.putFloat(strength).putFloat(progress / 20.0f).putFloat(0).putFloat(0).flip();
            for (PostPass pass : ((PostChainAccessor) chain).mobility$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).mobility$getCustomUniforms();
                GpuBuffer buffer = uniforms.get(UNIFORM);
                if (buffer == null) continue;
                if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                    // Vanilla JSON uniforms are immutable; replace only our block with an updatable one.
                    GpuBuffer dynamic = RenderSystem.getDevice().createBuffer(
                            () -> "mobility_device dash blur", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
                    uniforms.put(UNIFORM, dynamic);
                    buffer.close();
                } else {
                    RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
                }
            }
        }
        chain.process(target, allocator);
    }
}
