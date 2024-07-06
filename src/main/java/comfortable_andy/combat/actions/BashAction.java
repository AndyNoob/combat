package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.entity.Player;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@ToString
public class BashAction implements IAction {

    float triggerAmount = 8;
    private float windBackRotX = 15;
    private float attackRotX = 30;
    private float speedMultiplier = 0.8f;
    private int steps = 5;

    boolean triggered(Vector2f delta) {
        return (delta.x > triggerAmount);
    }

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        final Vector2f delta = data.averageCameraAngleDelta();
        if (!triggered(delta)) return ActionResult.NONE;

        // TODO separate windBack and attack

        PlayerUtil.doSweep(
                player,
                fromDir(player.getLocation()).mul(new Quaterniond().rotateX(-Math.toRadians(windBackRotX))),
                new Vector3d(attackRotX, 0, 0),
                steps,
                type == ActionType.ATTACK,
                speedMultiplier
        );
        return ActionResult.ACTIVATED;
    }

}
