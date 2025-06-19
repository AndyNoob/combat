package comfortable_andy.combat.compat.goals;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.compat.CombatTrait;
import net.citizensnpcs.api.ai.tree.BehaviorGoalAdapter;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

public class AttackGoal extends BehaviorGoalAdapter {

    public final NPC npc;
    public final CombatTrait trait;
    public CombatPlayerData data;

    public AttackGoal(NPC npc, CombatTrait trait) {
        assert npc.getEntity() instanceof Player;
        this.npc = npc;
        this.trait = trait;
    }

    @Override
    public void reset() {
    }

    @Override
    public BehaviorStatus run() {
        return CombatMain.getInstance().runAction(
                (Player) npc.getEntity(),
                IAction.ActionType.ATTACK,
                false
        ) ? BehaviorStatus.SUCCESS : BehaviorStatus.FAILURE;
    }

    @Override
    public boolean shouldExecute() {
        data = CombatMain.getInstance().getData((Player) npc.getEntity());
        return npc.getEntity() instanceof Player && trait.planningToAttack && data.getNoAttack(true) <= 0;
    }
}
