package com.example.mobilitydevice.mixin;

import com.example.mobilitydevice.client.SkinLayersWallRunCompatibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;

/** Wraps the entire render method, including Skin Layers' cancellable HEAD hook. */
@Mixin(value = ModelPart.class, priority = 2000)
public abstract class ModelPartWallRunSkinMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    private void mobility$flatWallRunSkin(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color, Operation<Void> original) {
        SkinLayersWallRunCompatibility.beforeRender((ModelPart) (Object) this);
        original.call(pose, consumer, light, overlay, color);
    }
}
