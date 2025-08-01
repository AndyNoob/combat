package comfortable_andy.combat.compat;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.util.PlayerUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import lombok.Data;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

@SuppressWarnings("UnstableApiUsage")
public class CombatSentinelIntegration extends SentinelIntegration implements Listener {

    public static final String META_KEY = "use-combat";
    public static final String ENABLE_SHIELD = "enable-shield-dash";
    public static final String ENABLE_LEAP = "enable-leap";
    public static final String ENABLE_CHARGE = "enable-charge";

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

                    TrackingData trackData = entry.getValue();
                    trackData.enterLocation(chasing.getLocation());

                    Entity npcEntity = trait.getNPC().getEntity();

                    if (npcEntity instanceof Player player)
                        CombatMain.getInstance().getData(player).updateDelays();

//                    if (trait.cTick >= SentinelPlugin.instance.tickRate) continue;

                    boolean attacking = isPlanningAttack(chasing, false);
                    if (chasing.hasActiveItem() || attacking) {
                        ItemStack activeItem = chasing.getActiveItem();
                        boolean notEating = !activeItem.hasData(DataComponentTypes.FOOD)
                                && !activeItem.hasData(DataComponentTypes.CONSUMABLE);
                        if (notEating) {
                            if (activeItem.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
                                if (npc.data().get(ENABLE_SHIELD, false)) trackData.shieldTicks++;
                            } else trackData.shieldTicks = 0;
                            if (trackData.shieldTicks < 5 && !trait.isBlocking) {
                                trait.startBlocking();
                            }
                        }
                        trait.faceLocation(chasing.getEyeLocation());
                    } else trackData.shieldTicks = 0;

                    if (!(trait.getLivingEntity() instanceof Player npcPlayer)) continue;
                    CombatPlayerData data = CombatMain.getInstance().getData(npcPlayer);
                    Location location = npcPlayer.getLocation();
                    var npcToChasingDir = chasing.getLocation()
                            .subtract(location)
                            .toVector().normalize();
//                            double maceDist = chasing.getLocation()
//                                    .distance(trait.getLivingEntity().getEyeLocation());
                    boolean planningAttack = isPlanningAttack(chasing, true);

                    if (!planningAttack && data.getExtraCooldown("leap") <= 0 && npc.data().get(ENABLE_LEAP, false) && chasing.getFallDistance() < 5) {
                        if (chasing.getY() - npcPlayer.getY() > 8) {
                            npcPlayer.setVelocity(
                                    npcPlayer.getVelocity()
                                            .add(new org.bukkit.util.Vector(
                                                    0,
                                                    (chasing.getY() - npcPlayer.getY()) / 8,
                                                    0
                                            ))
                            );
                            // only leaping up because the horizontal velocity is handled by
                            // the code that pulls npc-s towards land
                            npcPlayer.getWorld().spawnParticle(Particle.GUST, npcPlayer.getLocation(), 5, 0.1, 0.1, 0.1, 0.05);
                            data.setExtraCooldown("leap", 20 * 3);
                        }
                    }

                    double xyDistance = npcPlayer.getLocation()
                            .subtract(chasing.getLocation()).toVector()
                            .setY(0)
                            .length();
                    float chaseReach = PlayerUtil.getReach(chasing);
                    if (
                            trackData.shieldTicks > 0 ||
                            (planningAttack && data.getNoAttack(true) < 1)
                    ) {
                        boolean shouldAvoid = xyDistance > chaseReach && trackData.shieldTicks <= 0;
                        npcPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 1)
                                .rotateAroundY(-Math.toRadians(chasing.getLocation().getYaw()))
                                .multiply(shouldAvoid ? 1 : -1)
                                .multiply(trait.speed / (10 - Math.min(5, trackData.shieldTicks + chasing.getFallDistance())))
                                .setY(trackData.shieldTicks > 0 ? 0 : 0.05)
                        );
                    }

                    if (npc.data().get(ENABLE_CHARGE, false)) {
                        if (data.getExtraCooldown("charge") > 0) {
                            if (trackData.charging != 0) npcPlayer.setSneaking(false);
                            trackData.charging = 0;
                            npc.getNavigator().setPaused(false);
                        } else {
                            boolean random = ThreadLocalRandom.current().nextDouble() > 0.7 + (xyDistance / trait.chaseRange);
                            boolean tooFar = xyDistance / trait.chaseRange >= 0.75;
                            boolean activate = trackData.charging > 0 // already activating
                                    || tooFar || random;
                            if (activate && Math.abs(npcPlayer.getY() - chasing.getY()) < 2) {
                                if (trackData.charging == 0) {
                                    CombatMain.getInstance()
                                            .runAction(
                                                    npcPlayer,
                                                    IAction.ActionType.SNEAK,
                                                    false
                                            );
                                }
                                trackData.charging++;
                                if (trackData.charging > 17)
                                    chasing.sendActionBar(Component.text("Danger!"));
                                npc.getNavigator().setPaused(true);
                                npcPlayer.setSneaking(true);
                                if (trackData.charging > 17 && (xyDistance < chaseReach)) {
                                    npcPlayer.setSneaking(false);
                                }
                            } else {
                                if (trackData.charging > 0) npcPlayer.setSneaking(false);
                                trackData.charging = 0;
                                npc.getNavigator().setPaused(false);
                            }
                        }
                    }

                    // dash back to land
                    Block below = npcPlayer.getLocation().subtract(0, 0.1, 0).getBlock();
                    if (!npc.isFlyable() && below.isEmpty()
                            && below.getRelative(0, -1, 0).isEmpty()
                            && below.getRelative(0, -2, 0).isEmpty()) {
                        npcPlayer.setVelocity(
                                npcPlayer.getVelocity()
                                        .add(npcToChasingDir.clone()
                                                .setY(0)
                                                .multiply(trait.speed / 10)
                                        )
                        );
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHitBlockingNpc(ProjectileHitEvent event) {
        if (!(event.getHitEntity() != null
                && CitizensAPI.getNPCRegistry().isNPC(event.getHitEntity()))) return;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getHitEntity());
        SentinelTrait trait = npc.getTraitNullable(SentinelTrait.class);
        if (trait == null) return;
        if (trait.isBlocking && event.getHitEntity() instanceof LivingEntity le) {
            var direction = le.getLocation().getDirection();
            var toProjectile = event.getEntity()
                    .getLocation()
                    .subtract(le.getLocation())
                    .toVector();
            if (direction.dot(toProjectile) < 0) return;
            event.setCancelled(true);
            BlocksAttacks data = le.getActiveItem().getData(DataComponentTypes.BLOCKS_ATTACKS);
            if (data != null) {
                Key soundKey = data.blockSound();
                if (soundKey == null) return;
                Sound sound = Registry.SOUNDS.get(soundKey);
                if (sound == null) return;
                le.getWorld().playSound(le, sound, 1, 1);
            }
        }
    }

    @Override
    public boolean tryAttack(SentinelTrait st, LivingEntity ent) {
        final NPC npc = st.getNPC();
//        if (!npc.data().get(META_KEY, false)) return false;
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
            // allow long range
            return false;
        }
        if (combatData.getNoAttack(true) > 0) {
            st.timeSinceAttack = st.attackRate;
            return true;
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
            st.timeSinceAttack = st.attackRate;
            double len = average.lengthSquared();
            final double itemCd = PlayerUtil.getCd(player, EquipmentSlot.HAND);
            double scaleFactor = Math.max(0.85, 2 + Math.log10(Math.atan(len)));
            int deduction = len == 0 ? 0 : (int) Math.round(scaleFactor * itemCd);
            int noAttack = Math.max(st.attackRate, Math.round(combatData.getNoAttack(true) + deduction));
            combatData.setNoAttack(
                    true,
                    noAttack
            );
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
        private int shieldTicks = 0;
        private int charging = 0;

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
