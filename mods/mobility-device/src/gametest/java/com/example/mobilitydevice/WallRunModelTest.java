package com.example.mobilitydevice;

import com.example.mobilitydevice.ability.WallRunAbility;
import com.example.mobilitydevice.ability.WallRunVisuals;
import com.example.mobilitydevice.client.*;
import com.example.mobilitydevice.item.WaistJetItems;
import com.example.mobilitydevice.mixin.ModelPartAccessor;
import com.example.mobilitydevice.network.WallRunControlPayload;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WallRunModelTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(TestSingleplayerContext world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                WallRunPlayerPose.validate();
                try(var stream=WallRunModelTest.class.getResourceAsStream("/wallrun_expected_vertices.json")) {
                    JsonObject expected=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                    for(boolean slim:List.of(false,true)) {
                        PlayerModel model=new PlayerModel(client.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
                        int originalParts=model.allParts().size();
                        check(!((WallRunPlayerModelKind)model).mobility$isArmorModel(),"Ordinary player must use skin textures");
                        List<ModelPart.Cube> originalArm=access(model.rightArm).mobility$cubes();
                        AvatarRenderState state=new AvatarRenderState();state.entityType=EntityTypes.PLAYER;
                        state.skin=skinType(state.skin,slim);
                        state.showHat=state.showJacket=state.showLeftPants=state.showRightPants=state.showLeftSleeve=state.showRightSleeve=true;
                        for(String side:List.of("left","right")) for(JsonElement element:expected.getAsJsonArray(side)) {
                            JsonObject sample=element.getAsJsonObject();
                            ((WallRunRenderState)state).mobility$setWallRun(side.equals("left")?1:-1,1,sample.get("seconds").getAsFloat());
                            model.setupAnim(state);
                            Map<String,List<Vector3f>> actual=new HashMap<>();collect(model.root(),new Matrix4f(),"root",actual);
                            check(actual.size()==20,"Each source file must contribute exactly 20 skin cubes, got "+actual.keySet());
                            if(!slim)for(var cube:sample.getAsJsonObject("cubes").entrySet()) {
                                List<Vector3f> vertices=actual.get(cube.getKey());
                                if(vertices==null)vertices=actual.values().stream().filter(candidate-> {
                                    for(JsonElement expectedVertex:cube.getValue().getAsJsonArray()) {
                                        JsonArray xyz=expectedVertex.getAsJsonArray();Vector3f point=new Vector3f(xyz.get(0).getAsFloat(),xyz.get(1).getAsFloat(),xyz.get(2).getAsFloat());
                                        if(candidate.stream().noneMatch(v->v.distance(point)<.001F))return false;
                                    }
                                    return true;
                                }).findFirst().orElse(null);
                                check(vertices!=null,"Missing artist cube "+cube.getKey());
                                for(JsonElement vertex:cube.getValue().getAsJsonArray()) {
                                    JsonArray xyz=vertex.getAsJsonArray();Vector3f point=new Vector3f(xyz.get(0).getAsFloat(),xyz.get(1).getAsFloat(),xyz.get(2).getAsFloat());
                                    check(vertices.stream().anyMatch(v->v.distance(point)<.001F),"Source vertex mismatch "+side+" "+cube.getKey()+" "+point);
                                }
                            }
                            check(model.jacket.x==0 && model.jacket.y==0 && model.jacket.xRot==0,"Overlay must not apply the root transform twice");
                            PoseStack device=new PoseStack();WallRunPlayerPose.translateBody(device,(WallRunRenderState)state);
                            PoseStack torsoPose=new PoseStack();model.body.translateAndRotate(torsoPose);
                            check(device.last().pose().equals(torsoPose.last().pose(),.0001F),"Device must follow the corrected torso pose");
                            for(ModelPart body:new ModelPart[]{model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg})
                                check(body.getRandomCube(net.minecraft.util.RandomSource.create())!=null,"Arrow/stinger attachment must retain body cubes");
                            PoseStack hand=new PoseStack();model.translateToHand(state,HumanoidArm.RIGHT,hand);
                            check(hand.last().pose().isFinite(),"Held-item transform must stay finite");
                        }
                        ((WallRunRenderState)state).mobility$setWallRun(0,0,0);model.setupAnim(state);
                        check(model.allParts().size()==originalParts,"Standing must restore the original part registry");
                        check(access(model.rightArm).mobility$cubes()==originalArm,"Standing must restore original skin geometry");
                    }
                    testArmorFit(client.getEntityModels());
                    testArmorUvs(client.getEntityModels());
                    for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
                        HumanoidModel<AvatarRenderState> armor=new PlayerModel(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.get(slot)),false);
                        int originalParts=armor.allParts().size();
                        AvatarRenderState state=new AvatarRenderState();state.entityType=EntityTypes.PLAYER;
                        state.chestEquipment=new ItemStack(Items.DIAMOND_CHESTPLATE);
                        for(int side:List.of(-1,1)) {
                            ((WallRunRenderState)state).mobility$setWallRun(side,-1,.25F);armor.setupAnim(state);
                            check(!state.chestEquipment.isEmpty(),"Wall run must retain equipped armor data");
                            ModelPart limb=slot==EquipmentSlot.CHEST?armor.leftArm:slot==EquipmentSlot.HEAD?armor.head:armor.leftLeg;
                            check(access(limb).mobility$children().containsKey("mobility_wallrun"),"Armor must follow the artist's bones");
                            if(slot!=EquipmentSlot.HEAD) {
                                ModelPart mesh=access(limb).mobility$children().get("mobility_wallrun");
                                check(access(mesh).mobility$children().containsKey(slot==EquipmentSlot.CHEST?"left_forearm":"left_shin"),"Armor needs an elbow/knee joint");
                            }
                        }
                        ((WallRunRenderState)state).mobility$setWallRun(0,0,0);armor.setupAnim(state);
                        check(armor.allParts().size()==originalParts,"Armor must recover after exit");
                    }
                    check(WaistJetRenderer.wallRunModelIndex(1,1)==2 && WaistJetRenderer.wallRunModelIndex(1,-1)==4,"Right wall nozzle mapping");
                    check(WaistJetRenderer.wallRunModelIndex(-1,1)==8 && WaistJetRenderer.wallRunModelIndex(-1,-1)==6,"Left wall nozzle mapping");
                    check(WaistJetRenderer.wallRunModelIndex(-1,0)==0,"Idle wall run should not fire thrust");
                    WallRunVisuals forward=new WallRunVisuals(0,-1,1,2,100);
                    WallRunVisuals backward=new WallRunVisuals(0,-1,-1,forward.seconds(104,0)*20,104);
                    check(Math.abs(forward.seconds(104,0)-backward.seconds(104,0))<1e-6,"Direction change must preserve phase");
                    testSkinSuppression(client.getEntityModels());

                }catch(Exception e){throw new RuntimeException(e);}
            });
            world.getServer().runCommand("time set noon");
            world.getServer().runOnServer(server -> {
                var player=world.getConnection().getServerPlayer();BlockPos base=player.blockPosition();
                player.setAttached(TEST_ORIGIN,new BlockPos(base.getX(),base.getY(),base.getZ()));
                for(int x:List.of(-1,1))for(int y=-2;y<=18;y++)for(int z=-45;z<=45;z++)
                    player.level().setBlockAndUpdate(base.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState());
                WaistJetItems.setEquipped(player,new ItemStack(WaistJetItems.WAIST_JET));
            });
            world.getConnection().waitForClientboundPackets();
            context.setScreen(PosePreview::new);
            context.waitTicks(2);context.takeScreenshot("wallrun-corrected-pose-preview");
            context.setScreen(()->null);
            context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            for(int side:List.of(-1,1)) {
                start(context,world,side);
                context.getInput().holdKey(InputConstants.KEY_W);context.waitTicks(3);
                context.takeScreenshot(side<0?"wallrun-left-forward":"wallrun-right-forward");
                if(side<0) {
                    context.runOnClient(client->client.options.setCameraType(CameraType.FIRST_PERSON));
                    context.waitTicks(1);context.takeScreenshot("wallrun-first-person");
                    context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                }
                context.getInput().releaseKey(InputConstants.KEY_W);
                world.getServer().runOnServer(server -> {
                    var p=world.getConnection().getServerPlayer();
                    p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
                    p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
                    p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
                    p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
                });
                context.getInput().holdKey(InputConstants.KEY_S);
                context.waitFor(client->client.player.getAttachedOrElse(WallRunVisuals.STATE,WallRunVisuals.OFF).movement()==-1);
                context.waitTicks(3);context.takeScreenshot(side<0?"wallrun-left-backward-armor":"wallrun-right-backward-armor");
                context.getInput().releaseKey(InputConstants.KEY_S);
                context.waitFor(client->client.player.getAttachedOrElse(WallRunVisuals.STATE,WallRunVisuals.OFF).movement()==0);
                context.getInput().holdKey(InputConstants.KEY_SPACE);context.waitTicks(1);context.getInput().releaseKey(InputConstants.KEY_SPACE);
                context.waitFor(client->!WallRunClient.isActive()&&client.player.getAttachedOrElse(WallRunVisuals.STATE,WallRunVisuals.OFF).side()==0);
            }
            context.takeScreenshot("wallrun-exit-restored");
            context.runOnClient(client->client.options.setCameraType(CameraType.FIRST_PERSON));
        }
        check(context.computeOnClient(client->!WallRunClient.isActive()),"Disconnect must clear wall run visuals");
    }

    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<BlockPos> TEST_ORIGIN=
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(IdentifierHolder.ID);
    private static final class IdentifierHolder {static final net.minecraft.resources.Identifier ID=MobilityDeviceMod.id("wallrun_test_origin");}

    private static void start(ClientGameTestContext context,TestSingleplayerContext world,int side) {
        world.getServer().runOnServer(server->{
            var p=world.getConnection().getServerPlayer();BlockPos base=p.getAttached(TEST_ORIGIN);
            WallRunAbility.cancel(p);
            for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))p.setItemSlot(slot,ItemStack.EMPTY);
            p.teleportTo(base.getX()+(side<0?.68:.32),base.getY()+4,base.getZ()+.5);
            p.setYRot(0);p.setXRot(0);p.setOnGround(false);p.setDeltaMovement(0,0,.35);
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tacz")) {
                try {
                    ItemStack gun=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("tacz","modern_kinetic_gun")));
                    Class<?> api=Class.forName("com.tacz.guns.api.item.IGun");
                    Object instance=api.getMethod("getIGunOrNull",ItemStack.class).invoke(null,gun);
                    api.getMethod("setGunId",ItemStack.class,net.minecraft.resources.Identifier.class).invoke(instance,gun,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("tacz","m4a1"));
                    p.setItemSlot(EquipmentSlot.MAINHAND,gun);
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            WallRunAbility.control(p,new WallRunControlPayload(WallRunControlPayload.START,(byte)(side<0?0:1),(byte)1,(byte)0));
            check(WallRunAbility.isActive(p),"Test must enter real server wall-run state");
        });
        context.waitFor(client->WallRunClient.isActive());
        world.getConnection().waitForClientboundPackets();
        context.getInput().lookAt(0,0);
    }

    private static void testArmorUvs(net.minecraft.client.model.geom.EntityModelSet models) {
      for(boolean slim:List.of(false,true))for(int side:List.of(-1,1)) {
        var layers=slim?ModelLayers.PLAYER_SLIM_ARMOR:ModelLayers.PLAYER_ARMOR;
        PlayerModel armor=new PlayerModel(models.bakeLayer(layers.head()),slim);
        check(((WallRunPlayerModelKind)armor).mobility$isArmorModel(),"Actual player helmet must use the armor path");
        ModelPart.Cube reference=access(armor.head).mobility$cubes().getFirst();
        ModelPart.Cube hat=access(armor.hat).mobility$cubes().getFirst();
        AvatarRenderState state=new AvatarRenderState();state.entityType=EntityTypes.PLAYER;
        state.skin=skinType(state.skin,slim);
        ((WallRunRenderState)state).mobility$setWallRun(side,1,.25F);
        armor.setupAnim(state);
        ModelPart.Cube actual=access(armor.head).mobility$cubes().getFirst();
        checkBoxUvs(reference,actual,0,1,true,"helmet");
        checkBoxUvs(hat,access(armor.hat).mobility$cubes().getFirst(),0,1,true,"helmet overlay");
        WallRunPlayerPose.restore(armor);
        armor=new PlayerModel(models.bakeLayer(layers.chest()),slim);
        check(((WallRunPlayerModelKind)armor).mobility$isArmorModel(),"Actual player chestplate must use the armor path");
        ModelPart.Cube right=access(armor.rightArm).mobility$cubes().getFirst(),left=access(armor.leftArm).mobility$cubes().getFirst();
        armor.setupAnim(state);
        checkBoxUvs(right,access(armor.rightArm).mobility$cubes().getFirst(),0,.5F,false,"right shoulder");
        checkBoxUvs(left,access(armor.leftArm).mobility$cubes().getFirst(),0,.5F,false,"left shoulder");
        checkBoxUvs(right,lowerCube(armor.rightArm,"right_forearm"),.5F,1,false,"right forearm");
        checkBoxUvs(left,lowerCube(armor.leftArm,"left_forearm"),.5F,1,false,"left forearm");
        WallRunPlayerPose.restore(armor);
      }
      MobilityDeviceMod.LOGGER.info("Actual PlayerModel armor UV passed: helmet six faces, helmet overlay, both sleeve halves, classic/slim, both walls");
    }

    private static ModelPart.Cube lowerCube(ModelPart upper,String name) {
        ModelPart mesh=access(upper).mobility$children().get("mobility_wallrun");
        ModelPart lower=access(mesh).mobility$children().get(name);
        return access(lower).mobility$children().values().stream().filter(p->!access(p).mobility$cubes().isEmpty())
                .map(p->access(p).mobility$cubes().getFirst()).findFirst().orElseThrow();
    }

    private static void checkBoxUvs(ModelPart.Cube reference,ModelPart.Cube actual,float y0,float y1,boolean caps,String label) {
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f aMin=new Vector3f(Float.POSITIVE_INFINITY),aMax=new Vector3f(Float.NEGATIVE_INFINITY);
        for(ModelPart.Polygon f:reference.polygons)for(ModelPart.Vertex v:f.vertices()){Vector3f p=xyz(v);min.min(p);max.max(p);}
        for(ModelPart.Polygon f:actual.polygons)for(ModelPart.Vertex v:f.vertices()){Vector3f p=xyz(v);aMin.min(p);aMax.max(p);}
        for(ModelPart.Polygon face:actual.polygons) {
            if(!caps&&Math.abs(face.normal().y())>.5F)continue;
            ModelPart.Polygon original=Arrays.stream(reference.polygons).filter(p->p.normal().distance(face.normal())<.001F).findFirst().orElseThrow();
            ModelPart.Vertex[] q=original.vertices();Vector3f p0=xyz(q[0]),e1=xyz(q[1]).sub(p0),e3=xyz(q[3]).sub(p0);
            for(ModelPart.Vertex v:face.vertices()) {
                Vector3f f=xyz(v).sub(aMin).div(new Vector3f(aMax).sub(aMin));f.y=y0+f.y*(y1-y0);
                Vector3f p=f.mul(new Vector3f(max).sub(min)).add(min).sub(p0);
                float s=p.dot(e1)/e1.lengthSquared(),t=p.dot(e3)/e3.lengthSquared();
                float u=q[0].u()+s*(q[1].u()-q[0].u())+t*(q[3].u()-q[0].u());
                float w=q[0].v()+s*(q[1].v()-q[0].v())+t*(q[3].v()-q[0].v());
                check(Math.abs(v.u()-u)<.0001F&&Math.abs(v.v()-w)<.0001F,label+" UV mismatch normal="+face.normal()+" actual="+v.u()+","+v.v()+" expected="+u+","+w);
            }
        }
    }
    private static Vector3f xyz(ModelPart.Vertex v){return new Vector3f(v.x(),v.y(),v.z());}
    private static ModelPartAccessor access(ModelPart part){return (ModelPartAccessor)(Object)part;}
    private static PlayerSkin skinType(PlayerSkin skin,boolean slim) {
        return new PlayerSkin(skin.body(),skin.cape(),skin.elytra(),slim?PlayerModelType.SLIM:PlayerModelType.WIDE,skin.secure());
    }

    /** Exercise the actual render hook, including late injection after setup and shared model reuse. */
    private static void testSkinSuppression(net.minecraft.client.model.geom.EntityModelSet models) throws Exception {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skinlayers3d")) {
            SkinLayersWallRunCompatibility.suppress(new ModelPart(List.of(),Map.of())).run();
            check(!SkinLayersWallRunCompatibility.isLoaded(),"Absent optional mod must remain absent");
            return;
        }
        check(SkinLayersWallRunCompatibility.isLoaded(),"Installed Skin Layers API must be available");
        Class<?> injector=Class.forName("dev.tr7zw.skinlayers.accessor.ModelPartInjector");
        Class<?> mesh=Class.forName("dev.tr7zw.skinlayers.api.Mesh"),offset=Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
        var set=injector.getMethod("setInjectedMesh",mesh,offset);
        var get=injector.getMethod("getInjectedMesh");
        var getOffset=injector.getMethod("getOffsetProvider");
        int[] renders={0},vertices={0};
        Object original=java.lang.reflect.Proxy.newProxyInstance(mesh.getClassLoader(),new Class<?>[]{mesh},(proxy,method,args)->{
            if(method.getName().equals("render"))renders[0]++;
            return method.getReturnType()==boolean.class?Boolean.TRUE:null;
        });
        Object provider=java.lang.reflect.Proxy.newProxyInstance(offset.getClassLoader(),new Class<?>[]{offset},(proxy,method,args)->null);
        com.mojang.blaze3d.vertex.VertexConsumer sink=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                mesh.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
                    if(method.getName().equals("addVertex")&&args.length==3)vertices[0]++;
                    if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                    return method.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?proxy:null;
                });
        for(boolean slim:List.of(false,true)) {
            PlayerModel model=new PlayerModel(models.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            AvatarRenderState state=new AvatarRenderState();state.entityType=EntityTypes.PLAYER;state.skin=skinType(state.skin,slim);
            model.setupAnim(state);
            ModelPart[] parts={model.hat,model.jacket,model.rightSleeve,model.leftSleeve,model.rightPants,model.leftPants};
            for(int side:List.of(-1,1))for(float seconds:List.of(0F,.25F,.65F)) {
                for(ModelPart part:parts)set.invoke(part,original,provider);
                ((WallRunRenderState)state).mobility$setWallRun(side,1,seconds);
                model.setupAnim(state);
                for(ModelPart part:parts) {
                    // Reproduce the real ordering hazard: the voxel mod injects after our setup hook.
                    set.invoke(part,original,provider);
                    int before=renders[0],flatBefore=vertices[0];
                    boolean visible=part.visible;
                    part.render(new PoseStack(),sink,0,0,-1);
                    check(renders[0]==before,"Wall-run voxel Mesh.render must be blocked even after late injection");
                    check(get.invoke(part)==null,"Wall-run voxel mesh must be cleared");
                    check(vertices[0]>flatBefore,"Animated flat outer skin must remain visible");
                    check(part.visible==visible,"Fallback must preserve skin visibility switches");
                }
                // Another model/player is unaffected while this model is still wall running.
                ModelPart ordinary=new ModelPart(List.of(),Map.of());set.invoke(ordinary,original,provider);
                int before=renders[0];ordinary.render(new PoseStack(),sink,0,0,-1);
                check(renders[0]>before&&get.invoke(ordinary)==original,"Other players must retain their 3D skin");
                ((WallRunRenderState)state).mobility$setWallRun(0,0,0);model.setupAnim(state);
                for(ModelPart part:parts) {
                    check(get.invoke(part)==original&&getOffset.invoke(part)==provider,"Exit must restore original mesh and offset provider");
                    before=renders[0];part.render(new PoseStack(),sink,0,0,-1);
                    check(renders[0]>before,"3D skin must render again after exit or reuse for another player");
                }
            }
            // First-person hand restoration uses the same restore path without another setup call.
            ((WallRunRenderState)state).mobility$setWallRun(-1,1,.4F);model.setupAnim(state);
            WallRunPlayerPose.restore(model);
            for(ModelPart part:parts)check(get.invoke(part)==original,"Explicit first-person restore must re-enable original skin mesh");
        }
        MobilityDeviceMod.LOGGER.info("Wall-run Skin Layers fallback passed: six layers, late injection, other players, exit and first-person restoration");
    }
    private static final class PosePreview extends net.minecraft.client.gui.screens.Screen {
        PosePreview(){super(net.minecraft.network.chat.Component.literal("Wall run pose check"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial) {
            graphics.fill(0,0,width,height,0xff17212a);
            for(int column=0;column<4;column++) {
                graphics.text(font,new String[]{"STANDING","LEFT WALL","RIGHT WALL","WALL BACK"}[column],column*width/4+12,12,0xffeeeeee,false);
                var renderer=minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player);
                AvatarRenderState state=(AvatarRenderState)renderer.createRenderState(minecraft.player,1);
                int side=column==0?0:column==1?-1:1;
                ((WallRunRenderState)state).mobility$setWallRun(side,1,.25F);
                ((WaistJetRenderState)state).mobility$setWaistJet(true,-1);
                state.pose=net.minecraft.world.entity.Pose.STANDING;state.bodyRot=column==3?0:180;state.yRot=0;state.xRot=0;state.showCape=false;
                state.headEquipment=new ItemStack(Items.NETHERITE_HELMET);
                state.chestEquipment=new ItemStack(Items.NETHERITE_CHESTPLATE);
                state.legsEquipment=new ItemStack(Items.NETHERITE_LEGGINGS);
                state.feetEquipment=new ItemStack(Items.NETHERITE_BOOTS);
                int x0=column*width/4,x1=(column+1)*width/4;
                graphics.fill(side<0?x0+5:x1-10,30,side<0?x0+10:x1-5,height-12,0xff617684);
                graphics.entity(state,(int)(height*.35),new Vector3f(0,.9F,0),
                        new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(.5F),null,x0,32,x1,height-10);
            }
        }
    }

    private record Hull(List<Vector3f> points,List<Vector3f> normals) {
        boolean contains(Vector3f point) {
            for(int i=0;i<normals.size();i++)
                if(new Vector3f(point).sub(points.get(i*4)).dot(normals.get(i))>.002F)return false;
            return true;
        }
    }

    /** Compare rendered vertices rather than just matching joint names. Reuse armor across skin types. */
    private static void testArmorFit(net.minecraft.client.model.geom.EntityModelSet models) {
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            HumanoidModel<AvatarRenderState> armor=new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_ARMOR.get(slot)),false);
            for(boolean slim:List.of(false,true,false)) {
                PlayerModel skin=new PlayerModel(models.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
                AvatarRenderState state=new AvatarRenderState();state.entityType=EntityTypes.PLAYER;
                state.skin=skinType(state.skin,slim);
                for(int side:List.of(-1,1))for(int frame=0;frame<32;frame++) {
                    ((WallRunRenderState)state).mobility$setWallRun(side,1,frame*.025F);
                    skin.setupAnim(state);armor.setupAnim(state);
                    ModelPart[] a={armor.head,armor.body,armor.rightArm,armor.leftArm,armor.rightLeg,armor.leftLeg};
                    ModelPart[] s={skin.head,skin.body,skin.rightArm,skin.leftArm,skin.rightLeg,skin.leftLeg};
                    for(int i=0;i<a.length;i++) {
                        if(access(a[i]).mobility$cubes().isEmpty())continue;
                        check(matrix(a[i]).equals(matrix(s[i]),.0001F),"Armor joint drift slot="+slot+" slim="+slim+" part="+i);
                        List<Hull> shells=new ArrayList<>(),body=new ArrayList<>();
                        hulls(a[i],new Matrix4f(),shells,true);
                        hulls(s[i],new Matrix4f(),body,true);
                        for(Hull piece:body)for(Vector3f vertex:piece.points)
                            check(shells.stream().anyMatch(shell->shell.contains(vertex)),
                                    "Skin outside armor slot="+slot+" slim="+slim+" side="+side+" frame="+frame+" part="+i+" vertex="+vertex);
                    }
                }
                ((WallRunRenderState)state).mobility$setWallRun(0,0,0);skin.setupAnim(state);armor.setupAnim(state);
            }
        }
    }

    private static Matrix4f matrix(ModelPart part) {
        return new Matrix4f().translation(part.x,part.y,part.z).rotateZYX(part.zRot,part.yRot,part.xRot);
    }
    private static void hulls(ModelPart part,Matrix4f parent,List<Hull> result,boolean root) {
        Matrix4f transform=new Matrix4f(parent).mul(matrix(part));
        for(ModelPart.Cube cube:access(part).mobility$cubes()) {
            List<Vector3f> points=new ArrayList<>(),normals=new ArrayList<>();
            for(ModelPart.Polygon face:cube.polygons) {
                normals.add(transform.transformDirection(new Vector3f(face.normal())).normalize());
                for(ModelPart.Vertex v:face.vertices())points.add(transform.transformPosition(new Vector3f(v.x(),v.y(),v.z())));
            }
            result.add(new Hull(points,normals));
        }
        for(var child:access(part).mobility$children().entrySet()) {
            // Compare flat overlays as well as the base mesh. No unrelated accessories.
            if(root && !List.of("mobility_wallrun","hat","jacket","right_sleeve","left_sleeve","right_pants","left_pants").contains(child.getKey()))continue;
            hulls(child.getValue(),transform,result,false);
        }
    }
    private static void collect(ModelPart part,Matrix4f parent,String id,Map<String,List<Vector3f>> result) {
        Matrix4f matrix=new Matrix4f(parent).translate(part.x,part.y,part.z).rotateZYX(part.zRot,part.yRot,part.xRot);
        for(ModelPart.Cube cube:access(part).mobility$cubes()) {
            List<Vector3f> vertices=new ArrayList<>();
            for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ})
                vertices.add(matrix.transformPosition(new Vector3f(x,y,z)));
            check(result.put(id,vertices)==null,"A source cube must not be rendered twice: "+id);
        }
        for(var child:access(part).mobility$children().entrySet())collect(child.getValue(),matrix,child.getKey(),result);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}


