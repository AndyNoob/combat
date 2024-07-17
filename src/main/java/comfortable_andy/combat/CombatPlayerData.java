package comfortable_andy.combat;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.joml.Vector2f;

import java.util.Vector;

import static comfortable_andy.combat.util.PlayerUtil.getItemLess;
import static comfortable_andy.combat.util.PlayerUtil.getKnockBack;
import static comfortable_andy.combat.util.VecUtil.FORMAT;
import static net.minecraft.util.Mth.degreesDifference;

public class CombatPlayerData {

    private static final int CACHE_COUNT = 5;

    @Getter
    private final Player player;

    /**
     * 1 for each tick that passed
     */
    private final Vector<Vector2f> lastCameraAngles = new Vector<>();
    private Pair<Long, Long> attackDelayLeft = new Pair<>(0L, 0L);

    public CombatPlayerData(Player player) {
        this.player = player;
    }

    @SuppressWarnings("deprecation")
    public void tick() {
        final Location location = this.player.getLocation();
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        this.attackDelayLeft = this.attackDelayLeft.mapFirst(a -> Math.max(0, a - 1)).mapSecond(a -> Math.max(0, a - 1));
        if (CombatMain.getInstance().isShowActionBarDebug()) {
            this.player.sendActionBar("delta: " + averageCameraAngleDelta().toString(FORMAT) +
                    " cap: " + this.lastCameraAngles.capacity() +
                    " kb: " + getItemLess(player, Attribute.GENERIC_ATTACK_KNOCKBACK) +
                    " main kb: " + FORMAT.format(getKnockBack(player, EquipmentSlot.HAND)) +
                    " off kb: " + FORMAT.format(getKnockBack(player, EquipmentSlot.OFF_HAND)) +
                    " cd cd: " + attackDelayLeft.toString()
            );
        }
    }

    /**
     * @param v x-axis is rotX and y-axis is rotY
     */
    private void enterCamera(Vector2f v) {
        this.lastCameraAngles.add(0, v);
        this.lastCameraAngles.setSize(CACHE_COUNT);
    }

    /**
     * @return average camera angle delta from up to the last {@link #CACHE_COUNT} ticks, where x-axis is yaw (rotX) and y-axis is pitch (rotY)
     */
    public Vector2f averageCameraAngleDelta() {
        final Vector2f accumulator = new Vector2f();
        for (int i = 1; i < CACHE_COUNT; i++) {
            if (i >= this.lastCameraAngles.size()) break;
            final Vector2f cur = this.lastCameraAngles.get(i);
            if (cur == null) break;
            final Vector2f last = this.lastCameraAngles.get(i - 1);
            final Vector2f lastToCur = new Vector2f(degreesDifference(cur.x, last.x), degreesDifference(cur.y, last.y));
            accumulator.add(lastToCur);
        }
        return accumulator.div(CACHE_COUNT);
    }

    public long getCooldown(boolean main) {
        return (main ? this.attackDelayLeft.getFirst() : this.attackDelayLeft.getSecond());
    }

    public void setCooldown(boolean main, long amt) {
        this.attackDelayLeft = main ? this.attackDelayLeft.mapFirst(a -> amt) : this.attackDelayLeft.mapSecond(a -> amt);
    }

}
