package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import lombok.ToString;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector2f;

import java.util.List;

@ToString
public abstract class SweepingAction implements IAction {
    float triggerAmount = 8;
    protected float speedMultiplier = 0.25f;
    protected float damageMultiplierPerStep = 1.2f;
    protected int steps = 5;
    protected List<Material> blacklist = List.of(Material.BOW, Material.CROSSBOW);

    abstract boolean triggered(Vector2f v);

    protected abstract void run(Player player, CombatPlayerData data, Vector2f delta, boolean isAttack);

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        boolean isAttack = type == ActionType.ATTACK;
        ItemStack heldItem = player.getInventory().getItem(isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
        if (blacklist.contains(heldItem.getType()))
            return ActionResult.NONE;
        final Vector2f delta = data.averageCameraAngleDelta();
        if (!triggered(delta)) return ActionResult.NONE;
        run(player, data, delta, isAttack);
        return ActionResult.ACTIVATED;
    }
}
