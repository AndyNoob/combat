package comfortable_andy.combat.compat.goals;

import comfortable_andy.combat.compat.CombatTrait;
import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.ai.tree.BehaviorGoalAdapter;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.NPC;

@RequiredArgsConstructor
public class AttackGoal extends BehaviorGoalAdapter {

    public final NPC npc;
    public final CombatTrait trait;

    @Override
    public void reset() {
    }

    @Override
    public BehaviorStatus run() {

    }

    @Override
    public boolean shouldExecute() {
        return trait.planningToAttack;
    }
}
