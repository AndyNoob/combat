package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@ToString
public class StabAction implements IAction {

    @Override
    public @NotNull ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        if (type == ActionType.DOUBLE_SNEAK || true) return ActionResult.NONE;
        boolean isAttack = type == ActionType.ATTACK;
        PlayerUtil.doSweep(
                player,
                fromDir(data.latestCameraDir()),
                new Vector3d(),
                1,
                isAttack,
                1,
                1,
                data.getCooldown(isAttack),
                data
        );
        return ActionResult.ACTIVATED;
    }
}
