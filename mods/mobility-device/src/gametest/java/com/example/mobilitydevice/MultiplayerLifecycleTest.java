package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.DashAbility;
import com.example.mobilitydevice.ability.KillStreak;
import com.example.mobilitydevice.ability.SlideAbility;
import com.example.mobilitydevice.ability.WallRunVisuals;
import com.example.mobilitydevice.item.MobilitySettings;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.network.ActivateDashPayload;
import com.example.mobilitydevice.client.SlideRenderState;
import com.example.mobilitydevice.client.WaistJetRenderState;
import com.example.mobilitydevice.client.WallRunRenderState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import java.util.Properties;

/** Network and lifecycle checks against a real dedicated server. */
public final class MultiplayerLifecycleTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Properties settings = new Properties();
        settings.setProperty("max-players", "2");
        settings.setProperty("white-list", "false");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(settings)) {
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksDownload();
                if ("1".equals(System.getenv("MOBILITY_TWO_PLAYER_TEST"))) {
                    verifySecondPlayer(context, server, connection);
                }
                check(context.computeOnClient(client -> !WaistJetItems.isEquipped(client.player)),
                        "Device starts unequipped");

                // An unauthorised client packet cannot activate server movement.
                context.runOnClient(client -> ClientPlayNetworking.send(new ActivateDashPayload((byte) 1, (byte) 0, 0)));
                connection.waitForServerboundPackets();
                check(server.computeOnServer(s -> !DashAbility.isActive(connection.getServerPlayer())),
                        "Dedicated server must reject an unequipped dash");

                server.runOnServer(s -> {
                    var player = connection.getServerPlayer();
                    WaistJetItems.setEquipped(player, new ItemStack(WaistJetItems.WAIST_JET));
                    MobilitySettings.set(player, MobilitySettings.DEFAULT | MobilitySettings.MENDING_OFF);
                });
                context.waitFor(client -> WaistJetItems.isEquipped(client.player)
                        && !MobilitySettings.mendingEnabled(client.player));
                check(server.computeOnServer(s -> WaistJetItems.isEquipped(connection.getServerPlayer())),
                        "Equipment must exist on the dedicated server");

                server.runOnServer(s -> MobilitySettings.set(connection.getServerPlayer(),
                        MobilitySettings.SLIDE | MobilitySettings.WALL_RUN));
                context.waitFor(client -> !MobilitySettings.enabled(client.player, MobilitySettings.DASH));
                context.runOnClient(client -> ClientPlayNetworking.send(new ActivateDashPayload((byte) 1, (byte) 0, 0)));
                connection.waitForServerboundPackets();
                check(server.computeOnServer(s -> !DashAbility.isActive(connection.getServerPlayer())),
                        "Dedicated server must enforce the dash setting");

                server.runOnServer(s -> {
                    var player = connection.getServerPlayer();
                    MobilitySettings.set(player, MobilitySettings.DEFAULT);
                    player.setAttached(DashAbility.COOLDOWN_END, 0L);
                });
                context.waitFor(client -> MobilitySettings.enabled(client.player, MobilitySettings.DASH)
                        && MobilitySettings.mendingEnabled(client.player));
                context.runOnClient(client -> ClientPlayNetworking.send(new ActivateDashPayload((byte) 1, (byte) 0, 0)));
                server.waitFor(s -> connection.getServerPlayer().getAttachedOrElse(DashAbility.COOLDOWN_END, 0L) > 0);
                context.waitFor(client -> client.player.getAttachedOrElse(DashAbility.COOLDOWN_END, 0L) > 0);
                check(server.computeOnServer(s -> connection.getServerPlayer().getAttachedOrElse(
                        DashAbility.COOLDOWN_END, 0L) > 0), "Server must own cooldown");

                server.runOnServer(s -> {
                    var player = connection.getServerPlayer();
                    DashAbility.cancel(player);
                    KillStreak.award(player, 10);
                });
                context.waitFor(client -> KillStreak.count(client.player) == 1);
                check(server.computeOnServer(s -> KillStreak.count(connection.getServerPlayer()) == 1),
                        "Kill streak must be server-owned");
                server.runOnServer(s -> {
                    KillStreak.award(connection.getServerPlayer(), 10);
                    KillStreak.award(connection.getServerPlayer(), 10);
                });
                context.waitFor(client -> KillStreak.count(client.player) == 3);
                server.runOnServer(s -> connection.getServerPlayer().setAttached(KillStreak.EXPIRES_AT,
                        connection.getServerPlayer().level().getGameTime() + 1));
                context.waitFor(client -> KillStreak.count(client.player) == 0);

                server.runOnServer(s -> s.getGameRules().set(GameRules.KEEP_INVENTORY, true, s));
                server.runCommand("kill @p");
                context.waitFor(client -> !client.player.isAlive());
                check(server.computeOnServer(s -> KillStreak.count(connection.getServerPlayer()) == 0
                                && !DashAbility.isActive(connection.getServerPlayer())
                                && !SlideAbility.isActive(connection.getServerPlayer())
                                && connection.getServerPlayer().getAttachedOrElse(WallRunVisuals.STATE,
                                WallRunVisuals.OFF).side() == 0), "Death must clear transient abilities");
                context.clickScreenButton("Respawn");
                context.waitFor(client -> client.player.isAlive());
                context.waitFor(client -> WaistJetItems.isEquipped(client.player)
                        && MobilitySettings.enabled(client.player, MobilitySettings.DASH));
                check(server.computeOnServer(s -> KillStreak.count(connection.getServerPlayer()) == 0),
                        "Respawn must begin with a fresh streak");

                server.runOnServer(s -> {
                    var player = connection.getServerPlayer();
                    player.setAttached(DashAbility.COOLDOWN_END, 0L);
                    DashAbility.activate(player, new ActivateDashPayload((byte) 1, (byte) 0, 0));
                    check(DashAbility.isActive(player), "Dash must be active before dimension transfer");
                });
                server.runCommand("execute as @p in minecraft:the_nether run tp @s 0 80 0");
                context.waitFor(client -> client.level.dimension() == net.minecraft.world.level.Level.NETHER);
                server.waitFor(s -> !DashAbility.isActive(connection.getServerPlayer()));
                check(server.computeOnServer(s -> WaistJetItems.isEquipped(connection.getServerPlayer())
                                && KillStreak.count(connection.getServerPlayer()) == 0
                                && !connection.getServerPlayer().isNoGravity()
                                && connection.getServerPlayer().getAttachedOrElse(DashAbility.ACTIVE_DIRECTION, -1) == -1),
                        "Dimension change must retain equipment and clear active dash");
            }
            try (TestDedicatedServerConnection rejoined = server.connect()) {
                rejoined.waitForChunksDownload();
                context.waitFor(client -> WaistJetItems.isEquipped(client.player));
                check(server.computeOnServer(s -> WaistJetItems.isEquipped(rejoined.getServerPlayer())
                                && MobilitySettings.enabled(rejoined.getServerPlayer(), MobilitySettings.DASH)
                                && KillStreak.count(rejoined.getServerPlayer()) == 0
                                && !DashAbility.isActive(rejoined.getServerPlayer())),
                        "Relog must restore saved state without stale movement");

                server.runOnServer(s -> s.getGameRules().set(GameRules.KEEP_INVENTORY, false, s));
                server.runCommand("kill @p");
                context.waitFor(client -> !client.player.isAlive());
                check(server.computeOnServer(s -> !WaistJetItems.isEquipped(rejoined.getServerPlayer())),
                        "Death without keepInventory must drop the equipped device");
                context.clickScreenButton("Respawn");
                context.waitFor(client -> client.player.isAlive() && !WaistJetItems.isEquipped(client.player));
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void verifySecondPlayer(ClientGameTestContext context,
                                           TestDedicatedServerContext server,
                                           TestDedicatedServerConnection observer) {
        server.waitFor(s -> s.getPlayerList().getPlayers().size() == 2, 1200);
        server.runOnServer(s -> {
            ServerPlayer actor = s.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(observer.getServerPlayer().getUUID()))
                    .findFirst().orElseThrow();
            actor.level().setBlockAndUpdate(new BlockPos(4, -61, 0), Blocks.STONE.defaultBlockState());
            actor.teleportTo(4.5, -60, 0.5);
            WaistJetItems.setEquipped(actor, new ItemStack(WaistJetItems.WAIST_JET));
            actor.setAttached(DashAbility.ACTIVE_DIRECTION, 2);
        });
        context.waitFor(client -> remotePlayer(client) != null
                && WaistJetItems.isEquipped(remotePlayer(client))
                && remotePlayer(client).getAttachedOrElse(DashAbility.ACTIVE_DIRECTION, -1) == 2, 1200);
        context.runOnClient(client -> {
            AvatarRenderState state = remoteState(client);
            check(((WaistJetRenderState) state).mobility$hasWaistJet()
                    && ((WaistJetRenderState) state).mobility$waistJetDirection() == 2,
                    "Observer must render the other player's synced jet direction");
        });

        server.runOnServer(s -> {
            ServerPlayer actor = s.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(observer.getServerPlayer().getUUID()))
                    .findFirst().orElseThrow();
            actor.setPose(Pose.SWIMMING);
            actor.level().setBlockAndUpdate(new BlockPos(4, -59, 0), Blocks.STONE.defaultBlockState());
            actor.setAttached(DashAbility.ACTIVE_DIRECTION, -1);
            actor.setAttached(SlideAbility.POSTURE, true);
            actor.setAttached(SlideAbility.YAW, 45.0F);
        });
        context.waitFor(client -> remotePlayer(client).getAttachedOrElse(SlideAbility.POSTURE, false), 200);
        context.runOnClient(client -> check(((SlideRenderState) remoteState(client)).mobility$hasSlidePose(),
                "Observer must render the other player's synced slide posture"));

        server.runOnServer(s -> {
            ServerPlayer actor = s.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(observer.getServerPlayer().getUUID()))
                    .findFirst().orElseThrow();
            actor.level().setBlockAndUpdate(new BlockPos(4, -59, 0), Blocks.AIR.defaultBlockState());
            actor.setPose(Pose.STANDING);
            actor.setAttached(SlideAbility.POSTURE, false);
            actor.setAttached(WallRunVisuals.STATE,
                    new WallRunVisuals(0, 1, 1, 0, actor.level().getGameTime()));
        });
        context.waitFor(client -> remotePlayer(client).getAttachedOrElse(WallRunVisuals.STATE,
                WallRunVisuals.OFF).side() == 1, 200);
        context.runOnClient(client -> check(((WallRunRenderState) remoteState(client)).mobility$wallSide() == 1,
                "Observer must render the other player's synced wall-run posture"));
        server.runOnServer(s -> {
            ServerPlayer actor = s.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.getUUID().equals(observer.getServerPlayer().getUUID()))
                    .findFirst().orElseThrow();
            actor.setAttached(WallRunVisuals.STATE, WallRunVisuals.OFF);
        });
        context.waitFor(client -> remotePlayer(client).getAttachedOrElse(WallRunVisuals.STATE,
                WallRunVisuals.OFF).side() == 0, 200);
    }

    private static RemotePlayer remotePlayer(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        return client.level.players().stream()
                .filter(player -> player instanceof RemotePlayer && player != client.player)
                .map(player -> (RemotePlayer) player).findFirst().orElse(null);
    }

    private static AvatarRenderState remoteState(Minecraft client) {
        RemotePlayer remote = remotePlayer(client);
        check(remote != null, "Second player must be a remote avatar");
        return (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(remote)
                .createRenderState(remote, 1);
    }
}
