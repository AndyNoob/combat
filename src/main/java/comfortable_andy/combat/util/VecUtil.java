package comfortable_andy.combat.util;

import org.bukkit.util.Vector;
import org.joml.Vector3d;

public class VecUtil {

    public static Vector3d fromBukkit(Vector v) {
        return new Vector3d(v.getX(), v.getY(), v.getZ());
    }

    public static Vector fromJoml(Vector3d v) {
        return new Vector(v.x, v.y, v.z);
    }

}
