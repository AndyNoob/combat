package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatMain;
import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import comfortable_andy.combat.util.VecUtil;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.citizensnpcs.api.CitizensAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

public class ChargeAction implements IAction {
    @Override
    public @NotNull ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        if (!Bukkit.getServer().getPluginManager()
                .isPluginEnabled("Citizens")) return ActionResult.NONE;
        if (!CitizensAPI.getNPCRegistry().isNPC(player)) return ActionResult.NONE;
        if (type != ActionType.SNEAK) return ActionResult.NONE;
        new BukkitRunnable() {
            int counter = 0;
            final int maxChargeAmount = 20 * 2;
            final int minChargeAmount = 10;
            boolean chargeComplete = false;
            @SuppressWarnings("UnstableApiUsage")
            @Override
            public void run() {
                if (!player.isSneaking() && counter < minChargeAmount) {
                    cancel();
                    return;
                }
                if (player.isDead()) {
                    cancel();
                    return;
                }
                data.setNoAttack(true, 2);
                data.setNoAttack(false, 2);
                double progress = 1d * counter / maxChargeAmount;
                if (player.isSneaking() && !chargeComplete) {
                    counter++;
                    player.sendActionBar(Component
                            .text(Math.round(progress * 100) + "%")
                    );
                    VecUtil.summonCircle(player.getLocation(), 1);
                    chargeComplete = counter >= maxChargeAmount;
                    return;
                } else {
                    chargeComplete = true;
                }
                double distance = PlayerUtil.getReach(player) * 3 * progress;
                var forward = new Vector(0, 0, 1)
                        .rotateAroundY(-Math.toRadians(player.getYaw()))
                        .multiply(distance);
                data.setNoAttack(true, 5);
                data.setNoAttack(false, 5);
                PlayerUtil.sweep(
                        player,
                        player::getEyeLocation,
                        (float) (10 * progress),
                        3,
                        fromDir(player.getYaw(), 0),
                        new Vector3d(),
                        4,
                        1,
                        (e, v) -> {
                            if (PlayerUtil.canAttack(player, e) && e instanceof LivingEntity living) {
                                ((CraftLivingEntity) e).getHandle().knockback(
                                        2,
                                        -v.getX(),
                                        -v.getZ(),
                                        ((CraftPlayer) player).getHandle(),
                                        EntityKnockbackEvent.Cause.ENTITY_ATTACK
                                );
                                double damage = PlayerUtil.getDmg(player, EquipmentSlot.HAND) * 5 * progress;
                                living.damage(damage, DamageSource
                                        .builder(DamageType.PLAYER_ATTACK)
                                        .withDamageLocation(player.getLocation())
                                        .withDirectEntity(player)
                                        .build());
                            }
                        },
                        true
                );
                player.getWorld().playSound(
                        player,
                        Sound.ENTITY_WIND_CHARGE_WIND_BURST,
                        10,
                        1
                );
                player.setVelocity(forward);
                data.setExtraCooldown("charge", 20 * 3);
                cancel();
            }
        }.runTaskTimer(CombatMain.getInstance(), 0, 1);
        return ActionResult.ACTIVATED;
    }
}
