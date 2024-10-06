package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.ItemUtil;
import comfortable_andy.combat.util.PlayerUtil;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;

import static comfortable_andy.combat.util.VecUtil.fromDir;
import static comfortable_andy.combat.util.VecUtil.fromJoml;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class ShieldBashAction implements IAction {

    private boolean enabled = true;
    private double reach = 2;
    private double size = 1;
    private int ticks = 2;
    private boolean canParry = true;
    private double knockBackAmount = 0.7f;
    private double extraDamage = 2.5f;
    private int durabilityCost = 2;

    @Override
    public @NotNull ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        if (type != ActionType.DOUBLE_SNEAK) return ActionResult.NONE;
        if (!enabled) return ActionResult.NONE;
        if (!player.isBlocking()) return ActionResult.NONE;
        final var playerHandle = ((CraftPlayer) player).getHandle();
        playerHandle.disableShield(null);
        final ItemStack item = player.getInventory().getItemInMainHand().getType() == Material.SHIELD ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        final double damage = ItemUtil.getAttribute(item, EquipmentSlot.HAND, Attribute.GENERIC_ATTACK_DAMAGE);
        final AtomicBoolean launched = new AtomicBoolean();
        PlayerUtil.sweep(
                () -> fromJoml(data.latestPos().add(0, player.getEyeHeight(), 0)).toLocation(player.getWorld()).add(data.posDelta()),
                (float) reach,
                (float) size,
                fromDir(data.latestCameraDir()),
                new Vector3d(),
                2,
                ticks,
                (e, v) -> {
                    if (PlayerUtil.canAttack(player, e) && e instanceof LivingEntity living) {
                        ((CraftLivingEntity) e).getHandle().knockback(
                                knockBackAmount,
                                -v.getX(),
                                -v.getZ(),
                                playerHandle,
                                EntityKnockbackEvent.Cause.ENTITY_ATTACK
                        );
                        living.damage(damage + extraDamage);
                        if (!launched.get()) {
                            launched.set(true);
                            player.setVelocity(player.getLocation().getDirection().multiply(knockBackAmount));
                            if (!player.getGameMode().isInvulnerable())
                                item.damage(durabilityCost, player);
                        }
                    }
                },
                canParry
        );
        return ActionResult.ACTIVATED;
    }
}
