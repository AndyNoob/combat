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
import org.joml.Vector2f;
import org.mcmonkey.sentinel.SentinelIntegration;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
                    if (trait.chasing == null) {
                        iterator.remove();
                        continue;
                    }
                    if (npc == null
                            || !npc.isSpawned()
                            || !npc.hasTrait(trait.getClass())
                    ) {
                        iterator.remove();
                        continue;
                    }
                    TrackingData data = entry.getValue();
                    data.enterLocation(trait.chasing.getLocation());
                    Entity entity = trait.getNPC().getEntity();
                    if (entity instanceof Player player)
                        CombatMain.getInstance().getData(player).updateDelays();
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
                (k) -> new TrackingData()
        );
        final var average = data.getMovementAverage();
        final var attackedToNpc = attacker.getLocation().subtract(ent.getLocation()).toVector().normalize();
        final var left = attackedToNpc.clone().rotateAroundY(Math.toRadians(90)).setY(0).normalize();
        final var normalizedAverage = average.clone().normalize();
        final double direction = attackedToNpc.dot(normalizedAverage);
        final double leftness = left.dot(normalizedAverage);
        CombatPlayerData combatData = CombatMain.getInstance().getData(player);
        st.attackHelper.rechase();
        if (player.getEyeLocation().distanceSquared(ent.getEyeLocation()) > st.reach * st.reach) {
            return true;
        }
        if (ThreadLocalRandom.current().nextDouble() > direction) {
            final Vector2f entering;
            if (Math.abs(leftness) > 0.25) {
                // do sweep
                entering = new Vector2f(0, (float) (180 * CombatPlayerData.CACHE_COUNT * -Math.copySign(1, leftness)));
            } else {
                // do bash
                entering = new Vector2f(180 * CombatPlayerData.CACHE_COUNT, 0);
            }
            for (int i = 0; i < CombatPlayerData.CACHE_COUNT - 1; i++) {
                combatData.enterCamera(new Vector2f(entering).negate().mul(CombatPlayerData.CACHE_COUNT - i));
            }
            combatData.enterCamera(new Vector2f(entering));
        }
        ent.sendActionBar(Component.text(
                        str(average.toVector3d()) +
                                ", dot " + VecUtil.FORMAT.format(direction) +
                                ", dot left " + VecUtil.FORMAT.format(leftness)
                )
        );
        st.faceLocation(ent.getEyeLocation());
        if (CombatMain.getInstance()
                .runAction(player, IAction.ActionType.ATTACK, false))
            st.timeSinceAttack = 0;
        return true;
    }

    @Data
    public static class TrackingData {

        private static final int CACHE_SIZE = 10;

        private final Vector<Location> lastLocations = new Vector<>(CACHE_SIZE);
        private Class<? extends IAction> lastAction;
        private final Vector<org.bukkit.util.Vector> lastMovementAverages = new Vector<>(CACHE_SIZE);
        private final Vector<org.bukkit.util.Vector> lastMovementAverageAverages = new Vector<>(CACHE_SIZE);
        private org.bukkit.util.Vector movementAverage = new org.bukkit.util.Vector();
        private org.bukkit.util.Vector movementAverageAverage = new org.bukkit.util.Vector();

        public Vector<Location> getLastLocations() {
            return new Vector<>(lastLocations);
        }

        public void enterLocation(Location location) {
            if (location == null) return;
            lastLocations.add(0, location);
            lastLocations.setSize(CACHE_SIZE);
            final var movementAverage = bukkitAverage(
                    lastLocations.stream()
                            .filter(Objects::nonNull)
                            .map(Location::toVector)
                            .collect(Collectors.toList())
            );
            setMovementAverage(movementAverage);
        }

        public void setMovementAverage(org.bukkit.util.Vector movementAverage) {
            this.movementAverage = movementAverage;
            lastMovementAverages.add(0, movementAverage);
            lastMovementAverages.setSize(CACHE_SIZE);
            setMovementAverageAverage(bukkitAverage(lastMovementAverages));
        }

        public void setMovementAverageAverage(org.bukkit.util.Vector movementAverageAverage) {
            this.movementAverageAverage = movementAverageAverage;
            lastMovementAverageAverages.add(0, movementAverageAverage);
            lastMovementAverageAverages.setSize(CACHE_SIZE);
        }
    }

}
