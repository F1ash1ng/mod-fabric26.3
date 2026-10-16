package dev.smartcrafter.gametest;

import dev.smartcrafter.data.CrafterTemplateAttachments;
import dev.smartcrafter.template.CrafterTemplate;
import dev.smartcrafter.template.TemplateSlot;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.lang.reflect.Method;

public final class HopperCrafterInputGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void configuredCrafterReceivesOnlyMatchingEnabledSlots(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.CRAFTER);
        CrafterBlockEntity crafter = helper.getBlockEntity(new BlockPos(0, 0, 0), CrafterBlockEntity.class);
        CrafterTemplate template = CrafterTemplate.empty()
                .withSlot(0, TemplateSlot.forStack(new ItemStack(Items.GUNPOWDER)))
                .withSlot(1, TemplateSlot.forStack(new ItemStack(Items.SAND)))
                .withSlot(2, TemplateSlot.forStack(new ItemStack(Items.GUNPOWDER)))
                .withSlot(3, TemplateSlot.disabled());
        CrafterTemplateAttachments.set(crafter, template);

        ItemStack firstPowder = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.GUNPOWDER), null);
        ItemStack secondPowder = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.GUNPOWDER), null);
        ItemStack sand = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.SAND), null);
        ItemStack wrongItem = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.DIRT, 2), null);

        helper.assertTrue(firstPowder.isEmpty(), "first matching item should be inserted");
        helper.assertTrue(secondPowder.isEmpty(), "duplicate ingredient should use its second configured slot");
        helper.assertTrue(sand.isEmpty(), "sand should enter its configured slot");
        helper.assertTrue(wrongItem.getCount() == 2, "unmatched input must be returned without loss");
        helper.assertTrue(crafter.getItem(0).is(Items.GUNPOWDER), "first ingredient slot should contain gunpowder");
        helper.assertTrue(crafter.getItem(1).is(Items.SAND), "sand ingredient slot should contain sand");
        helper.assertTrue(crafter.getItem(2).is(Items.GUNPOWDER), "duplicate ingredient should be distributed");
        helper.assertTrue(crafter.getItem(3).isEmpty(), "disabled slot must remain empty");
        helper.succeed();
    }

    @GameTest
    public void crafterWithoutTemplateKeepsVanillaInsertion(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.CRAFTER);
        CrafterBlockEntity crafter = helper.getBlockEntity(new BlockPos(0, 0, 0), CrafterBlockEntity.class);
        ItemStack remainder = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.DIRT), null);

        helper.assertTrue(remainder.isEmpty(), "vanilla insertion should accept the item");
        helper.assertTrue(crafter.getItem(0).is(Items.DIRT), "vanilla insertion should use the first slot");
        helper.succeed();
    }

    @GameTest
    public void fullOrDisabledTemplateSlotsReturnItemsWithoutLoss(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.CRAFTER);
        CrafterBlockEntity crafter = helper.getBlockEntity(new BlockPos(0, 0, 0), CrafterBlockEntity.class);
        CrafterTemplate template = CrafterTemplate.empty()
                .withSlot(0, TemplateSlot.forStack(new ItemStack(Items.GUNPOWDER)))
                .withSlot(1, TemplateSlot.disabled());
        CrafterTemplateAttachments.set(crafter, template);
        crafter.setItem(0, new ItemStack(Items.GUNPOWDER, 64));

        ItemStack fullSlotRemainder = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.GUNPOWDER), null);
        helper.assertTrue(fullSlotRemainder.getCount() == 1, "full matching slot must return the offered item");
        helper.assertTrue(crafter.getItem(0).getCount() == 64, "full Crafter slot count must not change");
        helper.assertTrue(crafter.getItem(1).isEmpty(), "disabled slot must not receive matching ingredients");

        CrafterTemplate allDisabled = CrafterTemplate.empty();
        for (int slot = 0; slot < CrafterTemplate.SLOT_COUNT; slot++) {
            allDisabled = allDisabled.withSlot(slot, TemplateSlot.disabled());
        }
        CrafterTemplateAttachments.set(crafter, allDisabled);
        ItemStack blockedInput = HopperBlockEntity.addItem(null, crafter, new ItemStack(Items.SAND), null);
        helper.assertTrue(blockedInput.is(Items.SAND), "all-disabled template must return incoming items");
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
