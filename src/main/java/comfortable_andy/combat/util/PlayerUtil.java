package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static comfortable_andy.combat.util.VecUtil.fromDir;

public class PlayerUtil {

    public static void doSweep(Player player, Quaterniond windBack, Quaterniond attack, int steps) {
        final Quaterniond start = fromDir(player.getEyeLocation());
        start.mul(windBack);
        for (Map.Entry<Damageable, Vector> entry :
                PlayerUtil.sweep(
                        player::getEyeLocation,
                        PlayerUtil.getReach(player),
                        1,
                        start,
                        attack,
                        steps
                ).entrySet()) {
            if (entry.getKey() == player) continue;
            entry.getKey().teleport(entry.getKey().getLocation().add(entry.getValue()));
            // TODO do damage
        }
    }

    /**
     * @param supplier   the origin of the sweep, should be the eye location
     * @param start the rotation to start the sweep
     * @param delta this added to {@code start}
     * @param steps how many steps
     * @return the hit entities and their minimum translation vector.
     */
    @NotNull
    public static Map<Damageable, @NotNull Vector> sweep(Supplier<Location> supplier, float reach, float size, Quaterniond start, Quaterniond delta, int steps) {
        Location loc = supplier.get().clone();

        final Map<Damageable, Vector> map = new HashMap<>();
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

        final Quaterniond step = new Quaterniond().nlerp(delta, steps <= 1 ? 1 : 1 / (steps - 1d));

        if (steps <= 1)
            attackBox.rotateBy(step);

        for (int i = 0; i < steps; i++) {
            // TODO separate this to different ticks
            loc = supplier.get();
            final Map<Damageable, OrientedBox> possible = loc
                    .getNearbyEntitiesByType(Damageable.class, reach)
                    .stream()
                    .filter(e -> !map.containsKey(e))
                    .collect(HashMap::new, (m, d) -> m.put(d, new OrientedBox(d.getBoundingBox())), HashMap::putAll);

            if (possible.isEmpty()) break; // TODO check this first thing before creating so many oriented boxes

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
                    map.put(e.getKey(), mtv);
                    return true;
                } else return false;
            });
            attackBox.rotateBy(step);
        }

        return map;
    }

    public static float getReach(Player player) {
        return getValueFrom(player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE));
    }

    private static float getValueFrom(AttributeInstance instance) {
        if (instance == null) return 0;
        return (float) instance.getValue();
    }

}