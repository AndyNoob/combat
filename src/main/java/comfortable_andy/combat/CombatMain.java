package comfortable_andy.combat;

import comfortable_andy.combat.util.PlayerUtil;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
        for (Map.Entry<Damageable, Vector> entry : PlayerUtil.sweep(player.getEyeLocation(), 2, 1, player.getYaw(), 30, 5).entrySet()) {
            if (entry.getKey() == player) continue;
            entry.getKey().teleport(entry.getKey().getLocation().add(entry.getValue()));
        }
    }

    public void debug(Object... stuff) {
        if (!debugLog) return;
        getLogger().info(String.join(" ", Arrays.stream(stuff).map(Objects::toString).toArray(String[]::new)));
    }

}
