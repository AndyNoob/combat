package comfortable_andy.combat.handler;

import comfortable_andy.combat.util.OrientedBox;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class OrientedBoxHandler extends BukkitRunnable {

    private final Map<OrientedBox, TickHandler> boxes = new HashMap<>();

    @Override
    public void run() {
        final Iterator<Map.Entry<OrientedBox, TickHandler>> iterator = boxes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OrientedBox, TickHandler> entry = iterator.next();
            OrientedBox box = entry.getKey();
            TickHandler tick = entry.getValue();
            if (tick.shouldRemove(() -> boxes.keySet()
                    .stream()
                    .anyMatch(b -> System.identityHashCode(b) != System.identityHashCode(box)
                            && !b.collides(box, (aa, bb) -> 0).isEmpty()))
            ) {
                iterator.remove();
            }
        }
    }

    public void addBox(OrientedBox box, TickHandler info) {
        boxes.put(box, info);
    }

    @FunctionalInterface
    public interface TickHandler {

        boolean shouldRemove(CollisionHandler handler);

    }

    @FunctionalInterface
    public interface CollisionHandler {
        boolean checkCollided();
    }

}
