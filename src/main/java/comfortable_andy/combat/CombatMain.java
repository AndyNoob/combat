package comfortable_andy.combat;

import comfortable_andy.combat.util.PlayerUtil;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Quaterniond;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static comfortable_andy.combat.util.VecUtil.fromDir;

public final class CombatMain extends JavaPlugin implements Listener {

    private static CombatMain INSTANCE;

    public final Map<Player, CombatPlayerData> playerData = new ConcurrentHashMap<>();
    @Setter
    @Getter
    private boolean debugLog = false;

    @Override
    public void onEnable() {
        INSTANCE = this;
        new CombatRunnable().runTaskTimer(this, 0, 1);
        getServer().getPluginManager().registerEvents(this, this);
    }

    public static CombatMain getInstance() {
        return INSTANCE;
    }

    @EventHandler
    public void onPlayerInteract(PrePlayerAttackEntityEvent event) {
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final Location origin = player.getEyeLocation();
        final CombatPlayerData data = getData(player);
        final Quaterniond delta = fromDir(data.averageCameraAngleDelta());
        final Quaterniond start = fromDir(origin);
        start.mul(delta.invert(new Quaterniond()));
        for (Map.Entry<Damageable, Vector> entry :
                PlayerUtil.sweep(
                        origin,
                        2,
                        1,
                        start,
                        fromDir(data.averageCameraAngleDelta()),
                        5
                ).entrySet()) {
            if (entry.getKey() == player) continue;
            entry.getKey().teleport(entry.getKey().getLocation().add(entry.getValue()));
        }
    }

    public void debug(Object... stuff) {
        if (!debugLog) return;
        getLogger().info(String.join(" ", Arrays.stream(stuff).map(Objects::toString).toArray(String[]::new)));
    }

    public CombatPlayerData getData(Player player) {
        return playerData.computeIfAbsent(player, CombatPlayerData::new);
    }

    public void purgeData() {
        playerData.entrySet().removeIf(d -> !d.getValue().getPlayer().isOnline());
    }

}
