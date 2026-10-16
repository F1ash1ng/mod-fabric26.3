package dev.smartcrafter.gametest;

import dev.smartcrafter.data.CrafterTemplateAttachments;
import dev.smartcrafter.template.CrafterTemplate;
import dev.smartcrafter.template.TemplateSlot;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CrafterTemplatePersistenceGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void attachmentPersistsAndStaysPerCrafter(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.CRAFTER);
        helper.setBlock(1, 0, 0, Blocks.CRAFTER);

        BlockPos firstPos = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos secondPos = helper.absolutePos(new BlockPos(1, 0, 0));
        BlockEntity first = helper.getBlockEntity(new BlockPos(0, 0, 0), BlockEntity.class);
        BlockEntity second = helper.getBlockEntity(new BlockPos(1, 0, 0), BlockEntity.class);

        CrafterTemplate firstTemplate = CrafterTemplate.empty()
                .withSlot(0, TemplateSlot.forStack(new ItemStack(Items.GUNPOWDER)))
                .withSlot(4, TemplateSlot.disabled())
                .withRecipeMetadata(Optional.of(net.minecraft.resources.Identifier.withDefaultNamespace("tnt")), Optional.empty());
        CrafterTemplate secondTemplate = CrafterTemplate.empty()
                .withSlot(8, TemplateSlot.forStack(new ItemStack(Items.SAND)));

        CrafterTemplateAttachments.set(first, firstTemplate);
        CrafterTemplateAttachments.set(second, secondTemplate);

        helper.assertTrue(CrafterTemplateAttachments.hasTemplate(first), "first Crafter should have a template");
        helper.assertTrue(CrafterTemplateAttachments.hasTemplate(second), "second Crafter should have a template");
        helper.assertValueEqual(firstTemplate, CrafterTemplateAttachments.get(first).orElseThrow(), "first template changed");
        helper.assertValueEqual(secondTemplate, CrafterTemplateAttachments.get(second).orElseThrow(), "second template changed");

        var savedNbt = first.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity reloaded = BlockEntity.loadStatic(
                firstPos,
                Blocks.CRAFTER.defaultBlockState(),
                savedNbt,
                helper.getLevel().registryAccess()
        );

        helper.assertTrue(reloaded != null, "Crafter block entity should load from saved NBT");
        helper.assertValueEqual(firstTemplate, CrafterTemplateAttachments.get(reloaded).orElseThrow(), "template changed after NBT reload");
        helper.assertValueEqual(
                Items.GUNPOWDER,
                CrafterTemplateAttachments.get(reloaded).orElseThrow().slot(0).displayStack().orElseThrow().copy().getItem(),
                "display stack did not persist"
        );
        helper.assertFalse(
                CrafterTemplateAttachments.get(reloaded).orElseThrow().slot(4).enabled(),
                "disabled slot did not persist"
        );
        helper.assertTrue(
                CrafterTemplateAttachments.get(second).orElseThrow().slot(0).ingredient().isEmpty(),
                "template data leaked between Crafter instances"
        );
        helper.assertTrue(second.getBlockPos().equals(secondPos), "second Crafter position changed unexpectedly");
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
