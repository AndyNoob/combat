package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

public class StabAction implements IAction {

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        PlayerUtil.doSweep(
                player,
                fromDir(player.getLocation()),
                new Vector3d(),
                1,
                type == ActionType.ATTACK,
                1,
                1
        );
        return null;
    }
}
