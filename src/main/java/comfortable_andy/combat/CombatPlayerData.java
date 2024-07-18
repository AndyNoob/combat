package comfortable_andy.combat;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Vector2f;

import java.util.List;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static comfortable_andy.combat.util.VecUtil.FORMAT;
import static comfortable_andy.combat.util.VecUtil.fromBukkit;
import static net.minecraft.util.Mth.degreesDifference;

public class CombatPlayerData {

    private static final int CACHE_COUNT = 5;

    @Getter
    private final Player player;

    /**
     * 1 for each tick that passed
     */
    private final Vector<Vector2f> lastCameraAngles = new Vector<>();
    private World lastWorld = null;
    private final Vector<org.bukkit.util.Vector> lastPositions = new Vector<>();
    private Pair<Long, Long> attackDelayLeft = new Pair<>(0L, 0L);

    public CombatPlayerData(Player player) {
        this.player = player;
    }

    @SuppressWarnings("deprecation")
    public void tick() {
        final Location location = this.player.getLocation();
        if (location.getWorld() == null) return;
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        this.enterPos(location);
        this.attackDelayLeft = this.attackDelayLeft.mapFirst(a -> Math.max(0, a - 1)).mapSecond(a -> Math.max(0, a - 1));
        if (CombatMain.getInstance().isShowActionBarDebug()) {
            this.player.sendActionBar("cam delta: " + averageCameraAngleDelta().toString(FORMAT) +
                    " pos delta: " + fromBukkit(averagePosDelta()).toString(FORMAT) +
                    " cd cd: " + attackDelayLeft.toString()
            );
        }
        this.lastWorld = location.getWorld();
    }

    /**
     * @param v x-axis is rotX and y-axis is rotY
     */
    private void enterCamera(Vector2f v) {
        this.lastCameraAngles.add(0, v);
        this.lastCameraAngles.setSize(CACHE_COUNT);
    }

    public Vector2f averageCameraAngleDelta() {
        return average(
                Vector2f::new,
                this.lastCameraAngles,
                Vector2f::add,
                Vector2f::negate,
                (v, n) -> v.div(n.floatValue())
        );
    }

    private void enterPos(Location loc) {
        if (lastWorld != loc.getWorld()) lastPositions.clear();
        this.lastPositions.add(loc.toVector());
        this.lastPositions.setSize(CACHE_COUNT);
    }

    public org.bukkit.util.Vector averagePosDelta() {
        return average(
                org.bukkit.util.Vector::new,
                this.lastPositions,
                org.bukkit.util.Vector::add,
                v -> v.multiply(-1),
                (v, n) -> v.divide(new org.bukkit.util.Vector(n.floatValue(), n.floatValue(), n.floatValue()))
        );
    }

    /**
     * @return average camera angle delta from up to the last {@link #CACHE_COUNT} ticks, where x-axis is yaw (rotX) and y-axis is pitch (rotY)
     */
    private <Vec> Vec average(Supplier<Vec> vecSupplier, List<Vec> list, BiFunction<Vec, Vec, Vec> add, Function<Vec, Vec> negate, BiFunction<Vec, Number, Vec> multi) {
        final Vec accumulator = vecSupplier.get();
        final int size = list.size();
        for (int i = 1; i < size; i++) {
            final Vec cur = list.get(i);
            if (cur == null) break;
            final Vec last = list.get(i - 1);
            final Vec lastToCur = vecSupplier.get();
            add.apply(lastToCur, cur);
            add.apply(lastToCur, negate.apply(last));
            add.apply(accumulator, lastToCur);
        }
        return multi.apply(accumulator, size > 0 ? 1 / size : 1);
    }

    public long getCooldown(boolean main) {
        return (main ? this.attackDelayLeft.getFirst() : this.attackDelayLeft.getSecond());
    }

    public void setCooldown(boolean main, long amt) {
        this.attackDelayLeft = main ? this.attackDelayLeft.mapFirst(a -> amt) : this.attackDelayLeft.mapSecond(a -> amt);
    }

}
