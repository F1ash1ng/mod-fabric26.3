package com.example.mobilitydevice.client;

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
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders the editable huachan2 pose with the actual player's 64x64 skin. */
public final class SlidePlayerRenderer {
    private static final String RESOURCE = "/assets/mobility_device/models/player/huachan2.bbmodel";

    private SlidePlayerRenderer() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (entityType != EntityTypes.PLAYER || !(renderer instanceof AvatarRenderer<?> avatar)) return;
            helper.register(new SlideLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) avatar));
        });
    }

    private static final class SlideLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final SlideModel model = loadModel();

        private SlideLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                           AvatarRenderState state, float limbSwing, float limbSwingAmount) {
            if (!((SlideRenderState) state).mobility$hasSlidePose()
                    || state.isInvisibleToPlayer || state.skin == null) return;
            renderColoredCutoutModel(model, state.skin.body().texturePath(),
                    pose, collector, light, state, -1, 0);
        }
    }

    private static SlideModel loadModel() {
        try (InputStream stream = SlidePlayerRenderer.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing slide model " + RESOURCE);
            JsonObject project = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, JsonObject> elements = new HashMap<>();
            for (JsonElement entry : project.getAsJsonArray("elements")) {
                JsonObject box = entry.getAsJsonObject();
                elements.put(box.get("uuid").getAsString(), box);
            }
            Map<String, JsonObject> groups = new HashMap<>();
            for (JsonElement entry : project.getAsJsonArray("groups")) {
                JsonObject group = entry.getAsJsonObject();
                groups.put(group.get("uuid").getAsString(), group);
            }

            Map<String, ModelPart> parts = new LinkedHashMap<>();
            for (JsonElement entry : project.getAsJsonArray("outliner")) {
                JsonObject outline = entry.getAsJsonObject();
                parts.put(outline.get("uuid").getAsString(),
                        createGroup(outline, groups, elements, 0, 0, 0));
            }
            ModelPart root = new ModelPart(List.of(), parts);
            return new SlideModel(root);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load huachan2 slide model", e);
        }
    }

    private static ModelPart createGroup(JsonObject outline, Map<String, JsonObject> groups,
                                         Map<String, JsonObject> elements,
                                         float parentX, float parentY, float parentZ) {
        String id = outline.get("uuid").getAsString();
        JsonObject group = groups.get(id);
        if (group == null) throw new IllegalStateException("Missing slide group " + id);
        JsonArray origin = group.getAsJsonArray("origin");
        float gx = component(origin, 0);
        float gy = 24 - component(origin, 1);
        float gz = component(origin, 2);
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (JsonElement child : outline.getAsJsonArray("children")) {
            if (child.isJsonObject()) {
                JsonObject nested = child.getAsJsonObject();
                children.put(nested.get("uuid").getAsString(),
                        createGroup(nested, groups, elements, gx, gy, gz));
            } else {
                String childId = child.getAsString();
                JsonObject box = elements.get(childId);
                if (box == null) throw new IllegalStateException("Missing slide cube " + childId);
                children.put(childId, createCube(box, gx, gy, gz));
            }
        }
        ModelPart part = new ModelPart(List.of(), children);
        part.setPos(gx - parentX, gy - parentY, gz - parentZ);
        setRotation(part, group.getAsJsonArray("rotation"));
        return part;
    }

    private static ModelPart createCube(JsonObject box, float gx, float gy, float gz) {
        JsonArray from = box.getAsJsonArray("from");
        JsonArray to = box.getAsJsonArray("to");
        JsonArray origin = box.getAsJsonArray("origin");
        float ox = component(origin, 0), oy = 24 - component(origin, 1), oz = component(origin, 2);
        float x = component(from, 0) - ox;
        float y = 24 - component(to, 1) - oy;
        float z = component(from, 2) - oz;
        float dx = component(to, 0) - component(from, 0);
        float dy = component(to, 1) - component(from, 1);
        float dz = component(to, 2) - component(from, 2);
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, y, z, dx, dy, dz,
                0, 0, 0, false, 64, 64, Set.of(Direction.values()));
        remapFaces(cube, box.getAsJsonObject("faces"));
        ModelPart part = new ModelPart(List.of(cube), Map.of());
        part.setPos(ox - gx, oy - gy, oz - gz);
        setRotation(part, box.getAsJsonArray("rotation"));
        return part;
    }

    private static void setRotation(ModelPart part, JsonArray degrees) {
        if (degrees == null) return;
        part.xRot = (float) Math.toRadians(-component(degrees, 0));
        part.yRot = (float) Math.toRadians(component(degrees, 1));
        part.zRot = (float) Math.toRadians(-component(degrees, 2));
    }

    private static float component(JsonArray array, int index) {
        return array.get(index).getAsFloat();
    }

    private static void remapFaces(ModelPart.Cube cube, JsonObject faces) {
        for (int faceIndex = 0; faceIndex < cube.polygons.length; faceIndex++) {
            ModelPart.Polygon original = cube.polygons[faceIndex];
            String name = faceName(original);
            JsonArray uv = faces.getAsJsonObject(name).getAsJsonArray("uv");
            float u0 = component(uv, 0), v0 = component(uv, 1);
            float u1 = component(uv, 2), v1 = component(uv, 3);
            ModelPart.Vertex[] old = original.vertices();
            float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (ModelPart.Vertex vertex : old) {
                minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v());
            }
            ModelPart.Vertex[] mapped = new ModelPart.Vertex[old.length];
            for (int i = 0; i < old.length; i++) {
                ModelPart.Vertex vertex = old[i];
                float u = maxU == minU ? 0 : (vertex.u() - minU) / (maxU - minU);
                float v = maxV == minV ? 0 : (vertex.v() - minV) / (maxV - minV);
                mapped[i] = vertex.remap((u0 + u * (u1 - u0)) / 64.0f,
                        (v0 + v * (v1 - v0)) / 64.0f);
            }
            cube.polygons[faceIndex] = new ModelPart.Polygon(mapped, original.normal());
        }
    }

    private static String faceName(ModelPart.Polygon polygon) {
        var normal = polygon.normal();
        if (normal.x() > 0.5f) return "east";
        if (normal.x() < -0.5f) return "west";
        if (normal.y() < -0.5f) return "up";
        if (normal.y() > 0.5f) return "down";
        return normal.z() < 0 ? "north" : "south";
    }

    private static final class SlideModel extends EntityModel<AvatarRenderState> {
        private SlideModel(ModelPart root) { super(root); }
    }
}
