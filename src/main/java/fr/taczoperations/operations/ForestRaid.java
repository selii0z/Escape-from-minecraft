package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.entity.GuardCorpse;
import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.ForestRaidAnchors;
import fr.taczoperations.worldgen.IndustrialZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ForestRaid extends SavedData {
    static final String ENEMY = "taczoperations_enemy";
    private static final String PLAYER = "taczoperations_raid_player";
    private static final String CORPSE = "taczoperations_raid_corpse";
    private static final String DROP = "taczoperations_raid_drop";
    private static final String CRATE = "taczoperations_raid_crate";
    private static final String CRATE_INDEX = "taczoperations_raid_crate_index";
    private static final String AIRDROP_VISUAL = "taczoperations_airdrop_visual";
    private static final Factory<ForestRaid> FACTORY = new Factory<>(ForestRaid::new, ForestRaid::read);

    private static final long RECONNECT_GRACE_MS = 10 * 60 * 1000L;
    private final Set<UUID> participants = new HashSet<>();
    private long offlineSince;
    private UUID id;
    private long seed;
    private boolean industrial;
    private long dropDue, dropLanding;
    private int dropAttempts;
    private int reinforcementKills,reinforcementWaves,reinforcementLimit,reinforcementPending;
    private long reinforcementDue,reinforcementExpiry,reinforcementCooldown;
    private BlockPos dropPos;
    private int fieldEvent,fieldEventAttempts;
    private boolean bossConvergence;
    private long fieldEventDue;
    private RaidAtmosphere atmosphere;
    private long ambientDue;
    private int ambientSequence;
    private BlockPos fieldEventPos;
    private UUID dropVisual;
    private boolean dropLanded;
    private final BitSet vaultPrepared = new BitSet(), vaultOpened = new BitSet();
    private BitSet crates = new BitSet();
    private final Set<BlockPos> spawnedContainers=new HashSet<>();
    private BitSet enemies = new BitSet();
    private BitSet pmcGroups = new BitSet();
    private List<ForestPlan.Point> encounterPoints = List.of();
    private List<ForestRaidAnchors.Mark> locations = List.of();

    private static ForestRaid get(ServerLevel level,boolean industrial) {
        var raid=level.getDataStorage().computeIfAbsent(FACTORY,industrial?"taczoperations_raid_industrial":"taczoperations_raid");
        raid.industrial=industrial;
        return raid;
    }
    private static ForestRaid at(ServerLevel level,BlockPos pos){
        return get(level,IndustrialZone.inside(pos.getX(),pos.getZ()));
    }
    private static ForestRaid forPlayer(ServerPlayer p){
        var level=p.server.getLevel(ForestExpedition.FOREST);
        if(level==null)return null;
        for(boolean industrial:new boolean[]{false,true}){
            var raid=get(level,industrial);
            if(raid.participants.contains(p.getUUID())&&raid.matches(p.getPersistentData(),PLAYER))return raid;
        }
        return null;
    }
    private boolean owns(ServerPlayer p){
        return participants.contains(p.getUUID())&&matches(p.getPersistentData(),PLAYER)
            &&p.level().dimension().equals(ForestExpedition.FOREST)&&ForestExpedition.hasReturnPoint(p);
    }
    private boolean inArea(BlockPos pos){
        return industrial?IndustrialZone.inside(pos.getX(),pos.getZ()):ForestPlan.inside(pos.getX(),pos.getZ());
    }
    private boolean ownsEntity(net.minecraft.world.entity.Entity e){
        var data=e.getPersistentData();
        return matches(data,ENEMY)||matches(data,CORPSE)||matches(data,DROP)||matches(data,AIRDROP_VISUAL);
    }
    private static boolean raidEntity(net.minecraft.world.entity.Entity e){
        return e instanceof GuardCorpse||e instanceof ItemEntity||e.getPersistentData().hasUUID(AIRDROP_VISUAL)
            ||e instanceof Pillager guard&&guard.getPersistentData().hasUUID(ENEMY);
    }

    static void begin(ServerLevel level, ServerPlayer player, ServerPlayer partner, boolean industrial) {
        ForestRaid raid = get(level,industrial);
        // A previous raid may have left corpses in loaded chunks. Remove them
        // before assigning a new session ID, so their inventories cannot be looted.
        java.util.List<net.minecraft.world.entity.Entity> expired = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if(raidEntity(entity)&&(raid.inArea(entity.blockPosition())||raid.ownsEntity(entity)))expired.add(entity);
        }
        expired.forEach(net.minecraft.world.entity.Entity::discard);
        raid.id = UUID.randomUUID();
        raid.participants.clear();
        raid.participants.add(player.getUUID());
        if (partner != null) raid.participants.add(partner.getUUID());
        raid.offlineSince = 0;
        raid.seed = level.getRandom().nextLong();
        raid.bossConvergence=new Random(raid.seed^0x72c6aee3L).nextInt(10)==0;
        if(raid.bossConvergence){
            var warning=Component.literal("ALERTE EXCEPTIONNELLE · Convergence des boss : tous les chefs sont présents sur cette carte.");
            player.sendSystemMessage(warning);
            if(partner!=null)partner.sendSystemMessage(warning);
        }
        raid.atmosphere=new RaidAtmosphere(raid.seed,level.getGameTime(),ForestExpedition.initialWeather(player));
        raid.ambientDue=level.getGameTime()+400+new Random(raid.seed^0x88fae3L).nextInt(401);
        raid.ambientSequence=0;
        raid.industrial = industrial;
        raid.crates.clear();
        raid.spawnedContainers.clear();
        raid.vaultPrepared.clear();
        raid.vaultOpened.clear();
        raid.enemies.clear();
        raid.encounterPoints = chooseEncounterPoints(industrial, raid.seed);
        raid.pmcGroups = assignFactions(raid.encounterPoints, raid.seed);
        raid.dropDue = level.getGameTime() + 20L * (45 + new Random(raid.seed).nextInt(46));
        raid.dropLanding = 0;
        // Independent seeded roll: not every expedition has a bonus event.
        Random eventRandom=new Random(raid.seed^0x54ad733fe82L);
        int eventRoll=eventRandom.nextInt(100);
        raid.fieldEvent=eventRoll<40?0:eventRoll<65?1:eventRoll<85?2:3;
        raid.fieldEventDue=level.getGameTime()+20L*(120+eventRandom.nextInt(121));
        raid.fieldEventAttempts=0;
        raid.fieldEventPos=null;
        raid.dropAttempts = 0;
        raid.reinforcementKills=raid.reinforcementWaves=raid.reinforcementPending=0;
        raid.reinforcementLimit=partner==null?1:2;
        raid.reinforcementDue=raid.reinforcementExpiry=0;
        raid.reinforcementCooldown=level.getGameTime()+1200;
        raid.dropPos = null;
        raid.dropVisual = null;
        raid.dropLanded = false;
        raid.locations = raid.industrial ? IndustrialZone.loot(raid.seed) : ForestRaidAnchors.pickLoot(raid.seed);
        player.getPersistentData().putUUID(PLAYER, raid.id);
        SupplyContract.departure(player);
        if (partner != null) {
            partner.getPersistentData().putUUID(PLAYER, raid.id);
            SupplyContract.departure(partner);
        }
        // Clear stale containers in the chunks around the spawn immediately,
        // rather than waiting for the first scheduled player tick.
        raid.removeOldCrates(level, player);
        if (partner != null) {
            raid.removeOldCrates(level, partner);
        }
        raid.setDirty();
    }

    // Offline players retain their raid slot for a limited reconnection window.
    static void finishParticipant(ServerPlayer player) {
        ForestRaid raid=forPlayer(player);
        if(raid==null)return;
        raid.participants.remove(player.getUUID());
        raid.setDirty();
    }

    static void cleanupWhenEmpty(ServerLevel level) {
        for(boolean industrial:new boolean[]{false,true}){
            ForestRaid raid=get(level,industrial);
            if(raid.id!=null&&raid.participants.isEmpty())raid.closeSession(level);
        }
    }

    private void closeSession(ServerLevel level) {
        java.util.List<net.minecraft.world.entity.Entity> expired = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if(raidEntity(entity)&&(inArea(entity.blockPosition())||ownsEntity(entity)))expired.add(entity);
        }
        expired.forEach(net.minecraft.world.entity.Entity::discard);
        // Never force-load chunks just to clean a finished session. Unloaded
        // tagged containers remain inaccessible and are removed on later visits.
        for(BlockPos pos:spawnedContainers)if(level.hasChunkAt(pos))removeTaggedContainer(level,pos,true);
        spawnedContainers.clear();
        if(industrial)for(var vault:IndustrialZone.VAULTS){
            BlockPos door=vault.door();
            if(level.hasChunkAt(door)){
                ForestProtection.setVaultDoor(level,door,true);
                ForestProtection.setVaultDoor(level,door.above(),true);
            }
        }
        id = null;
        atmosphere=null;
        participants.clear();
        offlineSince = 0;
        setDirty();
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.serverLevel().dimension().equals(ForestExpedition.FOREST)) return;
        if (isParticipant(player)) {
            player.sendSystemMessage(Component.literal(
                "Expédition reprise. Ton raid est toujours actif : rejoins l'extraction pour conserver ton butin."));
        } else {
            player.sendSystemMessage(Component.literal(
                "Ton raid a expiré. Retour au départ : matériel initial encore détenu conservé, surplus perdu, coffre sécurisé protégé."));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.serverLevel().dimension().equals(ForestExpedition.FOREST)) return;
        ForestRaid raid=forPlayer(player);
        if(raid==null)return;
        // Exclude the disconnecting player, which can still appear in level.players().
        boolean othersOnline = player.serverLevel().players().stream().anyMatch(other ->
            other != player && raid.owns(other));
        if (!othersOnline) {
            raid.offlineSince = System.currentTimeMillis();
            raid.setDirty();
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        // One maintenance pass per second is sufficient for a ten-minute
        // grace period; avoid scanning the raid dimension 20 times a second.
        if (event.getServer().getTickCount() % 20 != 0) return;
        ServerLevel level = event.getServer().getLevel(ForestExpedition.FOREST);
        if (level == null) return;
        for(boolean industrial:new boolean[]{false,true})get(level,industrial).maintain(level);
    }

    private void maintain(ServerLevel level){
        ForestRaid raid=this;
        if(raid.id==null)return;
        if(raid.participants.removeIf(uuid->{
            ServerPlayer p=level.getServer().getPlayerList().getPlayer(uuid);
            return p!=null&&p.isAlive()&&!raid.owns(p);
        }))raid.setDirty();
        if(raid.participants.isEmpty()){raid.closeSession(level);return;}
        boolean online=level.players().stream().anyMatch(raid::owns);
        if (online) {
            raid.tickAirdrop(level);
            raid.tickReinforcements(level);
            raid.tickFieldEvent(level);
            if(raid.atmosphere!=null)raid.atmosphere.tick(level,level.players().stream().filter(raid::owns).toList());
            long now=level.getGameTime();
            if(now>=raid.ambientDue){
                Random audioRandom=new Random(raid.seed^0x16ca02bL^raid.ambientSequence++);
                raid.ambientDue=now+400+audioRandom.nextInt(1001);
                RaidAudio.ambient(level,level.players().stream().filter(p->raid.owns(p)&&p.isAlive()&&!p.isSpectator()).toList(),
                    raid.industrial,raid.atmosphere,audioRandom);
            }
            if (raid.offlineSince != 0) {
                raid.offlineSince = 0;
                raid.setDirty();
            }
        } else if (raid.offlineSince == 0) {
            raid.offlineSince = System.currentTimeMillis();
            raid.setDirty();
        } else if (System.currentTimeMillis() - raid.offlineSince >= RECONNECT_GRACE_MS) {
            raid.closeSession(level);
        }
    }

    private void tickFieldEvent(ServerLevel level){
        long now=level.getGameTime();
        if(fieldEvent==0||fieldEventPos!=null||now<fieldEventDue||fieldEventAttempts>=12)return;
        var players=level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&owns(p)).toList();
        if(players.isEmpty())return;
        fieldEventDue=now+100;
        Random rng=new Random(seed^0x295bb8aL^fieldEventAttempts++);
        var anchors=new ArrayList<>(industrial?IndustrialZone.GUARDS:ForestRaidAnchors.ENEMIES);
        java.util.Collections.shuffle(anchors,rng);
        int checked=0;
        for(var anchor:anchors){
            if(checked++>=12)break;
            var local=new ForestPlan.Point(anchor.x()+rng.nextInt(17)-8,anchor.z()+rng.nextInt(17)-8);
            BlockPos center=industrial?ForestExpedition.industrialPosition(local):ForestLandmarks.position(local);
            if(!level.hasChunkAt(center)||players.stream().noneMatch(p->center.distToCenterSqr(p.position())<100*100))continue;
            BlockPos place=findFloor(level,center,3,true);
            if(place==null||!inArea(place)||!validEncounterPosition(industrial,place))continue;
            if(dropPos!=null&&dropPos.distSqr(place)<12*12)continue;
            if(players.stream().anyMatch(p->place.distToCenterSqr(p.position())<28*28))continue;
            if(spawnedContainers.stream().anyMatch(p->p.distSqr(place)<8*8))continue;
            if(industrial&&IndustrialZone.VAULTS.stream().anyMatch(v->v.door().distSqr(place)<18*18))continue;
            if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(place).inflate(2)).isEmpty())continue;
            if(createFieldCache(level,place,rng)){
                fieldEventPos=place.immutable();
                for(var p:players){
                    RaidAudio.alert(p);
                    p.sendSystemMessage(Component.literal("SIGNAL · "+fieldEventTitle()+" · X "+place.getX()+" Z "+place.getZ()+" (réserve abandonnée)"));
                }
                setDirty();return;
            }
        }
    }
    private String fieldEventTitle(){
        return switch(fieldEvent){
            case 1->"Réserve de secours";
            case 2->"Dépôt de maintenance";
            default->"Archives confidentielles";
        };
    }
    private boolean createFieldCache(ServerLevel level,BlockPos pos,Random rng){
        if(!level.getBlockState(pos).isAir())return false;
        int furniture=fieldEvent==1?0:fieldEvent==2?1:5;
        if(!ForestProtection.setRaidContainer(level,pos,OperationsMod.FURNITURE.get(furniture).get().defaultBlockState()))return false;
        if(!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity cache))return false;
        cache.getPersistentData().putUUID(CRATE,id);
        cache.getPersistentData().putInt(CRATE_INDEX,-2);
        spawnedContainers.add(pos.immutable());
        ItemStack label=new ItemStack(Items.BARREL);
        label.set(DataComponents.CUSTOM_NAME,Component.literal(fieldEventTitle()));
        cache.applyComponentsFromItemStack(label);
        var indices=new ArrayList<Integer>();
        for(int i=0;i<27;i++)indices.add(i);
        java.util.Collections.shuffle(indices,rng);
        int cursor=0;
        int category=fieldEvent==1?0:fieldEvent==2?1:3;
        int count=2+rng.nextInt(3);
        for(int i=0;i<count;i++){
            TradeLoot loot=TradeLoot.roll(rng,category,0);
            if(loot!=null)cache.setItem(indices.get(cursor++),loot.stack());
        }
        if(fieldEvent==1){
            String[] medicines={"bandage","salewa","hemostat","splint","medkit"};
            for(int i=0;i<2;i++)cache.setItem(indices.get(cursor++),PmcSpecialty.medicine(medicines[rng.nextInt(medicines.length)],1+rng.nextInt(2)));
        }else if(fieldEvent==2){
            cache.setItem(indices.get(cursor++),new ItemStack(Items.IRON_INGOT,2+rng.nextInt(5)));
            if(rng.nextInt(100)<30)cache.setItem(indices.get(cursor++),BallisticVest.roll(rng,false));
        }else{
            cache.setItem(indices.get(cursor++),RaidRelic.roll(rng).stack());
            if(rng.nextInt(100)<25)cache.setItem(indices.get(cursor++),RaidRelic.roll(rng).stack());
        }
        cache.setChanged();
        return true;
    }

    private void tickReinforcements(ServerLevel level) {
        long now=level.getGameTime();
        var players=level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&owns(p)).toList();
        if(players.isEmpty())return;
        if(reinforcementPending==0){
            if(reinforcementWaves>=reinforcementLimit||now<reinforcementCooldown
                ||reinforcementKills<(reinforcementWaves+1)*10)return;
            reinforcementWaves++;
            reinforcementPending=reinforcementLimit==1?2:3;
            reinforcementDue=now+600;
            reinforcementExpiry=reinforcementDue+900;
            for(var p:players){RaidAudio.alert(p);p.sendSystemMessage(Component.literal("ALERTE · Des renforts ennemis approchent. Reste sur tes gardes."));}
            setDirty();
            return;
        }
        if(now<reinforcementDue)return;
        if(now>=reinforcementExpiry){
            reinforcementPending=0;reinforcementCooldown=now+1800;setDirty();return;
        }
        // Do not force-load terrain or exceed one reinforcement spawn per pass.
        var anchors=new ArrayList<>(industrial?IndustrialZone.GUARDS:ForestRaidAnchors.ENEMIES);
        Random rng=new Random(seed^now^reinforcementWaves);
        java.util.Collections.shuffle(anchors,rng);
        int checked=0;
        for(var anchor:anchors){
            if(checked++>=12)break;
            BlockPos center=industrial?ForestExpedition.industrialPosition(anchor):ForestLandmarks.position(anchor);
            if(!level.hasChunkAt(center))continue;
            if(players.stream().noneMatch(p->center.distToCenterSqr(p.position())<=110*110))continue;
            for(int attempt=0;attempt<3;attempt++){
                var local=new ForestPlan.Point(anchor.x()+rng.nextInt(17)-8,anchor.z()+rng.nextInt(17)-8);
                BlockPos around=industrial?ForestExpedition.industrialPosition(local):ForestLandmarks.position(local);
                BlockPos place=findFloor(level,around,3,true);
                if(place==null||!validEncounterPosition(industrial,place))continue;
                if(players.stream().anyMatch(p->place.distToCenterSqr(p.position())<45*45))continue;
                if(players.stream().anyMatch(p->{
                    var eye=net.minecraft.world.phys.Vec3.atBottomCenterOf(place).add(0,1.6,0);
                    return level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),eye,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType()
                        ==net.minecraft.world.phys.HitResult.Type.MISS;
                }))continue;
                if(!level.getEntitiesOfClass(Pillager.class,new net.minecraft.world.phys.AABB(place).inflate(2),
                    e->e.isAlive()&&currentGuard(level,e)).isEmpty())continue;
                if(spawnGuard(level,place,GuardRole.PMC,1000+reinforcementWaves)){
                    reinforcementPending--;
                    if(reinforcementPending==0)reinforcementCooldown=now+1800;
                    setDirty();return;
                }
            }
        }
    }

    private void tickAirdrop(ServerLevel level) {
        long now = level.getGameTime();
        if (dropPos == null && now >= dropDue && dropAttempts<8) {
            dropDue=now+100;
            var points = industrial ? IndustrialZone.GUARDS : ForestRaidAnchors.dropPoints();
            Random rng = new Random(seed ^ 0x5f9238acL ^ dropAttempts++);
            var point = points.get(rng.nextInt(points.size()));
            int origin = industrial ? IndustrialZone.ORIGIN : ForestPlan.ORIGIN;
            int cx = origin + point.x(), cz = origin + point.z();
            // Generate at most one destination chunk per maintenance pass.
            level.getChunk(cx >> 4, cz >> 4);
            for (int attempt = 0; attempt < 24 && dropPos == null; attempt++) {
                int x = cx + rng.nextInt(17)-8, z = cz + rng.nextInt(17)-8;
                if (!level.hasChunk(x >> 4, z >> 4) ||
                    !(industrial ? IndustrialZone.inside(x,z) : ForestPlan.inside(x,z))) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x,z);
                BlockPos p = new BlockPos(x,y,z);
                if (y <= ForestPlan.WATER || !level.getBlockState(p).isAir()
                    || !level.getBlockState(p.above()).isAir() || !level.canSeeSky(p)
                    || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                    || level.getBlockState(p.below()).is(Blocks.GOLD_BLOCK)
                    || level.getBlockState(p.below()).is(Blocks.MOSSY_STONE_BRICKS)) continue;
                dropPos = p;
            }
            if (dropPos != null) {
                dropLanding = now + 240;
                var visual = EntityType.BLOCK_DISPLAY.create(level);
                if (visual != null) {
                    CompoundTag nbt = new CompoundTag();
                    nbt.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.BARREL.defaultBlockState()));
                    nbt.putInt("teleport_duration", 20);
                    visual.load(nbt);
                    visual.setPos(dropPos.getX(), dropPos.getY()+24, dropPos.getZ());
                    visual.getPersistentData().putUUID(AIRDROP_VISUAL,id);
                    if (level.addFreshEntity(visual)) dropVisual = visual.getUUID();
                }
                for (ServerPlayer p : level.players()) if (owns(p))
                    p.sendSystemMessage(Component.literal("ALERTE · Largage de ravitaillement détecté ! X "
                        + dropPos.getX()+" Y "+dropPos.getY()+" Z "+dropPos.getZ()));
            }
        }
        if (dropPos != null && !dropLanded) {
            if (now >= dropLanding) {
                if (!level.hasChunkAt(dropPos)) level.getChunk(dropPos.getX() >> 4, dropPos.getZ() >> 4);
                removeTaggedContainer(level,dropPos,false);
                if (createAirdrop(level)) {
                    dropLanded = true;
                    if (dropVisual != null && level.getEntity(dropVisual) != null) level.getEntity(dropVisual).discard();
                }
            } else {
                double y = dropPos.getY()+Math.max(0, (dropLanding-now)/10.0);
                if (dropVisual != null && level.getEntity(dropVisual) != null)
                    level.getEntity(dropVisual).setPos(dropPos.getX(),y,dropPos.getZ());
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    dropPos.getX()+.5,y+1,dropPos.getZ()+.5,4,.3,.3,.3,.01);
            }
        }
        if (dropLanded && dropPos != null && level.hasChunkAt(dropPos))
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                dropPos.getX()+.5,dropPos.getY()+1,dropPos.getZ()+.5,2,.25,.2,.25,.01);
        RaidDropPayload packet = dropPos == null ? new RaidDropPayload(false,false,0,0,0)
            : new RaidDropPayload(true,dropLanded,dropPos.getX(),dropPos.getY(),dropPos.getZ());
        for (ServerPlayer p : level.players()) if (owns(p))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,packet);
    }

    private boolean createAirdrop(ServerLevel level) {
        if (level.getBlockEntity(dropPos) instanceof RandomizableContainerBlockEntity existing
            && matches(existing.getPersistentData(),CRATE) && existing.getPersistentData().getInt(CRATE_INDEX) == -1)
            return true;
        if (!level.getBlockState(dropPos).isAir()
            || !ForestProtection.setRaidContainer(level,dropPos,Blocks.BARREL.defaultBlockState())
            || !(level.getBlockEntity(dropPos) instanceof RandomizableContainerBlockEntity barrel)) return false;
        Random rng = new Random(seed ^ 0x62B64A31L);
        barrel.getPersistentData().putUUID(CRATE,id);
        barrel.getPersistentData().putInt(CRATE_INDEX,-1);
        spawnedContainers.add(dropPos.immutable());
        ItemStack label = new ItemStack(Items.BARREL);
        label.set(DataComponents.CUSTOM_NAME,Component.literal("Largage militaire · Butin rare"));
        barrel.applyComponentsFromItemStack(label);
        var guns = new ArrayList<>(com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex());
        guns.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
        guns.removeIf(e -> e.getValue().getGunData() == null || e.getValue().getGunData().getFireModeSet().isEmpty());
        for (int i=0;i<2 && !guns.isEmpty();i++) {
            var gun = guns.remove(rng.nextInt(guns.size()));
            var data = gun.getValue().getGunData();
            barrel.setItem(i,OperationsShop.weapon(gun.getKey(),Math.max(1,data.getAmmoAmount())));
            if (data.getAmmoId()!=null) barrel.setItem(3+i,com.tacz.guns.api.item.builder.AmmoItemBuilder.create()
                .setId(data.getAmmoId()).setCount(48).build());
        }
        var attachments = new ArrayList<>(com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex());
        attachments.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
        for (int i=0;i<3 && !attachments.isEmpty();i++) {
            var a = attachments.remove(rng.nextInt(attachments.size()));
            barrel.setItem(6+i,com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(a.getKey()).build());
        }
        barrel.setItem(9,BallisticVest.roll(rng,true));
        barrel.setItem(10,BallisticVest.roll(EquipmentSlot.HEAD,rng,true));
        barrel.setItem(17,BallisticVest.roll(rng.nextBoolean()?EquipmentSlot.LEGS:EquipmentSlot.FEET,rng,true));
        barrel.setItem(11,new ItemStack(Items.GOLDEN_APPLE,2));
        barrel.setItem(12,RaidRelic.CIRCUIT.stack());
        barrel.setItem(13,RaidRelic.values()[5+rng.nextInt(4)].stack());
        barrel.setItem(14,RaidRelic.roll(rng).stack());
        if (rng.nextInt(100)<5) barrel.setItem(15,RaidRelic.OMEGA.stack());
        if (rng.nextInt(100)<8) barrel.setItem(16,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse("taczoperations:night_vision_goggles"))));
        barrel.setChanged();
        return true;
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)
            || !(event.getEntity() instanceof ServerPlayer respawned)
            || !original.serverLevel().dimension().equals(ForestExpedition.FOREST)) {
            return;
        }
        // Raid deaths must not affect the vanilla Minecraft death statistic.
        // The vanilla death has already been counted when the clone event fires.
        boolean raidDeath=forPlayer(original)!=null;
        if (raidDeath) {
            // Explicitly transfer Escape progression before applying the penalty:
            // a respawned player may not yet have the original persistent account.
            CompoundTag oldRoot = original.getPersistentData();
            CompoundTag oldPersisted = oldRoot.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            CompoundTag oldAccount = oldPersisted.getCompound("taczoperations");
            CompoundTag newRoot = respawned.getPersistentData();
            CompoundTag newPersisted = newRoot.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            newPersisted.put("taczoperations", oldAccount.copy());
            newRoot.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, newPersisted);

            var deaths = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS);
            var statistics = respawned.getStats();
            statistics.setValue(respawned, deaths,
                Math.max(0, statistics.getValue(deaths) - 1));

            int adjustment = SupplyContract.finalizeRaidXp(respawned, oldRoot, false);
            long base = (long) Math.max(0, oldRoot.getInt("taczoperations_run_kill_xp"))
                + Math.max(0, oldRoot.getInt("taczoperations_run_search_xp"));
            long started = oldRoot.getLong("taczoperations_run_started");
            long duration = started > 0 ? Math.max(0, (original.server.overworld().getGameTime() - started) / 20) : 0;
            SupplyContract.saveRaidReport(respawned, false, duration,
                oldRoot.getInt("taczoperations_raid_kills"),
                oldRoot.getInt("taczoperations_run_crates"),
                oldRoot.getInt("taczoperations_run_contracts"),
                oldRoot.getInt("taczoperations_run_kill_xp"),
                oldRoot.getInt("taczoperations_run_search_xp"),
                oldRoot.getInt("taczoperations_run_contract_xp"), adjustment,
                oldRoot.contains("taczoperations_run_xp_before") ? oldRoot.getInt("taczoperations_run_xp_before") : -1);
            // Deliver the UI only after the new player has fully respawned.
            respawned.getPersistentData().putBoolean("taczoperations_pending_failed_report", true);
            respawned.sendSystemMessage(Component.literal(
                "Raid échoué · +" + (base + oldRoot.getInt("taczoperations_run_contract_xp") + adjustment)
                    + " XP Escape (malus " + adjustment + "). Bilan affiché à l'écran."));
        }
        // The normal death-drop process has already run. Do not duplicate
        // equipment into a custom corpse or copy the raid token on respawn.
        clearPlayer(respawned);
        RaidLootValue.clear(respawned);
        ForestExpedition.clearReturnPoint(respawned);
        // A surviving duo member can still collect vanilla death drops.
        finishParticipant(original);
        clearPlayer(original);
        cleanupWhenEmpty(original.serverLevel());
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.getPersistentData().getBoolean("taczoperations_pending_failed_report")) return;
        player.getPersistentData().remove("taczoperations_pending_failed_report");
        // Remove stale run state only after its report has been persisted.
        for (String key : new String[] {
            "taczoperations_run_kill_xp", "taczoperations_run_search_xp",
            "taczoperations_run_crates", "taczoperations_run_contracts", "taczoperations_run_contract_xp",
            "taczoperations_run_started", "taczoperations_run_xp_finalized", "taczoperations_run_xp_before",
            "taczoperations_raid_kills", "taczoperations_raid_boss",
            "taczoperations_relic_baseline"
        }) player.getPersistentData().remove(key);

        // The same delayed delivery path handles both success and failure.
    }

    private static final java.util.Map<ServerPlayer, Integer> REPORT_READY = new java.util.WeakHashMap<>();

    public static void deliverReport(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var account = SupplyContract.account(player);
        if (!account.getBoolean("pending_raid_report") || !player.isAlive()
            || player.serverLevel().dimension().equals(ForestExpedition.FOREST)) {
            REPORT_READY.remove(player);
            return;
        }
        int ready = REPORT_READY.computeIfAbsent(player, p -> p.tickCount + 40);
        if (player.tickCount < ready) return;
        CompoundTag report = account.getCompound("last_raid_report");
        if (!report.contains("final_xp")) {
            account.remove("pending_raid_report");
            REPORT_READY.remove(player);
            return;
        }
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new RaidResultPayload(report.getBoolean("success"), report.getLong("duration_seconds"),
                    report.getInt("kills"), report.getInt("crates"), report.getInt("contracts"),
                    report.getInt("combat_xp"), report.getInt("search_xp"),
                    report.getInt("contract_xp"), report.getInt("adjustment_xp"),
                    report.contains("xp_before") ? report.getInt("xp_before") : -1,
                    report.contains("xp_after") ? report.getInt("xp_after") : -1,
                    report.contains("loot_value")?report.getLong("loot_value"):-1,
                    report.getInt("loot_count")));
            account.remove("pending_raid_report");
            REPORT_READY.remove(player);
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Raid report delivery failed; retained for retry", exception);
            REPORT_READY.put(player, player.tickCount + 100);
        }
    }

    static boolean hasActiveSession(ServerLevel level,boolean industrial) {
        ForestRaid raid=get(level,industrial);
        if (raid.id == null) return false;
        if (raid.participants.removeIf(uuid -> {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
            return online != null && !raid.owns(online);
        })) raid.setDirty();
        if (raid.participants.isEmpty()) raid.closeSession(level);
        return raid.id != null;
    }

    static boolean isParticipant(ServerPlayer player) {
        if(!player.level().dimension().equals(ForestExpedition.FOREST))return false;
        var raid=forPlayer(player);
        return raid!=null&&raid.owns(player);
    }

    static boolean currentGuard(ServerLevel level, Pillager guard) {
        return at(level,guard.blockPosition()).matches(guard.getPersistentData(),ENEMY);
    }

    // Patrol destinations are confined to the guard's own session and map.
    // This is deliberately separate from combat pursuit.
    static boolean patrolPosition(ServerLevel level,Pillager guard,BlockPos pos){
        ForestRaid raid=at(level,guard.blockPosition());
        return raid.matches(guard.getPersistentData(),ENEMY)&&raid.inArea(pos)
            &&level.hasChunkAt(pos)&&validEncounterPosition(raid.industrial,pos);
    }

    static BlockPos patrolAnchor(ServerLevel level,Pillager guard){
        ForestRaid raid=at(level,guard.blockPosition());
        int squad=guard.getPersistentData().getInt("taczoperations_squad");
        if(squad>=0&&squad<raid.encounterPoints.size()){
            var point=raid.encounterPoints.get(squad);
            return raid.industrial?ForestExpedition.industrialPosition(point):ForestLandmarks.position(point);
        }
        var data=guard.getPersistentData();
        return new BlockPos(data.getInt("guard_home_x"),data.getInt("guard_home_y"),data.getInt("guard_home_z"));
    }

    static RaidAtmosphere atmosphere(ServerPlayer player){
        ForestRaid raid=forPlayer(player);
        return raid!=null&&raid.owns(player)?raid.atmosphere:null;
    }
    static boolean sameSession(ServerLevel level,Pillager guard,ServerPlayer player){
        return at(level,guard.blockPosition()).owns(player);
    }

    public static boolean canKeepCorpse(ServerLevel level, GuardCorpse corpse) {
        return level.dimension().equals(ForestExpedition.FOREST)
            && at(level,corpse.blockPosition()).matches(corpse.getPersistentData(),CORPSE);
    }

    public static boolean canSearchCorpse(ServerPlayer player, GuardCorpse corpse) {
        var raid=forPlayer(player);
        return player.isAlive()&&!player.isSpectator()&&corpse.isAlive()&&player.level()==corpse.level()
            &&player.distanceToSqr(corpse)<=64&&raid!=null&&raid.owns(player)&&raid.matches(corpse.getPersistentData(),CORPSE)&&canKeepCorpse(player.serverLevel(),corpse);
    }

    static boolean hasRaidToken(ServerPlayer player) {
        return player.getPersistentData().hasUUID(PLAYER);
    }

    static void clearPlayer(ServerPlayer player) {
        player.getPersistentData().remove(PLAYER);
    }

    private boolean matches(CompoundTag data, String key) {
        return id != null && data.hasUUID(key) && id.equals(data.getUUID(key));
    }

    static void populateNear(ServerPlayer player) {
        if (!isParticipant(player) || player.isSpectator() || player.tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ForestRaid raid=forPlayer(player);
        if(raid==null)return;
        raid.removeOldCrates(level,player);
        if(raid.industrial)for(int i=0;i<IndustrialZone.VAULTS.size();i++){
            var door=IndustrialZone.VAULTS.get(i).door();
            if(door.distToCenterSqr(player.position())<=80*80&&level.hasChunkAt(door))raid.prepareVault(level,i);
        }
        int budget = 5;
        for (int i = 0; i < raid.locations.size() && budget > 0; i++) {
            if (raid.crates.get(i)) {
                continue;
            }
            var mark = raid.locations.get(i);
            if(mark.kind()>=4&&!raid.vaultOpened.get(mark.kind()-4))continue;
            int origin = raid.industrial ? IndustrialZone.ORIGIN : ForestPlan.ORIGIN;
            BlockPos center = new BlockPos(origin + mark.x(), mark.y(), origin + mark.z());
            if (!near(player, center) || !level.hasChunkAt(center)) {
                continue;
            }
            raid.removeStaleNearMark(level,center);
            if (raid.existingCrate(level, center, i)) {
                raid.crates.set(i);
                raid.setDirty();
                continue;
            }
            BlockPos place = findFloor(level, center, 2, false);
            if (place != null && raid.putCrate(level, place, "Caisse de ravitaillement", new Random(raid.seed ^ i), i)) {
                raid.crates.set(i);
                raid.setDirty();
                budget--;
            }
        }

        var guards = raid.encounterPoints;
        // Prefer the nearest encounter anchors rather than always the first list entries.
        var ordered = new java.util.ArrayList<Integer>();
        for (int i = 0; i < guards.size(); i++) ordered.add(i);
        ordered.sort(java.util.Comparator.comparingDouble(i -> {
            var p = guards.get(i);
            BlockPos pos = raid.industrial ? ForestExpedition.industrialPosition(p) : ForestLandmarks.position(p);
            return pos.distToCenterSqr(player.position());
        }));
        for (int group : ordered) {
            if (budget <= 0) break;
            var point = guards.get(group);
            BlockPos center = raid.industrial ? ForestExpedition.industrialPosition(point) : ForestLandmarks.position(point);
            if (center.distToCenterSqr(player.position()) > 160.0 * 160.0 || !level.hasChunkAt(center)) continue;
            boolean pmc = raid.pmcGroups.get(group);
            Random squadRandom = new Random(raid.seed ^ (0x632BE59BD9B4E019L * (group + 1)));
            int members = 2 + squadRandom.nextInt(2);
            for (int member = 0; member < members && budget > 0; member++) {
                int slot = group * 3 + member;
                if (raid.enemies.get(slot)) continue;
                GuardRole role = pmc ? GuardRole.PMC : Math.floorMod(slot + (int) raid.seed, 5) == 0
                    ? GuardRole.HEAVY : member == 0 ? GuardRole.RAIDER : GuardRole.SCOUT;
                // Search several separate positions; never spawn two members on the same block.
                Random positions = new Random(raid.seed ^ (slot * 31L) ^ (player.tickCount / 20L));
                for (int attempt = 0; attempt < 6; attempt++) {
                    int dx = positions.nextInt(17) - 8, dz = positions.nextInt(17) - 8;
                    var local = new ForestPlan.Point(point.x() + dx, point.z() + dz);
                    BlockPos around = raid.industrial ? ForestExpedition.industrialPosition(local) : ForestLandmarks.position(local);
                    BlockPos place = findFloor(level, around, 4, true);
                    if (place == null || !validEncounterPosition(raid.industrial, place)
                        || level.players().stream().anyMatch(p -> isParticipant(p) && place.distToCenterSqr(p.position()) < 20*20)
                        || !level.getEntitiesOfClass(Pillager.class,
                        new net.minecraft.world.phys.AABB(place).inflate(1.5),
                        e -> e.isAlive() && currentGuard(level, e)).isEmpty()) continue;
                    if (raid.spawnGuard(level, place, role, group)) {
                        raid.enemies.set(slot);
                        raid.setDirty();
                        budget--;
                        break;
                    }
                }
            }
        }

        // Boss slots are reserved independently from regular guard groups.
        // A convergence includes every currently defined boss, once each.
        var bosses=java.util.Arrays.stream(GuardRole.values()).filter(GuardRole::boss).toList();
        for(int i=0;i<bosses.size();i++){
            var boss=bosses.get(i);
            if(!raid.bossConvergence&&boss!=(raid.industrial?GuardRole.FOREMAN:GuardRole.TRACKER))continue;
            int slot=guards.size()*3+i;
            if(raid.enemies.get(slot))continue;
            var locations=raid.industrial?IndustrialZone.BOSSES:ForestRaidAnchors.TRACKER;
            var point=locations.get(Math.floorMod(raid.seed+i,locations.size()));
            BlockPos center=raid.industrial?ForestExpedition.industrialPosition(point):ForestLandmarks.position(point);
            if(center.distToCenterSqr(player.position())>160.0*160.0||!level.hasChunkAt(center))continue;
            Random rng=new Random(raid.seed^slot^player.tickCount/20L);
            for(int attempt=0;attempt<6;attempt++){
                BlockPos near=center.offset(rng.nextInt(13)-6,0,rng.nextInt(13)-6);
                if(!level.hasChunkAt(near))continue;
                BlockPos place=findFloor(level,near,4,true);
                if(place==null||!validEncounterPosition(raid.industrial,place)
                    ||level.players().stream().anyMatch(p->raid.owns(p)&&place.distToCenterSqr(p.position())<20*20)
                    ||!level.getEntitiesOfClass(Pillager.class,new net.minecraft.world.phys.AABB(place).inflate(2),e->e.isAlive()&&currentGuard(level,e)).isEmpty())continue;
                if(raid.spawnGuard(level,place,boss,-1)){
                    raid.enemies.set(slot);raid.setDirty();break;
                }
            }
        }
    }

    // Sample a new, stable layout once per raid. Existing landmarks are
    // regional hints, not fixed spawn coordinates. No respawn after a kill.
    private static List<ForestPlan.Point> chooseEncounterPoints(boolean industrial, long seed) {
        var base = new java.util.ArrayList<>(industrial ? IndustrialZone.GUARDS : ForestRaidAnchors.ENEMIES);
        Random random = new Random(seed ^ 0x657a34deL);
        java.util.Collections.shuffle(base, random);
        int count = industrial ? 12 + random.nextInt(5) : 22 + random.nextInt(7);
        var result = new java.util.ArrayList<ForestPlan.Point>();
        int radius = industrial ? 24 : 48;
        for (var anchor : base) {
            if (result.size() >= count) break;
            for (int attempt = 0; attempt < 40; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = (attempt < 32 ? radius * (.35 + random.nextDouble() * .65) : random.nextDouble() * radius);
                var candidate = new ForestPlan.Point(anchor.x() + (int)Math.round(Math.cos(angle)*distance),
                    anchor.z() + (int)Math.round(Math.sin(angle)*distance));
                if (!validEncounterPoint(industrial, candidate)) continue;
                if (result.stream().anyMatch(p -> pointDistanceSquared(p, candidate) < 26L*26L)) continue;
                result.add(candidate);
                break;
            }
        }
        // Fall back to unused landmark hints if the random terrain samples
        // are insufficient; retain the planned encounter population.
        for (var anchor : base) {
            if (result.size() >= count) break;
            if (validEncounterPoint(industrial, anchor)
                && result.stream().noneMatch(p -> pointDistanceSquared(p, anchor) < 18L*18L))
                result.add(anchor);
        }
        return List.copyOf(result);
    }

    private static long pointDistanceSquared(ForestPlan.Point a, ForestPlan.Point b) {
        long dx = a.x()-b.x(), dz = a.z()-b.z();
        return dx*dx+dz*dz;
    }

    private static boolean validEncounterPoint(boolean industrial, ForestPlan.Point point) {
        int size = industrial ? IndustrialZone.SIZE : ForestPlan.SIZE;
        if (point.x()<12 || point.z()<12 || point.x()>=size-12 || point.z()>=size-12) return false;
        if (industrial) return pointDistanceSquared(point, IndustrialZone.ENTRY) >= 32L*32L
            && pointDistanceSquared(point, IndustrialZone.EXIT) >= 24L*24L
            && pointDistanceSquared(point, IndustrialZone.KEY_EXIT) >= 24L*24L
            && pointDistanceSquared(point, IndustrialZone.PAID_EXIT) >= 24L*24L;
        return ForestPlan.surfaceY(point) > ForestPlan.WATER + 1
            && ForestPlan.ENTRIES.stream().noneMatch(p -> pointDistanceSquared(p, point)<32L*32L)
            && pointDistanceSquared(point, ForestPlan.EXTRACTION)>=24L*24L
            && pointDistanceSquared(point, ForestPlan.CONDITIONAL_EXIT)>=24L*24L
            && pointDistanceSquared(point, ForestPlan.RELAY)>=16L*16L;
    }

    private static boolean validEncounterPosition(boolean industrial, BlockPos pos) {
        int origin = industrial ? IndustrialZone.ORIGIN : ForestPlan.ORIGIN;
        return validEncounterPoint(industrial, new ForestPlan.Point(pos.getX()-origin,pos.getZ()-origin));
    }

    // Group nearby anchors, without allowing an entire chain of distant
    // encounters to become one giant faction. Randomize once per raid.
    private static BitSet assignFactions(List<ForestPlan.Point> points, long seed) {
        java.util.List<java.util.List<Integer>> sectors = new java.util.ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            final int idx = i;
            var a = points.get(i);
            var sector = sectors.stream().filter(s -> s.stream().allMatch(j -> {
                var b = points.get(j);
                long dx = a.x()-b.x(), dz = a.z()-b.z();
                return dx*dx+dz*dz <= 75L*75L;
            })).findFirst();
            if (sector.isPresent()) sector.get().add(idx);
            else sectors.add(new java.util.ArrayList<>(java.util.List.of(idx)));
        }
        java.util.Collections.shuffle(sectors, new Random(seed ^ 0x74c1de92L));
        BitSet result = new BitSet(points.size());
        int target = Math.max(2, Math.min(points.size()-2, (int)Math.round(points.size()*.45)));
        int assigned = 0;
        for (var sector : sectors) {
            if (assigned >= target || assigned + sector.size() > points.size()-2) continue;
            for (int i : sector) result.set(i);
            assigned += sector.size();
        }
        // Preserve a minimum of two groups of each faction for these maps.
        for (int i=0; assigned<2 && i<points.size()-2; i++) {
            if (!result.get(i)) { result.set(i); assigned++; }
        }
        return result;
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return pos.distToCenterSqr(player.position()) <= 60 * 60;
    }

    private static BlockPos findFloor(ServerLevel level, BlockPos center, int radius, boolean headroom) {
        for (BlockPos pos : BlockPos.withinManhattan(center, radius, headroom ? 3 : 0, radius)) {
            if ((!ForestPlan.inside(pos.getX(), pos.getZ()) && !IndustrialZone.inside(pos.getX(), pos.getZ())) || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).isAir() || (headroom && !level.getBlockState(pos.above()).isAir())) {
                continue;
            }
            var below = level.getBlockState(pos.below());
            if (below.isFaceSturdy(level, pos.below(), Direction.UP)
                && !below.is(Blocks.GOLD_BLOCK) && !below.is(Blocks.MOSSY_STONE_BRICKS)
                && !below.is(Blocks.COPPER_BLOCK)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private boolean spawnGuard(ServerLevel level, BlockPos pos, GuardRole role, int squad) {
        Pillager guard = EntityType.PILLAGER.create(level);
        if (guard == null) {
            return false;
        }
        guard.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.getRandom().nextFloat() * 360, 0);
        if (!level.noCollision(guard)) {
            return false;
        }
        guard.getPersistentData().putUUID(ENEMY, id);
        guard.setPersistenceRequired();
        role.assign(guard);
        guard.getPersistentData().putInt("taczoperations_squad", squad);
        guard.getPersistentData().putInt("guard_home_x", pos.getX());
        guard.getPersistentData().putInt("guard_home_y", pos.getY());
        guard.getPersistentData().putInt("guard_home_z", pos.getZ());
        guard.setCustomName(Component.literal(role.title));
        // Keep enemy identity in server data without revealing positions through foliage.
        guard.setCustomNameVisible(false);
        guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(role.health);
        guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role.speed);
        guard.setHealth(role.health);
        // Armor variants are deterministic for each guard and preserve role tiers.
        int equipmentRoll = Math.floorMod(guard.getUUID().hashCode(), 6);
        net.minecraft.world.item.Item vest = role.armor;
        if (role == GuardRole.RAIDER || role == GuardRole.SCOUT) {
            if (equipmentRoll == 0) vest = Items.CHAINMAIL_CHESTPLATE;
        } else if (role == GuardRole.SOLDIER || role == GuardRole.MARKSMAN) {
            if (equipmentRoll == 0) vest = Items.IRON_CHESTPLATE;
        } else if (role != GuardRole.TRACKER && equipmentRoll == 0) {
            vest = Items.DIAMOND_CHESTPLATE;
        }
        if (role == GuardRole.PMC) {
            int rarity = guard.getRandom().nextInt(100);
            guard.getPersistentData().putInt("taczoperations_pmc_rarity", rarity < 40 ? 0 : rarity < 70 ? 1 : rarity < 90 ? 2 : rarity < 98 ? 3 : 4);
            vest = rarity < 40 ? Items.LEATHER_CHESTPLATE : rarity < 70 ? Items.CHAINMAIL_CHESTPLATE : rarity < 98 ? Items.IRON_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
        }
        guard.setItemSlot(EquipmentSlot.CHEST, role == GuardRole.PMC
            ? BallisticVest.roll(new Random(guard.getUUID().getLeastSignificantBits()), false)
            : new ItemStack(vest));
        if (equipmentRoll == 1 || equipmentRoll == 4) {
            guard.setItemSlot(EquipmentSlot.LEGS, new ItemStack(
                role.elite() ? Items.IRON_LEGGINGS : Items.LEATHER_LEGGINGS));
        }
        if (role == GuardRole.SCOUT || role == GuardRole.MARKSMAN) {
            guard.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        } else if (role.elite()) {
            guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(role == GuardRole.TRACKER ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
        }
        if (role == GuardRole.PMC) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.LEGS,EquipmentSlot.FEET})
                guard.setItemSlot(slot, BallisticVest.roll(slot,new Random(guard.getRandom().nextLong()),false));
        }
        if (role == GuardRole.PMC) {
            // Mutually exclusive headgear rolls, independent of armor rarity.
            int optics = guard.getRandom().nextInt(100);
            if (optics < 6) {
                String id = optics < 3 ? "night_vision_goggles" : "thermal_goggles";
                guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations", id))));
            }
        }
        guard.setDropChance(EquipmentSlot.LEGS, 0);
        guard.setDropChance(EquipmentSlot.HEAD, 0);
        guard.setDropChance(EquipmentSlot.FEET, 0);
        guard.setDropChance(EquipmentSlot.MAINHAND, 0);
        guard.setDropChance(EquipmentSlot.CHEST, 0);
        ForestCombat.arm(guard);
        WeaponCondition.usedLoot(guard.getMainHandItem(),55+guard.getRandom().nextInt(41));
        if(role.boss())BossLoot.equip(guard);
        GuardPouch.load(guard);
        return level.addFreshEntity(guard);
    }

    private boolean existingCrate(ServerLevel level, BlockPos center, int index) {
        for (BlockPos pos : BlockPos.withinManhattan(center, 2, 0, 2)) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel) {
                CompoundTag data = barrel.getPersistentData();
                if (matches(data, CRATE) && data.contains(CRATE_INDEX) && data.getInt(CRATE_INDEX) == index) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean putCrate(ServerLevel level, BlockPos pos, String name, Random random, int index) {
        if (pos.equals(dropPos)) return false;
        int kind=locations.get(index).kind();
        int furniture=kind>=4?3:kind==3?3+random.nextInt(3):kind;
        var block=OperationsMod.FURNITURE.get(furniture).get();
        if (!ForestProtection.setRaidContainer(level, pos, block.defaultBlockState())) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel)) {
            return false;
        }
        barrel.getPersistentData().putUUID(CRATE, id);
        barrel.getPersistentData().putInt(CRATE_INDEX, index);
        spawnedContainers.add(pos.immutable());
        if(kind>=4){
            var vault=IndustrialZone.VAULTS.get(kind-4);
            ItemStack label=new ItemStack(Items.BARREL);
            label.set(DataComponents.CUSTOM_NAME,Component.literal(vault.title()+" · réserve sécurisée"));
            barrel.applyComponentsFromItemStack(label);
            barrel.setItem(2, (kind==4?RaidRelic.DRIVE:kind==5?RaidRelic.CHIP:RaidRelic.OPTIC).stack());
            barrel.setItem(6,RaidRelic.roll(random).stack());
            TradeLoot bonus=TradeLoot.roll(random,kind==5?0:1,2);
            if(bonus!=null)barrel.setItem(7,bonus.stack());
            var slot=BallisticVest.SLOTS[random.nextInt(BallisticVest.SLOTS.length)];
            int tier=random.nextInt(100)<12?5:4;
            barrel.setItem(12,BallisticVest.create(slot,tier,BallisticVest.classColor(tier)));
            barrel.setItem(18,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","salewa")),2));
            barrel.setItem(24,new ItemStack(Items.GOLD_INGOT,4+random.nextInt(5)));
            barrel.setChanged();
            return true;
        }
        ItemStack labeled = new ItemStack(Items.BARREL);
        labeled.set(DataComponents.CUSTOM_NAME, Component.literal(switch (kind) {
            case 0 -> "Sac médical";
            case 1 -> "Boîte à outils";
            case 2 -> "Caisse militaire";
            default -> furniture==4?"Ordinateur · données":furniture==5?"Armoire à dossiers":"Coffre-fort confidentiel";
        }));
        barrel.applyComponentsFromItemStack(labeled);
        var supplies = switch (kind) {
            case 0 -> List.of(Items.COOKED_BEEF, Items.BREAD, Items.GOLDEN_CARROT, Items.HONEY_BOTTLE);
            case 1 -> List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.COAL, Items.FLINT);
            case 2 -> List.of(Items.CHAINMAIL_BOOTS, Items.LEATHER_HELMET, Items.IRON_HELMET, Items.SHIELD, Items.ARROW);
            default -> List.of(Items.COMPASS, Items.CLOCK, Items.PAPER, Items.EMERALD, Items.GOLD_INGOT);
        };
        var item = supplies.get(random.nextInt(supplies.size()));
        ItemStack primary = new ItemStack(item);
        primary.setCount(Math.min(primary.getMaxStackSize(), 1 + random.nextInt(kind == 1 ? 6 : 3)));
        barrel.setItem(random.nextInt(9), primary);
        int relicChance = switch (kind) { case 0 -> 30; case 1 -> 50; case 2 -> 55; default -> 77; };
        if (random.nextInt(100) < relicChance + (industrial ? 10 : 0))
            barrel.setItem(9 + random.nextInt(8), RaidRelic.roll(random).stack());
        if (random.nextInt(100) < (kind == 3 ? 15 : 4) + (industrial ? 5 : 0))
            barrel.setItem(17, RaidRelic.roll(random).stack());
        switch (kind) {
            case 0 -> {
                barrel.setItem(18, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                    Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
                if (random.nextInt(5) == 0) barrel.setItem(26, new ItemStack(Items.GOLDEN_APPLE));
            }
            case 1 -> {
                barrel.setItem(18, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(9)));
                barrel.setItem(26, new ItemStack(Items.REDSTONE, 2 + random.nextInt(7)));
            }
            case 2 -> {
                var calibers = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
                for (var entry : com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex()) {
                    var data = entry.getValue().getGunData();
                    if (data != null && data.getAmmoId() != null && !calibers.contains(data.getAmmoId()))
                        calibers.add(data.getAmmoId());
                }
                if (!calibers.isEmpty()) {
                    var ammoId = calibers.get(random.nextInt(calibers.size()));
                    barrel.setItem(18, com.tacz.guns.api.item.builder.AmmoItemBuilder.create()
                        .setId(ammoId).setCount(12 + random.nextInt(19)).build());
                }
                if (random.nextInt(industrial ? 5 : 8) == 0)
                    barrel.setItem(25, OperationsShop.attachment(random.nextBoolean() ? "sight_552" : "extended_mag_1"));
            }
            default -> barrel.setItem(18, new ItemStack(Items.PAPER, 2 + random.nextInt(5)));
        }
        if(random.nextInt(100)<(kind==0?45:kind==1?60:kind==3?35:20)){
            TradeLoot loot=TradeLoot.roll(random,kind==2?1:kind,0);
            if(loot!=null)barrel.setItem(20,loot.stack());
        }
        if(industrial&&random.nextInt(100)<8)barrel.setItem(23,new ItemStack(industrialKey()));
        if(kind==0){
            // All injury treatments are obtainable through exploration, not just shops.
            int roll=random.nextInt(100);
            String medical=roll<30?"bandage":roll<50?"salewa":roll<65?"splint":roll<75?"hemostat":roll<80?"medkit":null;
            if(medical!=null)barrel.setItem(22,medicalLoot(medical,medical.equals("bandage")?1+random.nextInt(3):1));
            if(random.nextInt(100)<20)barrel.setItem(21,medicalLoot("bandage",1));
        }
        barrel.setChanged();
        return true;
    }

    private static ItemStack medicalLoot(String name,int amount){
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",name)),amount);
    }

    private static net.minecraft.world.item.Item industrialKey(){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","industrial_key"));
    }

    private boolean prepareVault(ServerLevel level,int index){
        if(vaultPrepared.get(index))return true;
        var door=IndustrialZone.VAULTS.get(index).door();
        if(!level.hasChunkAt(door))return false;
        boolean closed=!vaultOpened.get(index);
        if(!ForestProtection.setVaultDoor(level,door,closed)||!ForestProtection.setVaultDoor(level,door.above(),closed))return false;
        vaultPrepared.set(index);
        return true;
    }

    private static boolean handleVault(PlayerInteractEvent.RightClickBlock event,ServerPlayer p){
        if(!isParticipant(p))return false;
        ForestRaid raid=forPlayer(p);
        if(!raid.industrial)return false;
        for(int i=0;i<IndustrialZone.VAULTS.size();i++){
            var v=IndustrialZone.VAULTS.get(i);
            if(!event.getPos().equals(v.door())&&!event.getPos().equals(v.door().above()))continue;
            event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
            if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return true;
            if(raid.vaultOpened.get(i))return true;
            if(!raid.prepareVault(p.serverLevel(),i))return true;
            var inventory=p.getInventory();
            if(inventory.items.stream().noneMatch(s->s.is(industrialKey()))){
                p.displayClientMessage(Component.literal(v.title()+" · clé industrielle requise"),true);
                return true;
            }
            var level=p.serverLevel();var door=v.door();
            if(!ForestProtection.setVaultDoor(level,door,false)||!ForestProtection.setVaultDoor(level,door.above(),false)){
                ForestProtection.setVaultDoor(level,door,true);ForestProtection.setVaultDoor(level,door.above(),true);
                return true;
            }
            if(!SupplyContract.consume(p,industrialKey(),1)){
                ForestProtection.setVaultDoor(level,door,true);ForestProtection.setVaultDoor(level,door.above(),true);
                return true;
            }
            raid.vaultOpened.set(i);raid.setDirty();
            SupplyContract.activityXp(p,25,false,"Réserve sécurisée ouverte");
            p.sendSystemMessage(Component.literal(v.title()+" ouverte pour ton équipe. Clé consommée."));
            return true;
        }
        return false;
    }

    private void removeTaggedContainer(ServerLevel level,BlockPos pos,boolean current){
        if(!inArea(pos)||!level.hasChunkAt(pos))return;
        if(!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel))return;
        var data=barrel.getPersistentData();
        if(!data.hasUUID(CRATE)||matches(data,CRATE)!=current)return;
        barrel.clearContent();
        ForestProtection.setRaidContainer(level,pos,Blocks.AIR.defaultBlockState());
    }
    private void removeStaleNearMark(ServerLevel level,BlockPos center){
        for(BlockPos pos:BlockPos.withinManhattan(center,2,0,2))
            removeTaggedContainer(level,pos,false);
    }

    private void removeOldCrates(ServerLevel level, ServerPlayer player) {
        var origin = player.chunkPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                var chunk = level.getChunkSource().getChunkNow(origin.x + dx, origin.z + dz);
                if (chunk == null) continue;
                List<BlockPos> expired = new ArrayList<>();
                for (BlockEntity block : chunk.getBlockEntities().values()) {
                    CompoundTag data = block.getPersistentData();
                    if (inArea(block.getBlockPos())&&block instanceof RandomizableContainerBlockEntity && data.hasUUID(CRATE) && !matches(data, CRATE)) {
                        expired.add(block.getBlockPos());
                    }
                }
                for (BlockPos pos : expired) {
                    removeTaggedContainer(level,pos,false);
                }
            }
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ForestExpedition.FOREST)) {
            return;
        }
        ForestRaid raid=at(level,event.getEntity().blockPosition());
        if (event.getEntity().getPersistentData().hasUUID(AIRDROP_VISUAL)) {
            if (!raid.matches(event.getEntity().getPersistentData(), AIRDROP_VISUAL) || raid.dropLanded) event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof GuardCorpse corpse && !canKeepCorpse(level, corpse)) {
            // Expired raid corpses must never survive into another session.
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ItemEntity item) {
            // Items are temporary raid loot, including player death drops.
            // Persist their raid identity so stale drops cannot enter a later raid.
            if (event.loadedFromDisk()) {
                if (!raid.matches(item.getPersistentData(), DROP)) {
                    event.setCanceled(true);
                }
            } else if (raid.id != null&&raid.inArea(item.blockPosition())) {
                item.getPersistentData().putUUID(DROP, raid.id);
            } else {
                event.setCanceled(true);
            }
        } else if (event.getEntity() instanceof Mob mob) {
            if (!(mob instanceof Pillager) || !raid.matches(mob.getPersistentData(),ENEMY)) {
                event.setCanceled(true);
            }
        } else if (!event.loadedFromDisk() && event.getEntity() instanceof AbstractArrow arrow
            && arrow.getOwner() instanceof Pillager guard && guard.getMainHandItem().is(Items.CROSSBOW)
            && guard.getPersistentData().hasUUID(ENEMY)
            && (!currentGuard(level, guard) || GuardPouch.consume(guard, 1, true) == 0)) {
            event.setCanceled(true);
        }
    }

    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Pillager guard) || !(guard.level() instanceof ServerLevel level)
            || !level.dimension().equals(ForestExpedition.FOREST) || !guard.getPersistentData().hasUUID(ENEMY)) {
            return;
        }
        event.getDrops().clear();
        ForestRaid raid=at(level,guard.blockPosition());
        if (!raid.matches(guard.getPersistentData(), ENEMY)) {
            return;
        }
        GuardRole role = GuardRole.get(guard);
        if (event.getSource().getEntity() instanceof ServerPlayer killer
            && raid.owns(killer)&& !guard.getPersistentData().getBoolean("taczoperations_kill_rewarded")) {
            guard.getPersistentData().putBoolean("taczoperations_kill_rewarded", true);
            SupplyContract.kill(killer, role.elite(), role == GuardRole.TRACKER);
            if(isParticipant(killer))raid.reinforcementKills++;
            int xp = role.boss() ? 40 : role.elite() ? 12 : role == GuardRole.PMC ? 8 : 5;
            SupplyContract.activityXp(killer, xp, true, role.title + " éliminé");
        }
        GuardCorpse corpse = OperationsMod.GUARD_CORPSE.get().create(level);
        if (corpse == null) {
            return;
        }
        corpse.setAppearance(switch(role){
            case FOREMAN->"foreman";
            case TRACKER->"tracker";
            case PMC->(guard.getUUID().getLeastSignificantBits()&1)==0?"pmc_usec":"pmc_bird_eye";
            default->"";
        });
        if(role==GuardRole.PMC)corpse.setCustomName(Component.literal("PMC · "+PmcSpecialty.get(guard).title));
        corpse.moveTo(guard.getX(), guard.getY(), guard.getZ(), guard.getYRot(), 0);
        corpse.getPersistentData().putUUID(CORPSE, raid.id);
        EquipmentSlot[] equipment = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < equipment.length; i++) {
            corpse.put(i, guard.getItemBySlot(equipment[i]));
        }
        var pouch = GuardPouch.load(guard);
        for (int i = 0; i < pouch.size(); i++) {
            corpse.put(9 + i, pouch.get(i));
        }
        if (role == GuardRole.TRACKER) {
            corpse.put(18, RaidRelic.CHIP.stack());
        } else if (role == GuardRole.FOREMAN) {
            corpse.put(18, RaidRelic.CIRCUIT.stack());
            corpse.put(19,new ItemStack(industrialKey()));
        }
        if(role.boss()){
            corpse.put(20,BossLoot.spare(guard));
            corpse.put(21,BossLoot.bonus(guard));
        }
        if (raid.industrial && !role.boss() && guard.getRandom().nextInt(100)<3) corpse.put(19,new ItemStack(industrialKey()));
        if (level.addFreshEntity(corpse)) {
            for (EquipmentSlot slot : equipment) {
                guard.setItemSlot(slot, ItemStack.EMPTY);
            }
            GuardPouch.clear(guard);
            if(role.boss())BossLoot.clear(guard);
        }
    }

    public static boolean canSearchFurniture(ServerPlayer player,RaidFurnitureEntity furniture){
        return player.isAlive()&&!player.isSpectator()&&player.level()==furniture.getLevel()
            &&!furniture.isRemoved()&&furniture.getBlockPos().distToCenterSqr(player.position())<=64
            &&isParticipant(player)&&forPlayer(player).matches(furniture.getPersistentData(),CRATE);
    }

    public static void onInspect(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer p && p.level().dimension().equals(ForestExpedition.FOREST)&&handleVault(event,p))return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(ForestExpedition.FOREST)
            || !(player.level().getBlockEntity(event.getPos()) instanceof RandomizableContainerBlockEntity barrel)
            || !barrel.getPersistentData().hasUUID(CRATE)) {
            return;
        }
        if (!isParticipant(player) || !forPlayer(player).matches(barrel.getPersistentData(), CRATE)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Cette caisse ne fait pas partie de ton raid."), true);
            return;
        }
        // One point per distinct raid container and player, not per click.
        // Scope the record to the current raid UUID to prevent farming by reopening.
        CompoundTag tracking = player.getPersistentData();
        String raidKey=forPlayer(player).id.toString();
        if (!raidKey.equals(tracking.getString("taczoperations_xp_raid"))) {
            tracking.putString("taczoperations_xp_raid", raidKey);
            tracking.put("taczoperations_xp_crates", new CompoundTag());
        }
        CompoundTag opened = tracking.getCompound("taczoperations_xp_crates");
        String crateKey = event.getPos().asLong() + "";
        if (!opened.getBoolean(crateKey)) {
            opened.putBoolean(crateKey, true);
            tracking.put("taczoperations_xp_crates", opened);
            tracking.putInt("taczoperations_run_crates",
                tracking.getInt("taczoperations_run_crates") + 1);
            RaidAudio.search(player);
            SupplyContract.activityXp(player, 3, false, "Coffre fouillé");
            SupplyContract.chestOpened(player);
        }
        // Furniture opens its own menu. The military airdrop and legacy
        // tagged vanilla containers use the identical progressive view.
        if(!(barrel instanceof RaidFurnitureEntity)){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
            var pos=event.getPos().immutable();
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id,inventory,viewer)->new RaidSearchMenu(id,inventory,barrel,barrel.getPersistentData(),
                    v->v instanceof ServerPlayer sp&&sp.isAlive()&&!sp.isSpectator()&&isParticipant(sp)
                        &&sp.level().getBlockEntity(pos)==barrel&&forPlayer(sp).matches(barrel.getPersistentData(),CRATE)),
                barrel.getDisplayName()));
        }
    }

    private static ForestRaid read(CompoundTag tag, HolderLookup.Provider registries) {
        // Raids are temporary sessions, not persistent worlds. Never restore
        // an active raid ID after a server restart: otherwise a returning
        // player could rejoin an abandoned raid with stale loot and enemies.
        // The next departure creates a fresh ID and clears nearby old crates.
        ForestRaid raid = new ForestRaid();
        raid.setDirty();
        return raid;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (id != null) {
            tag.putUUID("id", id);
            tag.putLong("seed", seed);
            tag.putBoolean("industrial", industrial);
            tag.putLongArray("crates", crates.toLongArray());
            tag.putLongArray("enemies", enemies.toLongArray());
        }
        return tag;
    }
}
