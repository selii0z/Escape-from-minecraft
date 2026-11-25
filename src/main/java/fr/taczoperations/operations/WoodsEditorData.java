package fr.taczoperations.operations;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Persistent editor anchors, separate from the procedural raid maps. */
public final class WoodsEditorData extends SavedData {
    public enum Kind { PLAYER, PMC, SCAV, BOSS, EXTRACTION, LOOT, DROP, LANDMARK }
    public record Anchor(UUID id, Kind kind, BlockPos pos) {}
    private final LinkedHashMap<UUID, Anchor> anchors = new LinkedHashMap<>();

    public static WoodsEditorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WoodsEditorData::new, WoodsEditorData::load, null),
            "taczoperations_woods_editor");
    }

    public List<Anchor> anchors() { return List.copyOf(anchors.values()); }

    public Anchor add(Kind kind, BlockPos pos) {
        Anchor anchor = new Anchor(UUID.randomUUID(), kind, pos.immutable());
        anchors.put(anchor.id(), anchor);
        setDirty();
        return anchor;
    }

    public boolean move(UUID id, BlockPos pos) {
        Anchor old = anchors.get(id);
        if (old == null) return false;
        anchors.put(id, new Anchor(id, old.kind(), pos.immutable()));
        setDirty();
        return true;
    }

    public boolean remove(UUID id) {
        if (anchors.remove(id) == null) return false;
        setDirty();
        return true;
    }

    private static WoodsEditorData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        WoodsEditorData data = new WoodsEditorData();
        ListTag list = tag.getList("anchors", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID id = entry.getUUID("id");
                Kind kind = Kind.valueOf(entry.getString("kind"));
                data.anchors.put(id, new Anchor(id, kind, BlockPos.of(entry.getLong("pos"))));
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Anchor anchor : anchors.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", anchor.id());
            entry.putString("kind", anchor.kind().name());
            entry.putLong("pos", anchor.pos().asLong());
            list.add(entry);
        }
        tag.put("anchors", list);
        return tag;
    }
}
