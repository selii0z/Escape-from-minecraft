package fr.taczoperations.entity;

import fr.taczoperations.operations.ForestRaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class GuardCorpse extends Entity {
    private final CorpseInventory items = new CorpseInventory();
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> APPEARANCE=
        SynchedEntityData.defineId(GuardCorpse.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
    public void setAppearance(String value){
        entityData.set(APPEARANCE,switch(value){
            case "foreman","tracker","pmc_usec","pmc_bird_eye"->value;
            default->"";
        });
    }
    public String appearance(){return entityData.get(APPEARANCE);}

    public GuardCorpse(EntityType<? extends GuardCorpse> type, Level level) {
        super(type, level);
    }

    public void put(int slot, ItemStack stack) {
        items.setItem(slot, stack.copy());
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !ForestRaid.canKeepCorpse(server, this)) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel) {
            setDeltaMovement(getDeltaMovement().add(0, -.06, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(0, onGround() ? 0 : Math.max(-.7, getDeltaMovement().y * .98), 0);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && items.stillValid(server)) {
            server.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new fr.taczoperations.operations.RaidSearchMenu(id,inventory,items,getPersistentData(),items::stillValid),
                hasCustomName()?getCustomName():Component.translatable("container.taczoperations.guard_corpse")));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {builder.define(APPEARANCE,"");}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.loadAllItems(tag, items.getItems(), registryAccess());
        setAppearance(tag.getString("appearance"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, items.getItems(), registryAccess());
        tag.putString("appearance",appearance());
    }

    private final class CorpseInventory extends SimpleContainer {
        private CorpseInventory() {
            super(27);
        }

        @Override
        public boolean stillValid(Player player) {
            return player instanceof ServerPlayer server && !server.isSpectator() && server.isAlive()
                && !isRemoved() && server.level() == level() && distanceToSqr(server) < 36
                && ForestRaid.canSearchCorpse(server, GuardCorpse.this);
        }
    }

}
