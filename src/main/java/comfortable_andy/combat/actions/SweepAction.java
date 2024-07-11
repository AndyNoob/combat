package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@ToString
public class SweepAction implements IAction {

    float triggerAmount = 8;
    private float windBackRotY = 15;
    private float attackRotY = 30;
    private float speedMultiplier = 0.25f;
    private float damageMultiplierPerStep = 1.2f;
    private int steps = 5;
    private List<Material> blacklist = List.of(Material.BOW, Material.CROSSBOW);

    boolean triggered(Vector2f delta) {
        return Math.abs(delta.y) > triggerAmount;
    }

    @Override
    public ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        boolean isAttack = type == ActionType.ATTACK;
        ItemStack heldItem = player.getInventory().getItem(isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
        if (blacklist.contains(heldItem.getType()))
            return ActionResult.NONE;
        final Vector2f delta = data.averageCameraAngleDelta();
        if (!triggered(delta)) return ActionResult.NONE;

        final Vector3d windBack = new Vector3d(0, -windBackRotY, 0);
        final Vector3d attack = new Vector3d(0, attackRotY, 0);

        // TODO separate windBack and attack

        if (delta.y > 0) {
            windBack.negate();
            attack.negate();
        }

        PlayerUtil.doSweep(
                player,
                fromDir(player.getEyeLocation()).mul(new Quaterniond().rotateY(Math.toRadians(windBack.y))),
                attack,
                steps,
                isAttack,
                speedMultiplier,
                damageMultiplierPerStep
        );
        return ActionResult.ACTIVATED;
    }

}
