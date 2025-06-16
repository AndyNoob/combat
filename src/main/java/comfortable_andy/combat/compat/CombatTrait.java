package comfortable_andy.combat.compat;

import net.citizensnpcs.api.trait.Trait;

public class CombatTrait extends Trait {



    public CombatTrait() {
        super("combat");
    }

    @Override
    public void run() {
        if (getNPC() == null) return;

    }

    public void tick() {

    }

    @Override
    public boolean isRunImplemented() {
        return true;
    }
}
