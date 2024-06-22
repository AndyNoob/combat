package comfortable_andy.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public class CombatRunnable extends BukkitRunnable {

    @Override
    public void run() {
        CombatMain.getInstance().purgeData();
        for (Player player : Bukkit.getOnlinePlayers()) {
            CombatMain.getInstance().getData(player).tick();
        }
    }

}
