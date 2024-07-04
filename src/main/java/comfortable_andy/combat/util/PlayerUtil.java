package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import net.kyori.adventure.key.Key;
import net.minecraft.world.item.Item;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.intellij.lang.annotations.Subst;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static comfortable_andy.combat.util.VecUtil.rotateLocal;
import static org.bukkit.util.NumberConversions.ceil;

public class PlayerUtil {

    @SuppressWarnings("UnstableApiUsage")
    public static void doSweep(Player player, Quaterniond start, Vector3d attack, int steps, boolean isAttack) {
        final EquipmentSlot slot = isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        final int ticks = ceil(getCd(player, slot));
        final double damagePerHit = getDmg(player, slot) / steps;
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
                    damaged.teleport(damaged.getLocation().add(mtv));
                    damaged.damage(
                            damagePerHit,
                            DamageSource
                                    .builder(DamageType.PLAYER_ATTACK)
                                    .withCausingEntity(player)
                                    .withDirectEntity(player)
                                    .build()
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
                // TODO separate this to different ticks
                Location loc = supplier.get();
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
                    final Vector mtv = attackBox.collides(entityBox);
                    if (mtv != null) {
                        callback.accept(entity, mtv);
                        return true;
                    } else return false;
                });
                attackBox.rotateBy(step);
                if (stepsLeft-- <= 0) cancel();
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

}