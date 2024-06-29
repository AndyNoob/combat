package comfortable_andy.combat;

import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.actions.SweepAction;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.Getter;
import lombok.Setter;
import me.comfortable_andy.mapable.Mapable;
import me.comfortable_andy.mapable.MapableBuilder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatMain extends JavaPlugin implements Listener {

    private static CombatMain INSTANCE;

    public final Map<Player, CombatPlayerData> playerData = new ConcurrentHashMap<>();
    @Setter
    @Getter
    private boolean debugLog = false;
    private final List<IAction> actions = new ArrayList<>();
    private final Mapable mapable = new MapableBuilder().createMapable();

    @Override
    public void onEnable() {
        INSTANCE = this;
        loadActions();
        new CombatRunnable().runTaskTimer(this, 0, 1);
        getServer().getPluginManager().registerEvents(this, this);
    }

    public static CombatMain getInstance() {
        return INSTANCE;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        for (IAction action : actions) {
            if (action.tryActivate(getData(player), IAction.ActionType.ATTACK) == IAction.ActionResult.ACTIVATED) break;
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

    public void loadActions() {
        saveResource("actions/sweep.yml", false);
        try {
            actions.add(mapable.fromMap(configToMap(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "actions/sweep.yml"))), SweepAction.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        getLogger().info("Loaded " + actions);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> configToMap(YamlConfiguration config) {
        final Map<String, Object> map = new HashMap<>();
        final Map<List<String>, Set<String>> toRead = new ConcurrentHashMap<>();
        toRead.put(new ArrayList<>(), config.getKeys(false));
        while (!toRead.isEmpty()) {
            for (Iterator<Map.Entry<List<String>, Set<String>>> iterator = toRead.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<List<String>, Set<String>> e = iterator.next();

                final List<String> path = e.getKey();
                final Set<String> keys = e.getValue();

                Map<String, Object> addingTo = path.isEmpty() ? map : (Map<String, Object>) map
                        .computeIfAbsent(path.get(0), k -> new HashMap<>());

                for (int i = 1; i < path.size(); i++) {
                    final String s = path.get(i);
                    addingTo = (Map<String, Object>) addingTo
                            .computeIfAbsent(s, k -> new HashMap<>());
                }

                for (String key : keys) {
                    final List<String> newPath = new ArrayList<>(path);
                    newPath.add(key);
                    final String concatNewPath = String.join(".", newPath);
                    if (config.isConfigurationSection(concatNewPath)) {
                        toRead.computeIfAbsent(newPath, k -> new HashSet<>()).addAll(
                                Objects.requireNonNull(config.getConfigurationSection(concatNewPath)).getKeys(false)
                        );
                    } else {
                        addingTo.put(key, config.get(concatNewPath));
                    }
                }
                iterator.remove();
            }
        }
        return map;
    }

}
