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

@SuppressWarnings("FieldCanBeLocal")
@ToString
public class SweepAction implements IAction {

    private final float windBackRotY = 15;
    private final float attackRotY = 30;

    @Override
    public ActionResult tryActivate(CombatPlayerData data, ActionType type) {
        final Vector2f delta = data.averageCameraAngleDelta();
        if (Math.abs(delta.y) <= 8) return ActionResult.NONE;

        final Player player = data.getPlayer();
        final Location origin = player.getEyeLocation();
        // TODO item based reach
        final Quaterniond windBack = fromDir(this.windBackRotY, 0);
        final Quaterniond attack = fromDir(this.attackRotY, 0);

        // TODO separate windBack and attack

        if (delta.y > 0)
            windBack.invert();
        else attack.invert();

        final Quaterniond start = fromDir(origin);
        start.mul(windBack);
        for (Map.Entry<Damageable, Vector> entry :
                PlayerUtil.sweep(
                        player::getLocation,
                        PlayerUtil.getReach(player),
                        1,
                        start,
                        attack,
                        5
                ).entrySet()) {
            if (entry.getKey() == data.getPlayer()) continue;
            entry.getKey().teleport(entry.getKey().getLocation().add(entry.getValue()));
            // TODO do damage
        }
        return ActionResult.ACTIVATED;
    }

}
