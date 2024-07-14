package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import lombok.Getter;
import org.apache.commons.lang.math.DoubleRange;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.text.DecimalFormat;
import java.util.*;

import static comfortable_andy.combat.util.VecUtil.*;

public class OrientedBox implements Cloneable {

    @Getter
    private Vector center;
    private Vector[] vertices = new Vector[8];
    @Getter
    private final Matrix3d axis = new Matrix3d();

    public OrientedBox(OrientedBox b) {
        this.center = b.center.clone();
        this.vertices = Arrays.stream(b.vertices).map(Vector::clone).toArray(Vector[]::new);
        this.axis.set(b.axis);
    }

    public OrientedBox(BoundingBox box) {
        this.center = box.getCenter();
        this.vertices[0] = box.getMin();
        this.vertices[7] = box.getMax();
        final Vector min = box.getMin();
        final double widthX = box.getWidthX();
        final double widthZ = box.getWidthZ();
        final double height = box.getHeight();
        this.vertices[1] = min.clone().add(new Vector(widthX, 0, 0));
        this.vertices[2] = min.clone().add(new Vector(0, 0, widthZ));
        this.vertices[3] = min.clone().add(new Vector(widthX, 0, widthZ));
        this.vertices[4] = min.clone().add(new Vector(0, height, 0));
        this.vertices[5] = min.clone().add(new Vector(widthX, height, 0));
        this.vertices[6] = min.clone().add(new Vector(0, height, widthZ));
    }

    public OrientedBox setCenter(Vector center) {
        this.center = center;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public OrientedBox moveBy(Vector move) {
        this.center.add(move);
        for (Vector vertex : this.vertices) {
            vertex.add(move);
        }
        return this;
    }

    public OrientedBox rotateBy(Quaterniondc rot) {
        for (int i = 0; i < this.vertices.length; i++) {
            final Vector v = this.vertices[i];
            this.vertices[i] = fromJoml(fromBukkit(v.subtract(this.center)).rotate(rot)).add(this.center);
        }
        this.axis.rotate(rot);
        return this;
    }

    private DoubleRange project(Vector axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Vector vertex : vertices) {
            final double d = axis.dot(vertex);
            if (d > max) max = d;
            if (d < min) min = d;
        }
        return new DoubleRange(min, max);
    }

    @NotNull
    public List<Vector> collides(OrientedBox other, Comparator<Vector> comparator) {
        final List<Vector> options = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final Vector axis = fromJoml((i <= 2 ? this : other).axis.getColumn(i % 3, new Vector3d()));
            final DoubleRange thisRange = this.project(axis);
            final DoubleRange otherRange = other.project(axis);

            if (!thisRange.overlapsRange(otherRange)) return new ArrayList<>();

            final List<Double> vals = Arrays.asList(
                    thisRange.getMinimumDouble(),
                    thisRange.getMaximumDouble(),
                    otherRange.getMinimumDouble(),
                    otherRange.getMaximumDouble()
            );

            vals.sort(Double::compare);

            final Vector mtvCandidate;
            double multi = vals.get(2) - vals.get(1);

            CombatMain.getInstance().debug("axis " + axis + " " + axis.isNormalized());

            if (thisRange.containsRange(otherRange) || otherRange.containsRange(thisRange)) {
                CombatMain.getInstance().debug("contains");
                multi += Math.copySign(1, multi) * Math.min(Math.abs(vals.get(3) - vals.get(2)), Math.abs(vals.get(1) - vals.get(0)));
            }

            if (vals.get(0) == otherRange.getMinimumDouble()) {
                CombatMain.getInstance().debug("negating");
                multi *= -1;
            }

            mtvCandidate = axis.clone().multiply(multi);

            CombatMain.getInstance().debug("ranges -- this: " + thisRange + ", other: " + otherRange);
            CombatMain.getInstance().debug("sorted -- " + vals);
            CombatMain.getInstance().debug("multi -- " + multi);
            CombatMain.getInstance().debug("candidate -- " + mtvCandidate.toVector3f().toString(new DecimalFormat("#.##")));

            options.add(mtvCandidate);
        }
        options.sort(comparator);
        return options;
    }

    public void display(World world) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                for (Vector vertex : vertices) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, vertex.toLocation(world), 1, 0, 0, 0);
                }
                final Iterator<Color> colors = Arrays.asList(Color.RED, Color.GREEN, Color.BLUE).iterator();
                for (int i = 0; i < 3; i++) {
                    final Vector3d vector = axis.getColumn(i, new Vector3d());
                    final Color col = colors.next();
                    for (int j = 0; j < 5; j++) {
                        world.spawnParticle(
                                Particle.DUST,
                                fromJoml(center.toVector3d().lerp(center.toVector3d().add(vector), j / 5d)).toLocation(world),
                                1,
                                0,
                                0,
                                0,
                                0,
                                new Particle.DustOptions(col, 1)
                        );
                    }
                }
                if (count-- <= 0) cancel();
            }
        }.runTaskTimer(CombatMain.getInstance(), 0, 20);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public OrientedBox clone() {
        return new OrientedBox(this);
    }

    @Override
    public String toString() {
        return "OrientedBox{\n" +
                "    center=" + str(fromBukkit(center)) +
                "\n    vertices=" + Arrays.stream(vertices).map(VecUtil::fromBukkit).map(VecUtil::str).toList() +
                "\n    axis=" + axis.toString(FORMAT).replace("\n", "  |  ") +
                "\n}";
    }
}
