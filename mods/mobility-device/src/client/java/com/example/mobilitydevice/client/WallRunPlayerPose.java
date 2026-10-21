package com.example.mobilitydevice.client;

import com.example.mobilitydevice.mixin.ModelPartAccessor;
import com.example.mobilitydevice.mixin.ModelPartsAccessor;
import com.example.mobilitydevice.mixin.PlayerModelAccessor;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Drives the real player parts; segmented skin/armor meshes are children of those same parts. */
public final class WallRunPlayerPose {
    // Saved authoring sides are opposite to the side seen by the in-game player.
    private static final Rig LEFT = load("wall_left");
    private static final Rig RIGHT = load("wall_right");
    private static final Map<HumanoidModel<?>, Snapshot> SAVED = new WeakHashMap<>();
    private static final Map<HumanoidModel<?>, Map<String, Instance>> CACHE = new WeakHashMap<>();
    private static final String[] ROOTS = {"head", "torso", "right_upper_arm", "left_upper_arm", "right_thigh", "left_thigh"};
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};
    private static final Map<String, float[][]> UV = Map.of(
            "head", rects(8,8,16,16, 0,8,8,16, 24,8,32,16, 16,8,24,16, 8,0,16,8, 16,0,24,8),
            "torso", rects(20,20,28,32, 16,20,20,32, 32,20,40,32, 28,20,32,32, 20,16,28,20, 28,16,36,20),
            "right_arm", rects(44,20,48,32, 40,20,44,32, 52,20,56,32, 48,20,52,32, 44,16,48,20, 48,16,52,20),
            "left_arm", rects(36,52,40,64, 32,52,36,64, 44,52,48,64, 40,52,44,64, 36,48,40,52, 40,48,44,52),
            "right_leg", rects(4,20,8,32, 0,20,4,32, 12,20,16,32, 8,20,12,32, 4,16,8,20, 8,16,12,20),
            "left_leg", rects(20,52,24,64, 16,52,20,64, 28,52,32,64, 24,52,28,64, 20,48,24,52, 24,48,28,52));
    private static final Map<String, float[][]> SLIM = Map.of(
            "right_arm", rects(44,20,47,32, 40,20,44,32, 51,20,54,32, 47,20,51,32, 44,16,47,20, 47,16,50,20),
            "left_arm", rects(36,52,39,64, 32,52,36,64, 43,52,46,64, 39,52,43,64, 36,48,39,52, 39,48,42,52));

    private WallRunPlayerPose() {}

    public static void validate() {
        for (Rig rig : List.of(LEFT, RIGHT)) {
            for (String name : ROOTS) rig.require(name);
            for (String name : List.of("left_forearm", "right_forearm", "left_shin", "right_shin")) rig.require(name);
            for (float t = 0; t < rig.length; t += .01F) {
                for (Matrix4f matrix : rig.evaluate(t, false).values()) {
                    if (!matrix.isFinite()) throw new IllegalStateException("Non-finite wall run pose");
                }
            }
        }
    }

    /** Called before vanilla resets the shared model, including when wall running has ended. */
    public static void restore(HumanoidModel<?> model) {
        Snapshot snapshot = SAVED.remove(model);
        if (snapshot != null) snapshot.restore(model);
    }

    public static void apply(PlayerModel model, AvatarRenderState state) {
        WallRunRenderState run = (WallRunRenderState) state;
        boolean armor = ((WallRunPlayerModelKind) model).mobility$isArmorModel();
        boolean slim = armor ? state.skin.model() == PlayerModelType.SLIM : ((PlayerModelAccessor) model).mobility$slim();
        apply(model, state, armor, slim, run);
    }

    public static void applyArmor(HumanoidModel<?> model, AvatarRenderState state) {
        apply(model, state, true, state.skin.model() == PlayerModelType.SLIM, (WallRunRenderState) state);
    }

    private static Rig rigForSide(int side) { return side < 0 ? RIGHT : LEFT; }

    private static void apply(HumanoidModel<?> model, AvatarRenderState state, boolean armor,
                              boolean slim, WallRunRenderState run) {
        if (run.mobility$wallSide() == 0) return;
        Rig rig = rigForSide(run.mobility$wallSide());
        Snapshot snapshot = new Snapshot(model);
        SAVED.put(model, snapshot);
        String key = (rig == LEFT ? "left" : "right") + (armor ? "armor" : "skin") + (slim ? "slim" : "classic");
        Instance instance = CACHE.computeIfAbsent(model, ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new Instance(model, rig, armor, slim));
        instance.apply(model, run.mobility$wallAnimationSeconds());
        ((ModelPartsAccessor) model).mobility$allParts(model.root().getAllParts());
    }

    /** Held items follow the hand after the forearm bend, rather than a rigid vanilla arm. */
    public static void translateToHand(PlayerModel model, HumanoidArm arm, PoseStack pose) {
        model.root().translateAndRotate(pose);
        ModelPart upper = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        upper.translateAndRotate(pose);
        Map<String, ModelPart> children = access(upper).mobility$children();
        ModelPart geometry = children.get("mobility_wallrun");
        if (geometry == null) return;
        geometry.translateAndRotate(pose);
        String name = arm == HumanoidArm.RIGHT ? "right_forearm" : "left_forearm";
        ModelPart forearm = access(geometry).mobility$children().get(name);
        if (forearm != null) {
            forearm.translateAndRotate(pose);
            // Vanilla ItemInHandLayer adds its own 10-pixel downward offset.
            pose.translate(0, -4.0F / 16, 0);
        }
    }

    public static void translateBody(PoseStack pose, WallRunRenderState state) {
        Rig rig=rigForSide(state.mobility$wallSide());
        Bone torso=rig.require("torso");
        JsonObject box=rig.elements.get(torso.cubes.stream().filter(id->rig.elements.get(id).get("name").getAsString().endsWith("_skin")).findFirst().orElseThrow());
        Vector3f from=vector(box.getAsJsonArray("from")),to=vector(box.getAsJsonArray("to"));
        Vector3f point=new Vector3f((from.x+to.x)/2,to.y,(from.z+to.z)/2).sub(torso.origin);
        Matrix4f matrix=rig.evaluate(state.mobility$wallAnimationSeconds(),false).get(torso);
        matrix.transformPosition(point);
        pose.translate(point.x/16,(24-point.y)/16,point.z/16);
        Quaternionf q=matrix.getNormalizedRotation(new Quaternionf());
        pose.mulPose(new Matrix4f().rotation(new Quaternionf(-q.x,q.y,-q.z,q.w)));
    }

    private static ModelPartAccessor access(ModelPart part) { return (ModelPartAccessor) (Object) part; }

    private record PartSaved(ModelPart part, PartPose pose, boolean visible, boolean skipDraw,
                             List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        PartSaved(ModelPart part) {
            this(part, part.storePose(), part.visible, part.skipDraw,
                    access(part).mobility$cubes(), access(part).mobility$children());
        }
        void restore() {
            part.loadPose(pose); part.visible = visible; part.skipDraw = skipDraw;
            access(part).mobility$cubes(cubes); access(part).mobility$children(children);
        }
    }

    private record Snapshot(List<ModelPart> originalParts, List<PartSaved> parts, List<Runnable> compatRestores) {
        Snapshot(HumanoidModel<?> model) {
            this(model.allParts(), model.root().getAllParts().stream().map(PartSaved::new).toList(),new ArrayList<>());
        }
        void restore(HumanoidModel<?> model) {
            compatRestores.forEach(Runnable::run);
            parts.forEach(PartSaved::restore);
            ((ModelPartsAccessor) model).mobility$allParts(originalParts);
        }
    }

    private static ModelPart[] canonical(HumanoidModel<?> model) {
        return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    private static final class Instance {
        final Rig rig;
        final boolean slim;
        final List<Binding> bindings = new ArrayList<>();
        Instance(HumanoidModel<?> model, Rig rig, boolean armor, boolean slim) {
            this.rig = rig; this.slim = slim;
            ModelPart[] bases = canonical(model);
            ModelPart[] overlays = !armor && model instanceof PlayerModel p
                    ? new ModelPart[]{p.hat,p.jacket,p.rightSleeve,p.leftSleeve,p.rightPants,p.leftPants} : null;
            for (int i = 0; i < ROOTS.length; i++) {
                List<ModelPart.Cube> originals = access(bases[i]).mobility$cubes();
                // ArmorModelSet has slot-specific empty parts. Never invent armor in another slot.
                if (armor && originals.isEmpty()) continue;
                Bone bone = rig.require(ROOTS[i]);
                Vector3f anchor = new Vector3f(bone.origin);
                if (i == 1) {
                    JsonObject cube = rig.elements.get(bone.cubes.stream()
                            .filter(id -> rig.elements.get(id).get("name").getAsString().endsWith("_skin"))
                            .findFirst().orElseThrow());
                    Vector3f from = vector(cube.getAsJsonArray("from")), to = vector(cube.getAsJsonArray("to"));
                    anchor.set((from.x+to.x)/2, to.y, (from.z+to.z)/2);
                }
                ModelPart.Cube armorCube = armor ? originals.getFirst() : null;
                bindings.add(new Binding(bases[i], bone, anchor, false, armorCube));
                if (overlays != null) bindings.add(new Binding(overlays[i], bone, anchor, true, null));
                // Armor has a helmet overlay too. Its original rigid cube must use the same saved head geometry.
                if (armor && i == 0 && !access(model.hat).mobility$cubes().isEmpty())
                    bindings.add(new Binding(model.hat, bone, anchor, true, access(model.hat).mobility$cubes().getFirst()));
            }
        }

        void apply(HumanoidModel<?> model, float seconds) {
            Map<Bone, Matrix4f> world = rig.evaluate(seconds, slim);
            for (Binding binding : bindings) {
                binding.apply(world, seconds);
            }
            // Armor is also PlayerModel. Suppress every optional voxel layer, including its empty overlays.
            if (model instanceof PlayerModel p && SkinLayersWallRunCompatibility.isLoaded())
                for (ModelPart part : new ModelPart[]{p.hat,p.jacket,p.rightSleeve,p.leftSleeve,p.rightPants,p.leftPants})
                    SAVED.get(model).compatRestores.add(SkinLayersWallRunCompatibility.suppress(part));
        }

        final class Binding {
            final ModelPart target, geometry;
            final List<ModelPart.Cube> rootCubes;
            final Bone bone;
            final Vector3f anchor;
            final boolean overlay;
            final Map<Bone, ModelPart> animated = new HashMap<>();
            final Map<String, ModelPart> originalChildren;

            Binding(ModelPart target, Bone bone, Vector3f anchor, boolean overlay, ModelPart.Cube armorCube) {
                this.target=target; this.bone=bone; this.anchor=anchor; this.overlay=overlay;
                originalChildren = access(target).mobility$children();
                boolean outer = overlay && armorCube == null;
                geometry = mesh(bone, outer, armorCube, animated);
                Vector3f difference = new Vector3f(bone.origin).sub(anchor);
                geometry.setPos(difference.x, -difference.y, difference.z);
                geometry.setRotation(0,0,0);
                animated.remove(bone); // The canonical player part already owns the root rotation.
                // Vanilla stuck-arrow/stinger layers choose a cube on the real body part.
                // Keep the upper cube there instead of making the canonical part empty.
                String rootId=bone.cubes.stream().filter(id->rig.elements.get(id).get("name").getAsString().endsWith(outer?"_outer":"_skin")).findFirst().orElseThrow();
                Map<String,ModelPart> meshChildren=new LinkedHashMap<>(access(geometry).mobility$children());
                ModelPart shape=meshChildren.remove(rootId);
                Matrix4f transform=partMatrix(geometry).mul(partMatrix(shape));
                rootCubes=access(shape).mobility$cubes().stream().map(cube->transformCube(cube,transform)).toList();
                access(geometry).mobility$children(meshChildren);
            }

            ModelPart mesh(Bone current, boolean outer, ModelPart.Cube armorCube, Map<Bone, ModelPart> joints) {
                Map<String, ModelPart> children = new LinkedHashMap<>();
                for (String id : current.cubes) {
                    JsonObject element = rig.elements.get(id);
                    String name = element.get("name").getAsString();
                    if (name.endsWith("_outer") != outer) continue;
                    if (element.has("export") && !element.get("export").getAsBoolean()) continue;
                    JsonObject cover=armorCube==null?null:current.cubes.stream().map(rig.elements::get)
                            .filter(e->e.get("name").getAsString().endsWith("_outer")).findFirst().orElse(null);
                    children.put(id, makeCube(element, current, slim, armorCube,cover));
                }
                for (Bone child : current.children) {
                    // Head and upper arms are already bound to their own vanilla parts.
                    // Only elbow/knee sub-bones belong in this part's mesh subtree.
                    if (Arrays.asList(ROOTS).contains(child.name)) continue;
                    children.put(child.name, mesh(child, outer, armorCube, joints));
                }
                ModelPart part = new ModelPart(List.of(), children);
                joints.put(current, part);
                return part;
            }

            void apply(Map<Bone, Matrix4f> world, float seconds) {
                if (overlay) {
                    // Existing overlay objects remain children of the real base parts, exactly once.
                    target.loadPose(PartPose.ZERO);
                } else {
                    Vector3f point = new Vector3f(anchor).sub(bone.origin);
                    world.get(bone).transformPosition(point);
                    target.setPos(point.x, 24-point.y, point.z);
                    Quaternionf q = world.get(bone).getNormalizedRotation(new Quaternionf());
                    Vector3f angles = new Quaternionf(-q.x,q.y,-q.z,q.w).getEulerAnglesZYX(new Vector3f());
                    target.setRotation(angles.x,angles.y,angles.z);
                    target.xScale=target.yScale=target.zScale=1;
                }
                access(target).mobility$cubes(rootCubes);
                Map<String,ModelPart> children = new LinkedHashMap<>(originalChildren);
                children.put("mobility_wallrun", geometry);
                access(target).mobility$children(children);
                for (var entry : animated.entrySet()) {
                    Bone joint = entry.getKey(); ModelPart part = entry.getValue();
                    Vector3f offset = origin(joint,slim).sub(origin(joint.parent,slim))
                            .add(joint.channel("position",seconds,rig.length));
                    part.setPos(offset.x,-offset.y,offset.z);
                    Vector3f rotation = new Vector3f(joint.rotation).add(joint.channel("rotation",seconds,rig.length));
                    part.setRotation(rad(-rotation.x),rad(rotation.y),rad(-rotation.z));
                }
            }

        }
    }

    private static Matrix4f partMatrix(ModelPart part) {
        return new Matrix4f().translation(part.x,part.y,part.z).rotateZYX(part.zRot,part.yRot,part.xRot);
    }

    private static ModelPart.Cube transformCube(ModelPart.Cube source,Matrix4f matrix) {
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(float x:new float[]{source.minX,source.maxX})for(float y:new float[]{source.minY,source.maxY})for(float z:new float[]{source.minZ,source.maxZ}) {
            Vector3f point=matrix.transformPosition(new Vector3f(x,y,z));min.min(point);max.max(point);
        }
        ModelPart.Cube cube=new ModelPart.Cube(0,0,min.x,min.y,min.z,max.x-min.x,max.y-min.y,max.z-min.z,0,0,0,false,64,64,Set.of(Direction.values()));
        for(int i=0;i<source.polygons.length;i++) {
            ModelPart.Polygon face=source.polygons[i];ModelPart.Vertex[] vertices=new ModelPart.Vertex[face.vertices().length];
            for(int j=0;j<vertices.length;j++) {
                ModelPart.Vertex v=face.vertices()[j];Vector3f p=matrix.transformPosition(new Vector3f(v.x(),v.y(),v.z()));
                vertices[j]=new ModelPart.Vertex(p.x,p.y,p.z,v.u(),v.v());
            }
            Vector3f normal=matrix.transformDirection(new Vector3f(face.normal())).normalize();
            cube.polygons[i]=new ModelPart.Polygon(vertices,normal);
        }
        return cube;
    }

    private static ModelPart makeCube(JsonObject element, Bone bone, boolean slim, ModelPart.Cube armor) {
        return makeCube(element,bone,slim,armor,null);
    }

    private static ModelPart makeCube(JsonObject element, Bone bone, boolean slim, ModelPart.Cube armor,JsonObject cover) {
        Vector3f from=vector(element.getAsJsonArray("from")), to=vector(element.getAsJsonArray("to"));
        Vector3f pivot=element.has("origin") ? vector(element.getAsJsonArray("origin")) : new Vector3f(bone.origin);
        if (slim && bone.name.contains("arm")) {
            float shift = bone.name.startsWith("left") ? -.5F : .5F;
            from.x=bone.origin.x+(from.x-bone.origin.x)*.75F+shift;
            to.x=bone.origin.x+(to.x-bone.origin.x)*.75F+shift;
            pivot.x=bone.origin.x+(pivot.x-bone.origin.x)*.75F+shift;
        }
        float inflation=0;
        if (armor != null) {
            float min=Float.POSITIVE_INFINITY;
            for (ModelPart.Polygon face : armor.polygons) for (ModelPart.Vertex vertex : face.vertices()) min=Math.min(min,vertex.x());
            inflation=Math.max(0,armor.minX-min);
        }
        Vector3f minBox=new Vector3f(from.x-pivot.x,pivot.y-to.y,from.z-pivot.z).sub(inflation,inflation,inflation);
        Vector3f maxBox=new Vector3f(to.x-pivot.x,pivot.y-from.y,to.z-pivot.z).add(inflation,inflation,inflation);
        if(cover!=null) {
            ModelPart outer=makeCube(cover,bone,slim,null);
            Vector3f r=vector(element.getAsJsonArray("rotation")),offset=new Vector3f(pivot).sub(origin(bone,slim));
            Matrix4f toSkin=new Matrix4f().translation(offset.x,-offset.y,offset.z)
                    .rotateZYX(rad(-r.z),rad(r.y),rad(-r.x)).invert().mul(partMatrix(outer));
            ModelPart.Cube box=access(outer).mobility$cubes().getFirst();
            // Wall running uses flat overlays, including independently edited pivots/bounds.
            float clearance=.06F;
            for(float x:new float[]{box.minX-clearance,box.maxX+clearance})
                for(float y:new float[]{box.minY-clearance,box.maxY+clearance})
                    for(float z:new float[]{box.minZ-clearance,box.maxZ+clearance}) {
                        Vector3f p=toSkin.transformPosition(new Vector3f(x,y,z));minBox.min(p);maxBox.max(p);
                    }
        }
        ModelPart.Cube cube = new ModelPart.Cube(0,0,minBox.x,minBox.y,minBox.z,
                maxBox.x-minBox.x,maxBox.y-minBox.y,maxBox.z-minBox.z,0,0,0,false,
                64,armor==null ? 64 : 32,Set.of(Direction.values()));
        JsonObject faces=element.getAsJsonObject("faces");
        String fullPart=skinPart(bone.name);
        for (int i=0;i<cube.polygons.length;i++) {
            ModelPart.Polygon polygon=cube.polygons[i]; String faceName=faceName(polygon);
            int face=Arrays.asList(FACE_NAMES).indexOf(faceName);
            JsonObject faceData=faces.getAsJsonObject(faceName);
            float[] uv=array(faceData.getAsJsonArray("uv"));
            if (armor != null) {
                int sourceFace=inside(uv,UV.get(fullPart)[face])?face:0;
                String armorFace=FACE_NAMES[sourceFace];
                ModelPart.Polygon original=Arrays.stream(armor.polygons).filter(p->faceName(p).equals(armorFace)).findFirst().orElseThrow();
                float[] armorUv=uvBounds(original,64,32);
                if (fullPart.endsWith("_arm")) {
                    // Preserve the original mirrored sleeve's spatial UV orientation, not just its bounds.
                    float[] crop=mapRect(uv,UV.get(fullPart)[sourceFace],new float[]{0,0,1,1});
                    remapArmorArm(cube,i,crop,armorFace,original,armor);
                    continue;
                }
                // Source skin UV selects the upper/lower limb slice; armor uses its own UV layout.
                uv=mapRect(uv,UV.get(fullPart)[sourceFace],armorUv);
            } else if (slim && bone.name.contains("arm")) {
                float[] oldRect=UV.get(fullPart)[face], newRect=SLIM.get(fullPart)[face];
                if (element.get("name").getAsString().endsWith("_outer")) {
                    oldRect=oldRect.clone();newRect=newRect.clone();
                    float du=fullPart.startsWith("right") ? 0 : 16;
                    float dv=fullPart.startsWith("right") ? 16 : 0;
                    oldRect[0]+=du;oldRect[2]+=du;oldRect[1]+=dv;oldRect[3]+=dv;
                    newRect[0]+=du;newRect[2]+=du;newRect[1]+=dv;newRect[3]+=dv;
                }
                if (!inside(uv,oldRect)) {
                    oldRect=UV.get(fullPart)[0].clone();newRect=SLIM.get(fullPart)[0].clone();
                    if (element.get("name").getAsString().endsWith("_outer")) {
                        float du=fullPart.startsWith("right")?0:16,dv=fullPart.startsWith("right")?16:0;
                        oldRect[0]+=du;oldRect[2]+=du;oldRect[1]+=dv;oldRect[3]+=dv;
                        newRect[0]+=du;newRect[2]+=du;newRect[1]+=dv;newRect[3]+=dv;
                    }
                }
                uv=mapRect(uv,oldRect,newRect);
            }
            remap(cube,i,uv,armor==null?64:32,faceData.has("rotation")?faceData.get("rotation").getAsInt():0);
        }
        ModelPart part=new ModelPart(List.of(cube),Map.of());
        Vector3f offset=new Vector3f(pivot).sub(origin(bone,slim));
        part.setPos(offset.x,-offset.y,offset.z);
        Vector3f rotation=vector(element.getAsJsonArray("rotation"));
        part.setRotation(rad(-rotation.x),rad(rotation.y),rad(-rotation.z));
        return part;
    }

    private static String skinPart(String name) {
        if (name.equals("head") || name.equals("torso")) return name;
        return (name.startsWith("left")?"left":"right")+(name.contains("arm")?"_arm":"_leg");
    }

    private static void remapArmorArm(ModelPart.Cube cube,int index,float[] crop,String face,
                                      ModelPart.Polygon reference,ModelPart.Cube template) {
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(ModelPart.Polygon polygon:template.polygons)for(ModelPart.Vertex v:polygon.vertices()) {
            Vector3f p=new Vector3f(v.x(),v.y(),v.z());min.min(p);max.max(p);
        }
        ModelPart.Vertex[] q=reference.vertices();
        Vector3f p0=new Vector3f(q[0].x(),q[0].y(),q[0].z());
        Vector3f e1=new Vector3f(q[1].x(),q[1].y(),q[1].z()).sub(p0);
        Vector3f e3=new Vector3f(q[3].x(),q[3].y(),q[3].z()).sub(p0);
        ModelPart.Polygon old=cube.polygons[index];float[] bounds=uvBounds(old,1,1);
        ModelPart.Vertex[] vertices=new ModelPart.Vertex[old.vertices().length];
        for(int i=0;i<vertices.length;i++) {
            ModelPart.Vertex v=old.vertices()[i];
            float u=crop[0]+(v.u()-bounds[0])/(bounds[2]-bounds[0])*(crop[2]-crop[0]);
            float w=crop[1]+(v.v()-bounds[1])/(bounds[3]-bounds[1])*(crop[3]-crop[1]);
            Vector3f f=switch(face) {
                case "north" -> new Vector3f(u,w,0);
                case "south" -> new Vector3f(1-u,w,1);
                case "east" -> new Vector3f(1,w,u);
                case "west" -> new Vector3f(0,w,1-u);
                case "up" -> new Vector3f(u,0,1-w);
                default -> new Vector3f(u,1,1-w);
            };
            Vector3f point=f.mul(new Vector3f(max).sub(min)).add(min).sub(p0);
            float s=point.dot(e1)/e1.lengthSquared(),t=point.dot(e3)/e3.lengthSquared();
            vertices[i]=v.remap(q[0].u()+s*(q[1].u()-q[0].u())+t*(q[3].u()-q[0].u()),
                    q[0].v()+s*(q[1].v()-q[0].v())+t*(q[3].v()-q[0].v()));
        }
        cube.polygons[index]=new ModelPart.Polygon(vertices,old.normal());
    }

    private static void remap(ModelPart.Cube cube,int index,float[] uv,float textureHeight,int rotation) {
        ModelPart.Polygon old=cube.polygons[index];
        float[] bounds=uvBounds(old,1,1);
        ModelPart.Vertex[] vertices=new ModelPart.Vertex[old.vertices().length];
        for (int v=0;v<vertices.length;v++) {
            ModelPart.Vertex vertex=old.vertices()[v];
            float u=(vertex.u()-bounds[0])/(bounds[2]-bounds[0]);
            float w=(vertex.v()-bounds[1])/(bounds[3]-bounds[1]);
            for (int r=0;r<Math.floorMod(rotation,360)/90;r++) {float previous=u;u=w;w=1-previous;}
            vertices[v]=vertex.remap((uv[0]+u*(uv[2]-uv[0]))/64,(uv[1]+w*(uv[3]-uv[1]))/textureHeight);
        }
        cube.polygons[index]=new ModelPart.Polygon(vertices,old.normal());
    }

    private static float[] uvBounds(ModelPart.Polygon face,float width,float height) {
        float u0=Float.POSITIVE_INFINITY,v0=u0,u1=Float.NEGATIVE_INFINITY,v1=u1;
        for (ModelPart.Vertex v:face.vertices()) {u0=Math.min(u0,v.u()*width);u1=Math.max(u1,v.u()*width);v0=Math.min(v0,v.v()*height);v1=Math.max(v1,v.v()*height);}
        return new float[]{u0,v0,u1,v1};
    }
    private static String faceName(ModelPart.Polygon polygon) {
        Vector3fc n=polygon.normal();
        if(n.x()>.5F)return "east"; if(n.x()<-.5F)return "west";
        if(n.y()<-.5F)return "up"; if(n.y()>.5F)return "down";
        return n.z()<0?"north":"south";
    }
    private static float[] mapRect(float[] uv,float[] from,float[] to) {
        float[] result=new float[4];
        for(int i=0;i<4;i++) {int axis=i%2;result[i]=to[axis]+(uv[i]-from[axis])/(from[axis+2]-from[axis])*(to[axis+2]-to[axis]);}
        return result;
    }
    private static boolean inside(float[] rect,float[] full) {
        return rect[0]>=full[0]-.001F && rect[1]>=full[1]-.001F && rect[2]<=full[2]+.001F && rect[3]<=full[3]+.001F;
    }
    private static float[][] rects(float... numbers) {float[][] result=new float[6][4];for(int i=0;i<6;i++)System.arraycopy(numbers,i*4,result[i],0,4);return result;}
    private static float rad(float degrees) {return (float)Math.toRadians(degrees);}
    private static Vector3f vector(JsonArray a) {return a==null?new Vector3f():new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    private static float[] array(JsonArray a) {return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat(),a.get(3).getAsFloat()};}
    private static Vector3f origin(Bone bone,boolean slim) {
        Vector3f result=new Vector3f(bone.origin);
        if(slim&&bone.name.contains("arm"))result.x+=bone.name.startsWith("left")?-.5F:.5F;
        return result;
    }

    private record Key(float time,Vector3f value,String interpolation) {}
    private static final class Bone {
        final String id,name; final Vector3f origin,rotation;
        Bone parent;
        final List<Bone> children=new ArrayList<>(); final List<String> cubes=new ArrayList<>();
        final Map<String,List<Key>> channels=new HashMap<>();
        Bone(JsonObject json) {id=json.get("uuid").getAsString();name=json.get("name").getAsString();origin=vector(json.getAsJsonArray("origin"));rotation=vector(json.getAsJsonArray("rotation"));}
        Vector3f channel(String name,float seconds,float length) {
            List<Key> keys=channels.get(name);if(keys==null||keys.isEmpty())return new Vector3f();
            float t=(seconds%length+length)%length;
            Key before=keys.getFirst(),after=keys.getLast();
            for(Key key:keys) {if(key.time<=t)before=key;if(key.time>=t){after=key;break;}}
            float alpha=after.time>before.time?(t-before.time)/(after.time-before.time):0;
            return new Vector3f(before.value).lerp(after.value,before.interpolation.equals("step")?0:alpha);
        }
    }
    private record Rig(Map<String,Bone> bones,Map<String,JsonObject> elements,List<Bone> roots,float length) {
        Bone require(String name) {return Objects.requireNonNull(bones.get(name),"Missing wall run bone "+name);}
        Map<Bone,Matrix4f> evaluate(float seconds,boolean slim) {
            Map<Bone,Matrix4f> result=new HashMap<>();for(Bone root:roots)evaluate(root,new Matrix4f(),seconds,slim,result);return result;
        }
        void evaluate(Bone bone,Matrix4f parent,float seconds,boolean slim,Map<Bone,Matrix4f> result) {
            Vector3f position=origin(bone,slim);
            if(bone.parent!=null)position.sub(origin(bone.parent,slim));
            position.add(bone.channel("position",seconds,length));
            Vector3f angles=new Vector3f(bone.rotation).add(bone.channel("rotation",seconds,length));
            Matrix4f matrix=new Matrix4f(parent).translate(position).rotateZYX(rad(angles.z),rad(angles.y),rad(angles.x));
            result.put(bone,matrix);for(Bone child:bone.children)evaluate(child,matrix,seconds,slim,result);
        }
    }
    private static Rig load(String file) {
        String path="/assets/mobility_device/models/player/"+file+".bbmodel";
        try(var stream=WallRunPlayerPose.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalStateException("Missing "+path);
            JsonObject json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,Bone> ids=new HashMap<>(),names=new LinkedHashMap<>();
            for(JsonElement e:json.getAsJsonArray("groups")){Bone b=new Bone(e.getAsJsonObject());ids.put(b.id,b);names.put(b.name,b);}
            Map<String,JsonObject> elements=new HashMap<>();
            for(JsonElement e:json.getAsJsonArray("elements")){JsonObject cube=e.getAsJsonObject();elements.put(cube.get("uuid").getAsString(),cube);}
            List<Bone> roots=new ArrayList<>();
            for(JsonElement node:json.getAsJsonArray("outliner"))roots.add(outline(node.getAsJsonObject(),null,ids));
            JsonObject animation=json.getAsJsonArray("animations").get(0).getAsJsonObject();
            float length=animation.get("length").getAsFloat();if(length<=0)throw new IllegalStateException("Invalid loop length");
            for(var entry:animation.getAsJsonObject("animators").entrySet()) {
                Bone bone=ids.get(entry.getKey());if(bone==null)continue;
                for(JsonElement e:entry.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                    JsonObject frame=e.getAsJsonObject();String channel=frame.get("channel").getAsString();
                    if(!channel.equals("position")&&!channel.equals("rotation"))throw new IllegalStateException("Unsupported wall run channel "+channel);
                    String interpolation=frame.get("interpolation").getAsString();
                    if(!interpolation.equals("linear")&&!interpolation.equals("step"))throw new IllegalStateException("Unsupported wall run interpolation "+interpolation);
                    JsonObject point=frame.getAsJsonArray("data_points").get(0).getAsJsonObject();
                    Vector3f value=new Vector3f(point.get("x").getAsFloat(),point.get("y").getAsFloat(),point.get("z").getAsFloat());
                    bone.channels.computeIfAbsent(channel,ignored->new ArrayList<>()).add(new Key(frame.get("time").getAsFloat(),value,interpolation));
                }
            }
            for(Bone bone:names.values())for(List<Key> keys:bone.channels.values())keys.sort(Comparator.comparing(Key::time));
            return new Rig(names,elements,roots,length);
        }catch(Exception e){throw new IllegalStateException("Cannot load wall run file "+file,e);}
    }
    private static Bone outline(JsonObject node,Bone parent,Map<String,Bone> ids) {
        Bone bone=Objects.requireNonNull(ids.get(node.get("uuid").getAsString()));bone.parent=parent;
        for(JsonElement child:node.getAsJsonArray("children")) {
            if(child.isJsonObject())bone.children.add(outline(child.getAsJsonObject(),bone,ids));else bone.cubes.add(child.getAsString());
        }
        return bone;
    }
}
