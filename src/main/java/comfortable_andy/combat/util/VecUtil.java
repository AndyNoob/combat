package comfortable_andy.combat.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.*;

import java.lang.Math;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static net.minecraft.util.Mth.degreesDifference;

public class VecUtil {

    public static DecimalFormat FORMAT = new DecimalFormat("#.##");
    public static final Vector ZERO = new Vector(0, 0, 0);

    public static Vector3d fromBukkit(Vector v) {
        return new Vector3d(v.getX(), v.getY(), v.getZ());
    }

    public static Vector fromJoml(Vector3d v) {
        return new Vector(v.x, v.y, v.z);
    }

    public static Quaterniond fromDir(float rotY, float rotX) {
        return new Quaterniond().rotationXYZ(-Math.toRadians(rotX), Math.toRadians(rotY), 0).invert();
    }

    public static Quaterniond fromDir(Vector2f v) {
        return fromDir(v.y, v.x);
    }

    public static Quaterniond fromDir(Location location) {
        return fromDir(location.getYaw(), location.getPitch());
    }

    public static Quaterniond rotateLocal(Quaterniond q, Vector3d v, Matrix3d axes) {
        return q
                .rotateAxis(Math.toRadians(v.x), axes.getColumn(0, new Vector3d()))
                .rotateAxis(Math.toRadians(v.y), axes.getColumn(1, new Vector3d()))
                .rotateAxis(Math.toRadians(v.z), axes.getColumn(2, new Vector3d()));
    }

    public static String str(Quaterniond q) {
        return q.toString(FORMAT);
    }

    public static String str(Vector3d v) {
        return v.toString(FORMAT);
    }

    public static Vector2f jomlPitchYawAverage(List<Vector2f> list) {
        return averageList(
                Vector2f::new,
                list,
                Vector2f::add,
                (v, sub) -> v.set(degreesDifference(v.x, sub.x), degreesDifference(v.y, sub.y)),
                (v, n) -> v.div(n.floatValue())
        );
    }

    public static Vector bukkitAverage(List<Vector> list) {
        return averageList(
                Vector::new,
                list,
                Vector::add,
                Vector::subtract,
                (v, n) -> v.divide(new Vector(n.floatValue(), n.floatValue(), n.floatValue()))
        );
    }

    public static <Vec> Vec averageList(Supplier<Vec> vecSupplier, List<Vec> list, BiFunction<Vec, Vec, Vec> add, BiFunction<Vec, Vec, Vec> sub, BiFunction<Vec, Number, Vec> div) {
        final Vec accumulator = vecSupplier.get();
        final int size = list.size();
        for (int i = 1; i < size; i++) {
            final Vec cur = list.get(i);
            if (cur == null) break;
            final Vec last = list.get(i - 1);
            final Vec lastToCur = vecSupplier.get();
            add.apply(lastToCur, cur);
            sub.apply(lastToCur, last);
            add.apply(accumulator, lastToCur);
        }
        return div.apply(accumulator, size > 0 ? size : 1);
    }
}
