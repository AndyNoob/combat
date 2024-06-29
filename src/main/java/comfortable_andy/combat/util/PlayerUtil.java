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

public class PlayerUtil {

    /**
     * @param loc   the origin of the sweep, should be the eye location
     * @param start the rotation to start the sweep
     * @param delta this added to {@code start}
     * @param steps how many steps
     * @return the hit entities and their minimum translation vector.
     */
    @NotNull
    public static Map<Damageable, @NotNull Vector> sweep(Location loc, float reach, float size, Quaterniond start, Quaterniond delta, int steps) {
        loc = loc.clone();

        start.normalize();
        delta.normalize();

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

        final Map<Damageable, OrientedBox> possible = loc
                .getNearbyEntitiesByType(Damageable.class, reach)
                .stream()
                .collect(HashMap::new, (m, d) -> m.put(d, new OrientedBox(d.getBoundingBox())), HashMap::putAll);
        final Quaterniond step = new Quaterniond().nlerp(delta, steps <= 1 ? 1 : 1 / (steps - 1d));

        if (steps <= 1)
            attackBox.rotateBy(step);

        for (int i = 0; i < steps; i++) {
            // TODO separate this to different ticks
            // TODO don't recreate everytime, create once and use #move if needed
            if (possible.isEmpty()) break;

            attackBox.display(loc.getWorld());
            // TODO efficient expansion of collider?
            possible.entrySet().removeIf(e -> {
                final Damageable entity = e.getKey();
                final OrientedBox entityBox = e.getValue();
                // TODO also move box
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