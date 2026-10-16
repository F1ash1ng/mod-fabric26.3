package dev.smartcrafter;

import dev.smartcrafter.data.CrafterTemplateAttachments;
import dev.smartcrafter.network.CrafterUiNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartCrafterMod implements ModInitializer {
    public static final String MOD_ID = "smartcrafter";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CrafterTemplateAttachments.initialize();
        CrafterUiNetworking.initialize();
        LOGGER.info("Smart Crafter template data, editor networking, and template-aware hopper input are ready.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
