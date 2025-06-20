package comfortable_andy.combat.compat;

import comfortable_andy.combat.compat.goals.AttackGoal;
import comfortable_andy.combat.compat.goals.MoveToEntityGoal;
import net.citizensnpcs.api.ai.Goal;
import net.citizensnpcs.api.ai.GoalController;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Marker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TraitName("combat")
public class CombatTrait extends Trait {

    @Persist("markerId")
    public String markerId;
    private Marker marker;
    public boolean planningToAttack = false;
    private final List<Goal> goals = new ArrayList<>();
    private ArmorStand markerMarker;
    private ArmorStand npcMarker;
    public LivingEntity target = null;

    public CombatTrait() {
        super("combat");
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) return;
        // TODO targeting logic
        npc.getEntity().setInvulnerable(false);
        npc.getDefaultGoalController().run();
        Marker marker = findMarker();
        if (markerMarker == null)
            markerMarker = marker.getWorld().spawn(marker.getLocation(), ArmorStand.class, a -> {
                a.setMarker(true);
                a.setPersistent(false);
                a.setSmall(true);
                a.setGlowing(true);
                a.setArms(true);
            });
        if (npcMarker == null)
            npcMarker = npc.getEntity().getLocation().getWorld().spawn(npc.getStoredLocation(), ArmorStand.class, a -> {
                a.setMarker(true);
                a.setPersistent(false);
                a.setSmall(true);
                a.setGlowing(true);
                a.setArms(false);
                a.setBasePlate(false);
            });
        markerMarker.teleport(marker);
        npcMarker.teleport(npc.getStoredLocation());
        moveMarkerToTarget();
    }

    public void moveMarkerToTarget() {
        if (target == null) {
            tryGroundMarker();
            return;
        }
        Marker marker = findMarker();
        var offset = target.getLocation()
                .subtract(getLocation())
                .toVector();
        offset.normalize()
                .rotateAroundY(Math.PI / 2);
        var newPos = target.getLocation().add(offset);
        marker.teleport(newPos);
    }

    public void tryGroundMarker() {
        Marker marker = findMarker();
        Location location = marker.getLocation();
        Block block = location.getBlock();
        if (!block.isEmpty()
                || !block.getRelative(0, -1, 0).isEmpty()) {
            return;
        }
        for (int i = 2; i < 20; i++) {
            Block relative = block.getRelative(0, -i, 0);
            if (!relative.isEmpty()) {
                marker.teleport(relative.getLocation().add(0, 1, 0));
                break;
            }
        }
    }

    @Override
    public void onAttach() {
        GoalController controller = npc.getDefaultGoalController();
        Goal attackGoal = new AttackGoal(npc, this);
        controller.addGoal(attackGoal, 1);
        Goal moveToGoal = new MoveToEntityGoal(
                npc,
                this,
                findMarker()
        );
        controller.addGoal(
                moveToGoal,
                1
        );
        goals.add(attackGoal);
        goals.add(moveToGoal);
        System.out.println("attached");
        // TODO other goals
    }

    @Override
    public void onRemove() {
        goals.forEach(g -> npc.getDefaultGoalController().removeGoal(g));
    }

    @Override
    public boolean isRunImplemented() {
        return true;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {

    }

    public Marker findMarker() {
        if (marker == null || marker.isDead()) {
            Entity e = markerId == null ? null : Bukkit.getEntity(UUID.fromString(markerId));
            if (!(e instanceof Marker m)) {
                Location location = npc.getStoredLocation();
                marker = location.getWorld().spawn(location, Marker.class, m -> {
                    m.customName(Component.text("combat trait marker " + npc.getId()));
                    m.setPersistent(true);
                });
                markerId = marker.getUniqueId().toString();
            } else marker = m;
        }
        return marker;
    }

    public @NotNull Location getLocation() {
        return npc.isSpawned() ? npc.getEntity().getLocation() : npc.getStoredLocation();
    }
}
