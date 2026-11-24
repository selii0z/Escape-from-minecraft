package fr.taczoperations.operations;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
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
    private static final Map<Pillager, Aim> AIM = new WeakHashMap<>();

    private static final class Aim {
        int nextShot;
        int failures;
    }

    static void arm(Pillager enemy) {
        try {
            GuardRole role = GuardRole.get(enemy);
            // Deterministic per-guard loadout variation; ammunition stays
            // compatible with the assigned role's reserve pouch.
            String gunId = role.gun;
            if (role == GuardRole.RAIDER || role == GuardRole.SCOUT) gunId = new String[]{"glock_17","hk_mp5a5","m870"}[enemy.getRandom().nextInt(3)];
            long roll = enemy.getUUID().getLeastSignificantBits() & Long.MAX_VALUE;
            if (role == GuardRole.RAIDER && roll % 4 == 0) gunId = "hk_mp5a5";
            if (role == GuardRole.SCOUT && roll % 5 == 0) gunId = "glock_17";
            // One guard in four receives a weapon from the complete installed
            // TaCZ catalogue; gun packs are included automatically.
            if (role == GuardRole.PMC) {
                var guns = TimelessAPI.getAllCommonGunIndex().stream()
                    .filter(entry -> entry.getValue().getGunData() != null
                        && !entry.getValue().getGunData().getFireModeSet().isEmpty()
                        && entry.getValue().getGunData().getAmmoId() != null)
                    .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
                    .toList();
                if (!guns.isEmpty()) {
                    var selected = guns.get((int) ((roll / 4) % guns.size()));
                    gunId = selected.getKey().toString();
                }
            }
            net.minecraft.resources.ResourceLocation weaponId = gunId.contains(":")
                ? net.minecraft.resources.ResourceLocation.parse(gunId)
                : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz", gunId);
            int rounds = TimelessAPI.getCommonGunIndex(weaponId)
                .map(index -> Math.max(1, index.getGunData().getAmmoAmount()))
                .orElse(role.magazine);
            ItemStack gun = OperationsShop.weapon(weaponId, rounds);
            if (!gunId.equals(role.gun)) {
                enemy.getPersistentData().putString("taczoperations_guard_weapon", gunId);
            }
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
        GuardRole role = GuardRole.get(guard);
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
            net.minecraft.world.entity.LivingEntity target = null;
            double range = (role.range + 12) * (role.range + 12);
            for (ServerPlayer candidate : level.players()) {
                double distance = guard.distanceToSqr(candidate);
                if (distance < range && candidate.isAlive() && !candidate.isCreative() && !candidate.isSpectator()
                    && ForestRaid.isParticipant(candidate)) {
                    target = candidate;
                    range = distance;
                }
            }
            if (guard.tickCount % 12 == 0 || target == null) {
                for (Pillager candidate : level.getEntitiesOfClass(Pillager.class, guard.getBoundingBox().inflate(role.range), e -> e != guard && e.isAlive() && ForestRaid.currentGuard(level, e) && ((GuardRole.get(e) == GuardRole.PMC) != (role == GuardRole.PMC)) && GuardRole.get(e) != GuardRole.TRACKER)) {
                    double distance = guard.distanceToSqr(candidate);
                    if (distance < range && guard.hasLineOfSight(candidate)) { target = candidate; range = distance; }
                }
            }
            if (target == null) {
                guard.setTarget(null);
                operator.aim(false);
                if (guard.tickCount % 80 == 0 && guard.getNavigation().isDone()) {
                    var home = guard.getPersistentData();
                    if (home.contains("guard_home_x")) {
                        guard.getNavigation().moveTo(home.getInt("guard_home_x") + guard.getRandom().nextInt(role == GuardRole.PMC ? 41 : 11) - (role == GuardRole.PMC ? 20 : 5),
                            home.getInt("guard_home_y"), home.getInt("guard_home_z") + guard.getRandom().nextInt(11) - 5, .75);
                    }
                }
                return;
            }
            guard.setTarget(target);
            guard.getLookControl().setLookAt(target, 60, 60);
            if (range > role.range * role.range || !guard.hasLineOfSight(target)) {
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
            aim.nextShot = guard.tickCount + role.pause + guard.getRandom().nextInt(17);
            switch (result) {
                case SUCCESS -> aim.failures = 0;
                case NO_AMMO -> {
                    int magazineCapacity = TimelessAPI.getCommonGunIndex(gun.getGunId(guard.getMainHandItem()))
                        .map(index -> Math.max(1, index.getGunData().getAmmoAmount()))
                        .orElse(role.magazine);
                    int loaded = GuardPouch.consume(guard, magazineCapacity, false);
                    if (loaded > 0) {
                        gun.setCurrentAmmoCount(guard.getMainHandItem(), loaded);
                        aim.nextShot += 24;
                    } else {
                        operator.aim(false);
                    }
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
        GuardPouch.switchToArrows(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
    }

    private ForestCombat() {}
}
