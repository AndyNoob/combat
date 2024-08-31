package comfortable_andy.combat;

import com.destroystokyo.paper.MaterialTags;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import comfortable_andy.combat.actions.*;
import comfortable_andy.combat.compat.CombatSentinelIntegration;
import comfortable_andy.combat.handler.OrientedBoxHandler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import lombok.Setter;
import me.comfortable_andy.mapable.Mapable;
import me.comfortable_andy.mapable.MapableBuilder;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.mcmonkey.sentinel.SentinelPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static comfortable_andy.combat.util.PlayerUtil.getCd;
import static org.bukkit.util.NumberConversions.ceil;

public final class CombatMain extends JavaPlugin implements Listener {

    private static CombatMain INSTANCE;

    public final Map<Player, CombatPlayerData> playerData = new ConcurrentHashMap<>();
    public final OrientedBoxHandler boxHandler = new OrientedBoxHandler();
    @Setter
    @Getter
    private boolean debugLog = false;
    private final List<IAction> actions = new ArrayList<>();
    private final Mapable mapable = new MapableBuilder().createMapable();
    private boolean enabled;
    @Getter
    private boolean showActionBarDebug = false;
    @Getter
    private boolean showCameraDir = false;
    @Getter
    private SweepAction sweep;
    @Getter
    private BashAction bash;
    private ShieldBashAction shieldBash;

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        INSTANCE = this;
        reload();
        loadActions();
        loadCompat();
        new CombatRunnable().runTaskTimer(this, 0, 1);
        boxHandler.runTaskTimer(this, 0, 1);
        getServer().getPluginManager().registerEvents(this, this);

        final LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            final var reload = Commands
                    .literal("reload")
                    .requires(s -> s.getSender()
                            .hasPermission("combat.command.reload"))
                    .executes(s -> {
                        reload();
                        s.getSource().getSender().sendMessage("Done!");
                        return Command.SINGLE_SUCCESS;
                    });
            final Command<CommandSourceStack> enableExecutor = s -> {
                Boolean enable;
                try {
                    enable = s.getArgument("enable", Boolean.class);
                } catch (Exception e) {
                    enable = !enabled;
                }
                enabled = enable;
                s.getSource().getSender().sendMessage("Enabled: " + enabled);
                return Command.SINGLE_SUCCESS;
            };
            final var enableArg = Commands.argument("enable", BoolArgumentType.bool())
                    .executes(enableExecutor);
            final var enable = Commands
                    .literal("enable")
                    .requires(s -> s.getSender()
                            .hasPermission("combat.command.enable"))
                    .then(enableArg)
                    .executes(enableExecutor);
            final var show = Commands
                    .literal("show")
                    .then(Commands.literal("debug_msg").executes(s -> {
                        showActionBarDebug = !showActionBarDebug;
                        s.getSource().getSender().sendMessage("Debug Msg: " + showActionBarDebug);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("camera_dir").executes(s -> {
                        showCameraDir = !showCameraDir;
                        s.getSource().getSender().sendMessage("Show Camera Dir: " + showCameraDir);
                        return Command.SINGLE_SUCCESS;
                    }));
            commands.register(
                    Commands.literal("combat")
                            .requires(s -> s.getSender()
                                    .hasPermission("combat.command.use"))
                            .then(reload)
                            .then(enable)
                            .then(show)
                            .build(),
                    "Combat plugin command.",
                    List.of("cb")
            );
        });
    }

    private void reload() {
        saveDefaultConfig();
        reloadConfig();
        enabled = getConfig().getBoolean("enabled");
        showCameraDir = getConfig().getBoolean("camera-direction-title");
    }

    public static CombatMain getInstance() {
        return INSTANCE;
    }

    public final Set<Player> interactBlacklist = Collections.synchronizedSet(new HashSet<>());

    private void tempBlacklist(Player player) {
        interactBlacklist.add(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                interactBlacklist.remove(player);
            }
        }.runTaskLater(this, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        tempBlacklist(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        tempBlacklist(event.getPlayer());
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        if (!event.getInstaBreak())
            interactBlacklist.add(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        interactBlacklist.remove(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onStopBreaking(BlockDamageAbortEvent event) {
        interactBlacklist.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction().isLeftClick() && interactBlacklist.contains(event.getPlayer())) {
            interactBlacklist.remove(event.getPlayer());
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return; // interact event fires once for each hand
        if (event.getAction() == Action.PHYSICAL) return;
        final ItemStack itemMain = event.getPlayer().getInventory().getItemInMainHand();
        final ItemStack itemOff = event.getPlayer().getInventory().getItemInOffHand();
        final String mainHand = itemMain.getType().toString();
        final String offHand = itemOff.getType().toString();
        if (event.getAction().isLeftClick()
                && getConfig().getStringList("left-click-blacklist").contains(mainHand)) {
            return;
        }
        if (event.getAction().isRightClick()) {
            if (getConfig().getBoolean("block-food-right-click", true)
                    && (itemMain.getType().isEdible() || itemOff.getType().isEdible())) return;
            if (getConfig().getBoolean("block-block-right-click", true)
                    && (itemMain.getType().isBlock() || itemOff.getType().isBlock())) return;
            if (getConfig().getBoolean("block-armor-right-click", true)
                    && (MaterialTags.ARMOR.isTagged(itemMain) || MaterialTags.ARMOR.isTagged(itemOff))) return;
            if (getConfig().getBoolean("block-bucket-right-click", true)
                    && (MaterialTags.ARMOR.isTagged(itemMain) || MaterialTags.BUCKETS.isTagged(itemOff))) return;
            if (getConfig().getStringList("right-click-blacklist").contains(mainHand)
                    || getConfig().getStringList("right-click-blacklist").contains(offHand)) {
                return;
            }
        }
        final boolean cancel = runAction(event.getPlayer(), event.getAction().isLeftClick() ? IAction.ActionType.ATTACK : IAction.ActionType.INTERACT, event.getClickedBlock() != null);
        if (cancel && event.getAction() != Action.LEFT_CLICK_BLOCK) event.setCancelled(true);
    }

    public boolean runAction(Player player, IAction.ActionType actionType, boolean clickedBlock) {
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        final CombatPlayerData data = getData(player);
        final boolean isConventional = actionType != IAction.ActionType.DOUBLE_SNEAK;
        final boolean isAttack = actionType == IAction.ActionType.ATTACK;
        if (isConventional) {
            if (data.getNoAttack(isAttack) > 0)
                return false;
            if (!isAttack) {
                if (player.getInventory().getItemInOffHand().isEmpty())
                    return false;
                player.swingOffHand();
                if (!clickedBlock) interactBlacklist.add(player);
            }
        }
        for (IAction action : actions) {
            if (action.tryActivate(player, data, actionType) == IAction.ActionResult.ACTIVATED) {
                final EquipmentSlot slot = isAttack ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
                final int cd = ceil(getCd(player, slot));
                data.setCooldown(isAttack, cd);
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerAttack(PrePlayerAttackEntityEvent e) {
        if (!enabled) return;
        Player player = e.getPlayer();
        Location attackedLoc = e.getAttacked().getLocation();
        Vector direction = attackedLoc.clone().subtract(player.getLocation()).toVector().normalize();
        attackedLoc.setDirection(direction);
        getData(player).overridePosAndCamera(attackedLoc.subtract(direction.clone().multiply(3)));
        IAction.ActionType type = IAction.ActionType.ATTACK;
        if (player.isRiptiding()) {
            boolean main = canRiptide(player.getInventory().getItemInMainHand());
            boolean off = canRiptide(player.getInventory().getItemInOffHand());
            if (off || main) return;
        }
        e.setCancelled(true);
        runAction(player, type, false);
    }

    private final Map<Player, Long> lastUnSneak = new HashMap<>();

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) {
            if (!lastUnSneak.containsKey(player))
                lastUnSneak.put(player, System.currentTimeMillis());
        } else {
            if (!lastUnSneak.containsKey(player)) return;
            if (System.currentTimeMillis() - lastUnSneak.remove(player) > getConfig().getLong("double-sneak-threshold-ms", 500)) return;
            runAction(player, IAction.ActionType.DOUBLE_SNEAK, false);
        }
    }

    private boolean canRiptide(ItemStack item) {
        return item.getType() == Material.TRIDENT && item.getEnchantmentLevel(Enchantment.RIPTIDE) > 0;
    }

    public void debug(Object... stuff) {
        if (!debugLog) return;
        getLogger().info(String.join(" ", Arrays.stream(stuff).map(Objects::toString).toArray(String[]::new)));
    }

    public CombatPlayerData getData(Player player) {
        return playerData.computeIfAbsent(player, CombatPlayerData::new);
    }

    public void purgeData() {
        playerData.entrySet().removeIf(d -> {
            if (getServer().getPluginManager().isPluginEnabled("Sentinel")) {
                if (CitizensAPI.getNPCRegistry().isNPC(d.getKey())) return false;
            }
            return !d.getValue().getPlayer().isOnline();
        });
    }

    public void loadActions() {
        actions.clear();
        final Path dataPath = getDataFolder().toPath();
        final File sweepFile = new File(getDataFolder(), "actions/sweep.yml");
        final File bashFile = new File(getDataFolder(), "actions/bash.yml");
        final File shieldBashFile = new File(getDataFolder(), "actions/shield_bash.yml");
        saveResource(dataPath.relativize(sweepFile.toPath()).toString(), false);
        saveResource(dataPath.relativize(bashFile.toPath()).toString(), false);
        saveResource(dataPath.relativize(shieldBashFile.toPath()).toString(), false);
        sweep = loadAction(sweepFile, SweepAction.class);
        bash = loadAction(bashFile, BashAction.class);
        shieldBash = loadAction(shieldBashFile, ShieldBashAction.class);
        actions.addAll(Arrays.asList(
                shieldBash,
                sweep,
                bash,
                new StabAction()
        ));
        getLogger().info("Loaded " + actions);
    }

    private void loadCompat() {
        if (getServer().getPluginManager().isPluginEnabled("Sentinel")) {
            SentinelPlugin.instance.registerIntegration(new CombatSentinelIntegration());
            getLogger().info("Found Sentinel, added integration.");
        }
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

    public void prependAction(IAction action) {
        this.actions.add(0, action);
    }

    public void appendAction(IAction action) {
        this.actions.add(action);
    }

    public void insertAction(int i, IAction action) {
        this.actions.add(i, action);
    }

    public List<IAction> getActions() {
        return new ArrayList<>(actions);
    }

}
