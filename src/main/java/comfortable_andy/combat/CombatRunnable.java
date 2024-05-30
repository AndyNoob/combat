package comfortable_andy.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CombatRunnable extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            CombatMain.getInstance().playerData.computeIfAbsent(player, CombatPlayerData::new);
        }
        CombatMain.getInstance().playerData.values()
                .forEach(CombatPlayerData::tick);
    }

}
