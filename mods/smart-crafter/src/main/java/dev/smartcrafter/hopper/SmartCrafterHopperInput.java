package dev.smartcrafter.hopper;

import dev.smartcrafter.data.CrafterTemplateAttachments;
import dev.smartcrafter.template.CrafterTemplate;
import dev.smartcrafter.template.TemplateSlot;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes hopper input only for vanilla Crafters with an explicitly saved template. */
public final class SmartCrafterHopperInput {
    private static final Logger LOGGER = LoggerFactory.getLogger("smartcrafter/hopper");
    private static final Set<Class<?>> WARNED_INGREDIENT_TYPES = Collections.synchronizedSet(new HashSet<>());

    private SmartCrafterHopperInput() {
    }

    /**
     * Distributes the offered stack into matching recipe slots and returns any remainder.
     * A null result means the caller should run the vanilla insertion code unchanged.
     */
    public static ItemStack tryInsert(Container destination, ItemStack offered, Direction direction) {
        if (!(destination instanceof CrafterBlockEntity crafter)
                || !CrafterTemplateAttachments.hasTemplate(crafter)) {
            return null;
        }

        CrafterTemplate template = CrafterTemplateAttachments.getOrEmpty(crafter);
        if (offered.isEmpty()) {
            return ItemStack.EMPTY;
        }

        List<Integer> eligibleSlots = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < CrafterTemplate.SLOT_COUNT; slotIndex++) {
            TemplateSlot templateSlot = template.slot(slotIndex);
            if (!templateSlot.enabled() || templateSlot.ingredient().isEmpty()) {
                continue;
            }
            var ingredient = templateSlot.ingredient().orElseThrow();
            boolean matches;
            try {
                matches = ingredient.test(offered);
            } catch (RuntimeException exception) {
                if (WARNED_INGREDIENT_TYPES.add(ingredient.getClass())) {
                    LOGGER.warn("Skipping hopper distribution for unsupported template ingredient type {}",
                            ingredient.getClass().getName(), exception);
                }
                continue;
            }
            if (!matches || !canInsertThroughFace(destination, slotIndex, offered, direction)) {
                continue;
            }
            eligibleSlots.add(slotIndex);
        }

        // Fill unused recipe slots first. A Crafter consumes one item from each slot per craft,
        // so this keeps identical ingredients distributed across their configured positions.
        for (int slotIndex : eligibleSlots) {
            if (offered.isEmpty()) {
                break;
            }
            if (destination.getItem(slotIndex).isEmpty()) {
                destination.setItem(slotIndex, offered.copyWithCount(1));
                offered.shrink(1);
            }
        }

        // After each matching recipe slot has one item, allow any remaining input to buffer in
        // those same slots, preserving normal inventory capacity and item component checks.
        for (int slotIndex : eligibleSlots) {
            if (offered.isEmpty()) {
                break;
            }
            ItemStack existing = destination.getItem(slotIndex);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, offered)) {
                continue;
            }
            int maxCount = Math.min(destination.getMaxStackSize(existing), existing.getMaxStackSize());
            int moved = Math.min(offered.getCount(), maxCount - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                offered.shrink(moved);
                destination.setChanged();
            }
        }

        return offered.isEmpty() ? ItemStack.EMPTY : offered;
    }

    private static boolean canInsertThroughFace(Container container, int slot, ItemStack stack, Direction direction) {
        if (!container.canPlaceItem(slot, stack)) {
            return false;
        }
        if (direction == null || !(container instanceof WorldlyContainer sidedContainer)) {
            return true;
        }
        boolean exposed = false;
        for (int exposedSlot : sidedContainer.getSlotsForFace(direction)) {
            if (exposedSlot == slot) {
                exposed = true;
                break;
            }
        }
        return exposed && sidedContainer.canPlaceItemThroughFace(slot, stack, direction);
    }
}
