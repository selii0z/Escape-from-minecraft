package fr.taczoperations.worldgen;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original ZombieTopia by RPC, supplied by the user. Terrain is private packaged data.
 * Chunk-local run-length encoding preserves original block states without copying world
 * entities, commands or old container inventories into a live raid. */
public final class ZombiepolisMap {
    public static final int ORIGIN=65536,SIZE=1152,SOURCE_MIN=-512,MINUTES=45;
    private static final String ROOT="/data/taczoperations/zombiepolis/";
    private static final ForestPlan.Point ENTRY=local(-115,-245), EXIT_A=local(-230,-40), EXIT_B=local(203,45);
    private record Anchor(int x,int y,int z){}
    private record Data(BlockState[] palette,short[] heights,List<Anchor> guards,List<ForestRaidAnchors.Mark> loot){}
    private static volatile Data data;
    private static volatile boolean failed;
    private static ForestPlan.Point local(int x,int z){return new ForestPlan.Point(x-SOURCE_MIN,z-SOURCE_MIN);}
    public static ForestPlan.Point entry(){return ENTRY;}
    public static ForestPlan.Point exitA(){return EXIT_A;}
    public static ForestPlan.Point exitB(){return EXIT_B;}
    public static boolean available(){return get()!=null;}
    private static Data get(){
        if(data!=null||failed)return data;
        synchronized(ZombiepolisMap.class){
            if(data!=null||failed)return data;
            try(var p=ZombiepolisMap.class.getResourceAsStream(ROOT+"palette.txt");
                var h=ZombiepolisMap.class.getResourceAsStream(ROOT+"height.bin");
                var a=ZombiepolisMap.class.getResourceAsStream(ROOT+"source.json")){
                if(p==null||h==null||a==null){failed=true;return null;}
                var palette=new BufferedReader(new InputStreamReader(p,StandardCharsets.UTF_8)).lines().map(ZombiepolisMap::state).toArray(BlockState[]::new);
                if(palette.length<1||palette.length>65535)throw new IOException("bad palette");
                short[] heights=new short[SIZE*SIZE];var in=new DataInputStream(h);
                for(int i=0;i<heights.length;i++)heights[i]=in.readShort();
                var j=JsonParser.parseReader(new InputStreamReader(a,StandardCharsets.UTF_8)).getAsJsonObject();
                var guards=new ArrayList<Anchor>();
                for(var e:j.getAsJsonArray("guards")){
                    var v=e.getAsJsonArray();guards.add(new Anchor(v.get(0).getAsInt()-SOURCE_MIN,v.get(1).getAsInt(),v.get(2).getAsInt()-SOURCE_MIN));
                }
                var loot=new ArrayList<ForestRaidAnchors.Mark>();int i=0;
                for(var e:j.getAsJsonArray("loot")){
                    var v=e.getAsJsonArray();loot.add(new ForestRaidAnchors.Mark(v.get(0).getAsInt()-SOURCE_MIN,v.get(1).getAsInt(),v.get(2).getAsInt()-SOURCE_MIN,i++%4));
                }
                data=new Data(palette,heights,List.copyOf(guards),List.copyOf(loot));
                return data;
            }catch(Exception e){failed=true;com.mojang.logging.LogUtils.getLogger().error("Cannot load Zombiepolis terrain",e);return null;}
        }
    }
    private static BlockState state(String raw){
        int i=raw.indexOf('[');String name=i<0?raw:raw.substring(0,i);
        var key=ResourceLocation.parse(name);
        if(!BuiltInRegistries.BLOCK.containsKey(key)){
            com.mojang.logging.LogUtils.getLogger().warn("Zombiepolis legacy block {} unavailable",name);
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(i>=0&&raw.endsWith("]"))for(String pair:raw.substring(i+1,raw.length()-1).split(",")){
            int eq=pair.indexOf('=');if(eq<1)continue;
            Property<?> property=state.getBlock().getStateDefinition().getProperty(pair.substring(0,eq));
            if(property!=null)state=property(state,property,pair.substring(eq+1));
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> prop,String value){
        return prop.getValue(value).map(v->state.setValue(prop,v)).orElse(state);
    }
    public static int height(int x,int z){
        var d=get();if(d==null||x<0||z<0||x>=SIZE||z>=SIZE)return 36;
        return d.heights()[z*SIZE+x];
    }
    public static BlockPos position(ForestPlan.Point p){
        int y=36;
        var d=get();
        if(d!=null&&!p.equals(ENTRY)&&!p.equals(EXIT_A)&&!p.equals(EXIT_B)){
            int best=18*18;
            for(var a:d.guards()){
                int dx=p.x()-a.x(),dz=p.z()-a.z(),dist=dx*dx+dz*dz;
                if(dist<best){best=dist;y=a.y();}
            }
        }
        return new BlockPos(ORIGIN+p.x(),y,ORIGIN+p.z());
    }
    public static List<ForestPlan.Point> encounters(){
        var d=get();return d==null?List.of():d.guards().stream().map(a->new ForestPlan.Point(a.x(),a.z())).toList();
    }
    public static List<ForestRaidAnchors.Mark> loot(long seed){
        var d=get();if(d==null)return List.of();
        var marks=new ArrayList<>(d.loot());Collections.shuffle(marks,new Random(seed));return List.copyOf(marks.subList(0,Math.min(60,marks.size())));
    }
    public static boolean valid(ForestPlan.Point p){
        if(p.x()<185||p.x()>755||p.z()<250||p.z()>680)return false;
        for(var q:List.of(ENTRY,EXIT_A,EXIT_B))if(Math.hypot(p.x()-q.x(),p.z()-q.z())<28)return false;
        return true;
    }
    static void generate(ForestChunkGenerator.Painter out){
        var d=get();if(d==null)return;
        String name=ROOT+"chunks/"+Math.floorDiv(out.minX,16)+"_"+Math.floorDiv(out.minZ,16)+".bin";
        try(var raw=ZombiepolisMap.class.getResourceAsStream(name)){
            if(raw==null)return;
            var in=new DataInputStream(new BufferedInputStream(raw));
            if(in.readUnsignedShort()!=0x5a50)throw new IOException("bad map chunk");
            int sections=in.readUnsignedByte();if(sections>24)throw new IOException("bad sections");
            for(int section=0;section<sections;section++){
                int sy=in.readByte(),runs=in.readUnsignedShort(),index=0;
                for(int r=0;r<runs;r++){
                    int len=in.readUnsignedShort(),id=in.readUnsignedShort();
                    if(len<1||index+len>4096||id>=d.palette().length)throw new IOException("bad run");
                    var state=d.palette()[id];int end=index+len;
                    if(state.isAir()){index=end;continue;}
                    while(index<end){
                        out.put(out.minX+(index&15),sy*16+(index>>8),out.minZ+((index>>4)&15),state);
                        index++;
                    }
                }
                if(index!=4096)throw new IOException("incomplete chunk section");
            }
        }catch(IOException e){throw new IllegalStateException("Invalid Zombiepolis chunk "+name,e);}
        for(var p:List.of(EXIT_A,EXIT_B)){
            if(!out.intersects(p.x()-4,p.z()-4,p.x()+4,p.z()+4))continue;
            ExtractionLayout.paint(ExtractionLayout.Style.INDUSTRIAL,(x,y,z,b)->out.put(p.x()+x,36+y,p.z()+z,b));
        }
    }
    private ZombiepolisMap(){}
}
