package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidResultPayload(boolean success, long duration, int kills, int crates,
                                int contracts, int combatXp, int searchXp, int contractXp, int adjustment)
    implements CustomPacketPayload {
    public static final Type<RaidResultPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, "raid_result"));

    public static final StreamCodec<ByteBuf, RaidResultPayload> STREAM_CODEC = StreamCodec.of(
        (buf, report) -> {
            buf.writeBoolean(report.success());
            ByteBufCodecs.VAR_LONG.encode(buf, report.duration());
            ByteBufCodecs.VAR_INT.encode(buf, report.kills());
            ByteBufCodecs.VAR_INT.encode(buf, report.crates());
            ByteBufCodecs.VAR_INT.encode(buf, report.contracts());
            ByteBufCodecs.VAR_INT.encode(buf, report.combatXp());
            ByteBufCodecs.VAR_INT.encode(buf, report.searchXp());
            ByteBufCodecs.VAR_INT.encode(buf, report.contractXp());
            ByteBufCodecs.VAR_INT.encode(buf, report.adjustment());
        },
        buf -> new RaidResultPayload(
            buf.readBoolean(),
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
