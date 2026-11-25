package fr.taczoperations.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.monster.Pillager;
import java.util.*;

public final class RaidEnemyModels {
    public record Preview(IllagerModel<Pillager> model,ModelPart corpse,ResourceLocation texture){}
    private static final Map<String,Optional<Preview>> CACHE=new HashMap<>();
    private static ResourceManager resources;
    private static final String[] PARTS={"head","body","right_arm","left_arm","right_leg","left_leg"};
    public static Preview select(Pillager guard){
        if(!guard.level().dimension().equals(ForestExpedition.FOREST)||!guard.hasCustomName())return null;
        String role=switch(guard.getCustomName().getString()){
            case "Le Contremaître"->"foreman";
            case "Tagilla"->"tagilla";
            case "Shturman"->"shturman";
            case "Killa"->"killa";
            case "Reshala"->"reshala";
            case "Glukhar"->"glukhar";
            case "Sanitar"->"sanitar";
            case "Kaban"->"kaban";
            case "Kollontay"->"kollontay";
            case "Partisan"->"partisan";
            case "Zryachiy"->"zryachiy";
            case "Knight"->"knight";
            case "Big Pipe"->"big_pipe";
            case "Birdeye"->"birdeye";
            case "Cultist Priest"->"cultist_priest";
            case "The Wedge"->"wedge";
            case "The Wedge (Labs)"->"wedge_labs";
            case "Shadow of Tagilla"->"shadow_tagilla";
            case "Vengeful Killa"->"vengeful_killa";
            case "Infected Tagilla"->"infected_tagilla";
            case "Black Div."->"black_division";
            case "Le Traqueur"->"tracker";
            case "Opérateur PMC"->(guard.getUUID().getLeastSignificantBits()&1)==0?"pmc_usec":"pmc_bird_eye";
            default->null;
        };
        return selectAppearance(role);
    }
    public static Preview selectAppearance(String role){
        if(role==null||!role.matches("[a-z_]{1,32}"))return null;
        ResourceManager manager=Minecraft.getInstance().getResourceManager();
        if(resources!=manager){CACHE.clear();resources=manager;}
        return CACHE.computeIfAbsent(role,key->load(manager,key)).orElse(null);
    }
    private static ResourceLocation path(String path){return ResourceLocation.fromNamespaceAndPath("taczoperations",path);}
    private static Optional<Preview> load(ResourceManager manager,String role){
        ResourceLocation texture=path("textures/entity/raid/"+role+".png");
        var asset=manager.getResource(path("enemy_models/"+role+".json"));
        if(asset.isEmpty()||manager.getResource(texture).isEmpty())return Optional.empty();
        try(var reader=asset.get().openAsReader()){
            JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
            int width=json.get("width").getAsInt(),height=json.get("height").getAsInt();
            JsonArray cubes=json.getAsJsonArray("cubes");
            if(width<16||width>2048||height<16||height>2048||cubes.size()<6||cubes.size()>128)throw new IllegalArgumentException("Invalid model dimensions");
            MeshDefinition mesh=new MeshDefinition();
            PartDefinition root=mesh.getRoot();
            Map<String,PartDefinition> parts=new HashMap<>();
            for(String part:PARTS){
                PartPose pose=switch(part){
                    case "right_arm"->PartPose.offset(-5,2,0);
                    case "left_arm"->PartPose.offset(5,2,0);
                    case "right_leg"->PartPose.offset(-2,12,0);
                    case "left_leg"->PartPose.offset(2,12,0);
                    default->PartPose.ZERO;
                };
                parts.put(part,root.addOrReplaceChild(part,CubeListBuilder.create(),pose));
            }
            parts.get("head").addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
            root.addOrReplaceChild("arms",CubeListBuilder.create(),PartPose.ZERO);
            int index=0;
            for(var entry:cubes){
                JsonObject cube=entry.getAsJsonObject();
                PartDefinition part=parts.get(cube.get("part").getAsString());
                if(part==null)throw new IllegalArgumentException("Unknown model part");
                float[] o=vec(cube,"origin",3),s=vec(cube,"size",3),off=vec(cube,"offset",3),rot=vec(cube,"rotation",3),uv=vec(cube,"uv",2);
                float inflate=cube.get("inflate").getAsFloat();
                if(s[0]<0||s[1]<0||s[2]<0||Math.abs(inflate)>4)throw new IllegalArgumentException("Invalid cube");
                var builder=CubeListBuilder.create().texOffs((int)uv[0],(int)uv[1])
                    .addBox("cube",o[0],o[1],o[2],s[0],s[1],s[2],new CubeDeformation(inflate));
                float rad=(float)(Math.PI/180);
                part.addOrReplaceChild("cube_"+index++,builder,PartPose.offsetAndRotation(off[0],off[1],off[2],rot[0]*rad,rot[1]*rad,rot[2]*rad));
            }
            LayerDefinition layer=LayerDefinition.create(mesh,width,height);
            ModelPart baked=layer.bakeRoot();
            IllagerModel<Pillager> model=new IllagerModel<Pillager>(baked){
                @Override public void setupAnim(Pillager e,float swing,float amount,float age,float yaw,float pitch){
                    super.setupAnim(e,swing,amount,age,yaw,pitch);
                    // These models have separate arms, not a baked crossed-arm mesh.
                    root().getChild("arms").visible=false;
                    root().getChild("right_arm").visible=true;
                    root().getChild("left_arm").visible=true;
                }
            };
            ModelPart corpse=layer.bakeRoot();
            corpse.getChild("arms").visible=false;
            corpse.getChild("left_arm").zRot=-.22F;
            corpse.getChild("right_arm").zRot=.28F;
            corpse.getChild("left_leg").zRot=.06F;
            corpse.getChild("right_leg").zRot=-.08F;
            return Optional.of(new Preview(model,corpse,texture));
        }catch(Exception e){
            com.mojang.logging.LogUtils.getLogger().warn("Optional raid model {} unavailable; using vanilla",role,e);
            return Optional.empty();
        }
    }
    private static float[] vec(JsonObject obj,String name,int len){
        var arr=obj.getAsJsonArray(name);
        if(arr.size()!=len)throw new IllegalArgumentException(name);
        float[] out=new float[len];
        for(int i=0;i<len;i++){out[i]=arr.get(i).getAsFloat();if(!Float.isFinite(out[i])||Math.abs(out[i])>2048)throw new IllegalArgumentException(name);}
        return out;
    }
    private RaidEnemyModels(){}
}
