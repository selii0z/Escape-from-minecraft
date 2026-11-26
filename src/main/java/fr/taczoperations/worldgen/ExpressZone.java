package fr.taczoperations.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/** Independent fixed arenas, outside both classic map footprints. */
public enum ExpressZone {
    BUNKER(1,49152,224,"Le Bunker"),
    WAREHOUSE(2,53248,256,"L'Entrepôt"),
    VILLAGE(3,57344,256,"Village abandonné");
    public final int id,origin,size;
    public final String title;
    public static final int FLOOR=69, MINUTES=15;
    ExpressZone(int id,int origin,int size,String title){this.id=id;this.origin=origin;this.size=size;this.title=title;}
    public static ExpressZone byId(int id){for(var z:values())if(z.id==id)return z;return null;}
    public static ExpressZone at(int x,int z){for(var m:values())if(m.inside(x,z))return m;return null;}
    public boolean inside(int x,int z){return x>=origin&&z>=origin&&x<origin+size&&z<origin+size;}
    public ForestPlan.Point entry(){return new ForestPlan.Point(size/2,16);}
    public ForestPlan.Point exitA(){return new ForestPlan.Point(18,size-19);}
    public ForestPlan.Point exitB(){return new ForestPlan.Point(size-19,size-19);}
    public BlockPos pos(ForestPlan.Point p){return new BlockPos(origin+p.x(),FLOOR+1,origin+p.z());}
    public List<ForestPlan.Point> encounters(){
        return List.of(new ForestPlan.Point(size/4,55),new ForestPlan.Point(3*size/4,55),new ForestPlan.Point(size/2,size/2),new ForestPlan.Point(size/4,size-62),new ForestPlan.Point(3*size/4,size-62));
    }
    public List<ForestRaidAnchors.Mark> loot(long seed){
        var marks=new ArrayList<ForestRaidAnchors.Mark>();
        for(int x=34;x<size-30;x+=42)for(int z=44;z<size-32;z+=45)
            marks.add(new ForestRaidAnchors.Mark(x,FLOOR+1,z,Math.floorMod(x+z,4)));
        java.util.Collections.shuffle(marks,new Random(seed));
        return List.copyOf(marks.subList(0,Math.min(14,marks.size())));
    }
    public boolean checkpoint(BlockPos p){
        for(var q:List.of(entry(),exitA(),exitB())){var c=pos(q);if(Math.abs(p.getX()-c.getX())<=4&&Math.abs(p.getZ()-c.getZ())<=4)return true;}
        return false;
    }
    public boolean valid(ForestPlan.Point p){
        if(p.x()<12||p.z()<12||p.x()>=size-12||p.z()>=size-12)return false;
        for(var q:List.of(entry(),exitA(),exitB()))if(Math.hypot(p.x()-q.x(),p.z()-q.z())<24)return false;
        return true;
    }
    void generate(ForestChunkGenerator.Painter out){
        for(int x=Math.max(0,out.minX);x<Math.min(size,out.minX+16);x++)for(int z=Math.max(0,out.minZ);z<Math.min(size,out.minZ+16);z++){
            boolean edge=x<3||z<3||x>=size-3||z>=size-3;
            out.put(x,FLOOR-5,z,Blocks.BEDROCK);
            for(int y=FLOOR-4;y<FLOOR;y++)out.put(x,y,z,Blocks.STONE);
            boolean road=Math.abs(x-size/2)<5||Math.abs(z-size/2)<5;
            out.put(x,FLOOR,z,this==VILLAGE?(road?Blocks.GRAVEL:Blocks.GRASS_BLOCK):road?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE);
            if(edge)for(int y=1;y<=6;y++)out.put(x,FLOOR+y,z,Blocks.DEEPSLATE_BRICKS);
            if(this==BUNKER){
                out.put(x,FLOOR+10,z,Blocks.DEEPSLATE_TILES);
                if(x%16==8&&z%16==8)out.put(x,FLOOR+9,z,Blocks.SEA_LANTERN);
            }
        }
        if(this==BUNKER){
            for(int x=30;x<size-30;x+=46)for(int z=35;z<size-34;z+=44)
                room(out,x,z,30,26,6,Blocks.STONE_BRICKS,Blocks.POLISHED_DEEPSLATE);
        }else if(this==WAREHOUSE){
            for(int x=26;x<size-35;x+=70)for(int z=32;z<size-36;z+=62)
                room(out,x,z,49,42,9,Blocks.GRAY_CONCRETE,Blocks.IRON_BLOCK);
        }else{
            for(int x=26;x<size-30;x+=42)for(int z=35;z<size-34;z+=45)
                room(out,x,z,20,17,5,Blocks.SPRUCE_PLANKS,Blocks.DARK_OAK_PLANKS);
        }
        for(var p:List.of(entry(),exitA(),exitB())){
            if(!out.intersects(p.x()-4,p.z()-4,p.x()+4,p.z()+4))continue;
            ExtractionLayout.paint(this==VILLAGE?ExtractionLayout.Style.FOREST:ExtractionLayout.Style.INDUSTRIAL,
                (dx,dy,dz,b)->out.put(p.x()+dx,FLOOR+1+dy,p.z()+dz,b));
        }
    }
    private void room(ForestChunkGenerator.Painter out,int x,int z,int w,int d,int h,Block wall,Block roof){
        if(!out.intersects(x,z,x+w-1,z+d-1))return;
        for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){
            out.put(x+dx,FLOOR,z+dz,this==VILLAGE?Blocks.SPRUCE_PLANKS:Blocks.SMOOTH_STONE);
            boolean boundary=dx==0||dz==0||dx==w-1||dz==d-1;
            boolean door=(dz==0||dz==d-1)&&Math.abs(dx-w/2)<=1||(dx==0||dx==w-1)&&Math.abs(dz-d/2)<=1;
            if(boundary)for(int y=1;y<=h;y++){
                if(door&&y<=3)continue;
                out.put(x+dx,FLOOR+y,z+dz,y==3&&this==VILLAGE&&(dx+dz)%5==2?Blocks.GLASS_PANE:wall);
            }
            out.put(x+dx,FLOOR+h+1,z+dz,roof);
            if(!boundary&&dx%8==4&&dz%8==4)out.put(x+dx,FLOOR+h,z+dz,Blocks.SEA_LANTERN);
            if(!boundary&&dx==3&&dz%5==2&&dz>3&&dz<d-3)
                out.put(x+dx,FLOOR+1,z+dz,this==VILLAGE?Blocks.BOOKSHELF:Blocks.STRIPPED_SPRUCE_WOOD);
        }
    }
}
