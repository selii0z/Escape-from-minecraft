package fr.taczoperations.operations;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class ForestCombat {
    private static final ResourceLocation PISTOL = ResourceLocation.fromNamespaceAndPath("tacz", "glock_17");
    private static final int MAGAZINE = 12;
    private static final Map<Pillager, Aim> AIM = new WeakHashMap<>();

    private static final class Aim {
        int nextShot;
        int failures;
    }

    static void arm(Pillager enemy) {
        try {
            ItemStack gun = GunItemBuilder.create().setId(PISTOL).setFireMode(FireMode.SEMI)
                .setAmmoCount(MAGAZINE).setAmmoInBarrel(true).build();
            if (!gun.isEmpty()) {
                enemy.setItemSlot(EquipmentSlot.MAINHAND, gun);
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            LogUtils.getLogger().warn("TaCZ weapon unavailable for a forest guard", exception);
        }
        crossbow(enemy);
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Pillager guard)
            || !(guard.level() instanceof ServerLevel level)
            || !level.dimension().equals(ForestExpedition.FOREST)
            || !guard.getPersistentData().hasUUID(ForestRaid.ENEMY)
            || guard.tickCount % 4 != 0 || !guard.isAlive()) {
            return;
        }
        if (!ForestRaid.currentGuard(level, guard)) {
            guard.discard();
            return;
        }
        if (!(guard.getMainHandItem().getItem() instanceof IGun gun)) {
            return;
        }
        Aim aim = AIM.get(guard);
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(guard);
            if (aim == null) {
                aim = new Aim();
                AIM.put(guard, aim);
                operator.initialData();
                operator.draw(guard::getMainHandItem);
                aim.nextShot = guard.tickCount + 20;
            }
            ServerPlayer target = null;
            double range = 32 * 32;
            for (ServerPlayer candidate : level.players()) {
                double distance = guard.distanceToSqr(candidate);
                if (distance < range && candidate.isAlive() && !candidate.isCreative() && !candidate.isSpectator()
                    && ForestRaid.isParticipant(candidate)) {
                    target = candidate;
                    range = distance;
                }
            }
            if (target == null) {
                operator.aim(false);
                return;
            }
            guard.setTarget(target);
            guard.getLookControl().setLookAt(target, 60, 60);
            if (range > 22 * 22 || !guard.hasLineOfSight(target)) {
                operator.aim(false);
                if (guard.tickCount % 12 == 0) {
                    guard.getNavigation().moveTo(target, 1.05);
                }
                return;
            }

            guard.getNavigation().stop();
            operator.aim(true);
            if (guard.tickCount < aim.nextShot) {
                return;
            }
            double dx = target.getX() - guard.getX();
            double dy = target.getY() + target.getBbHeight() * .62 - guard.getEyeY();
            double dz = target.getZ() - guard.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            ShootResult result = operator.shoot(() -> pitch, () -> yaw);
            aim.nextShot = guard.tickCount + 24 + guard.getRandom().nextInt(17);
            switch (result) {
                case SUCCESS -> aim.failures = 0;
                case NO_AMMO -> {
                    gun.setCurrentAmmoCount(guard.getMainHandItem(), MAGAZINE);
                    aim.nextShot += 24;
                }
                case NOT_DRAW -> operator.draw(guard::getMainHandItem);
                case NEED_BOLT -> operator.bolt();
                case UNKNOWN_FAIL, ID_NOT_EXIST, NOT_GUN -> {
                    if (++aim.failures >= 3) {
                        crossbow(guard);
                    }
                }
                default -> { }
            }
        } catch (RuntimeException | LinkageError exception) {
            LogUtils.getLogger().warn("Forest guard switched to a crossbow after a TaCZ error", exception);
            crossbow(guard);
        }
    }

    private static void crossbow(Pillager guard) {
        AIM.remove(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
    }

    private ForestCombat() {}
}
