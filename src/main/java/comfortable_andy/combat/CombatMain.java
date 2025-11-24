package comfortable_andy.combat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import lombok.Setter;
import me.comfortable_andy.mapable.Mapable;
import me.comfortable_andy.mapable.MapableBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public final class CombatMain extends JavaPlugin {

    private static CombatMain INSTANCE;

    public final Map<Player, CombatPlayerData> playerData = new ConcurrentHashMap<>();
    @Setter
    @Getter
    private boolean debugLog = false;
    private final Mapable mapable = new MapableBuilder().createMapable();
    private boolean enabled;
    @Getter
    private boolean showActionBarDebug = false;
    @Getter
    private CombatOptions combatOptions;

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        INSTANCE = this;
        reload();

        final LifecycleEventManager<@NotNull Plugin> manager = this.getLifecycleManager();
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
                s.getSource().getSender().sendMessage("Enabled Combat: " + enabled);
                return Command.SINGLE_SUCCESS;
            };
            final var enable = Commands
                    .literal("enable")
                    .requires(s -> s.getSender()
                            .hasPermission("combat.command.enable"))
                    .then(Commands.literal("combat")
                            .then(Commands
                                    .argument("enable", BoolArgumentType.bool())
                                    .executes(enableExecutor)
                            )
                            .executes(enableExecutor)
                    );
            final var show = Commands
                    .literal("show")
                    .then(Commands.literal("debug_msg").executes(s -> {
                        showActionBarDebug = !showActionBarDebug;
                        s.getSource().getSender().sendMessage("Debug Msg: " + showActionBarDebug);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("camera_dir").executes(s -> {
                        combatOptions.cameraDirectionTitle(!combatOptions.cameraDirectionTitle());
                        s.getSource().getSender().sendMessage("Show Camera Dir: " + combatOptions.cameraDirectionTitle());
                        return Command.SINGLE_SUCCESS;
                    }));
            /*final var box = Commands.literal("box")
                    .requires(s -> s.getSender().hasPermission("combat.command.box")
                            && s.getSender() instanceof Player)
                    .then(Commands
                            .literal("rotate")
                            .then(Commands
                                    .argument("rot", DoubleArgumentType.doubleArg())
                                    .then(Commands
                                            .argument("axis", Vec2Argument.vec2())
                                            .executes(s -> {
                                                if (!(s.getSource().getSender() instanceof Player player)) {
                                                    throw new SimpleCommandExceptionType(Component.literal("You must be a player.")).create();
                                                }
                                                OrientedBox testBox = getData(player).getTestBox();
                                                if (testBox == null) {
                                                    throw new SimpleCommandExceptionType(Component.literal("You must create your box first.")).create();
                                                }
                                                Vec2 rot = s.getArgument("axis", Coordinates.class)
                                                        .getRotation((net.minecraft.commands.CommandSourceStack) s.getSource());
                                                Vector3d axis = new Location(null, 0, 0, 0, rot.y, rot.x).getDirection().toVector3d();
                                                AtomicInteger counter = new AtomicInteger();
                                                Bukkit.getScheduler().runTaskTimer(this, task -> {
                                                    if (counter.getAndIncrement() >= 10) {
                                                        task.cancel();
                                                        return;
                                                    }
                                                    OrientedBox.displayLine(
                                                            player.getWorld(),
                                                            testBox.getCenter()
                                                                    .toVector3d().sub(axis.mul(6, new Vector3d())),
                                                            axis.mul(12, new Vector3d()),
                                                            Color.BLACK,
                                                            Collections.singleton(player),
                                                            25
                                                    );
                                                }, 0, 10);
                                                Quaterniond quat = new Quaterniond().rotationAxis(
                                                        Math.toRadians(s.getArgument("rot", Double.class)),
                                                        axis.x,
                                                        axis.y,
                                                        axis.z
                                                );
                                                testBox.rotateBy(quat);
                                                s.getSource().getSender().sendMessage("Done! Rotated by " + quat);
                                                return Command.SINGLE_SUCCESS;
                                            })
                                    ))
                    )
                    .then(Commands
                            .literal("make")
                            .then(Commands
                                    .argument("halfExtents", Vec3Argument.vec3())
                                    .executes(s -> {
                                        if (!(s.getSource().getSender() instanceof Player player)) {
                                            throw new SimpleCommandExceptionType(Component.literal("You must be a player.")).create();
                                        }
                                        Vector3f pos = player.getLocation().toVector().toVector3f();
                                        Vector3f halfExtents = s.getArgument("halfExtents", Coordinates.class)
                                                .getPosition((net.minecraft.commands.CommandSourceStack) s.getSource())
                                                .toVector3f();
                                        BoundingBox boundingBox = new BoundingBox(
                                                -halfExtents.x + pos.x, -halfExtents.y + pos.y, -halfExtents.z + pos.z,
                                                halfExtents.x + pos.x, halfExtents.y + pos.y, halfExtents.z + pos.z
                                        );
                                        getData(player).setTestBox(new OrientedBox(boundingBox));
                                        player.sendMessage("Done!");
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .then(Commands
                            .literal("remove")
                            .executes(s -> {
                                if (!(s.getSource().getSender() instanceof Player player)) {
                                    throw new SimpleCommandExceptionType(Component.literal("You must be a player.")).create();
                                }
                                getData(player).setTestBox(null);
                                player.sendMessage("Done!");
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .then(Commands
                            .literal("look")
                            .executes(s -> {
                                if (!(s.getSource().getSender() instanceof Player player)) {
                                    throw new SimpleCommandExceptionType(Component.literal("You must be a player.")).create();
                                }
                                OrientedBox testBox = getData(player).getTestBox();
                                if (testBox == null) {
                                    throw new SimpleCommandExceptionType(Component.literal("You must create your box first.")).create();
                                }
                                Quaterniond dest = VecUtil.fromDir(player.getLocation());
                                testBox.rotateBy(testBox.getAxis()
                                        .getUnnormalizedRotation(new Quaterniond())
                                        .invert()
                                );
                                testBox.getAxis().identity();
                                testBox.rotateBy(dest.normalize());
                                player.sendMessage("Done!");
                                return Command.SINGLE_SUCCESS;
                            })
                    );*/
            commands.register(
                    Commands.literal("combat")
                            .requires(s -> s.getSender().hasPermission("combat.command.use"))
                            .then(reload)
                            .then(enable)
                            .then(show)
//                            .then(box)
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
        combatOptions = new CombatOptions();
    }

    public void debug(String value) {
        if (!debugLog) return;
        getLogger().info(value);
    }

    public static void debug(Object value) {
        getInstance().debug(String.valueOf(value));
    }

    public static CombatMain getInstance() {
        return INSTANCE;
    }

}
