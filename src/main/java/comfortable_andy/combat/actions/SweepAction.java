package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.entity.Player;
import org.joml.Vector2f;
import org.joml.Vector3d;

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

        final Vector3d windBack = new Vector3d(0, windBackRotY, 0);
        final Vector3d attack = new Vector3d(0, -attackRotY, 0);

        // TODO separate windBack and attack

        if (delta.y > 0)
            windBack.negate();
        else attack.negate();

        PlayerUtil.doSweep(player, fromDir(player.getEyeLocation()), attack, steps, type == ActionType.ATTACK);
        return ActionResult.ACTIVATED;
    }

}
