package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.AttackInfo;
import comfortable_andy.combat.util.PlayerUtil;
import lombok.ToString;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import static comfortable_andy.combat.util.VecUtil.fromDir;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
@ToString(callSuper = true)
public class BashAction extends SweepingAction {

    private double windBackRotX = 15;
    private double attackRotX = 30;

    public boolean triggered(Vector2f delta) {
        return (delta.x > triggerAmount);
    }

    @Override
    public void run(Player player, CombatPlayerData data, Vector2f delta, boolean isAttack) {
        AttackInfo info = PlayerUtil.computeAttackInfo(
                data,
                isAttack,
                damageMultiplierPerStep,
                data.getCooldown(isAttack)
        );
        PlayerUtil.staticSweep(
                fromDir(data.latestCameraDir()).mul(new Quaterniond().rotateX(-Math.toRadians(windBackRotX))),
                new Vector3d(attackRotX, 0, 0),
                steps,
                info,
                speedMultiplier
        );
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, 1);
    }

}
