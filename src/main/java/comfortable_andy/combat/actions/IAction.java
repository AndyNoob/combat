package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface IAction {

    @NotNull
    ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type);

    enum ActionType {
        ATTACK,
        INTERACT,
        DOUBLE_SNEAK
    }

    enum ActionResult {
        ACTIVATED,
        NONE
    }

}
