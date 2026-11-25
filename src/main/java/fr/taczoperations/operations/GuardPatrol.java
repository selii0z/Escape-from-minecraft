package fr.taczoperations.operations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Random;

/** Bounded, non-spawning idle patrols. Combat and health stay unchanged. */
final class GuardPatrol {
    private static final String STEP="efm_patrol_step";
    static boolean advance(ServerLevel level,Pillager guard,GuardRole role){
        var data=guard.getPersistentData();
        if(!data.contains("guard_home_x"))return false;
        BlockPos home=new BlockPos(data.getInt("guard_home_x"),data.getInt("guard_home_y"),data.getInt("guard_home_z"));
        BlockPos anchor=role.boss()?home:ForestRaid.patrolAnchor(level,guard);
        int squad=data.getInt("taczoperations_squad");
        var id=data.getUUID(ForestRaid.ENEMY);
        // Shared orientation for a squad, offset per member so they don't
        // converge on the same block. New raid UUID => different route.
        Random route=new Random(id.getLeastSignificantBits()^((long)squad*0x9e3779b97f4a7c15L));
        double angle=route.nextDouble()*Math.PI*2;
        int phase=Math.floorMod(data.getInt(STEP),4);
        int radius=role==GuardRole.PARTISAN?48:role.boss()?8:role==GuardRole.PMC?24:role==GuardRole.SCOUT?16:10;
        if(role==GuardRole.PMC&&PmcSpecialty.get(guard)==PmcSpecialty.MARKSMAN)radius=14;
        int offsetX=Math.floorMod(guard.getUUID().hashCode(),5)-2;
        int offsetZ=Math.floorMod(guard.getUUID().hashCode()/5,5)-2;
        for(int attempt=0;attempt<2;attempt++){
            int step=(phase+attempt)%4;
            double a=angle+step*Math.PI*2/3;
            int x=step==3?home.getX():anchor.getX()+(int)Math.round(Math.cos(a)*radius)+offsetX;
            int z=step==3?home.getZ():anchor.getZ()+(int)Math.round(Math.sin(a)*radius)+offsetZ;
            BlockPos flat=new BlockPos(x,home.getY(),z);
            if(!ForestRaid.patrolPosition(level,guard,flat))continue;
            // Try local floor before surface (industrial interiors should
            // not automatically route to the rooftop).
            BlockPos destination=null;
            for(int dy:new int[]{0,1,-1,2,-2,3,-3}){
                var candidate=flat.offset(0,dy,0);
                if(standable(level,guard,candidate)){destination=candidate;break;}
            }
            if(destination==null){
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                var candidate=new BlockPos(x,y,z);
                if(Math.abs(y-home.getY())<=8&&standable(level,guard,candidate))destination=candidate;
            }
            if(destination==null)continue;
            var path=guard.getNavigation().createPath(destination,0);
            if(path==null||!path.canReach()||path.getNodeCount()>96)continue;
            boolean safe=true;
            for(int i=0;i<path.getNodeCount();i++){
                var node=path.getNode(i);
                if(!ForestRaid.patrolPosition(level,guard,new BlockPos(node.x,node.y,node.z))){
                    safe=false;break;
                }
            }
            if(!safe)continue;
            if(guard.getNavigation().moveTo(path,role==GuardRole.PMC?.85:.72)){
                data.putInt(STEP,(step+1)%4);
                return true;
            }
        }
        // Don't get stuck retrying one impossible waypoint.
        data.putInt(STEP,(phase+1)%4);
        return false;
    }
    private static boolean standable(ServerLevel level,Pillager guard,BlockPos p){
        return ForestRaid.patrolPosition(level,guard,p)&&level.getBlockState(p).isAir()
            &&level.getBlockState(p.above()).isAir()
            &&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP);
    }
    private GuardPatrol(){}
}
