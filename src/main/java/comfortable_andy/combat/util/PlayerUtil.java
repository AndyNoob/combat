package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import io.papermc.paper.configuration.WorldConfiguration;
import net.kyori.adventure.key.Key;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.intellij.lang.annotations.Subst;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static comfortable_andy.combat.util.VecUtil.rotateLocal;
import static org.bukkit.util.NumberConversions.ceil;

public class PlayerUtil {

    @SuppressWarnings("UnstableApiUsage")
    public static void doSweep(Player player, Quaterniond start, Vector3d attack, int steps, boolean isAttack, float speedMod, float damageMod) {
        final EquipmentSlot slot = isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        final int ticks = ceil(getCd(player, slot) * speedMod);
        final ItemStack item = player.getInventory().getItem(slot);
        double damage = getDmg(player, slot);
        final int sharpness = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            damage += 0.5 * sharpness + 0.5;
        }
        player.damageItemStack(item, 1);
        final double knockBack = getKnockBack(player, slot) + (player.isSprinting() ? 1 : 0) + item.getEnchantmentLevel(Enchantment.KNOCKBACK);
        final double finalDamage = damage * damageMod;
        final int impalingLevel = item.getEnchantmentLevel(Enchantment.IMPALING);
        final int arthropodLevel = item.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        final int smiteLevel = item.getEnchantmentLevel(Enchantment.SMITE);
        PlayerUtil.sweep(
                player::getEyeLocation,
                PlayerUtil.getReach(player),
                1,
                start,
                attack,
                steps,
                ceil(ticks / (steps + 0d)),
                (damaged, mtv) -> {
                    if (damaged == player) return;
                    if (!player.hasLineOfSight(damaged)) return;
                    final World world = player.getWorld();
                    final ServerLevel level = ((CraftWorld) world).getHandle();
                    final WorldConfiguration paperConfig = level.paperConfig();
                    if (knockBack > 0 && damaged instanceof LivingEntity e) {
                        if (!paperConfig.misc.disableSprintInterruptionOnAttack)
                            player.setSprinting(false);
                        e.knockback(knockBack, -mtv.getX(), -mtv.getZ());
                    }
                    double mod = 0;

                    if (Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(damaged.getType())) mod += smiteLevel * 2.5;
                    if (Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(damaged.getType()))
                        mod += arthropodLevel * 2.5;
                    if (Tag.ENTITY_TYPES_SENSITIVE_TO_IMPALING.isTagged(damaged.getType())) mod += impalingLevel * 2.5;

                    final DamageSource source = DamageSource
                            .builder(DamageType.PLAYER_ATTACK)
                            .withCausingEntity(player)
                            .withDirectEntity(player)
                            .build();
                    @SuppressWarnings("deprecation") final boolean critical = !player.isClimbing() && player.getFallDistance() > 0 && !player.isOnGround() && !player.isInWater() && !player.isSprinting() && !player.isInsideVehicle() && !player.hasPotionEffect(PotionEffectType.BLINDNESS) && !paperConfig.entities.behavior.disablePlayerCrits;
                    double finalFinalDamage = finalDamage + mod / steps;
                    final Location location = player.getLocation();
                    if (critical) {
                        ((CraftDamageSource) source).getHandle().critical();
                        finalFinalDamage *= 1.5;
                        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1, 1);
                        ((CraftPlayer) player).getHandle().crit(((CraftEntity) damaged).getHandle());
                    }
                    world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1, 1);
                    if (player.isSprinting())
                        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1, 1);
                    damaged.damage(
                            finalFinalDamage,
                            source
                    );
                }
        );
    }

    /**
     * @param supplier the origin of the sweep, should be the eye location
     * @param start    the rotation to start the sweep
     * @param delta    this added to {@code start}
     * @param steps    how many steps
     */
    public static void sweep(Supplier<Location> supplier, float reach, float size, Quaterniond start, Vector3d delta, int steps, int ticksPerStep, BiConsumer<Damageable, Vector> callback) {
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
        final Map<Damageable, OrientedBox> possible = new HashMap<>();

        new BukkitRunnable() {
            int stepsLeft = steps;

            public void run() {
                if (stepsLeft-- <= 0) {
                    cancel();
                    return;
                }
                Location loc = supplier.get();
                Vector direction = loc.getDirection();
                possible.putAll(loc
                        .getNearbyEntitiesByType(Damageable.class, reach)
                        .stream()
                        .filter(e -> !possible.containsKey(e))
                        .collect(HashMap::new, (m, d) -> m.put(d, new OrientedBox(d.getBoundingBox())), HashMap::putAll)
                );

                if (possible.isEmpty()) return;

                attackBox.clone().display(loc.getWorld());

                // TODO efficient expansion of collider?
                possible.entrySet().removeIf(e -> {
                    final Damageable entity = e.getKey();
                    final OrientedBox entityBox = e.getValue();
                    entityBox.moveBy(entity.getBoundingBox().getCenter().subtract(entityBox.getCenter()));
                    CombatMain.getInstance().debug(entity.getName());
                    CombatMain.getInstance().debug(entityBox);
                    final Vector mtv = attackBox.collides(entityBox, Comparator.comparingDouble(direction::dot).reversed())
                            .stream().filter(v -> direction.dot(v) > 0).findFirst().orElse(null);
                    if (mtv != null) {
                        callback.accept(entity, mtv.normalize());
                        return true;
                    } else return false;
                });
                attackBox.rotateBy(step);
            }
        }.runTaskTimer(CombatMain.getInstance(), 0, ticksPerStep);
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