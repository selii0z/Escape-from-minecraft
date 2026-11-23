package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.worldgen.ForestChunkGenerator;
import fr.taczoperations.worldgen.ForestPlan;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;

public final class ForestExpedition {
    public static final ResourceKey<Level> FOREST = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, "black_forest"));
    private static final String RETURN_POINT = "taczoperations_forest_return";

    private ForestExpedition() {
    }

    static String depart(ServerPlayer player, BlockPos board, boolean night, boolean duo) {
        ServerLevel forest = player.server.getLevel(FOREST);
        if (forest == null || !(forest.getChunkSource().getGenerator() instanceof ForestChunkGenerator)) {
            return "La dimension Forêt Noire est indisponible.";
        }
        if (player.level() == forest) {
            return "Tu es déjà dans la Forêt Noire.";
        }
        if (!canDepart(player)) {
            return "Tu dois être à pied et vivant.";
        }

        UUID partnerId = duo ? OperationsGroups.of(player).partner(player) : null;
        if (duo && partnerId == null) {
            return "Invite d’abord un équipier.";
        }
        ServerPlayer partner = partnerId == null ? null : player.server.getPlayerList().getPlayer(partnerId);
        if (partnerId != null && (partner == null || partner.level() != player.level()
            || board.distToCenterSqr(partner.position()) > 64 || !canDepart(partner))) {
            return "Ton équipier doit être à pied près du tableau. Quitte le duo pour partir seul.";
        }

        if (forest.players().stream().anyMatch(other -> !other.isSpectator())) {
            return "Une équipe explore déjà la Forêt Noire.";
        }
        var random = forest.getRandom();
        var entry = ForestLandmarks.position(ForestPlan.ENTRIES.get(random.nextInt(ForestPlan.ENTRIES.size())));
        int weather = random.nextInt(3);
        if (!enter(player, forest, entry, night, weather)) {
            return "Le départ a été annulé.";
        }
        if (partner != null && !enter(partner, forest, entry.south(), night, weather)) {
            returnToOrigin(player);
            player.sendSystemMessage(Component.literal("Départ annulé : ton équipier n’a pas pu entrer."));
            return "";
        }
        ForestRaid.begin(forest, player, partner);
        return "";
    }

    private static boolean canDepart(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping();
    }

    private static boolean enter(ServerPlayer player, ServerLevel forest, BlockPos destination, boolean night, int weather) {
        CompoundTag point = new CompoundTag();
        point.putString("dimension", player.level().dimension().location().toString());
        point.putDouble("x", player.getX());
        point.putDouble("y", player.getY());
        point.putDouble("z", player.getZ());
        point.putFloat("yaw", player.getYRot());
        point.putFloat("pitch", player.getXRot());
        point.putBoolean("night", night);
        point.putInt("weather", weather);
        player.getPersistentData().put(RETURN_POINT, point);

        player.closeContainer();
        Vec3 spawn = Vec3.atBottomCenterOf(destination);
        if (!standingRoom(player, forest, spawn)) {
            spawn = safeSpawn(player, forest, destination);
        }
        player.teleportTo(forest, spawn.x, spawn.y, spawn.z, Set.of(), -90, 0);
        if (player.level() != forest) {
            player.getPersistentData().remove(RETURN_POINT);
            return false;
        }
        atmosphere(player, point);
        player.sendSystemMessage(Component.literal("Forêt Noire · sortie principale sur le bloc d’or. "
            + "Active le relais de la station pour ouvrir la sortie secondaire."));
        return true;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()
            || !player.level().dimension().equals(FOREST)) {
            return;
        }
        if (ForestRaid.hasRaidToken(player) && !ForestRaid.isParticipant(player)) {
            returnToOrigin(player);
            return;
        }
        if (!ForestPlan.inside(player.getBlockX(), player.getBlockZ()) || player.getY() < 1
            || player.getY() >= player.level().getMaxBuildHeight()) {
            returnToOrigin(player);
            return;
        }
        ForestRaid.populateNear(player);
        BlockPos primary = ForestLandmarks.position(ForestPlan.EXTRACTION);
        BlockPos secondary = ForestLandmarks.position(ForestPlan.CONDITIONAL_EXIT);
        CompoundTag point = player.getPersistentData().getCompound(RETURN_POINT);
        boolean relay = point.getBoolean("relay");
        BlockPos feet = player.blockPosition();
        if (!player.isSpectator() && player.onGround()
            && (feet.equals(primary) || (relay && feet.equals(secondary)))) {
            if (returnToOrigin(player)) {
                player.sendSystemMessage(Component.literal("Extraction réussie. Te voilà de retour."));
            }
            return;
        }
        if (player.tickCount % 20 == 0) {
            if (feet.equals(secondary) && !relay) {
                player.displayClientMessage(Component.literal("Sortie verrouillée · active le relais de la station."), true);
                return;
            }
            if (!SupplyContract.showExtraction(player)) {
                return;
            }
            BlockPos extraction = relay && secondary.distSqr(feet) < primary.distSqr(feet) ? secondary : primary;
            double distance = Math.hypot(player.getX() - extraction.getX() - .5, player.getZ() - extraction.getZ() - .5);
            player.displayClientMessage(Component.literal("Extraction · X " + extraction.getX()
                + "  Y " + extraction.getY() + "  Z " + extraction.getZ() + " · "
                + Math.round(distance) + " m"), true);
        }
    }

    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(FOREST)
            || player.isSpectator() || !player.getPersistentData().contains(RETURN_POINT)) {
            return;
        }
        BlockPos relay = ForestLandmarks.position(ForestPlan.RELAY);
        if (!event.getPos().equals(relay) && !event.getPos().equals(relay.above())) {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        player.getPersistentData().getCompound(RETURN_POINT).putBoolean("relay", true);
        BlockPos exit = ForestLandmarks.position(ForestPlan.CONDITIONAL_EXIT);
        player.sendSystemMessage(Component.literal("Relais activé. Sortie secondaire ouverte pour toi · X "
            + exit.getX() + " Z " + exit.getZ() + "."));
    }

    public static void synchronizeAtmosphere(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.getPersistentData().contains(RETURN_POINT)) {
                atmosphere(player, player.getPersistentData().getCompound(RETURN_POINT));
            }
        }
    }

    private static void atmosphere(ServerPlayer player, CompoundTag point) {
        player.connection.send(new ClientboundSetTimePacket(player.level().getGameTime(),
            point.getBoolean("night") ? 18000 : 6000, false));
        int weather = point.getInt("weather");
        if (weather == 0) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
            weather == 0 ? 0 : 1));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
            weather == 2 ? .8F : 0));
    }

    private static boolean returnToOrigin(ServerPlayer player) {
        CompoundTag point = player.getPersistentData().getCompound(RETURN_POINT);
        ResourceLocation key = ResourceLocation.tryParse(point.getString("dimension"));
        ServerLevel origin = key == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        Vec3 destination = new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
        if (origin == null || origin == player.level() || !standingRoom(player, origin, destination)) {
            origin = player.server.overworld();
            destination = safeSpawn(player, origin, origin.getSharedSpawnPos());
        }
        player.teleportTo(origin, destination.x, destination.y, destination.z, Set.of(),
            point.getFloat("yaw"), point.getFloat("pitch"));
        if (player.level() != origin) {
            return false;
        }
        player.getPersistentData().remove(RETURN_POINT);
        ForestRaid.clearPlayer(player);
        return true;
    }

    private static Vec3 safeSpawn(ServerPlayer player, ServerLevel level, BlockPos preferred) {
        return Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, preferred));
    }

    private static boolean standingRoom(ServerPlayer player, ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        return level.getWorldBorder().isWithinBounds(feet) && !level.isOutsideBuildHeight(feet)
            && level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(position))
            && level.getFluidState(feet).isEmpty()
            && level.getBlockState(feet.below()).isSolidRender(level, feet.below());
    }
}
