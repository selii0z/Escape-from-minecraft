package fr.taczoperations.operations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Cosmetic presets only: no block items are created, sold or refunded. */
final class HideoutDecor {
    private static final String THEME="efm_hideout_theme";
    enum Theme {
        MILITARY("Militaire",Blocks.SMOOTH_STONE,Blocks.POLISHED_ANDESITE,Blocks.STONE_BRICKS,Blocks.GREEN_TERRACOTTA),
        INDUSTRIAL("Industriel",Blocks.POLISHED_DEEPSLATE,Blocks.POLISHED_BLACKSTONE,Blocks.TUFF_BRICKS,Blocks.YELLOW_TERRACOTTA),
        COMFORT("Confort",Blocks.SPRUCE_PLANKS,Blocks.DARK_OAK_PLANKS,Blocks.SMOOTH_STONE,Blocks.BROWN_TERRACOTTA),
        MODERN("Moderne",Blocks.GRAY_CONCRETE,Blocks.LIGHT_GRAY_CONCRETE,Blocks.SMOOTH_QUARTZ,Blocks.CYAN_TERRACOTTA);
        final String label;final Block floor,grid,wall,accent;
        Theme(String label,Block floor,Block grid,Block wall,Block accent){
            this.label=label;this.floor=floor;this.grid=grid;this.wall=wall;this.accent=accent;
        }
    }
    static Theme current(ServerPlayer p){
        return Theme.values()[Math.clamp(SupplyContract.account(p).getInt(THEME),0,Theme.values().length-1)];
    }
    static void cycle(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu)return;
        int id=HideoutPlots.of(p.serverLevel()).find(p.getUUID());if(id<0)return;
        Theme next=Theme.values()[(current(p).ordinal()+1)%Theme.values().length];
        render(p,Hideout.center(id),next);
        SupplyContract.account(p).putInt(THEME,next.ordinal());
        Hideout.say(p,"Décoration de la planque : "+next.label+".");
    }
    static void refresh(ServerPlayer p,BlockPos center){render(p,center,current(p));}
    private static void set(ServerLevel level,BlockPos pos,Block block){
        if(!level.getBlockState(pos).is(block))level.setBlock(pos,block.defaultBlockState(),3);
    }
    private static void render(ServerPlayer p,BlockPos center,Theme theme){
        ServerLevel level=p.serverLevel();
        // Only the original protected bunker shell, never the firing lane or player inventories.
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++){
            set(level,center.offset(x,-1,z),(x%5==0||z%5==0)?theme.grid:theme.floor);
            if(Math.abs(x)==15||Math.abs(z)==15){
                for(int y=0;y<6;y++)set(level,center.offset(x,y,z),y==1?theme.accent:theme.wall);
            }
        }
        for(int i=0;i<Hideout.Station.values().length;i++){
            int x=-10+(i%4)*6,z=i<4?-11:11;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                set(level,center.offset(x+dx,-1,z+dz),Blocks.POLISHED_BLACKSTONE);
            var station=Hideout.Station.values()[i];
            int tier=Hideout.tier(p,station);
            for(int j=0;j<3;j++){
                set(level,center.offset(x+j-1,2,z+(i<4?-1:1)),j<tier?Blocks.LIME_CONCRETE:Blocks.GRAY_CONCRETE);
            }
            var pos=center.offset(x,2,z);
            var sign=Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING,i<4?net.minecraft.core.Direction.SOUTH:net.minecraft.core.Direction.NORTH);
            if(!level.getBlockState(pos).equals(sign))level.setBlock(pos,sign,3);
            if(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity entity){
                var signText=entity.getFrontText().setMessage(0,net.minecraft.network.chat.Component.literal(station==Hideout.Station.EAU?"Collecteur":station.label)).setMessage(1,net.minecraft.network.chat.Component.literal(station==Hideout.Station.EAU?"d'eau":"Niveau "+tier+"/3")).setMessage(2,net.minecraft.network.chat.Component.literal(station==Hideout.Station.EAU?"Niveau "+tier+"/3":"Clic droit")).setMessage(3,net.minecraft.network.chat.Component.literal(station==Hideout.Station.EAU?"Clic droit":""));
                entity.setText(signText,true);
            }
        }
    }
    private HideoutDecor(){}
}
