package comfortable_andy.combat;

import comfortable_andy.combat.actions.BashAction;
import comfortable_andy.combat.actions.IAction;
import comfortable_andy.combat.actions.StabAction;
import comfortable_andy.combat.actions.SweepAction;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.Getter;
import lombok.Setter;
import me.comfortable_andy.mapable.Mapable;
import me.comfortable_andy.mapable.MapableBuilder;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static comfortable_andy.combat.util.PlayerUtil.getCd;
import static org.bukkit.util.NumberConversions.ceil;

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
        saveDefaultConfig();
        loadActions();
        new CombatRunnable().runTaskTimer(this, 0, 1);
        getServer().getPluginManager().registerEvents(this, this);
    }

    public static CombatMain getInstance() {
        return INSTANCE;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // interact event fires twice
        if (event.getAction() == Action.PHYSICAL) return;
        final String mainHand = event.getPlayer().getInventory().getItemInMainHand().getType().toString();
        final String offHand = event.getPlayer().getInventory().getItemInOffHand().getType().toString();
        if (event.getAction().isLeftClick()
                && getConfig().getStringList("left-click-blacklist").contains(mainHand)) {
            return;
        }
        if (event.getAction().isRightClick()) {
            if (getConfig().getBoolean("block-food-right-click", true)
                    && event.getItem() != null && event.getItem().getType().isEdible()) {
                return;
            }
            if (getConfig().getStringList("right-click-blacklist").contains(mainHand)
                    || getConfig().getStringList("right-click-blacklist").contains(offHand)) {
                return;
            }
        }
        final boolean cancel = runAction(event.getPlayer(), event.getAction().isLeftClick() ? IAction.ActionType.ATTACK : IAction.ActionType.INTERACT);
        if (cancel) event.setCancelled(true);
    }

    private boolean runAction(Player player, IAction.ActionType actionType) {
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        final CombatPlayerData data = getData(player);
        final boolean isAttack = actionType == IAction.ActionType.ATTACK;
        if (data.inCooldown(isAttack)) return false;
        for (IAction action : actions) {
            if (action.tryActivate(player, data, actionType) == IAction.ActionResult.ACTIVATED) {
                data.addCooldown(
                        isAttack,
                        ceil(getCd(player, isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND))
                );
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerAttack(PrePlayerAttackEntityEvent e) {
        e.setCancelled(true);
        runAction(e.getPlayer(), IAction.ActionType.ATTACK);
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
        actions.clear();
        final Path dataPath = getDataFolder().toPath();
        final File sweepFile = new File(getDataFolder(), "actions/sweep.yml");
        final File bashFile = new File(getDataFolder(), "actions/bash.yml");
        saveResource(dataPath.relativize(sweepFile.toPath()).toString(), false);
        saveResource(dataPath.relativize(bashFile.toPath()).toString(), false);
        SweepAction sweep = loadAction(sweepFile, SweepAction.class);
        BashAction bash = loadAction(bashFile, BashAction.class);
        actions.addAll(Arrays.asList(
                sweep,
                bash,
                new StabAction()
        ));
        getLogger().info("Loaded " + actions);
    }

    public <V extends IAction> V loadAction(File file, Class<V> clazz) {
        try {
            return mapable.fromMap(configToMap(YamlConfiguration.loadConfiguration(file)), clazz);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
