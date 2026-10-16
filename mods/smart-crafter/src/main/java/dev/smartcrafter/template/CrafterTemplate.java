package dev.smartcrafter.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/** Immutable template data attached to an individual vanilla Crafter block entity. */
public record CrafterTemplate(
        int dataVersion,
        List<TemplateSlot> slots,
        Optional<Identifier> recipeId,
        Optional<Identifier> recipeType
) {
    public static final int CURRENT_DATA_VERSION = 1;
    public static final int SLOT_COUNT = 9;

    private static final Codec<List<TemplateSlot>> NINE_SLOTS_CODEC = TemplateSlot.CODEC.listOf().flatXmap(
            slots -> slots.size() == SLOT_COUNT
                    ? DataResult.success(List.copyOf(slots))
                    : DataResult.error(() -> "Crafter template must have exactly " + SLOT_COUNT + " slots"),
            slots -> slots.size() == SLOT_COUNT
                    ? DataResult.success(slots)
                    : DataResult.error(() -> "Crafter template must have exactly " + SLOT_COUNT + " slots")
    );

    public static final Codec<CrafterTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, CURRENT_DATA_VERSION).fieldOf("data_version").forGetter(CrafterTemplate::dataVersion),
            NINE_SLOTS_CODEC.fieldOf("slots").forGetter(CrafterTemplate::slots),
            Identifier.CODEC.optionalFieldOf("recipe_id").forGetter(CrafterTemplate::recipeId),
            Identifier.CODEC.optionalFieldOf("recipe_type").forGetter(CrafterTemplate::recipeType)
    ).apply(instance, CrafterTemplate::new));

    public CrafterTemplate {
        if (dataVersion < 1 || dataVersion > CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported template data version: " + dataVersion);
        }
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(recipeType, "recipeType");
        if (slots.size() != SLOT_COUNT) {
            throw new IllegalArgumentException("Crafter template must have exactly " + SLOT_COUNT + " slots");
        }
        slots = List.copyOf(slots);
    }

    public static CrafterTemplate empty() {
        return new CrafterTemplate(
                CURRENT_DATA_VERSION,
                IntStream.range(0, SLOT_COUNT).mapToObj(ignored -> TemplateSlot.openEmpty()).toList(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public TemplateSlot slot(int index) {
        return slots.get(index);
    }

    public CrafterTemplate withSlot(int index, TemplateSlot slot) {
        Objects.requireNonNull(slot, "slot");
        ArrayList<TemplateSlot> updated = new ArrayList<>(slots);
        updated.set(index, slot);
        return new CrafterTemplate(dataVersion, updated, recipeId, recipeType);
    }

    public CrafterTemplate withRecipeMetadata(Optional<Identifier> newRecipeId, Optional<Identifier> newRecipeType) {
        return new CrafterTemplate(dataVersion, slots, newRecipeId, newRecipeType);
    }

    public Optional<String> sourceModId() {
        return recipeId.map(Identifier::getNamespace);
    }
}
