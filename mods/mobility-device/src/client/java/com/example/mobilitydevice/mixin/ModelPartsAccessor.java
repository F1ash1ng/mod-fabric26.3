package com.example.mobilitydevice.mixin;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(Model.class)
public interface ModelPartsAccessor {
    @Mutable @Accessor("allParts") void mobility$allParts(List<ModelPart> parts);
}
