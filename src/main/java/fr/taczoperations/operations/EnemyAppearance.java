package fr.taczoperations.operations;

import java.util.UUID;

/** Pure UUID-based visual choice, independent of combat role and loot. */
public final class EnemyAppearance {
    private static final String[] SCAVS={"raider","scout","soldier","marksman","heavy","leader","scav_extra"};
    public static String choose(String role,UUID identity){
        if(role==null)return null;
        return switch(role){
            case "raider","scout","soldier","marksman","heavy","leader","scav_extra" -> SCAVS[Math.floorMod(identity.hashCode(),SCAVS.length)];
            default -> role;
        };
    }
    private EnemyAppearance(){}
}
