package fr.taczoperations.campaign;

import java.util.Objects;
import java.util.UUID;

/** A single uninterrupted server-side action; never serialized across reconnects. */
public record FieldAction(UUID raid, String quest, String objective, String map, String item, int duration, int elapsed) {
    public FieldAction {
        Objects.requireNonNull(raid); Objects.requireNonNull(quest); Objects.requireNonNull(objective);
        Objects.requireNonNull(map); Objects.requireNonNull(item);
        if(duration<1||duration>1200||elapsed<0||elapsed>duration)throw new IllegalArgumentException("Invalid action duration");
    }
    public FieldAction tick(UUID currentRaid, boolean eligible){
        if(!eligible||!raid.equals(currentRaid))return null;
        return new FieldAction(raid,quest,objective,map,item,duration,Math.min(duration,elapsed+1));
    }
    public boolean finished(){return elapsed==duration;}
}
