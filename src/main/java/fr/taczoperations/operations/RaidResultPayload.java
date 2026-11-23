package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidResultPayload(boolean success, long duration, int kills, int crates,
                                int contracts, int combatXp, int searchXp, int adjustment)
    implements CustomPacketPayload {
    public static final Type<RaidResultPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, "raid_result"));

    public static final StreamCodec<ByteBuf, RaidResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, RaidResultPayload::success,
            ByteBufCodecs.VAR_LONG, RaidResultPayload::duration,
            ByteBufCodecs.VAR_INT, RaidResultPayload::kills,
            ByteBufCodecs.VAR_INT, RaidResultPayload::crates,
            ByteBufCodecs.VAR_INT, RaidResultPayload::contracts,
            ByteBufCodecs.VAR_INT, RaidResultPayload::combatXp,
            ByteBufCodecs.VAR_INT, RaidResultPayload::searchXp,
            ByteBufCodecs.VAR_INT, RaidResultPayload::adjustment,
            RaidResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
