package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import org.bukkit.entity.Player;

public interface IAction {


    ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type);

    enum ActionType {
        ATTACK,
        INTERACT
    }

    enum ActionResult {
        ACTIVATED,
        NONE
    }

}
