package fr.taczoperations.operations;

import java.util.List;
import java.util.Random;

/** Adaptation of EFT boss identities to the two existing EFM maps. */
final class BossRoster {
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
