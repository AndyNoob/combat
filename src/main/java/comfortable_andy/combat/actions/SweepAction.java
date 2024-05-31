package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;

public class SweepAction implements IAction {

    @Override
    public ActionResult tryActivate(CombatPlayerData player, ActionType type) {
        if (type != ActionType.ATTACK) return ActionResult.NONE;
        if (player.averageCameraAngleDelta().x <= 8) return ActionResult.NONE;
        // TODO actually sweep
        return ActionResult.ACTIVATED;
    }

}
