package com.example.mobilitydevice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Applies the saved Blockbench pose to the real player bones, as a fixed animation frame. */
public final class SlidePlayerPose {
    private static final String RESOURCE = "/assets/mobility_device/models/player/huachan2.bbmodel";
    private static final Map<String, BonePose> POSES = loadPoses();

    private SlidePlayerPose() {}

    public static void validate() {
        if (POSES.size() != 6) throw new IllegalStateException("Incomplete huachan2 player pose");
    }

    public static void apply(PlayerModel model) {
        applyPair(model.head, model.hat, "head");
        applyPair(model.body, model.jacket, "torso");
        applyPair(model.rightArm, model.rightSleeve, "right_arm");
        applyPair(model.leftArm, model.leftSleeve, "left_arm");
        applyPair(model.rightLeg, model.rightPants, "right_leg");
        applyPair(model.leftLeg, model.leftPants, "left_leg");
        model.head.visible = true;
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
    }

    /** Uses the same six bone transforms for vanilla armor models. */
    public static void applyArmor(HumanoidModel<?> model) {
        POSES.get("head").apply(model.head);
        POSES.get("torso").apply(model.body);
        POSES.get("right_arm").apply(model.rightArm);
        POSES.get("left_arm").apply(model.leftArm);
        POSES.get("right_leg").apply(model.rightLeg);
        POSES.get("left_leg").apply(model.leftLeg);
    }

    public static ModelPart[] affectedParts(PlayerModel model) {
        return new ModelPart[] {
                model.head, model.hat, model.body, model.jacket,
                model.rightArm, model.rightSleeve, model.leftArm, model.leftSleeve,
                model.rightLeg, model.rightPants, model.leftLeg, model.leftPants
        };
    }

    private static void applyPair(ModelPart base, ModelPart overlay, String name) {
        BonePose pose = POSES.get(name);
        pose.apply(base);
        // Skin overlays are children of their base parts in PlayerModel's mesh.
        // Their local transform must stay neutral or the slide pose is applied twice.
        overlay.loadPose(PartPose.ZERO);
    }

    private static Map<String, BonePose> loadPoses() {
        try (InputStream stream = SlidePlayerPose.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing slide model " + RESOURCE);
            JsonObject project = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, JsonObject> byId = new HashMap<>();
            for (JsonElement entry : project.getAsJsonArray("groups")) {
                JsonObject group = entry.getAsJsonObject();
                byId.put(group.get("uuid").getAsString(), group);
            }
            Map<String, Joint> joints = new HashMap<>();
            for (JsonElement entry : project.getAsJsonArray("outliner")) {
                collect(entry.getAsJsonObject(), null, byId, joints);
            }
            Joint torso = require(joints, "torso");
            JsonObject body = null;
            for (JsonElement entry : project.getAsJsonArray("elements")) {
                JsonObject element = entry.getAsJsonObject();
                if ("body".equals(element.get("name").getAsString())) {
                    body = element;
                    break;
                }
            }
            if (body == null) throw new IllegalStateException("huachan2 has no body cube");
            JsonArray from = body.getAsJsonArray("from"), to = body.getAsJsonArray("to");
            // The Blockbench torso pivots at the waist. Vanilla PlayerModel.body
            // pivots at the shoulders, so use the top center of its body cube.
            float bodyX = (at(from, 0) + at(to, 0)) / 2;
            float bodyY = at(to, 1);
            float bodyZ = (at(from, 2) + at(to, 2)) / 2;
            Map<String, BonePose> poses = new HashMap<>();
            poses.put("torso", flatten(torso, bodyX, bodyY, bodyZ, 0));
            poses.put("head", flattenOrigin(require(joints, "head"), 0));
            // Vanilla arms have an asymmetrical cube offset around pivots at
            // +/-5, while the Blockbench arm cubes pivot at +/-6.
            poses.put("right_arm", flattenOrigin(require(joints, "right_arm"), 1));
            poses.put("left_arm", flattenOrigin(require(joints, "left_arm"), -1));
            poses.put("right_leg", flattenOrigin(require(joints, "right_leg"), 0));
            poses.put("left_leg", flattenOrigin(require(joints, "left_leg"), 0));
            return Map.copyOf(poses);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read huachan2 player bone pose", e);
        }
    }

    private static void collect(JsonObject node, Joint parent, Map<String, JsonObject> byId,
                                Map<String, Joint> joints) {
        String id = node.get("uuid").getAsString();
        JsonObject group = byId.get(id);
        if (group == null) throw new IllegalStateException("Missing slide group " + id);
        JsonArray origin = group.getAsJsonArray("origin");
        JsonArray rotation = group.getAsJsonArray("rotation");
        if (rotation != null && (at(rotation, 1) != 0 || at(rotation, 2) != 0)) {
            throw new IllegalStateException("huachan2 contains unsupported Y/Z bone rotation");
        }
        Joint joint = new Joint(parent, at(origin, 0), at(origin, 1), at(origin, 2),
                rotation == null ? 0 : at(rotation, 0));
        String name = group.get("name").getAsString();
        if (joints.put(name, joint) != null) throw new IllegalStateException("Duplicate slide group " + name);
        for (JsonElement child : node.getAsJsonArray("children")) {
            if (child.isJsonObject()) collect(child.getAsJsonObject(), joint, byId, joints);
        }
    }

    private static Joint require(Map<String, Joint> joints, String name) {
        Joint joint = joints.get(name);
        if (joint == null) throw new IllegalStateException("Missing slide bone " + name);
        return joint;
    }

    private static BonePose flattenOrigin(Joint joint, float xCorrection) {
        return flatten(joint, joint.x, joint.y, joint.z, xCorrection);
    }

    private static BonePose flatten(Joint joint, float x, float y, float z, float xCorrection) {
        float angle = 0;
        for (Joint current = joint; current != null; current = current.parent) {
            float radians = (float) Math.toRadians(current.xDegrees);
            float dy = y - current.y, dz = z - current.z;
            y = current.y + (float) (dy * Math.cos(radians) - dz * Math.sin(radians));
            z = current.z + (float) (dy * Math.sin(radians) + dz * Math.cos(radians));
            angle += current.xDegrees;
        }
        return new BonePose(x + xCorrection, 24 - y, z, (float) Math.toRadians(-angle));
    }

    private static float at(JsonArray values, int index) {
        return values.get(index).getAsFloat();
    }

    private record Joint(Joint parent, float x, float y, float z, float xDegrees) {}

    private record BonePose(float x, float y, float z, float xRot) {
        private void apply(ModelPart part) {
            part.setPos(x, y, z);
            part.setRotation(xRot, 0, 0);
        }
    }
}
