package comfortable_andy.combat.actions;

import comfortable_andy.combat.CombatPlayerData;
import comfortable_andy.combat.util.AttackInfo;
import comfortable_andy.combat.util.OrientedBox;
import comfortable_andy.combat.util.PlayerUtil;
import net.minecraft.util.Mth;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;
import java.util.concurrent.atomic.AtomicBoolean;

import static comfortable_andy.combat.util.VecUtil.*;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class SwingAction implements IAction {

    private boolean enabled = true;

    @Override
    public @NotNull ActionResult tryActivate(Player player, CombatPlayerData data, ActionType type) {
        if (!enabled || type == ActionType.DOUBLE_SNEAK) return ActionResult.NONE;
        boolean isAttack = type == ActionType.ATTACK;
        final AttackInfo info = PlayerUtil.computeAttackInfo(
                data,
                isAttack,
                1,
                data.getCooldown(isAttack)
        );
        Location pos = data.latestPosBukkit();
        final OrientedBox box = PlayerUtil.makeAttackBox(
                PlayerUtil.getReach(player),
                (float) info.strengthScale(),
                fromDir(pos),
                pos
        );
        PlayerUtil.doBoxStuff(
                box,
                data::latestPosBukkit,
                (e, v) -> player.getInventory().setItem(info.slot(), PlayerUtil.handleVanillaLikeAttack(
                        player,
                        e,
                        info.item(),
                        v,
                        info.unModdedKnockback(),
                        info.unEnchantedDamage(),
                        info.strengthScale(),
                        new AtomicBoolean(false)
                )),
                b -> {
                    final Vector2f cameraMoveDir = data.averageCameraAngleDelta();
                    cameraMoveDir.setComponent(0, Mth.clamp(cameraMoveDir.x, -10, 10));
                    cameraMoveDir.setComponent(1, Mth.clamp(cameraMoveDir.y, -10, 10));
//                    float zRotRad = cameraMoveDir.lengthSquared() != 0 ? cameraMoveDir.angle(new Vector2f(1, 0)) : 0;
                    b.rotateBy(new Quaterniond().rotationXYZ(
                            Math.toRadians(cameraMoveDir.x),
                            Math.toRadians(cameraMoveDir.y),
                            0
                    )/*rotateAxis(zRotRad, b.getAxis().getColumn())*/.invert());
                },
                PlayerUtil.getReach(player),
                info.cdTicks(),
                1,
                info.strengthScale() > 0.9
        );
        return ActionResult.ACTIVATED;
    }
}
