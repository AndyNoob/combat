package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;

public interface IAction {

    ActionResult tryActivate(CombatPlayerData player, ActionType type);

    enum ActionType {
        ATTACK,
        INTERACT
    }

    enum ActionResult {
        CRITICAL,
        SWEEP,
        POKE,
        NONE
    }

}
