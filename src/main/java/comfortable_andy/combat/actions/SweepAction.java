package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaterniond;
import org.joml.Vector2f;

import java.util.Map;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@ToString
public class SweepAction implements IAction {

    private float windBackRotY = 15;
    private float attackRotY = 30;
    private int steps = 5;

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        final Vector2f delta = data.averageCameraAngleDelta();
        if (Math.abs(delta.y) <= 8) return ActionResult.NONE;

        final Location origin = player.getEyeLocation();
        // TODO item based reach
        final Quaterniond windBack = new Quaterniond().rotateY(Math.toRadians(this.windBackRotY));
        final Quaterniond attack = new Quaterniond().rotateY(Math.toRadians(this.attackRotY));

        // TODO separate windBack and attack

        if (delta.y > 0)
            windBack.invert();
        else attack.invert();

        final Quaterniond start = fromDir(origin);
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
            if (entry.getKey() == data.getPlayer()) continue;
            entry.getKey().teleport(entry.getKey().getLocation().add(entry.getValue()));
            // TODO do damage
        }
        return ActionResult.ACTIVATED;
    }

}
