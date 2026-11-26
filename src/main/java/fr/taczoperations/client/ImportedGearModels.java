package fr.taczoperations.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.*;

/** Original per-face UVs and rotated vertices converted offline from supplied Bedrock geometry. */
public final class ImportedGearModels {
    private record Face(float[] xyz,float[] uv,float[] normal){}
    private record Model(ResourceLocation texture,Map<String,List<Face>> parts){}
    private static final Map<String,Optional<Model>> CACHE=new HashMap<>();
    private static ResourceManager manager;
    private static ResourceLocation path(String p){return ResourceLocation.fromNamespaceAndPath("taczoperations",p);}
    private static Optional<Model> find(String id){
        if(id==null||!id.matches("[a-z0-9_]{1,48}"))return Optional.empty();
        var current=Minecraft.getInstance().getResourceManager();
        if(manager!=current){manager=current;CACHE.clear();}
        return CACHE.computeIfAbsent(id,k->load(current,k));
    }
    public static boolean exists(String id){return find(id).isPresent();}
    private static Optional<Model> load(ResourceManager rm,String id){
        var texture=path("textures/entity/gear/"+id+".png");
        var asset=rm.getResource(path("gear_models/"+id+".json"));
        if(asset.isEmpty()||rm.getResource(texture).isEmpty())return Optional.empty();
        try(var reader=asset.get().openAsReader()){
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            var entries=json.getAsJsonArray("faces");
            if(entries.size()>3072||entries.isEmpty())return Optional.empty();
            Map<String,List<Face>> parts=new HashMap<>();
            for(var e:entries){
                var a=e.getAsJsonObject();String part=a.get("part").getAsString();
                if(!Set.of("head","body","left_arm","right_arm").contains(part))throw new IllegalArgumentException("part");
                parts.computeIfAbsent(part,k->new ArrayList<>()).add(new Face(vec(a,"xyz",12),vec(a,"uv",8),vec(a,"normal",3)));
            }
            return Optional.of(new Model(texture,parts));
        }catch(Exception e){com.mojang.logging.LogUtils.getLogger().warn("Invalid imported gear {}",id,e);return Optional.empty();}
    }
    private static float[] vec(com.google.gson.JsonObject obj,String key,int length){
        var a=obj.getAsJsonArray(key);
        if(a.size()!=length)throw new IllegalArgumentException(key);
        float[] out=new float[length];
        for(int i=0;i<length;i++){out[i]=a.get(i).getAsFloat();if(!Float.isFinite(out[i])||Math.abs(out[i])>16)throw new IllegalArgumentException(key);}
        return out;
    }
    public static void draw(String id,String part,PoseStack pose,MultiBufferSource buffers,int light){
        var model=find(id).orElse(null);if(model==null)return;
        var faces=model.parts.get(part);if(faces==null)return;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture));
        var p=pose.last();
        for(Face f:faces)for(int i=0;i<4;i++){
            out.addVertex(p,f.xyz[i*3],f.xyz[i*3+1],f.xyz[i*3+2])
                .setColor(-1).setUv(f.uv[i*2],f.uv[i*2+1]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p,f.normal[0],f.normal[1],f.normal[2]);
        }
    }
    public static String armorId(net.minecraft.world.item.ItemStack item,net.minecraft.world.entity.EquipmentSlot slot){
        int tier=fr.taczoperations.operations.BallisticVest.tier(item,slot);
        return tier==0?null:slot==net.minecraft.world.entity.EquipmentSlot.HEAD?"c"+tier+"_head":
            slot==net.minecraft.world.entity.EquipmentSlot.CHEST?"c"+tier+"_chest":null;
    }
    private ImportedGearModels(){}
}
