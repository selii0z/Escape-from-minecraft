package fr.taczoperations.operations;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.Locale;

@EventBusSubscriber(modid = "taczoperations")
public final class WoodsEditorCommands {
    public static final ResourceKey<Level> WOODS = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("taczoperations", "woods"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("efm")
            .then(Commands.literal("mapeditor").requires(s -> s.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (var kind : WoodsEditorData.Kind.values()) {
                            String name = kind.name().toLowerCase(Locale.ROOT);
                            if (name.startsWith(builder.getRemainingLowerCase())) builder.suggest(name);
                        }
                        return builder.buildFuture();
                    }).executes(ctx -> {
                        var p = ctx.getSource().getPlayerOrException();
                        if (!inWoods(p)) return 0;
                        WoodsEditorData.Kind kind;
                        try { kind = WoodsEditorData.Kind.valueOf(StringArgumentType.getString(ctx, "kind").toUpperCase(Locale.ROOT)); }
                        catch (IllegalArgumentException e) { p.sendSystemMessage(Component.literal("Catégorie inconnue.")); return 0; }
                        var anchor = WoodsEditorData.get(p.serverLevel()).add(kind, p.blockPosition());
                        p.sendSystemMessage(Component.literal("Point ajouté : " + anchor.kind() + " " + anchor.id()));
                        return 1;
                    })))
                .then(Commands.literal("list").executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    if (!inWoods(p)) return 0;
                    var anchors = WoodsEditorData.get(p.serverLevel()).anchors();
                    p.sendSystemMessage(Component.literal("Woods : " + anchors.size() + " points"));
                    for (var a : anchors) p.sendSystemMessage(Component.literal(a.kind() + " " + a.pos().toShortString() + " " + a.id()));
                    return 1;
                }))
                .then(Commands.literal("move").then(Commands.argument("id", UuidArgument.uuid()).executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    if (!inWoods(p)) return 0;
                    boolean ok = WoodsEditorData.get(p.serverLevel()).move(UuidArgument.getUuid(ctx, "id"), p.blockPosition());
                    p.sendSystemMessage(Component.literal(ok ? "Point déplacé." : "Point introuvable."));
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("remove").then(Commands.argument("id", UuidArgument.uuid()).executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    if (!inWoods(p)) return 0;
                    boolean ok = WoodsEditorData.get(p.serverLevel()).remove(UuidArgument.getUuid(ctx, "id"));
                    p.sendSystemMessage(Component.literal(ok ? "Point supprimé." : "Point introuvable."));
                    return ok ? 1 : 0;
                })))));
    }

    private static boolean inWoods(ServerPlayer player) {
        if (player.level().dimension().equals(WOODS)) return true;
        player.sendSystemMessage(Component.literal("Cette commande doit être utilisée dans la dimension Woods."));
        return false;
    }

    private WoodsEditorCommands() {}
}
