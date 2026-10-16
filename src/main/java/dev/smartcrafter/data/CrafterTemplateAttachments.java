package dev.smartcrafter.data;

import dev.smartcrafter.SmartCrafterMod;
import dev.smartcrafter.template.CrafterTemplate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Optional;

/** Persistent per-block-entity storage for smart Crafter templates. */
public final class CrafterTemplateAttachments {
    public static final AttachmentType<CrafterTemplate> TEMPLATE = AttachmentRegistry.createPersistent(
            SmartCrafterMod.id("crafter_template"), CrafterTemplate.CODEC
    );

    private CrafterTemplateAttachments() {
    }

    /** Forces attachment registration during mod initialization. */
    public static void initialize() {
        // Loading this class initializes TEMPLATE.
    }

    public static Optional<CrafterTemplate> get(BlockEntity blockEntity) {
        requireCrafter(blockEntity);
        return Optional.ofNullable(((AttachmentTarget) blockEntity).getAttached(TEMPLATE));
    }

    public static boolean hasTemplate(BlockEntity blockEntity) {
        requireCrafter(blockEntity);
        return ((AttachmentTarget) blockEntity).hasAttached(TEMPLATE);
    }

    public static CrafterTemplate getOrEmpty(BlockEntity blockEntity) {
        return get(blockEntity).orElseGet(CrafterTemplate::empty);
    }

    public static void set(BlockEntity blockEntity, CrafterTemplate template) {
        requireCrafter(blockEntity);
        Objects.requireNonNull(template, "template");
        Level level = blockEntity.getLevel();
        if (level != null && level.isClientSide()) {
            throw new IllegalStateException("Crafter templates can only be changed on the logical server");
        }
        ((AttachmentTarget) blockEntity).setAttached(TEMPLATE, template);
    }

    public static void clear(BlockEntity blockEntity) {
        requireCrafter(blockEntity);
        Level level = blockEntity.getLevel();
        if (level != null && level.isClientSide()) {
            throw new IllegalStateException("Crafter templates can only be changed on the logical server");
        }
        ((AttachmentTarget) blockEntity).removeAttached(TEMPLATE);
    }

    private static void requireCrafter(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        BlockPos pos = blockEntity.getBlockPos();
        if (!blockEntity.getBlockState().is(Blocks.CRAFTER)) {
            throw new IllegalArgumentException("Template attachments are only supported on vanilla Crafters at " + pos);
        }
    }
}
