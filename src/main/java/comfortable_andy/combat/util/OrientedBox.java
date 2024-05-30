package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatMain;
import lombok.ToString;
import org.apache.commons.lang.math.DoubleRange;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import static comfortable_andy.combat.util.VecUtil.fromBukkit;
import static comfortable_andy.combat.util.VecUtil.fromJoml;

@ToString
public class OrientedBox {

    private Vector center;
    private final Vector[] vertices = new Vector[8];
    private final Matrix3d axis = new Matrix3d();

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

    public OrientedBox move(Vector move) {
        this.center.add(move);
        for (Vector vertex : this.vertices) {
            vertex.add(move);
        }
        return this;
    }

    public OrientedBox rotateBy(Vector axis, double angRadians) {
        for (int i = 0; i < this.vertices.length; i++) {
            final Vector v = this.vertices[i];
            this.vertices[i] = v.subtract(this.center).rotateAroundNonUnitAxis(axis, angRadians).add(this.center);
        }
        this.axis.rotate(angRadians, fromBukkit(axis));
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

    @Nullable
    public Vector collides(OrientedBox other) {
        Vector mtv = null;
        for (int i = 0; i < 6; i++) {
            final Vector axis = fromJoml((i <= 2 ? this : other).axis.getColumn(i % 3, new Vector3d()));
            final DoubleRange thisRange = this.project(axis);
            final DoubleRange otherRange = other.project(axis);

            if (!thisRange.overlapsRange(otherRange)) return null;

            final List<Double> vals = Arrays.asList(
                    thisRange.getMinimumDouble(),
                    thisRange.getMaximumDouble(),
                    otherRange.getMinimumDouble(),
                    otherRange.getMaximumDouble()
            );

            vals.sort(Double::compare);

            final Vector mtvCandidate;
            double multi = vals.get(2) - vals.get(1);

            CombatMain.getInstance().getLogger().info("axis " + axis + " " + axis.isNormalized());

            if (thisRange.containsRange(otherRange) || otherRange.containsRange(thisRange)) {
                CombatMain.getInstance().getLogger().info("contains");
                multi += Math.copySign(1, multi) * Math.min(Math.abs(vals.get(3) - vals.get(2)), Math.abs(vals.get(1) - vals.get(0)));
            }

            if (vals.get(0) == otherRange.getMinimumDouble()) {
                CombatMain.getInstance().getLogger().info("negating");
                multi *= -1;
            }

            mtvCandidate = axis.clone().multiply(multi);

            CombatMain.getInstance().getLogger().info("ranges -- this: " + thisRange + ", other: " + otherRange);
            CombatMain.getInstance().getLogger().info("sorted -- " + vals);
            CombatMain.getInstance().getLogger().info("multi -- " + multi);
            CombatMain.getInstance().getLogger().info("candidate -- " + mtvCandidate.toVector3f().toString(new DecimalFormat("#.##")));

            if (mtv == null || mtv.lengthSquared() > mtvCandidate.lengthSquared()) {
                mtv = mtvCandidate;
                System.out.println("     candidate selected");
            }
        }
        return mtv;
    }

    public void display(World world) {
        for (Vector vertex : this.vertices) {
            world.spawnParticle(Particle.VILLAGER_HAPPY, vertex.toLocation(world), 1, 0, 0, 0);
        }
    }

}
