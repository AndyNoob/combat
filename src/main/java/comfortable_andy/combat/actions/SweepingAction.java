package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import lombok.ToString;
import net.minecraft.util.Mth;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;

import java.util.List;

import static comfortable_andy.combat.util.PlayerUtil.getCd;
import static org.bukkit.util.NumberConversions.ceil;

@ToString
public abstract class SweepingAction implements IAction {
    double triggerAmount = 8;
    protected double speedMultiplier = 0.25f;
    protected double damageMultiplierPerStep = 1.2f;
    protected int steps = 5;
    protected double minStrength = 0.75;
    protected int minCooldownTicks = 12;
    protected List<Material> blacklist = List.of(Material.BOW, Material.CROSSBOW);

    abstract boolean triggered(Vector2f v);

    protected abstract void run(Player player, CombatPlayerData data, Vector2f delta, boolean isAttack);

    @Override
    public @NotNull ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        if (type == ActionType.DOUBLE_SNEAK) return ActionResult.NONE;
        if (data.getExtraCooldown("sweep") > 0) return ActionResult.NONE;
        boolean isAttack = type == ActionType.ATTACK;
        EquipmentSlot slot = isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        ItemStack heldItem = player.getInventory().getItem(slot);
        final double cd = getCd(player, slot);
        final int ticks = ceil(cd);
        final double strengthScale = Mth.clamp((ticks - data.getCooldown(isAttack) + 0.5) / ticks, 0, 1);
        if (strengthScale < minStrength) return ActionResult.NONE;
        if (blacklist.contains(heldItem.getType()))
            return ActionResult.NONE;
        final Vector2f delta = data.averageCameraAngleDelta();
        if (!triggered(delta)) return ActionResult.NONE;
        data.setExtraCooldown("sweep", minCooldownTicks);
        run(player, data, delta, isAttack);
        return ActionResult.ACTIVATED;
    }
}
