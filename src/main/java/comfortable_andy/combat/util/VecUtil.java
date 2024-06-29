package comfortable_andy.combat.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaterniond;
import org.joml.Vector2f;
import org.joml.Vector3d;

import java.text.DecimalFormat;

public class VecUtil {

    public static DecimalFormat FORMAT = new DecimalFormat("#.##");

    public static Vector3d fromBukkit(Vector v) {
        return new Vector3d(v.getX(), v.getY(), v.getZ());
    }

    public static Vector fromJoml(Vector3d v) {
        return new Vector(v.x, v.y, v.z);
    }

    public static Quaterniond fromDir(float rotY, float rotX) {
        return new Quaterniond().rotationY(-Math.toRadians(rotY)).rotateX(Math.toRadians(rotX));
    }

    public static Quaterniond fromDir(Vector2f v) {
        return fromDir(v.y, v.x);
    }

    public static Quaterniond fromDir(Location location) {
        return fromDir(location.getYaw(), location.getPitch());
    }

    public static String str(Quaterniond q) {
        return q.toString(FORMAT);
    }

    public static String str(Vector3d v) {
        return v.toString(FORMAT);
    }

}
