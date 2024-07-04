package comfortable_andy.combat;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.joml.Vector2f;

import java.util.Vector;

import static comfortable_andy.combat.util.ItemUtil.getAttribute;
import static comfortable_andy.combat.util.PlayerUtil.getItemLess;
import static comfortable_andy.combat.util.VecUtil.FORMAT;

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

    public void tick() {
        final Location location = this.player.getLocation();
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        this.attackDelayLeft = this.attackDelayLeft.mapFirst(a -> Math.max(0, a - 1)).mapSecond(a -> Math.max(0, a - 1));
        this.player.sendActionBar("delta: " + averageCameraAngleDelta().toString(FORMAT) +
                " cap: " + this.lastCameraAngles.capacity() +
                " dmg: " + getItemLess(player, Attribute.GENERIC_ATTACK_DAMAGE/*, Item.BASE_ATTACK_DAMAGE_ID.toString()*/) +
                " main dmg: " + FORMAT.format(getAttribute(player.getInventory().getItemInMainHand(), EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_DAMAGE)) +
                " off dmg: " + FORMAT.format(getAttribute(player.getInventory().getItemInOffHand(), EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_DAMAGE)) +
                " cd cd: " + attackDelayLeft.toString()
        );
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
            final Vector2f cur = this.lastCameraAngles.get(i);
            if (cur == null) break;
            final Vector2f last = this.lastCameraAngles.get(i - 1);
            final Vector2f lastToCur = cur.sub(last, new Vector2f());
            accumulator.add(lastToCur);
        }
        return accumulator.div(CACHE_COUNT);
    }

    public boolean inCooldown(boolean main) {
        return (main ? this.attackDelayLeft.getFirst() : this.attackDelayLeft.getSecond()) > 0;
    }

    public void addCooldown(boolean main, long amt) {
        this.attackDelayLeft = main ? this.attackDelayLeft.mapFirst(a -> amt) : this.attackDelayLeft.mapSecond(a -> amt);
    }

}
