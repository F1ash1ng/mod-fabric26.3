package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.ability.SlideAbility;
import com.example.mobilitydevice.ability.WallRunAbility;
import com.example.mobilitydevice.ability.KillStreak;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.network.ActivateDashPayload;
import com.example.mobilitydevice.network.ActivateSlidePayload;
import com.example.mobilitydevice.network.CreativeEquipPayload;
import com.example.mobilitydevice.network.DashStartedPayload;
import com.example.mobilitydevice.network.SlideStartedPayload;
import com.example.mobilitydevice.network.SlideJumpPayload;
import com.example.mobilitydevice.network.SlideStoppedPayload;
import com.example.mobilitydevice.network.WallRunControlPayload;
import com.example.mobilitydevice.network.WallRunStartedPayload;
import com.example.mobilitydevice.network.WallRunStoppedPayload;
import com.example.mobilitydevice.network.UpdateSettingsPayload;
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
		MobilitySettings.register();
		DashAbility.register();
		SlideAbility.register();
		WallRunAbility.register();
		KillStreak.register();
		PayloadTypeRegistry.serverboundPlay().register(ActivateDashPayload.TYPE, ActivateDashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActivateSlidePayload.TYPE, ActivateSlidePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SlideJumpPayload.TYPE, SlideJumpPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WallRunControlPayload.TYPE, WallRunControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreativeEquipPayload.TYPE, CreativeEquipPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DashStartedPayload.TYPE, DashStartedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SlideStartedPayload.TYPE, SlideStartedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SlideStoppedPayload.TYPE, SlideStoppedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WallRunStartedPayload.TYPE, WallRunStartedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WallRunStoppedPayload.TYPE, WallRunStoppedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ActivateDashPayload.TYPE,
				(payload, context) -> DashAbility.activate(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ActivateSlidePayload.TYPE,
				(payload, context) -> SlideAbility.activate(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SlideJumpPayload.TYPE,
				(payload, context) -> SlideAbility.interruptForJump(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(WallRunControlPayload.TYPE,
				(payload, context) -> WallRunAbility.control(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(CreativeEquipPayload.TYPE, (payload, context) -> {
			if (!context.player().hasInfiniteMaterials()) return;
			if (!payload.stack().isEmpty() && !payload.stack().is(WaistJetItems.WAIST_JET)) return;
			WaistJetItems.setEquipped(context.player(), payload.stack());
		});
		ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.TYPE, (payload, context) -> {
			if ((payload.flags() & ~MobilitySettings.ALL) != 0) return;
			MobilitySettings.set(context.player(), payload.flags());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DashAbility.cancel(handler.player);
			SlideAbility.cancel(handler.player);
			WallRunAbility.cancel(handler.player);
			KillStreak.reset(handler.player);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (victim instanceof ServerPlayer player) {
				DashAbility.cancel(player);
				SlideAbility.cancel(player);
				WallRunAbility.cancel(player);
				KillStreak.reset(player);
			}
			WaistJetItems.onDeath(victim);
			DashAbility.onKill(victim, source);
		});
		LOGGER.info("Mobility Device loaded");
	}
}
