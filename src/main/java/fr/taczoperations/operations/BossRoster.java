package fr.taczoperations.operations;

import java.util.List;
import java.util.Random;

/** Shared boss policy for every classic and express raid. */
final class BossRoster {
    static final int CONVERGENCE_PERCENT=5, NORMAL_PERCENT=80;
    record Selection(boolean convergence,List<GuardRole> bosses){}
    static Selection select(boolean industrial,boolean night,long seed){
        var rng=new Random(seed^0x72c6aee3L);
        if(rng.nextInt(100)<CONVERGENCE_PERCENT)return new Selection(true,all());
        if(rng.nextInt(100)>=NORMAL_PERCENT)return new Selection(false,List.of());
        // Preserve the ordinary/rare roster mix, separately from appearance odds.
        return new Selection(false,normal(industrial,night,seed,rng.nextInt(75)<10));
    }
    static List<GuardRole> all(){return java.util.Arrays.stream(GuardRole.values()).filter(GuardRole::boss).toList();}
    static List<GuardRole> normal(boolean industrial,boolean night,long seed,boolean rare){
        var choices=new java.util.ArrayList<>(rare
            ?(industrial?List.of(GuardRole.GLUKHAR,GuardRole.KABAN,GuardRole.WEDGE,GuardRole.WEDGE_LABS,GuardRole.BLACK_DIVISION,GuardRole.KNIGHT)
                        :List.of(GuardRole.ZRYACHIY,GuardRole.KNIGHT,GuardRole.PARTISAN))
            :(industrial?List.of(GuardRole.FOREMAN,GuardRole.KILLA,GuardRole.RESHALA,GuardRole.KOLLONTAY)
                        :List.of(GuardRole.TRACKER,GuardRole.SANITAR,GuardRole.RESHALA)));
        if(rare&&night)choices.add(GuardRole.CULTIST_PRIEST);
        var selected=choices.get(new Random(seed^0x4c3af6aL).nextInt(choices.size()));
        return selected==GuardRole.KNIGHT?List.of(GuardRole.KNIGHT,GuardRole.BIG_PIPE,GuardRole.BIRDEYE):List.of(selected);
    }
    static int escorts(GuardRole role){
        return switch(role){
            case TRACKER,ZRYACHIY,WEDGE->2;
            case RESHALA,KOLLONTAY,CULTIST_PRIEST,BLACK_DIVISION->4;
            case GLUKHAR->6;
            case KABAN->8;
            case SANITAR,WEDGE_LABS->3;
            default->0;
        };
    }
    static GuardRole escort(GuardRole role,int index){
        return switch(role){
            case GLUKHAR->index<2?GuardRole.SOLDIER:index<4?GuardRole.HEAVY:GuardRole.SCOUT;
            case KABAN->index<2?GuardRole.LEADER:index<6?GuardRole.HEAVY:GuardRole.MARKSMAN;
            case TRACKER,ZRYACHIY->GuardRole.MARKSMAN;
            case SANITAR->GuardRole.SOLDIER;
            case CULTIST_PRIEST->GuardRole.SCOUT;
            case KOLLONTAY->index<2?GuardRole.SOLDIER:GuardRole.HEAVY;
            default->GuardRole.LEADER;
        };
    }
    private BossRoster(){}
}
