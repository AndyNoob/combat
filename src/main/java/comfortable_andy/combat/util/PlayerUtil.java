package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.handler.OrientedBoxHandler;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.key.Key;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static comfortable_andy.combat.util.VecUtil.rotateLocal;
import static org.bukkit.util.NumberConversions.ceil;

public class PlayerUtil {

    @SuppressWarnings("UnstableApiUsage")
    public static void doSweep(Player player, Quaterniond start, Vector3d attack, int steps, boolean isAttack, double speedMod, double damageMod, long ticksLeft, CombatPlayerData data) {
        final EquipmentSlot slot = isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        final double cd = getCd(player, slot);
        final int ticks = ceil(cd * speedMod);
        final ItemStack item = player.getInventory().getItem(slot);
        final double strengthScale = Mth.clamp((ticks - ticksLeft + 0.5) / ticks, 0, 1);
        double damage = getDmg(player, slot) * (0.2 + strengthScale * strengthScale * 0.8);
        final int sharpness = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            damage += (0.5 * sharpness + 0.5) * strengthScale;
        }
        final AtomicBoolean sentStrongKnockBack = new AtomicBoolean();
        final AtomicBoolean updatedExhaust = new AtomicBoolean();
        final double knockBack = getKnockBack(player, slot) + (strengthScale > 0.9 && player.isSprinting() ? 1 : 0) + item.getEnchantmentLevel(Enchantment.KNOCKBACK);
        final double finalDamage = damage * damageMod;
        final World world = player.getWorld();
        final ServerLevel level = ((CraftWorld) world).getHandle();
        final WorldConfiguration paperConfig = level.paperConfig();
        final ServerPlayer playerHandle = ((CraftPlayer) player).getHandle();
        if (CombatMain.getInstance().getConfig().getBoolean("compensate-camera-movement", true)) {
            Vector2f delta = data.averageCameraAngleDelta();
            start.mul(new Quaterniond().rotateX(-Math.toRadians(delta.x)).rotateY(Math.toRadians(delta.y)));
        }
        data.resetCameraDelta();

        final var nmsStack = CraftItemStack.asNMSCopy(item);
        final Item nmsItem = nmsStack.getItem();

        PlayerUtil.sweep(
                player,
                () -> player.getEyeLocation().add(data.posDelta()),
                PlayerUtil.getReach(player),
                1,
                start,
                attack,
                steps,
                steps <= 1 ? 1 : ceil(ticks / (steps + 0d)),
                (entity, mtv) -> {
                    if (entity == player) return;
                    if (entity instanceof Player pl && pl.getGameMode().isInvulnerable()) return;
                    if (!player.hasLineOfSight(entity)) return;
                    if (knockBack > 0 && entity instanceof LivingEntity e) {
                        playerHandle.setDeltaMovement(playerHandle.getDeltaMovement().multiply(0.6, 1, 0.6));
                        if (!paperConfig.misc.disableSprintInterruptionOnAttack) {
                            player.setSprinting(false);
                        }
                        ((CraftLivingEntity) e).getHandle().knockback(
                                knockBack,
                                -mtv.getX(),
                                -mtv.getZ(),
                                playerHandle,
                                EntityKnockbackEvent.Cause.ENTITY_ATTACK
                        );
                    }
                    final DamageSource source = DamageSource
                            .builder(DamageType.PLAYER_ATTACK)
                            .withCausingEntity(player)
                            .withDirectEntity(player)
                            .build();
                    final var entityHandle = ((CraftEntity) entity).getHandle();
                    net.minecraft.world.damagesource.DamageSource sourceHandle = ((CraftDamageSource) source).getHandle();
                    final double enchantmentDamage = EnchantmentHelper.modifyDamage(
                            level,
                            nmsStack,
                            entityHandle,
                            sourceHandle,
                            (float) finalDamage
                    ) - finalDamage;
                    final double bonus = nmsItem
                            .getAttackDamageBonus(entityHandle, (float) finalDamage, sourceHandle);

                    @SuppressWarnings("deprecation") final boolean critical = strengthScale > 0.9 && !player.isClimbing() && player.getFallDistance() > 0 && !player.isOnGround() && !player.isInWater() && !player.isSprinting() && !player.isInsideVehicle() && !player.hasPotionEffect(PotionEffectType.BLINDNESS) && !paperConfig.entities.behavior.disablePlayerCrits;
                    double finalFinalDamage = finalDamage + bonus + enchantmentDamage;
                    final Location location = player.getLocation();
                    if (critical) {
                        sourceHandle.critical();
                        finalFinalDamage *= 1.5;
                    }
                    if (player.isSprinting() && !sentStrongKnockBack.get()) {
                        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1, 1);
                        sentStrongKnockBack.set(true);
                    }
                    final double hpBefore = entity instanceof LivingEntity le ? le.getHealth() : -1;
                    final boolean hurt = entityHandle.hurt(
                            sourceHandle,
                            (float) finalFinalDamage
                    );
                    boolean doPost = false;
                    if (entity instanceof LivingEntity livingEntity) {
                        doPost = nmsStack.hurtEnemy(
                                ((CraftLivingEntity) livingEntity).getHandle(),
                                playerHandle
                        );
                        EnchantmentHelper.doPostAttackEffects(
                                level,
                                entityHandle,
                                sourceHandle
                        );
                    }

                    if (doPost)
                        nmsStack.postHurtEnemy(
                                (net.minecraft.world.entity.LivingEntity) entityHandle,
                                playerHandle
                        );

                    if (!(hurt)) {
                        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1, 1);
                        return;
                    }

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
                    if (hpBefore != -1) {
                        final double actualDamage = hpBefore - ((LivingEntity) entity).getHealth();
                        final int addingToStat = (int) Math.round(actualDamage * 10);
                        if (addingToStat > 0) player.incrementStatistic(Statistic.DAMAGE_DEALT, addingToStat);
                        final int hearts = (int) (actualDamage / 2);

                        if (hearts > 0) {
                            world.spawnParticle(
                                    Particle.DAMAGE_INDICATOR,
                                    entity.getLocation().add(0, entity.getBoundingBox().getHeight() / 2, 0),
                                    hearts,
                                    0.1,
                                    0.0,
                                    0.1,
                                    0.2
                            );
                        }
                    }

                    if (!updatedExhaust.get()) {
                        playerHandle.causeFoodExhaustion(level.spigotConfig.combatExhaustion, EntityExhaustionEvent.ExhaustionReason.ATTACK);
                        updatedExhaust.set(true);
                    }
                },
                strengthScale > 0.9
        );
    }

    /**
     * @param supplier the origin of the sweep, should be the eye location
     * @param start    the rotation to start the sweep
     * @param delta    this added to {@code start}
     * @param steps    how many steps
     */
    public static void sweep(Object owner, Supplier<Location> supplier, float reach, float size, Quaterniond start, Vector3d delta, int steps, int ticksPerStep, BiConsumer<Entity, Vector> callback, boolean collide) {
        Location loc = supplier.get().clone();

        final float halfSize = size / 2;
        final OrientedBox attackBox = new OrientedBox(
                BoundingBox.of(
                        loc.clone()
                                .add(+halfSize, +halfSize, 0),
                        loc.clone()
                                .add(-halfSize, -halfSize, reach)
                ))
                .setCenter(loc.toVector())
                .rotateBy(start);

        final Vector3d rawStep = delta.mul(steps <= 1 ? 1 : 1 / (steps - 1d));
        final Quaterniond step = rotateLocal(new Quaterniond(), rawStep, attackBox.getAxis());

        if (steps <= 1) attackBox.rotateBy(step);
        final Map<Entity, OrientedBox> possible = new ConcurrentHashMap<>();
        final World world = loc.getWorld();
        final AtomicReference<Vector> direction = new AtomicReference<>();
        final AtomicInteger ticker = new AtomicInteger(0);

        CombatMain.getInstance().boxHandler.addBox(
                attackBox,
                OrientedBoxHandler.BoxInfo.<Entity>builder()
                        .owner(owner)
                        .boxSupplier(() -> {
                            possible.forEach((e, box) ->
                                    box.moveBy(
                                            e.getBoundingBox()
                                                    .getCenter()
                                                    .subtract(box.getCenter())
                                    )
                            );
                            return possible;
                        })
                        .collideCallback(((damageable, vector) -> {
                            if (direction.get().dot(vector) <= 0) return;
                            callback.accept(damageable, vector);
                        }))
                        .mtvComparator(((Comparator<Vector>) (a, b) -> {
                            final Vector dir = direction.get();
                            return Double.compare(dir.dot(a), dir.dot(b));
                        }).reversed())
                        .tickCheck(left -> {
                            if (ticker.getAndIncrement() % ticksPerStep != 0) return false;
                            final Location curLoc = supplier.get();
                            possible.putAll(collectNearby(reach, curLoc, possible.keySet()));
                            direction.set(curLoc.getDirection());
                            return true;
                        })
                        .postTickCallback(() -> {
                            if (CombatMain.getInstance()
                                    .getConfig().getBoolean("box-display-particle", false))
                                attackBox.clone().display(world);
                            attackBox.rotateBy(step);
                        })
                        .collidesWithOthers(collide)
                        .collidedWithOther((box, info) -> {
                            if (owner != info.getOwner()) {
                                world.playSound(loc, Sound.BLOCK_ANVIL_PLACE, 1, 1);
                                return true;
                            }
                            return false;
                        })
                        .ticks(Math.max(1, steps))
                        .build()
        );
    }

    @NotNull
    private static Map<Entity, OrientedBox> collectNearby(
            float reach,
            Location curLoc,
            Collection<Entity> excluding
    ) {
        return curLoc
                .getNearbyEntitiesByType(Entity.class, reach)
                .stream()
                .filter(e -> !excluding.contains(e))
                .collect(HashMap::new, (m, d) -> m.put(d, new OrientedBox(d.getBoundingBox())), HashMap::putAll);
    }

    public static float getReach(Player player) {
        return getValueFrom(player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE));
    }

    private static float getValueFrom(AttributeInstance instance) {
        if (instance == null) return 0;
        return (float) instance.getValue();
    }

    @SuppressWarnings("DataFlowIssue")
    public static double getItemLess(Player player, Attribute attribute, String... modKeys) {
        final AttributeInstance instance = player.getAttribute(attribute);
        final List<AttributeModifier> mods = new ArrayList<>();
        for (@Subst("minecraft:base_attack_speed") String itemModKey : modKeys) {
            final AttributeModifier itemMod = instance.getModifier(Key.key(itemModKey));
            if (itemMod != null) {
                instance.removeModifier(itemMod);
                mods.add(itemMod);
            }
        }
        final double val = instance.getValue();
        for (AttributeModifier mod : mods) instance.addModifier(mod);
        return val;
    }

    public static double getCd(Player player, EquipmentSlot slot) {
        return 1 / (getItemLess(player, Attribute.GENERIC_ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID.toString()) + ItemUtil.getAttribute(player.getInventory().getItem(slot), EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_SPEED)) * 20;
    }

    public static double getDmg(Player player, EquipmentSlot slot) {
        return getItemLess(player, Attribute.GENERIC_ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID.toString()) + ItemUtil.getAttribute(player.getInventory().getItem(slot), EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_DAMAGE);
    }

    public static double getKnockBack(Player player, EquipmentSlot slot) {
        return getItemLess(player, Attribute.GENERIC_ATTACK_KNOCKBACK) + ItemUtil.getAttribute(player.getInventory().getItem(slot), EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_KNOCKBACK);
    }

}