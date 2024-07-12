package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@ToString(callSuper = true)
public class SweepAction extends SweepingAction {

    private float windBackRotY = 15;
    private float attackRotY = 30;

    boolean triggered(Vector2f delta) {
        return Math.abs(delta.y) > triggerAmount;
    }

    @Override
    protected void run(Player player, CombatPlayerData data, Vector2f delta, boolean isAttack) {
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
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, 1);
        ((CraftPlayer) player).getHandle().sweepAttack();
    }
}
