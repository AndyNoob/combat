package comfortable_andy.combat.compat;

import comfortable_andy.combat.CombatMain;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.mcmonkey.sentinel.SentinelIntegration;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CombatSentinelIntegration extends SentinelIntegration {

    private Map<SentinelTrait, TrackingData> ticking = new ConcurrentHashMap<>();

    public CombatSentinelIntegration() {
        new BukkitRunnable() {
            @Override
            public void run() {

            }
        }.runTaskTimer(CombatMain.getInstance(), 0, 1);
    }

    @Override
    public boolean tryAttack(SentinelTrait st, LivingEntity ent) {
        final Entity attacker = st.getNPC().getEntity();
        if (!(attacker instanceof Player player)) return false;
        ticking.put(st, new TrackingData());
        return true;
    }

    public static class TrackingData {

    }

}
