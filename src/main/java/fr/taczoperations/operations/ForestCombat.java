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
    private static final Map<ServerPlayer, DamageBudget> DAMAGE_BUDGET = new WeakHashMap<>();

    private static final class DamageBudget {
        long tick = Long.MIN_VALUE;
        float used;
    }

    // Balance NPC damage only; player-fired TaCZ weapons keep their original stats.
    public static void onGunHurt(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) return;
        // Apply localized ballistic protection to both players and guards.
        if (!(event.getAttacker() instanceof Pillager guard)
            || !(event.getHurtEntity() instanceof ServerPlayer player)
            || !(guard.level() instanceof ServerLevel level)
            || player.level() != level || !level.dimension().equals(ForestExpedition.FOREST)
            || !ForestRaid.currentGuard(level, guard) || !ForestRaid.isParticipant(player)) {
            if (event.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity target) {
                float protection = BallisticVest.protection(target, event.getBullet(), event.isHeadShot(), event.getBaseAmount());
                if (protection < 1F) event.setBaseAmount(event.getBaseAmount() * protection);
            }
            return;
        }

        GuardRole role = GuardRole.get(guard);
        float multiplier = role.boss() ? .65F : role == GuardRole.PMC ? .55F : .45F;
        float hitCap = role.boss() ? 10F : role == GuardRole.PMC ? 7F : 5F;
        float headshot = event.isHeadShot() ? Math.clamp(event.getHeadshotMultiplier(), 1F, 1.25F) : 1F;
        event.setHeadshotMultiplier(headshot);
        float raw = event.getBaseAmount();
        if (!Float.isFinite(raw) || raw <= 0) return;
        float damage = Math.min(hitCap, raw * multiplier * headshot);
        // A shotgun or multiple simultaneous projectiles must not bypass the cap.
        DamageBudget budget = DAMAGE_BUDGET.computeIfAbsent(player, ignored -> new DamageBudget());
        long tick = level.getGameTime();
        if (budget.tick != tick) {
            budget.tick = tick;
            budget.used = 0;
        }
        damage = Math.min(damage, Math.max(0F, 10F - budget.used));
        if (damage <= 0) {
            event.setCanceled(true);
            return;
        }
        budget.used += damage;
        float protection = BallisticVest.protection(player, event.getBullet(), event.isHeadShot(), damage);
        event.setBaseAmount(damage * protection / headshot);
        RaidInjuries.hit(player,event.getBullet(),event.isHeadShot(),damage*protection);
    }

    private static final class Aim {
        int nextShot;
        int failures;
        java.util.UUID visibleTarget;
        java.util.UUID rememberedTarget;
        net.minecraft.world.phys.Vec3 lastSeen;
        int lastSeenTick, nextManeuver, movingUntil, alertUntil;
        float previousHealth=-1;
        boolean retreating;
        int nextPatrol;
    }

    static void arm(Pillager enemy) {
        try {
            GuardRole role = GuardRole.get(enemy);
            // Per-guard loadout variation; magazine size comes from the selected gun.
            String gunId = role.gun;
            if (role == GuardRole.RAIDER || role == GuardRole.SCOUT) gunId = new String[]{"glock_17","hk_mp5a5","m870"}[enemy.getRandom().nextInt(3)];
            long roll = enemy.getUUID().getLeastSignificantBits() & Long.MAX_VALUE;
            if (role == GuardRole.RAIDER && roll % 4 == 0) gunId = "hk_mp5a5";
            if (role == GuardRole.SCOUT && roll % 5 == 0) gunId = "glock_17";
            // PMC weapons are selected from the installed TaCZ catalogue, including gun packs.
            if (role == GuardRole.PMC) {
                var guns = TimelessAPI.getAllCommonGunIndex().stream()
                    .filter(entry -> entry.getValue().getGunData() != null
                        && !entry.getValue().getGunData().getFireModeSet().isEmpty()
                        && entry.getValue().getGunData().getAmmoId() != null)
                    .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
                    .toList();
                if (!guns.isEmpty()) {
                    var selected = guns.get((int) ((roll / 4) % guns.size()));
                    // Most operators have a specialty loadout; keep 30% of the
                    // unrestricted catalogue so installed gun packs still appear.
                    var rng=new java.util.Random(enemy.getUUID().getMostSignificantBits());
                    if(rng.nextInt(100)<70){
                        var preferred=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz",PmcSpecialty.get(enemy).preferredGun(rng));
                        selected=guns.stream().filter(e->e.getKey().equals(preferred)).findFirst().orElse(selected);
                    }
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
            if(gun.isEmpty()&&role.boss())gun=OperationsShop.weapon("m4a1",30);
            if (!gun.isEmpty()) {
                if (role == GuardRole.PMC) {
                    gun = customizePmcWeapon(enemy, gun);
                }
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
            boolean hit=aim.previousHealth>=0&&guard.getHealth()<aim.previousHealth;
            aim.previousHealth=guard.getHealth();
            net.minecraft.world.entity.LivingEntity target = null;
            double range = (role.range + 12) * (role.range + 12);
            for (ServerPlayer candidate : level.players()) {
                double distance = guard.distanceToSqr(candidate);
                double detection = role.range + 12;
                if (ForestExpedition.isNightRaid(candidate)) {
                    var helmet = guard.getItemBySlot(EquipmentSlot.HEAD);
                    boolean goggles = java.util.Set.of("taczoperations:night_vision_goggles", "taczoperations:thermal_goggles")
                        .contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(helmet.getItem()).toString());
                    int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, candidate.blockPosition());
                    detection = goggles ? detection * .85 : Math.min(detection, 7 + light * 1.4);
                    if (candidate.isCrouching() && !goggles) detection *= .75;
                    if (candidate.getUUID().equals(aim.visibleTarget)) detection *= 1.25;
                }
                if (distance < range && distance < detection * detection
                    && candidate.isAlive() && !candidate.isCreative() && !candidate.isSpectator()
                    && ForestRaid.sameSession(level,guard,candidate) && guard.hasLineOfSight(candidate)) {
                    target = candidate;
                    range = distance;
                }
            }
            {
                // Evaluate opposing guards on every combat update to avoid target flicker.
                for (Pillager candidate : level.getEntitiesOfClass(Pillager.class, guard.getBoundingBox().inflate(role.range), e -> e != guard && e.isAlive() && ForestRaid.currentGuard(level, e) && ((GuardRole.get(e) == GuardRole.PMC) != (role == GuardRole.PMC)) && !GuardRole.get(e).boss())) {
                    double distance = guard.distanceToSqr(candidate);
                    if (distance < range && guard.hasLineOfSight(candidate)) { target = candidate; range = distance; }
                }
            }
            if (target == null) {
                // A recent sighting is a short-lived investigation, not wall vision.
                if(aim.lastSeen!=null&&guard.tickCount-aim.lastSeenTick<100){
                    guard.setTarget(null);operator.aim(false);aim.visibleTarget=null;
                    if(guard.tickCount%20==0&&guard.getNavigation().isDone())
                        guard.getNavigation().moveTo(aim.lastSeen.x,aim.lastSeen.y,aim.lastSeen.z,.95);
                    return;
                }
                aim.lastSeen=null;aim.rememberedTarget=null;
                guard.setTarget(null);
                aim.visibleTarget = null;
                operator.aim(false);
                if(guard.tickCount>=aim.nextPatrol&&guard.getNavigation().isDone()){
                    boolean moved=GuardPatrol.advance(level,guard,role);
                    // stagger path queries; idle at the next waypoint for a
                    // short period, retry later if terrain is not passable.
                    aim.nextPatrol=guard.tickCount+(moved?160:80)+guard.getRandom().nextInt(61);
                }
                return;
            }
            guard.setTarget(target);
            guard.getLookControl().setLookAt(target, 60, 60);
            if(guard.hasLineOfSight(target)){
                aim.lastSeen=target.position();aim.lastSeenTick=guard.tickCount;aim.rememberedTarget=target.getUUID();
                if(target instanceof ServerPlayer&&guard.tickCount>=aim.alertUntil){
                    aim.alertUntil=guard.tickCount+80;
                    alertSquad(level,guard,role,target);
                }
            }
            if(guard.tickCount<aim.movingUntil&&!guard.getNavigation().isDone()){
                operator.aim(false);return;
            }
            boolean advanced=role==GuardRole.PMC||role==GuardRole.LEADER||role==GuardRole.SOLDIER||role.boss();
            if(guard.tickCount>=aim.nextManeuver && (hit||advanced&&guard.getRandom().nextInt(12)==0)){
                aim.nextManeuver=guard.tickCount+100+guard.getRandom().nextInt(80);
                boolean cover=hit&&guard.getHealth()<=guard.getMaxHealth()*.8F;
                if(maneuver(level,guard,target,cover)){
                    aim.movingUntil=guard.tickCount+(cover?45:35);
                    aim.nextShot=Math.max(aim.nextShot,aim.movingUntil+12);
                    operator.aim(false);return;
                }
            }
            if (range > role.range * role.range || !guard.hasLineOfSight(target)) {
                aim.visibleTarget = null;
                operator.aim(false);
                if (guard.tickCount % 12 == 0) {
                    guard.getNavigation().moveTo(target, 1.05);
                }
                return;
            }

            guard.getNavigation().stop();
            operator.aim(true);
            if (!target.getUUID().equals(aim.visibleTarget)) {
                aim.visibleTarget = target.getUUID();
                // Give players a reaction window when first spotted or spotted again.
                aim.nextShot = Math.max(aim.nextShot, guard.tickCount + 16 + guard.getRandom().nextInt(13));
            }
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
                        if(guard.tickCount>=aim.nextManeuver){
                            aim.nextManeuver=guard.tickCount+80;
                            if(maneuver(level,guard,target,true))aim.movingUntil=guard.tickCount+50;
                        }
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

    private static void alertSquad(ServerLevel level,Pillager guard,GuardRole role,net.minecraft.world.entity.LivingEntity target){
        int squad=guard.getPersistentData().getInt("taczoperations_squad");
        if(squad<0)return;
        for(Pillager ally:level.getEntitiesOfClass(Pillager.class,guard.getBoundingBox().inflate(24),
            e->e!=guard&&e.isAlive()&&ForestRaid.currentGuard(level,e)
                &&e.getPersistentData().getInt("taczoperations_squad")==squad
                &&(GuardRole.get(e)==GuardRole.PMC)==(role==GuardRole.PMC))){
            Aim other=AIM.get(ally);
            if(other==null||other.visibleTarget!=null||other.lastSeen!=null&&ally.tickCount-other.lastSeenTick<80)continue;
            other.lastSeen=target.position();
            other.lastSeenTick=ally.tickCount;
            other.rememberedTarget=target.getUUID();
        }
    }

    // Bounded path checks, only on a maneuver cooldown. A guard does not shoot
    // through blocks or while moving; normal damage caps remain unchanged.
    private static boolean maneuver(ServerLevel level,Pillager guard,net.minecraft.world.entity.LivingEntity target,boolean cover){
        var delta=guard.position().subtract(target.position());
        var horizontal=new net.minecraft.world.phys.Vec3(delta.x,0,delta.z).normalize();
        if(horizontal.lengthSqr()<.01)return false;
        var side=new net.minecraft.world.phys.Vec3(-horizontal.z,0,horizontal.x);
        int sign=guard.getRandom().nextBoolean()?1:-1;
        for(int i=0;i<4;i++){
            double direction=i%2==0?sign:-sign;
            var offset=side.scale(direction*(i<2?5:8)).add(horizontal.scale(cover?4:-2));
            var candidate=guard.position().add(offset);
            for(int dy:new int[]{0,1,-1}){
                var pos=net.minecraft.core.BlockPos.containing(candidate.x,candidate.y+dy,candidate.z);
                if(!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir()||!level.getBlockState(pos.above()).isAir()
                    ||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))continue;
                if(!fr.taczoperations.worldgen.ForestPlan.inside(pos.getX(),pos.getZ())&&!fr.taczoperations.worldgen.IndustrialZone.inside(pos.getX(),pos.getZ()))continue;
                if(cover){
                    var eye=net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).add(0,guard.getEyeHeight(),0);
                    var trace=level.clip(new net.minecraft.world.level.ClipContext(eye,target.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,guard));
                    if(trace.getType()==net.minecraft.world.phys.HitResult.Type.MISS)continue;
                }
                var path=guard.getNavigation().createPath(pos,0);
                if(path!=null&&path.canReach()&&guard.getNavigation().moveTo(path,cover?1.12:1.04))return true;
            }
        }
        return false;
    }

    private static ItemStack customizePmcWeapon(Pillager guard, ItemStack base) {
        return customizeWeapon(guard,base,false);
    }
    static ItemStack customizeWeapon(Pillager guard,ItemStack base,boolean premium) {
        if (!(base.getItem() instanceof IGun gun)) return base;
        ItemStack modified = base.copy();
        try {
            // Cumulative quality thresholds: 3% full, 10% >=4, 20% >=3,
            // 30% >=2, 50% >=1. Incompatible slots are never forced.
            int roll = guard.getRandom().nextInt(100);
            int limit = premium || roll < 3 ? Integer.MAX_VALUE : roll < 10 ? 4 :
                roll < 20 ? 3 : roll < 30 ? 2 : roll < 50 ? 1 : 0;
            if (limit == 0) return base;
            var candidates = new java.util.ArrayList<>(TimelessAPI.getAllCommonAttachmentIndex());
            // Shuffle with the entity RNG so installed gun packs participate too.
            for (int i = candidates.size() - 1; i > 0; i--) {
                java.util.Collections.swap(candidates, i, guard.getRandom().nextInt(i + 1));
            }
            var installed = java.util.EnumSet.noneOf(com.tacz.guns.api.item.attachment.AttachmentType.class);
            for (var candidate : candidates) {
                if (installed.size() >= limit) break;
                var type = candidate.getValue().getType();
                if (type == null || installed.contains(type) || !gun.allowAttachmentType(modified, type)
                    || !gun.getAttachment(guard.registryAccess(), modified, type).isEmpty()
                    || !gun.getBuiltinAttachment(modified, type).isEmpty()) continue;
                ItemStack attachment = com.tacz.guns.api.item.builder.AttachmentItemBuilder.create()
                    .setId(candidate.getKey()).build();
                if (attachment.isEmpty() || !gun.allowAttachment(modified, attachment)) continue;
                gun.installAttachment(guard.registryAccess(), modified, attachment);
                if (!gun.getAttachment(guard.registryAccess(), modified, type).isEmpty()) {
                    installed.add(type);
                }
            }
            return modified;
        } catch (RuntimeException | LinkageError exception) {
            LogUtils.getLogger().warn("PMC attachment generation failed; keeping original weapon", exception);
            return base;
        }
    }

    private static void crossbow(Pillager guard) {
        AIM.remove(guard);
        GuardPouch.switchToArrows(guard);
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
    }

    private ForestCombat() {}
}
