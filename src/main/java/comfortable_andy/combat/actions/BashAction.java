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
public class BashAction implements IAction {

    private float windBackRotX = 15;
    private float attackRotX = 30;
    private int steps = 5;

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        final Vector2f delta = data.averageCameraAngleDelta();
        if (delta.x >= -8) return ActionResult.NONE;

        // TODO separate windBack and attack

        PlayerUtil.doSweep(
                player,
                fromDir(player.getLocation()),
                new Vector3d(-attackRotX, 0, 0),
                steps
        );
        return ActionResult.ACTIVATED;
    }

}
