package comfortable_andy.combat;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Vector2f;

import java.util.Vector;

public class CombatPlayerData {

    @Getter
    private final Player player;

    /**
     * 1 for each tick that passed
     */
    @Getter
    private final Vector<Vector2f> lastCameraAngles = new Vector<>();
    private Pair<Long, Long> attackDelayLeft = new Pair<>(0L, 0L);
    private Pair<Long, Long> noAttackDelayLeft = new Pair<>(0L, 0L);
    @Getter
    private final CombatOptions options;
//    @Getter
//    @Setter
//    private OrientedBox testBox = null;

    public CombatPlayerData(Player player) {
        this.player = player;
        this.options = CombatMain.getInstance().getCombatOptions().clone();
    }

    @SuppressWarnings("deprecation")
    public void tick() {
        final Location location = this.player.getLocation();
        if (location.getWorld() == null) return;
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        updateDelays();
        if (CombatMain.getInstance().isShowActionBarDebug()) {
            this.player.sendActionBar(
                    "cd cd: " + attackDelayLeft.toString() +
                    " no cd: " + noAttackDelayLeft.toString()
            );
        }
    }

    public void updateDelays() {
        this.attackDelayLeft = this.attackDelayLeft.mapFirst(a -> Math.max(0, a - 1))
                .mapSecond(a -> Math.max(0, a - 1));
        this.noAttackDelayLeft = this.noAttackDelayLeft.mapFirst(a -> Math.max(0, a - 1))
                .mapSecond(a -> Math.max(0, a - 1));
    }

    /**
     * @param v x-axis is rotX and y-axis is rotY
     */
    public void enterCamera(Vector2f v) {
        this.lastCameraAngles.addFirst(v);
    }

    public long getCooldown(boolean main) {
        return (main ? this.attackDelayLeft.getFirst() : this.attackDelayLeft.getSecond());
    }

    public void setCooldown(boolean main, long amt) {
        this.attackDelayLeft = main ? this.attackDelayLeft.mapFirst(a -> amt) : this.attackDelayLeft.mapSecond(a -> amt);
    }

    public long getNoAttack(boolean main) {
        return (main ? this.noAttackDelayLeft.getFirst() : this.noAttackDelayLeft.getSecond());
    }

    public void setNoAttack(boolean main, long amt) {
        this.noAttackDelayLeft = main ? this.noAttackDelayLeft.mapFirst(a -> amt) : this.noAttackDelayLeft.mapSecond(a -> amt);
    }
}
