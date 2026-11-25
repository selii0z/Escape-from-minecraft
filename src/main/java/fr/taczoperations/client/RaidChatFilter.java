package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.ForestExpedition;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** Keep global Pokemon spawn broadcasts out of the raid chat, without
 * hiding normal player chat, raid notices or other system messages. */
@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT)
public final class RaidChatFilter {
    private static final Pattern RARITY=Pattern.compile("\\b(shiny|shinies|chromatique[s]?|legendary|legendaire[s]?|mythical|fabuleux|fabuleuse[s]?|ultra[- ]?beast[s]?|ultra[- ]?chimere[s]?)\\b");
    private static final Pattern SPAWN=Pattern.compile("\\b(spawn(?:ed|ing|s)?|appear(?:ed|s|ing)?|appar(?:u[e]?[s]?|ait|aitra|aitre|ition)|vient d[' ]apparaitre|vien d[' ]apparaitre)\\b");

    static boolean pokemonSpawnNotice(String message){
        String normalized=Normalizer.normalize(message,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","")
            .replace('\u2019','\'')
            .replaceAll("(?i)§[0-9a-fk-or]","")
            .toLowerCase(Locale.ROOT);
        return RARITY.matcher(normalized).find()&&SPAWN.matcher(normalized).find();
    }

    @SubscribeEvent
    public static void received(ClientChatReceivedEvent.System event){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.level.dimension().equals(ForestExpedition.FOREST))return;
        if(pokemonSpawnNotice(event.getMessage().getString()))event.setCanceled(true);
    }

    private RaidChatFilter(){}
}
