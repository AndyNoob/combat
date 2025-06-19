package comfortable_andy.combat.compat.goals;

import comfortable_andy.combat.compat.CombatTrait;
import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.ai.event.CancelReason;
import net.citizensnpcs.api.ai.tree.BehaviorGoalAdapter;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;

@RequiredArgsConstructor
public class MoveToEntityGoal extends BehaviorGoalAdapter {

    public final NPC npc;
    public final CombatTrait trait;
    public final Entity target;
    private boolean finished = false;
    private CancelReason reason = null;

    @Override
    public void reset() {
        this.npc.getNavigator().cancelNavigation();
        this.reason = null;
        this.finished = false;
    }

    @Override
    public BehaviorStatus run() {
        if (trait.planningToAttack) return BehaviorStatus.FAILURE;
        if (this.finished) {
            return this.reason == null ? BehaviorStatus.SUCCESS : BehaviorStatus.FAILURE;
        } else {
            return BehaviorStatus.RUNNING;
        }
    }

    @Override
    public boolean shouldExecute() {
        if (trait.planningToAttack) return false;
        boolean executing = !this.npc.getNavigator().isNavigating() && this.target != null;

        if (executing) {
            this.npc.getNavigator().setTarget(target, false);
            this.npc.getNavigator().getLocalParameters()
                    .addSingleUseCallback(reason -> {
                        this.finished = true;
                        this.reason = reason;
                    });
        }

        return executing;
    }
}
