package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WaistJetRenderer {
    private static final String[] MODEL_NAMES = {
            // The approved V2 files appear left/right reversed when worn by the player.
            // Swap only the visual lookup; the actual dash direction remains unchanged.
            "idle", "forward", "forward_left", "left", "backward_left",
            "backward", "backward_right", "right", "forward_right"
    };
    private static final Identifier TEXTURE = MobilityDeviceMod.id("textures/entity/waist_jet.png");
    private static final float WAIST_RAISE_PIXELS = 1.0f;

    private WaistJetRenderer() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (entityType != EntityTypes.PLAYER || !(renderer instanceof AvatarRenderer<?> avatar)) return;
            helper.register(new WaistLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) avatar));
        });
    }

    private static final class WaistLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final WaistModel[] models = new WaistModel[MODEL_NAMES.length];

        private WaistLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
            for (int i = 0; i < models.length; i++) models[i] = loadModel(MODEL_NAMES[i]);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                           AvatarRenderState state, float limbSwing, float limbSwingAmount) {
            WaistJetRenderState jet = (WaistJetRenderState) state;
            if (!jet.mobility$hasWaistJet() || state.isInvisibleToPlayer
                    || ((SlideRenderState) state).mobility$hasSlidePose()) return;
            int direction = jet.mobility$waistJetDirection();
            int modelIndex = direction >= 0 && direction < 8 ? direction + 1 : 0;
            WallRunRenderState wallRun = (WallRunRenderState) state;
            if (wallRun.mobility$wallSide() != 0) {
                modelIndex = wallRunModelIndex(wallRun.mobility$wallSide(), wallRun.mobility$wallMovement());
            }
            pose.pushPose();
            if (wallRun.mobility$wallSide() != 0) WallRunPlayerPose.translateBody(pose, wallRun);
            else getParentModel().body.translateAndRotate(pose);
            renderColoredCutoutModel(models[modelIndex], TEXTURE, pose, collector, light, state, -1, 0);
            pose.popPose();
        }
    }

    public static int wallRunModelIndex(int side, int movement) {
        if (movement == 0 || side == 0) return 0;
        // Retain the established left/right correction for the worn V2 device.
        // Right wall: rear + LEFT port (forward), front + LEFT port (backward).
        // Left wall:  rear + RIGHT port (forward), front + RIGHT port (backward).
        if (side > 0) return movement > 0 ? 2 : 4;
        return movement > 0 ? 8 : 6;
    }

    private static WaistModel loadModel(String name) {
        String path = "/assets/mobility_device/models/waist_jet/waist_jet_" + name + ".bbmodel";
        try (InputStream stream = WaistJetRenderer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing model " + path);
            JsonObject project = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<ModelPart.Cube> cubes = new ArrayList<>();
            for (JsonElement element : project.getAsJsonArray("elements")) {
                JsonObject box = element.getAsJsonObject();
                if (!"cube".equals(box.get("type").getAsString())) continue;
                JsonArray from = box.getAsJsonArray("from");
                JsonArray to = box.getAsJsonArray("to");
                float x = from.get(0).getAsFloat(), z = from.get(2).getAsFloat();
                float dx = to.get(0).getAsFloat() - x;
                float dy = to.get(1).getAsFloat() - from.get(1).getAsFloat();
                float dz = to.get(2).getAsFloat() - z;
                // BBModel measures Y upward from the feet; PlayerModel measures Y
                // downward from the top. Raise the whole device one extra pixel
                // so even the side straps clear the top of the legs.
                float playerY = 24.0f - to.get(1).getAsFloat() - WAIST_RAISE_PIXELS;
                ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, playerY, z, dx, dy, dz,
                        0, 0, 0, false, 64, 16, Set.of(Direction.values()));
                JsonArray uv = box.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
                remapUvs(cube, uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        uv.get(2).getAsFloat(), uv.get(3).getAsFloat());
                cubes.add(cube);
            }
            return new WaistModel(new ModelPart(cubes, Map.of()));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load waist jet model " + name, e);
        }
    }

    private static void remapUvs(ModelPart.Cube cube, float u0, float v0, float u1, float v1) {
        for (int face = 0; face < cube.polygons.length; face++) {
            ModelPart.Polygon old = cube.polygons[face];
            ModelPart.Vertex[] original = old.vertices();
            float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (ModelPart.Vertex vertex : original) {
                minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v());
            }
            ModelPart.Vertex[] mapped = new ModelPart.Vertex[original.length];
            for (int i = 0; i < original.length; i++) {
                ModelPart.Vertex vertex = original[i];
                float u = maxU == minU ? 0 : (vertex.u() - minU) / (maxU - minU);
                float v = maxV == minV ? 0 : (vertex.v() - minV) / (maxV - minV);
                mapped[i] = vertex.remap((u0 + u * (u1 - u0)) / 64.0f,
                        (v0 + v * (v1 - v0)) / 16.0f);
            }
            cube.polygons[face] = new ModelPart.Polygon(mapped, old.normal());
        }
    }

    private static final class WaistModel extends EntityModel<AvatarRenderState> {
        private WaistModel(ModelPart root) { super(root); }
    }
}
