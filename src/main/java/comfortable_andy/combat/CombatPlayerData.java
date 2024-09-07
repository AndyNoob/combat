package comfortable_andy.combat;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.util.Ticks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Vector;

import static comfortable_andy.combat.util.VecUtil.*;

public class CombatPlayerData {

    public static final int CACHE_COUNT = 5;
    private static final Field FIRST_GOOD_X;
    private static final Field FIRST_GOOD_Y;
    private static final Field FIRST_GOOD_Z;

    static {
        try {
            FIRST_GOOD_X = ServerGamePacketListenerImpl.class.getDeclaredField("firstGoodX");
            FIRST_GOOD_Y = ServerGamePacketListenerImpl.class.getDeclaredField("firstGoodY");
            FIRST_GOOD_Z = ServerGamePacketListenerImpl.class.getDeclaredField("firstGoodZ");
            FIRST_GOOD_X.trySetAccessible();
            FIRST_GOOD_Y.trySetAccessible();
            FIRST_GOOD_Z.trySetAccessible();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    private final Player player;

    /**
     * 1 for each tick that passed
     */
    @Getter
    private final Vector<Vector2f> lastCameraAngles = new Vector<>();
    private World lastWorld = null;
    private Vector3d positionDelta = new Vector3d();
    private Pair<Long, Long> attackDelayLeft = new Pair<>(0L, 0L);
    private Pair<Long, Long> noAttackDelayLeft = new Pair<>(0L, 0L);
    private Vector2f cameraOverride = null;
    private Vector3d positionOverride = null;
    @Getter
    private final CombatOptions options;

    public CombatPlayerData(Player player) {
        this.player = player;
        this.options = CombatMain.getInstance().getCombatOptions().clone();
    }

    @SuppressWarnings("deprecation")
    public void tick() {
        final Location location = this.player.getLocation();
        if (location.getWorld() == null) return;
        this.cameraOverride = null;
        this.positionOverride = null;
        this.enterCamera(new Vector2f(location.getPitch(), location.getYaw()));
        updateDelays();
        final ServerPlayer handle = ((CraftPlayer) player).getHandle();
        final var connection = handle.connection;
        try {
            if (lastWorld == location.getWorld()) {
                final Vector3d firstGood = new Vector3d(
                        (Double) FIRST_GOOD_X.get(connection),
                        (Double) FIRST_GOOD_Y.get(connection),
                        (Double) FIRST_GOOD_Z.get(connection)
                );
                final Vector3f pos = handle.position()
                        .toVector3f();
                positionDelta = new Vector3d(pos.x, pos.y, pos.z)
                        .sub(new Vector3d(firstGood.x, firstGood.y, firstGood.z));
            } else positionDelta = new Vector3d();
            lastWorld = location.getWorld();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        Vector2f delta = averageCameraAngleDelta();
        if (CombatMain.getInstance().isShowActionBarDebug()) {
            this.player.sendActionBar("cam delta: " + delta.toString(FORMAT) +
                    " pos delta: " + fromBukkit(posDelta()).toString(FORMAT) +
                    " cd cd: " + attackDelayLeft.toString() +
                    " no cd: " + noAttackDelayLeft.toString() +
                    " blacklist: " + CombatMain.getInstance().interactBlacklist.contains(player)
            );
        }
        if (options.cameraDirectionTitle()) {
            String arrow = "";
            if (CombatMain.getInstance().getSweep().triggered(delta))
                arrow = delta.y > 0 ? "→" : "←";
            else if (CombatMain.getInstance().getBash().triggered(delta))
                arrow = "↓";
            if (arrow.isEmpty()) return;
            player.sendTitlePart(TitlePart.TITLE, Component.empty());
            player.sendTitlePart(TitlePart.SUBTITLE, Component.text(arrow));
            player.sendTitlePart(TitlePart.TIMES, Title.Times.times(
                    Ticks.duration(0),
                    Ticks.duration(5),
                    Ticks.duration(0)
            ));
        }
        this.lastWorld = location.getWorld();
    }

    public void updateDelays() {
        this.attackDelayLeft = this.attackDelayLeft.mapFirst(a -> Math.max(0, a - 1)).mapSecond(a -> Math.max(0, a - 1));
        this.noAttackDelayLeft = this.noAttackDelayLeft.mapFirst(a -> Math.max(0, a - 1)).mapSecond(a -> Math.max(0, a - 1));
    }

    /**
     * @param v x-axis is rotX and y-axis is rotY
     */
    public void enterCamera(Vector2f v) {
        this.lastCameraAngles.add(0, v);
        this.lastCameraAngles.setSize(CACHE_COUNT);
    }

    public void overrideCamera(Vector2f v) {
        this.cameraOverride = v;
    }

    public Vector2f latestCameraDir() {
        return this.cameraOverride == null ? new Vector2f(this.player.getPitch(), this.player.getYaw()) : this.cameraOverride;
    }

    public Vector2f averageCameraAngleDelta() {
        return jomlPitchYawAverage(this.lastCameraAngles);
    }

    public void resetCameraDelta() {
        this.lastCameraAngles.clear();
    }

    public org.bukkit.util.Vector posDelta() {
        return fromJoml(positionDelta);
    }

    public void overridePos(Vector3d v) {
        this.positionOverride = v;
    }

    public void overridePosAndCamera(Location v) {
        this.overridePos(new Vector3d(v.x(), v.y(), v.z()));
        this.overrideCamera(new Vector2f(v.getPitch(), v.getYaw()));
    }

    public Vector3d latestPos() {
        return this.positionOverride == null ? new Vector3d(this.player.getX(), this.player.getY(), this.player.getZ()) : this.positionOverride;
    }

    public long getCooldown(boolean main) {
        return (main ? this.attackDelayLeft.getFirst() : this.attackDelayLeft.getSecond());
    }

    public void setCooldown(boolean main, long amt) {
        this.attackDelayLeft = main ? this.attackDelayLeft.mapFirst(a -> amt) : this.attackDelayLeft.mapSecond(a -> amt);
    }

    public long getNoAttack(boolean main) {
        return (main ? this.noAttackDelayLeft.getFirst() : this.noAttackDelayLeft.getSecond());
    }

    public void setNoAttack(boolean main, long amt) {
        this.noAttackDelayLeft = main ? this.noAttackDelayLeft.mapFirst(a -> amt) : this.noAttackDelayLeft.mapSecond(a -> amt);
    }
}
