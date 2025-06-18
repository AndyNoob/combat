package comfortable_andy.combat.compat;

import comfortable_andy.combat.compat.goals.AttackGoal;
import comfortable_andy.combat.compat.goals.MoveToEntityGoal;
import net.citizensnpcs.api.ai.GoalController;
import net.citizensnpcs.api.ai.tree.Loop;
import net.citizensnpcs.api.ai.tree.Selector;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;

import java.util.UUID;

@TraitName("combat")
public class CombatTrait extends Trait {

    @Persist("markerId")
    public UUID markerId;
    private Marker marker;
    public boolean planningToAttack;

    public CombatTrait() {
        super("combat");
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) return;
        // TODO targeting logic
    }

    @Override
    public void onAttach() {
        GoalController controller = npc.getDefaultGoalController();
        Selector composite = Selector
                .selecting(
                        new AttackGoal(npc, this),
                        new Loop(new MoveToEntityGoal(npc, this, findMarker()), () -> !planningToAttack)
                )
                .retryChildren(false)
                .selectionFunction(li -> planningToAttack ? li.getFirst() : li.getLast())
                .build();
        controller.addGoal(composite, 0);
        // TODO other goals
    }

    @Override
    public boolean isRunImplemented() {
        return true;
    }

    public Marker findMarker() {
        if (marker == null || marker.isDead()) {
            Entity e = Bukkit.getEntity(markerId);
            if (!(e instanceof Marker m)) {
                Location location = npc.getStoredLocation();
                marker = location.getWorld().spawn(location, Marker.class, m -> {
                    m.customName(Component.text("combat trait marker " + npc.getId()));
                    m.setPersistent(true);
                });
                markerId = marker.getUniqueId();
            } else marker = m;
        }
        return marker;
    }

}
