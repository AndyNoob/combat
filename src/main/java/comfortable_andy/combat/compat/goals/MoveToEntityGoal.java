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
        npc.faceLocation(trait.findMarker().getLocation().add(0, 1.5, 0));
        this.reason = null;
        this.finished = false;
    }

    @Override
    public BehaviorStatus run() {
        if (this.finished) {
            return this.reason == null ? BehaviorStatus.SUCCESS : BehaviorStatus.FAILURE;
        } else {
            return BehaviorStatus.RUNNING;
        }
    }

    @Override
    public boolean shouldExecute() {
        boolean executing = !this.npc.getNavigator().isNavigating() && this.target != null;

        if (executing) {
            System.out.println("speed " + trait.speed);
            this.npc.getNavigator().setTarget(target, false);
            this.npc.getNavigator().getLocalParameters()
                    .speedModifier((float) trait.speed)
//                    .lookAtFunction(n -> target.getLocation().add(0, 1.5, 0))
//                    .addRunCallback(() -> {
//                        if (trait.target != null)
//                            npc.faceLocation(trait.target.getEyeLocation().subtract(0, 2, 0));
//                    })
                    .addSingleUseCallback(reason -> {
                        this.finished = true;
                        this.reason = reason;
                    });
        }

        return executing;
    }
}
