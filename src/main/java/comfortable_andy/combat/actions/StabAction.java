package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import org.bukkit.entity.Player;
import org.joml.Vector2f;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

public class StabAction implements IAction {

    private final SweepAction sweep;
    private final BashAction bash;

    public StabAction(SweepAction sweep, BashAction bash) {
        this.sweep = sweep;
        this.bash = bash;
    }

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        final Vector2f delta = data.averageCameraAngleDelta();
        if (sweep.triggered(delta) || bash.triggered(delta)) return ActionResult.NONE;
        PlayerUtil.doSweep(
                player,
                fromDir(player.getLocation()),
                new Vector3d(),
                1,
                type == ActionType.ATTACK,
                1
        );
        return null;
    }
}
