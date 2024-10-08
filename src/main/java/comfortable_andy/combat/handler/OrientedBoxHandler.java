package comfortable_andy.combat.handler;

import comfortable_andy.combat.util.OrientedBox;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OrientedBoxHandler extends BukkitRunnable {

    private final Map<OrientedBox, BoxInfo<?>> boxes = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        final Iterator<Map.Entry<OrientedBox, BoxInfo<?>>> iterator = boxes.entrySet().iterator();
        final Set<OrientedBox> collidingBoxes = boxes.entrySet()
                .stream()
                .filter(e -> e.getValue().collidesWithOthers)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        while (iterator.hasNext()) {
            final Map.Entry<OrientedBox, BoxInfo<?>> entry = iterator.next();
            BoxInfo<?> info = entry.getValue();
            if (!info.tickCheck.apply(info.ticks)) continue;
            if (info.ticks-- <= 0) {
                collidingBoxes.remove(entry.getKey());
                iterator.remove();
                continue;
            }
            final OrientedBox box = entry.getKey();
            final OrientedBox collidedBox = collidingBoxes.stream().filter(b -> b != box && !b.collides(box, (c, d) -> 0).isEmpty()).findFirst().orElse(null);
            if (info.collidesWithOthers && collidedBox != null) {
                if (info.collidedWithOther.apply(collidedBox, boxes.get(collidedBox)))
                    continue;
            } else {
                for (Map.Entry<?, OrientedBox> checkEntry : info.boxSupplier.get().entrySet()) {
                    final List<Vector> mtvs = box.collides(checkEntry.getValue(), info.mtvComparator);
                    if (mtvs.isEmpty()) continue;
                    ((BiConsumer<Object, Vector>) info.collideCallback)
                            .accept(checkEntry.getKey(), mtvs.get(0));
                }
            }
            info.postTickCallback.run();
        }
    }

    public void addBox(OrientedBox box, BoxInfo<?> info) {
        boxes.put(box, info);
    }

    @Builder
    @Data
    @AllArgsConstructor
    public static final class BoxInfo<E> {

        private final Object owner;
        private final Supplier<Map<E, OrientedBox>> boxSupplier;
        private final BiConsumer<E, Vector> collideCallback;
        private final Function<Integer, Boolean> tickCheck;
        private final Runnable postTickCallback;
        private final Comparator<Vector> mtvComparator;
        private final boolean collidesWithOthers;
        private final BiFunction<OrientedBox, BoxInfo<?>, Boolean> collidedWithOther;
        private int ticks;

    }

}
