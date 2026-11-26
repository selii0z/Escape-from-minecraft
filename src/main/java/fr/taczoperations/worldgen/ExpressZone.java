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
    private record Site(int x,int z,int w,int d,int h,int theme){}
    private List<Site> sites(){
        return switch(this){
            case BUNKER -> List.of(new Site(24,35,56,38,6,0),new Site(144,35,56,38,6,1),
                new Site(24,92,56,40,7,2),new Site(144,92,56,40,7,3),
                new Site(24,154,56,36,6,3),new Site(144,154,56,36,6,0),
                new Site(89,96,46,31,7,1));
            case WAREHOUSE -> List.of(new Site(22,30,85,47,10,1),new Site(149,30,85,47,10,2),
                new Site(22,95,85,52,12,3),new Site(149,95,85,52,10,0),
                new Site(35,174,66,48,9,2),new Site(155,174,66,48,9,1));
            case VILLAGE -> List.of(new Site(25,35,24,20,5,0),new Site(62,39,29,23,6,1),
                new Site(162,32,26,22,5,2),new Site(201,44,22,19,5,0),
                new Site(29,95,32,22,6,3),new Site(76,102,26,19,5,0),
                new Site(164,93,32,26,6,1),new Site(209,103,21,20,5,0),
                new Site(38,166,27,22,5,2),new Site(76,191,29,26,7,3),
                new Site(151,170,33,28,6,1),new Site(199,185,26,24,5,2));
        };
    }
    public List<ForestRaidAnchors.Mark> loot(long seed){
        var marks=new ArrayList<ForestRaidAnchors.Mark>();
        for(Site site:sites()){
            marks.add(new ForestRaidAnchors.Mark(site.x()+6,FLOOR+1,site.z()+3,site.theme()));
            marks.add(new ForestRaidAnchors.Mark(site.x()+site.w()-7,FLOOR+1,site.z()+site.d()-4,(site.theme()+1)%4));
        }
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
            if(edge)for(int y=1;y<=(this==BUNKER?9:6);y++)out.put(x,FLOOR+y,z,Blocks.DEEPSLATE_BRICKS);
            if(this==BUNKER){
                out.put(x,FLOOR+10,z,Blocks.DEEPSLATE_TILES);
                if(x%12==6&&z%12==6)out.put(x,FLOOR+8,z,Blocks.SEA_LANTERN);
            }
        }
        for(Site site:sites())room(out,site);
        if(this==VILLAGE){
            // Gravel lanes, low stone garden boundaries and irregular hedges.
            for(int x=14;x<size-14;x+=24)for(int z=20;z<size-20;z+=27){
                if(!out.intersects(x-2,z-2,x+2,z+2)||Math.abs(x-size/2)<9||Math.abs(z-size/2)<9)continue;
                final int xx=x,zz=z;
                if(sites().stream().anyMatch(q->xx>=q.x()-4&&xx<q.x()+q.w()+4&&zz>=q.z()-4&&zz<q.z()+q.d()+4))continue;
                if(checkpoint(pos(new ForestPlan.Point(x,z))))continue;
                for(int y=1;y<=4;y++)out.put(x,FLOOR+y,z,Blocks.OAK_LOG);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                    if(Math.abs(dx)+Math.abs(dz)<=3)out.put(x+dx,FLOOR+5,z+dz,Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true));
            }
        }else{
            for(int z=32;z<size-35;z+=18)for(int x:new int[]{size/2-10,size/2+10}){
                final int xx=x,zz=z;
                if(sites().stream().anyMatch(q->xx>=q.x()&&xx<q.x()+q.w()&&zz>=q.z()&&zz<q.z()+q.d()))continue;
                out.put(x,FLOOR+1,z,Blocks.POLISHED_DEEPSLATE);
                out.put(x,FLOOR+2,z,Blocks.YELLOW_CONCRETE);
                out.put(x,FLOOR+3,z,Blocks.LANTERN);
            }
        }
        for(var p:List.of(entry(),exitA(),exitB())){
            if(!out.intersects(p.x()-4,p.z()-4,p.x()+4,p.z()+4))continue;
            ExtractionLayout.paint(this==VILLAGE?ExtractionLayout.Style.FOREST:ExtractionLayout.Style.INDUSTRIAL,
                (dx,dy,dz,b)->out.put(p.x()+dx,FLOOR+1+dy,p.z()+dz,b));
        }
    }
    private void room(ForestChunkGenerator.Painter out,Site site){
        int x=site.x(),z=site.z(),w=site.w(),d=site.d(),h=site.h(),theme=site.theme();
        if(!out.intersects(x-1,z-1,x+w,z+d))return;
        Block wall=this==VILLAGE?switch(theme){case 0->Blocks.SPRUCE_PLANKS;case 1->Blocks.BRICKS;case 2->Blocks.STRIPPED_OAK_WOOD;default->Blocks.COBBLESTONE;}:this==BUNKER?Blocks.STONE_BRICKS:Blocks.GRAY_CONCRETE;
        Block floor=this==VILLAGE?Blocks.SPRUCE_PLANKS:theme==0?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_STONE;
        for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){
            out.put(x+dx,FLOOR,z+dz,floor);
            boolean boundary=dx==0||dz==0||dx==w-1||dz==d-1;
            boolean door=(dz==0||dz==d-1)&&Math.abs(dx-w/2)<=1||(dx==0||dx==w-1)&&Math.abs(dz-d/2)<=1;
            if(boundary)for(int y=1;y<=h;y++){
                if(door&&y<=3)continue;
                boolean pillar=(dx==0||dx==w-1)?dz%7==0:dx%7==0;
                Block material=pillar?(this==VILLAGE?Blocks.STRIPPED_SPRUCE_LOG:Blocks.POLISHED_DEEPSLATE):wall;
                if(!pillar&&this!=BUNKER&&y==3&&(dx+dz)%5==2)material=Blocks.GLASS_PANE;
                if(this==BUNKER&&y==2)material=switch(theme){case 0->Blocks.GREEN_TERRACOTTA;case 1->Blocks.BLUE_TERRACOTTA;case 2->Blocks.RED_TERRACOTTA;default->Blocks.YELLOW_TERRACOTTA;};
                out.put(x+dx,FLOOR+y,z+dz,material);
            }
            int rise=this==VILLAGE?Math.min(4,Math.min(dx,w-1-dx)/2):0;
            out.put(x+dx,FLOOR+h+1+rise,z+dz,this==VILLAGE?Blocks.DARK_OAK_PLANKS:this==BUNKER?Blocks.POLISHED_DEEPSLATE:Blocks.LIGHT_GRAY_CONCRETE);
            if(this==VILLAGE&&boundary)for(int y=1;y<=rise;y++)out.put(x+dx,FLOOR+h+y,z+dz,wall);
            if(!boundary&&dx%9==4&&dz%9==4){
                int lamp=this==WAREHOUSE?h-3:h;
                for(int y=lamp+1;y<=h;y++)out.put(x+dx,FLOOR+y,z+dz,Blocks.CHAIN);
                out.put(x+dx,FLOOR+lamp,z+dz,Blocks.LANTERN);
            }
            if(boundary||dx<3||dz<5||dx>w-4||dz>d-5||Math.abs(dx-w/2)<=3||Math.abs(dz-d/2)<=3)continue;
            if(this==WAREHOUSE&&dx%12<=1&&dz%8<5){
                out.put(x+dx,FLOOR+1,z+dz,Blocks.STRIPPED_SPRUCE_WOOD);
                if(dz%8<3)out.put(x+dx,FLOOR+2,z+dz,Blocks.STRIPPED_OAK_WOOD);
                out.put(x+dx,FLOOR+3,z+dz,Blocks.IRON_TRAPDOOR);
            }else if(this!=WAREHOUSE&&dx%9==3&&dz%7<3){
                out.put(x+dx,FLOOR+1,z+dz,switch(theme){case 0->Blocks.BOOKSHELF;case 1->Blocks.POLISHED_ANDESITE;case 2->Blocks.SMOOTH_STONE;default->Blocks.STRIPPED_DARK_OAK_WOOD;});
                if(theme==0||theme==3)out.put(x+dx,FLOOR+2,z+dz,Blocks.SPRUCE_SLAB);
            }
        }
        // Thresholds distinguish the buildings while leaving every entrance clear.
        for(int dx=-1;dx<=1;dx++){
            out.put(x+w/2+dx,FLOOR,z-1,this==VILLAGE?Blocks.COBBLESTONE:Blocks.YELLOW_CONCRETE);
            out.put(x+w/2+dx,FLOOR,z+d,this==VILLAGE?Blocks.COBBLESTONE:Blocks.YELLOW_CONCRETE);
        }
    }
}
