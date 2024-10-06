package comfortable_andy.combat.util;

import comfortable_andy.combat.CombatPlayerData;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public record AttackInfo(
        CombatPlayerData playerData,
        ItemStack item,
        EquipmentSlot slot,
        int cdTicks,
        double strengthScale,
        double unModdedKnockback,
        double unEnchantedDamage
        ) {
}
