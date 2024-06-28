package comfortable_andy.combat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Vector2f;

import java.text.DecimalFormat;
import java.util.Vector;

public class CombatPlayerData {

    private static final int CACHE_COUNT = 5;

    private final Player player;

    /**
     * 1 for each tick that passed
     */
    private final Vector<Vector2f> lastCameraAngles = new Vector<>();
    public CombatPlayerData(Player player) {
        this.player = player;
    }

    public void tick() {
        final Location location = this.player.getLocation();
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        this.player.sendActionBar("delta: " + averageCameraAngleDelta().toString(new DecimalFormat("#.##")) + " cap: " + this.lastCameraAngles.capacity());
    }

    /**
     * @param v x-axis is left-right and y-axis is up-down
     */
    private void enterCamera(Vector2f v) {
        this.lastCameraAngles.add(0, v);
        this.lastCameraAngles.setSize(CACHE_COUNT);
    }

    /**
     *
     * @return average camera angle delta from up to the last {@link #CACHE_COUNT} ticks, where x-axis is yaw and y-axis is pitch
     */
    public Vector2f averageCameraAngleDelta() {
        final Vector2f accumulator = new Vector2f();
        for (int i = 1; i < CACHE_COUNT; i++) {
            final Vector2f cur = this.lastCameraAngles.get(i);
            if (cur == null) break;
            final Vector2f last = this.lastCameraAngles.get(i - 1);
            final Vector2f lastToCur = cur.sub(last, new Vector2f());
            accumulator.add(lastToCur);
        }
        return accumulator.div(CACHE_COUNT);
    }

    public Player getPlayer() {
        return player;
    }

}
