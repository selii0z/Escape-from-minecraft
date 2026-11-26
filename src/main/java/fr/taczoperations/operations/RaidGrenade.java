package fr.taczoperations.operations;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/** Fictional raid-only throwable. No terrain changes, fire or player damage. */
public final class RaidGrenade extends Item {
    static final String REFERENCE="5448be9a4bdc2dfd2f8b456a";
    private static final String RAID="efm_grenade_raid", OWNER="efm_grenade_owner", SPENT="efm_grenade_spent";
    public RaidGrenade(Properties props){super(props);}
    @Override public Component getName(ItemStack stack){return Component.literal("Grenade de raid EFM");}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.dimension().equals(ForestExpedition.FOREST)||player.isCreative()||player.isSpectator())
            return InteractionResultHolder.fail(stack);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer p)||!p.isAlive()||ScavRun.active(p)||!ForestRaid.isParticipant(p))
            return InteractionResultHolder.fail(stack);
        var raid=ForestRaid.campaignRaidId(p);
        if(raid==null||p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        var projectile=new Snowball(level,p);
        projectile.getPersistentData().putUUID(RAID,raid);
        projectile.getPersistentData().putUUID(OWNER,p.getUUID());
        projectile.shootFromRotation(p,p.getXRot(),p.getYRot(),0,1.0F,1.0F);
        if(!level.addFreshEntity(projectile))return InteractionResultHolder.fail(stack);
        stack.shrink(1);p.getCooldowns().addCooldown(this,40);
        return InteractionResultHolder.consume(stack);
    }
    static boolean credited(Entity direct,ServerPlayer p,java.util.UUID raid){
        if(!(direct instanceof Snowball ball)||raid==null)return false;
        var d=ball.getPersistentData();
        return d.getBoolean(SPENT)&&d.hasUUID(RAID)&&raid.equals(d.getUUID(RAID))
            &&d.hasUUID(OWNER)&&p.getUUID().equals(d.getUUID(OWNER));
    }
    public static void impact(ProjectileImpactEvent event){
        if(!(event.getProjectile() instanceof Snowball ball)||!(ball.level() instanceof ServerLevel level))return;
        var d=ball.getPersistentData();
        if(!d.hasUUID(RAID)||!d.hasUUID(OWNER))return;
        event.setCanceled(true);
        if(d.getBoolean(SPENT)){ball.discard();return;}
        d.putBoolean(SPENT,true);
        if(!(ball.getOwner() instanceof ServerPlayer p)||!p.isAlive()||p.isCreative()||p.isSpectator()
            ||ScavRun.active(p)||!ForestRaid.isParticipant(p)||p.level()!=level
            ||!d.getUUID(OWNER).equals(p.getUUID())||!d.getUUID(RAID).equals(ForestRaid.campaignRaidId(p))){
            ball.discard();return;
        }
        var at=event.getRayTraceResult().getLocation();
        level.sendParticles(ParticleTypes.EXPLOSION,at.x,at.y,at.z,1,0,0,0,0);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,0.8F,1.0F);
        for(var guard:level.getEntitiesOfClass(Pillager.class,new AABB(at,at).inflate(4),
            e->e.isAlive()&&ForestRaid.currentGuard(level,e)&&ForestRaid.sameSession(level,e,p))){
            if(guard.distanceToSqr(at)>16)continue;
            var hit=level.clip(new ClipContext(at,guard.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ball));
            if(hit.getType()!=HitResult.Type.MISS)continue;
            if(GuardRole.get(guard)==GuardRole.KABAN||GuardRole.get(guard)==GuardRole.KOLLONTAY)
                CampaignRuntime.distractionHit(p,d.getUUID(RAID));
            guard.hurt(p.damageSources().thrown(ball,p),24F);
        }
        ball.discard();
    }
}
