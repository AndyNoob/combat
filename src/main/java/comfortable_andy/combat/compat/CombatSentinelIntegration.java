package comfortable_andy.combat.compat;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.Data;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Vector2f;
import org.mcmonkey.sentinel.SentinelIntegration;
import org.mcmonkey.sentinel.SentinelPlugin;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static comfortable_andy.combat.util.VecUtil.bukkitAverage;

public class CombatSentinelIntegration extends SentinelIntegration {

    public static final String META_KEY = "use-combat";

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
                    LivingEntity chasing = trait.chasing;
                    if (chasing == null) {
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

                    Location targetAsLocation = npc.getNavigator().getTargetAsLocation();
                    /*Location loc = targetAsLocation;
                    if (loc != null && npc.getNavigator().isNavigating())
                        npc.getEntity().getWorld().spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0);*/

                    TrackingData data = entry.getValue();
                    data.enterLocation(chasing.getLocation());
                    Entity entity = trait.getNPC().getEntity();
                    if (entity instanceof Player player)
                        CombatMain.getInstance().getData(player).updateDelays();

                    if (trait.cTick < SentinelPlugin.instance.tickRate) {
                        boolean attacking = isPlanningAttack(chasing, false);
                        if (chasing.hasActiveItem() || attacking) {
                            if (!trait.isBlocking) trait.startBlocking();
                            trait.faceLocation(chasing.getEyeLocation());
                        }
                        double maceDist = chasing.getLocation()
                                .distance(trait.getLivingEntity().getEyeLocation());
                        if (trait.getLivingEntity() instanceof Player player) {
                            Location location = player.getLocation();
                            org.bukkit.util.Vector dir = chasing.getLocation().subtract(location).toVector().normalize();
                            float chaseReach = PlayerUtil.getReach(chasing);
                            if (
                                    isPlanningAttack(chasing, true)
                                            && maceDist < chaseReach * 2
                                            && CombatMain.getInstance().getData(player).getNoAttack(true) < 1
                            ) {
//                                trait.faceLocation(chasing.getEyeLocation());
//                                location.setDirection(dir);
//                                CombatMain.getInstance().getData(player).overridePosAndCamera(location);
//                                player.setSprinting(true);
//                                CombatMain.getInstance().runAction(player, IAction.ActionType.ATTACK, false);
//                                player.setSprinting(false);
                                double horizontalDistance = player.getLocation()
                                        .subtract(chasing.getLocation()).toVector()
                                        .setY(0)
                                        .length();
                                player.setVelocity(new org.bukkit.util.Vector(0, 0, 1)
                                        .rotateAroundY(-Math.toRadians(chasing.getLocation().getYaw()))
                                        .multiply(horizontalDistance > chaseReach ? 1 : -1)
                                        .multiply(Math.sqrt(trait.speed / 3))
                                        .setY(0.1)
                                );
                            }
                            Block below = player.getLocation().subtract(0, 0.1, 0).getBlock();
                            if (!npc.isFlyable() && below.isEmpty()
                                    && below.getRelative(0, -1, 0).isEmpty()
                                    && below.getRelative(0, -2, 0).isEmpty()
                                    && below.getRelative(0, -3, 0).isEmpty()
                                    && below.getRelative(0, -4, 0).isEmpty()) {
                                player.setVelocity(dir.clone().setY(0).multiply(Math.sqrt(trait.speed / 3)));
                            }
                        }
                        /*if (trait.cTick == 1) {
                            StrafeData strafe = strafing.computeIfAbsent(trait, k -> new StrafeData());
                            final var left = chasing.getLocation()
                                    .subtract(npc.getEntity().getLocation())
                                    .toVector()
                                    .rotateAroundY(Math.toRadians(90))
                                    .setY(0).normalize();
                            left.checkFinite();
                            Location strafeLoc = chasing.getLocation()
                                    .add(left.multiply(strafe.direction * 3));
                            strafeLoc.setY(npc.getEntity().getY());
                            strafe.target = strafeLoc;
                            trait.pathingTo = strafeLoc;
                            npc.getNavigator().setTarget(strafeLoc);
                        }*/
                    }
                }
            }
        }.

                runTaskTimer(CombatMain.getInstance(), 0, 1);
    }

    public boolean isPlanningAttack(LivingEntity chasing, boolean mace) {
        EntityEquipment equipment = chasing.getEquipment();
        if (equipment == null) return false;
        Material mainHand = equipment.getItemInOffHand().getType();
        if (!mace && mainHand == Material.CROSSBOW) return true;
        Material offHand = equipment.getItemInMainHand().getType();
        if (!mace && offHand == Material.CROSSBOW) return true;
        return (chasing instanceof Player player
                && ((CraftPlayer) player).getHandle().currentImpulseImpactPos != null)
                && (offHand == Material.MACE
                || mainHand == Material.MACE);
    }

    @Override
    public boolean tryAttack(SentinelTrait st, LivingEntity ent) {
        final NPC npc = st.getNPC();
        if (!npc.data().get(META_KEY, false)) return false;
        final Entity attacker = npc.getEntity();
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
        final double leftDot = left.dot(normalizedAverage);

        CombatPlayerData combatData = CombatMain.getInstance().getData(player);
        combatData.getOptions().compensateCameraMovement(false);

        st.faceLocation(ent.getEyeLocation());
        st.attackHelper.rechase();
        double reach = Math.max(PlayerUtil.getReach(ent), st.reach);
        if (player.getEyeLocation()
                .distanceSquared(ent.getEyeLocation()) > reach * reach) {
            if (!st.rangedChase) {
                npc.getNavigator().cancelNavigation();
            }
            // allow long range
            return false;
        }
        if (combatData.getNoAttack(true) > 0) {
            return false;
        }

        final double sign = Math.copySign(1, leftDot);

        if (ThreadLocalRandom.current().nextDouble() > direction) {
            Vector2f entering;
            if (Math.abs(leftDot) > 0.75) {
                // do sweep
                entering = new Vector2f(0, (float) (90 * -sign));
            } else {
                // do bash
                entering = new Vector2f(90, 0);
            }
            for (int i = 0; i < CombatPlayerData.CACHE_COUNT - 1; i++) {
                combatData.enterCamera(new Vector2f());
            }
            combatData.enterCamera(new Vector2f(entering));
        }
        combatData.overridePosAndCamera(player.getLocation());
        if (CombatMain.getInstance()
                .runAction(player, IAction.ActionType.ATTACK, false)) {
            st.timeSinceAttack = 0;
            double len = average.lengthSquared();
            final double itemCd = PlayerUtil.getCd(player, EquipmentSlot.HAND);
            double scaleFactor = Math.max(0.85, 2 + Math.log10(Math.atan(len)));
            int deduction = len == 0 ? 0 : (int) Math.round(scaleFactor * itemCd);
            combatData.setNoAttack(true, Math.round(combatData.getNoAttack(true) + deduction));
        }
        return false;
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

    public static class StrafeData {
        public int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        public Location target = null;
    }

}
