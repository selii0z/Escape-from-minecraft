package fr.taczoperations.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared layout for fresh chunks and in-place renovation of the five fixed raid exits. */
public final class ExtractionLayout {
    public static final int RADIUS=2;
    public static final int BORDER=4;
    public enum Style { FOREST, RELAY, INDUSTRIAL, KEY, PAID }
    @FunctionalInterface public interface Painter {void put(int dx,int dy,int dz,Block block);}
    public static boolean contains(int dx,int dy,int dz){return Math.abs(dx)<=RADIUS&&dy==0&&Math.abs(dz)<=RADIUS;}
    public static void paint(Style style,Painter out){
        boolean forest=style==Style.FOREST||style==Style.RELAY;
        Block floor=forest?Blocks.SPRUCE_PLANKS:Blocks.SMOOTH_STONE;
        Block accent=forest?Blocks.STRIPPED_SPRUCE_WOOD:Blocks.YELLOW_CONCRETE;
        for(int x=-BORDER;x<=BORDER;x++)for(int z=-BORDER;z<=BORDER;z++){
            int edge=Math.max(Math.abs(x),Math.abs(z));
            Block ground=edge==4?(forest?Blocks.GRAVEL:Blocks.POLISHED_ANDESITE):edge==3?
                (forest?accent:(x+z)%2==0?accent:Blocks.BLACK_CONCRETE):floor;
            // A discreet H-shaped landing mark, never precious-metal blocks.
            if(edge<=2&&((Math.abs(x)==1&&Math.abs(z)<=1)||(z==0&&Math.abs(x)<=1)))
                ground=forest?Blocks.DARK_OAK_PLANKS:Blocks.LIGHT_GRAY_CONCRETE;
            out.put(x,-1,z,ground);
            for(int y=0;y<=3;y++)out.put(x,y,z,Blocks.AIR);
        }
        for(int x:new int[]{-4,4})for(int z:new int[]{-4,4}){
            out.put(x,0,z,forest?Blocks.STRIPPED_SPRUCE_LOG:Blocks.POLISHED_ANDESITE);
            out.put(x,1,z,forest?Blocks.SPRUCE_FENCE:Blocks.IRON_BARS);
            out.put(x,2,z,forest?Blocks.SPRUCE_FENCE:Blocks.IRON_BARS);
            out.put(x,3,z,forest?Blocks.LANTERN:Blocks.SEA_LANTERN);
        }
        for(int x:new int[]{-4,4})for(int z:new int[]{-2,-1,1,2})
            out.put(x,0,z,forest?Blocks.SPRUCE_FENCE:Blocks.IRON_BARS);
        // Non-loot props, outside the extraction footprint. Two open entrances.
        for(int x:new int[]{-3,3}){
            out.put(x,0,3,forest?Blocks.STRIPPED_DARK_OAK_WOOD:Blocks.POLISHED_DEEPSLATE);
            out.put(x,1,3,forest?Blocks.SPRUCE_SLAB:Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
        }
        if(forest){
            for(int x=-3;x<=3;x++)out.put(x,3,4,style==Style.RELAY?Blocks.BROWN_WOOL:Blocks.GREEN_WOOL);
        }else{
            Block marker=style==Style.KEY?Blocks.BLUE_CONCRETE:style==Style.PAID?Blocks.ORANGE_CONCRETE:Blocks.WHITE_CONCRETE;
            for(int x=-2;x<=2;x++)out.put(x,-1,3,marker);
        }
    }
    private ExtractionLayout(){}
}
