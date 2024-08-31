package comfortable_andy.combat;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
public class CombatOptions implements Cloneable {

    private boolean boxDisplayParticles;
    private boolean compensateCameraMovement;
    private boolean cameraDirectionTitle;

    public CombatOptions() {
        boxDisplayParticles = CombatMain.getInstance().getConfig().getBoolean("box-display-particle");
        compensateCameraMovement = CombatMain.getInstance().getConfig().getBoolean("compensate-camera-movement");
        cameraDirectionTitle = CombatMain.getInstance().getConfig().getBoolean("camera-direction-title");
    }

    @Override
    public CombatOptions clone() {
        try {
            return (CombatOptions) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

}
