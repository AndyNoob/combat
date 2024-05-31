package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Damageable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PlayerUtil {

    /**
     * Say the {@code startYaw} is 0, {@code delta} is 10, and {@code steps} is 5, there would be 6 collision checks (the initial + the 5 steps).
     * @param loc the origin of the sweep, should be the eye location
     * @param startYaw the yaw to start the sweep
     * @param delta this added to {@code startYaw} should be the end yaw
     * @param steps how many steps
     * @return the hit entities and their minimum translation vector.
     */
    @NotNull
    public static Map<Damageable, @NotNull Vector> sweep(Location loc, float reach, float size, float startYaw, float delta, int steps) {
        if (Math.abs(delta) <= Vector.getEpsilon()) return Collections.emptyMap();
        loc = loc.clone();

        final Map<Damageable, Vector> map = new HashMap<>();
        final float halfSize = size / 2;
        final OrientedBox box = new OrientedBox(
                BoundingBox.of(loc.clone()
                                .add(+halfSize, +halfSize, 0),
                        loc.clone()
                                .add(-halfSize, -halfSize, reach)
                )
        )
                .setCenter(loc.toVector())
                .rotateBy(BlockFace.EAST.getDirection(), Math.toRadians(loc.getPitch()));

        final Collection<Damageable> possible = loc.getNearbyEntitiesByType(Damageable.class, reach);

        for (int i = 0; i < steps; i++) {
            if (possible.isEmpty()) break;

            box.rotateBy(BlockFace.UP.getDirection(), -Math.toRadians(startYaw + delta / steps * i));
            box.display(loc.getWorld());
            // TODO efficient expansion of collider?
            possible.removeIf(e -> {
                CombatMain.getInstance().debug(e.getName());
                CombatMain.getInstance().debug(new OrientedBox(e.getBoundingBox()));
                final Vector mtv = box.collides(new OrientedBox(e.getBoundingBox()));
                if (mtv != null) {
                    map.put(e, mtv);
                    return true;
                }
                return false;
            });
        }

        return map;
    }

}