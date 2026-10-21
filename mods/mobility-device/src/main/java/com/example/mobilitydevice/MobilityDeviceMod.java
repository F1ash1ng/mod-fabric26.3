package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.network.ActivateDashPayload;
import com.example.mobilitydevice.network.CreativeEquipPayload;
import com.example.mobilitydevice.network.DashStartedPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobilityDeviceMod implements ModInitializer {
	public static final String MOD_ID = "mobility_device";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		WaistJetItems.register();
		DashAbility.register();
		PayloadTypeRegistry.serverboundPlay().register(ActivateDashPayload.TYPE, ActivateDashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreativeEquipPayload.TYPE, CreativeEquipPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DashStartedPayload.TYPE, DashStartedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActivateDashPayload.TYPE,
				(payload, context) -> DashAbility.activate(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(CreativeEquipPayload.TYPE, (payload, context) -> {
			if (!context.player().hasInfiniteMaterials()) return;
			if (!payload.stack().isEmpty() && !payload.stack().is(WaistJetItems.WAIST_JET)) return;
			WaistJetItems.setEquipped(context.player(), payload.stack());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DashAbility.cancel(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (victim instanceof ServerPlayer player) DashAbility.cancel(player);
			WaistJetItems.onDeath(victim);
			DashAbility.onKill(victim, source);
		});
		LOGGER.info("Mobility Device loaded");
	}
}
