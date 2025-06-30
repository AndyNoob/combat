package comfortable_andy.combat.compat.goals;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.compat.CombatTrait;
import comfortable_andy.combat.util.PlayerUtil;
import net.citizensnpcs.api.ai.tree.BehaviorGoalAdapter;
import net.citizensnpcs.api.ai.tree.BehaviorStatus;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import static org.bukkit.util.NumberConversions.ceil;

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
        /*if (!trait.planningToAttack) {
            System.out.println("what the hell");
            Thread.dumpStack();
            return BehaviorStatus.FAILURE;
        }*/
        if (trait.target == null) {
            System.out.println("why are you null");
            return BehaviorStatus.FAILURE;
        }
        npc.faceLocation(trait.target.getLocation());
        Location location = trait.getPlayer().getLocation();
        Vector dir = trait.target.getLocation().subtract(location).toVector().normalize();
        location.setDirection(dir);
        data.overridePosAndCamera(location);
        if (CombatMain.getInstance().runAction(
                (Player) npc.getEntity(),
                IAction.ActionType.ATTACK,
                false
        )) {
            int noAttack = ceil(PlayerUtil.getCd(data.getPlayer(), EquipmentSlot.HAND));
            System.out.println(noAttack);
            data.setNoAttack(
                    true,
                    noAttack
            );
        }
        return BehaviorStatus.SUCCESS;
    }

    @Override
    public boolean shouldExecute() {
        data = CombatMain.getInstance().getData((Player) npc.getEntity());
        return trait.target != null && npc.getEntity() instanceof Player && trait.planningToAttack && data.getNoAttack(true) <= 0;
    }
}
