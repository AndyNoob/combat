package comfortable_andy.combat.compat;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.util.VecUtil;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.mcmonkey.sentinel.SentinelIntegration;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static comfortable_andy.combat.util.VecUtil.bukkitAverage;
import static comfortable_andy.combat.util.VecUtil.str;

public class CombatSentinelIntegration extends SentinelIntegration {

    private final Map<SentinelTrait, TrackingData> ticking = new ConcurrentHashMap<>();

    public CombatSentinelIntegration() {
        new BukkitRunnable() {
            @Override
            public void run() {
                final var iterator = ticking.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<SentinelTrait, TrackingData> entry = iterator.next();
                    SentinelTrait trait = entry.getKey();
                    NPC npc = trait.getNPC();
                    if (trait.chasing == null
                            || trait.chasing.isDead()
                            || npc == null
                            || !npc.isSpawned()
                            || !npc.hasTrait(trait.getClass())
                    ) {
                        iterator.remove();
                        continue;
                    }
                    TrackingData data = entry.getValue();
                    data.enterLocation(trait.chasing.getLocation());
                }
            }
        }.runTaskTimer(CombatMain.getInstance(), 0, 1);
    }

    @Override
    public boolean tryAttack(SentinelTrait st, LivingEntity ent) {
        final Entity attacker = st.getNPC().getEntity();
        if (!(attacker instanceof Player player)) return false;
        TrackingData data = ticking.computeIfAbsent(
                st,
                (k) -> new TrackingData(CombatMain.getInstance().getData(player))
        );
        final var average = data.getMovementAverage();
        final var attackedToNpc = attacker.getLocation().subtract(ent.getLocation()).toVector().normalize();
        ent.sendActionBar(Component.text(str(average.toVector3d()) + ", dot " + attackedToNpc.dot(average.normalize())));
        return true;
    }

    @Data
    public static class TrackingData {
        private final CombatPlayerData combatData;
        private final Vector<Location> lastLocations = new Vector<>(20);
        private Class<? extends IAction> lastAction;
        private final Vector<org.bukkit.util.Vector> lastMovementAverages = new Vector<>(20);
        private final Vector<org.bukkit.util.Vector> lastMovementAverageAverages = new Vector<>(20);
        private org.bukkit.util.Vector movementAverage = new org.bukkit.util.Vector();
        private org.bukkit.util.Vector movementAverageAverage = new org.bukkit.util.Vector();

        public Vector<Location> getLastLocations() {
            return new Vector<>(lastLocations);
        }

        public void enterLocation(Location location) {
            if (location == null) return;
            lastLocations.add(0, location);
            lastLocations.setSize(20);
            final var movementAverage = bukkitAverage(
                    lastLocations.stream()
                            .map(Location::toVector)
                            .collect(Collectors.toList())
            );
            setMovementAverage(movementAverage);
        }

        public void setMovementAverage(org.bukkit.util.Vector movementAverage) {
            this.movementAverage = movementAverage;
            lastMovementAverages.add(0, movementAverage);
            lastMovementAverages.setSize(20);
            setMovementAverageAverage(bukkitAverage(lastMovementAverages));
        }

        public void setMovementAverageAverage(org.bukkit.util.Vector movementAverageAverage) {
            this.movementAverageAverage = movementAverageAverage;
            lastMovementAverageAverages.add(0, movementAverageAverage);
            lastMovementAverageAverages.setSize(20);
        }
    }

}
