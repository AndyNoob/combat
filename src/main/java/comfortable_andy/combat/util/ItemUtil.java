package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static comfortable_andy.combat.CombatMain.debug;

public class ItemUtil {

    /**
     * @see AttributeInstance#calculateValue()
     */
    public static double getAttribute(ItemStack item, EquipmentSlot slot, Attribute attribute) {
        final Material material = item.getType();
        // all the modifiers here should all be add_value
        double d = material.getDefaultAttributeModifiers(slot).get(attribute)
                .stream()
                .mapToDouble(AttributeModifier::getAmount).sum();

        if (!item.hasItemMeta()) return d;

        final Collection<AttributeModifier> modifiers = item.getItemMeta().getAttributeModifiers(slot).get(attribute);

        // copied from net.minecraft.world.entity.ai.attributes.AttributeInstance#calculateValue
        for (AttributeModifier mod : modifiers.stream().filter(a -> a.getOperation() == AttributeModifier.Operation.ADD_NUMBER).toList()) {
            d += mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        double e = d;

        for (AttributeModifier mod : modifiers
                .stream()
                .filter(a -> a.getOperation() == AttributeModifier.Operation.ADD_SCALAR)
                .toList()) {
            e += d * mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        for (AttributeModifier mod : modifiers
                .stream()
                .filter(a -> a.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                .toList()) {
            e *= 1.0 + mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        return e;
    }

    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    public static void handleVanillaLikeAttack(Player player,
                                               Entity target,
                                               ItemStack item,
                                               EquipmentSlot slot,
                                               Vector knockBackDir,
                                               double knockback,
                                               double damage,
                                               double strengthScale,
                                               AtomicBoolean sentStrongKnockBack) {
        if (!canAttack(player, target)) return;

        final var playerHandle = ((CraftPlayer) player).getHandle();
        final World world = player.getWorld();
        final var level = ((CraftWorld) world).getHandle();
        final WorldConfiguration paperConfig = level.paperConfig();
        final var nmsStack = CraftItemStack.asNMSCopy(item);
        final var nmsItem = nmsStack.getItem();
        final var entityHandle = ((CraftEntity) target).getHandle();

        if (Tag.ENTITY_TYPES_REDIRECTABLE_PROJECTILE.isTagged(target.getType())) {
            // TODO decide if this should call non living damage event
            if (((Projectile) entityHandle).deflect(ProjectileDeflection.AIM_DEFLECT, playerHandle, playerHandle, true)) {
                world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 1, 1);
                return;
            }
        }
        final DamageSource source = DamageSource
                .builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build();
        net.minecraft.world.damagesource.DamageSource sourceHandle = ((CraftDamageSource) source).getHandle();
        final double enchantmentDamage = EnchantmentHelper.modifyDamage(
                level,
                nmsStack,
                entityHandle,
                sourceHandle,
                (float) damage
        ) - damage;
        final double bonus = nmsItem
                .getAttackDamageBonus(entityHandle, (float) damage, sourceHandle);

        @SuppressWarnings("deprecation") boolean critical = CombatMain.getInstance().getConfig().getBoolean("enable-critical", false) && strengthScale > 0.9 && !player.isClimbing() && player.getFallDistance() > 0 && !player.isOnGround() && !player.isInWater() && !player.isSprinting() && !player.isInsideVehicle() && !player.hasPotionEffect(PotionEffectType.BLINDNESS) && !paperConfig.entities.behavior.disablePlayerCrits;
        double finalFinalDamage = damage + bonus + enchantmentDamage * strengthScale;
        final Location location = player.getLocation();
        if (critical) {
            sourceHandle.critical();
            finalFinalDamage *= 1.5;
        }
        final double hpBefore = target instanceof LivingEntity le ? le.getHealth() : -1;
        final boolean hurt = entityHandle.hurtServer(
                level,
                sourceHandle,
                (float) finalFinalDamage
        );
        if (CombatMain.getInstance().isDebugLog()) {
            debug(player.getName() + " -> " + target.getName() + " success? " + hurt);
            debug("    enchant damage is " + enchantmentDamage);
            debug("    bonus damage is " + bonus);
            debug("    strength is " + strengthScale);
        }
        if (!hurt) {
            debug("    played no damage");
            world.playSound(
                    location,
                    Sound.ENTITY_PLAYER_ATTACK_NODAMAGE,
                    1,
                    1
            );
            return;
        }
        boolean shouldDoKnockback = player.isSprinting() && strengthScale > 0.9 && !sentStrongKnockBack.get();
        if (shouldDoKnockback) {
            debug("    should knockback? true");
            world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1, 1);
            sentStrongKnockBack.set(true);
            if (!paperConfig.misc.disableSprintInterruptionOnAttack) {
                player.setSprinting(false);
            }
            if (entityHandle instanceof net.minecraft.world.entity.LivingEntity le) {
                le.knockback(
                        knockback * 0.5F,
                        Mth.sin(playerHandle.getYRot() * (float) (Math.PI / 180.0)),
                        -Mth.cos(playerHandle.getYRot() * (float) (Math.PI / 180.0)),
                        playerHandle,
                        io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK
                );
            } else {
                entityHandle.push(
                        -Mth.sin(playerHandle.getYRot() * (float) (Math.PI / 180.0)) * knockback * 0.5F,
                        0.1,
                        Mth.cos(playerHandle.getYRot() * (float) (Math.PI / 180.0)) * knockback * 0.5F
                        , playerHandle // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                );
            }
            debug("    reduced velocity");
            playerHandle.setDeltaMovement(
                    playerHandle
                            .getDeltaMovement()
                            .multiply(
                                    0.6,
                                    1,
                                    0.6
                            )
            );
        }
        boolean doPost = false;

        if (target instanceof LivingEntity livingEntity) {
            debug("    is living? true");
            doPost = nmsStack.hurtEnemy(
                    ((CraftLivingEntity) livingEntity).getHandle(),
                    playerHandle
            );
            EnchantmentHelper.doPostAttackEffectsWithItemSource(
                    level,
                    entityHandle,
                    sourceHandle,
                    nmsStack
            );
        }

        if (!item.isEmpty() && doPost) {
            debug("    do post? true");
            nmsStack.postHurtEnemy(
                    (net.minecraft.world.entity.LivingEntity) entityHandle,
                    playerHandle
            );
        }
        if (player.getInventory().getItem(slot).equals(item)) { // prevent dropping
            CraftItemStack mirror = CraftItemStack.asCraftMirror(nmsStack);
            player.getInventory().setItem(slot, mirror);
        }

        final double actualDamage = hpBefore == -1 ? 0 : hpBefore - ((LivingEntity) target).getHealth();

        debug("    actual dealt damage is " + actualDamage);

        playerHandle.causeFoodExhaustion(level.spigotConfig.combatExhaustion, EntityExhaustionEvent.ExhaustionReason.ATTACK);

        if (actualDamage <= 0) return;

        debug("    attack sound playing");
        if (critical) {
            world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1, 1);
            playerHandle.crit(entityHandle);
        } else {
            if (strengthScale > 0.9)
                world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1, 1);
            else world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_WEAK, 1, 1);
        }
        if (enchantmentDamage > 0) {
            playerHandle.magicCrit(entityHandle);
        }
        final int addingToStat = (int) Math.round(actualDamage * 10);
        if (addingToStat > 0) player.incrementStatistic(Statistic.DAMAGE_DEALT, addingToStat);
        final int hearts = (int) (actualDamage / 2);

        if (hearts > 0) {
            world.spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, target.getBoundingBox().getHeight() / 2, 0),
                    hearts,
                    0.1,
                    0.0,
                    0.1,
                    0.2
            );
        }
        playerHandle.setLastHurtMob(entityHandle);

    }

    public static boolean canAttack(Player attacker, Entity attacked) {
        ServerPlayer playerHandle = ((CraftPlayer) attacker).getHandle();
        if (attacked == attacker) return false;
        final var entityHandle = ((CraftEntity) attacked).getHandle();
        if (!entityHandle.isAttackable() || entityHandle.skipAttackInteraction(playerHandle)) return false;
        if (attacked instanceof Player pl && pl.getGameMode().isInvulnerable()) return false;
        return attacker.hasLineOfSight(attacked);
    }

}
