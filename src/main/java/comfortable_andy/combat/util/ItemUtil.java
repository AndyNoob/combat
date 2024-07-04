package comfortable_andy.combat.util;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public class ItemUtil {

    /**
     * @see AttributeInstance#calculateValue()
     */
    public static double getAttribute(ItemStack item, EquipmentSlot slot, Attribute attribute) {
        final Material material = item.getType();
        // all the modifiers here should all be add_value
        double d = material.getDefaultAttributeModifiers(slot).get(attribute)
                .stream()
                .mapToDouble(AttributeModifier::getAmount).sum();

        if (!item.hasItemMeta()) return d;

        final Collection<AttributeModifier> modifiers = item.getItemMeta().getAttributeModifiers(slot).get(attribute);

        // copied from net.minecraft.world.entity.ai.attributes.AttributeInstance#calculateValue
        for (AttributeModifier mod : modifiers.stream().filter(a -> a.getOperation() == AttributeModifier.Operation.ADD_NUMBER).toList()) {
            d += mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        double e = d;

        for (AttributeModifier mod : modifiers
                .stream()
                .filter(a -> a.getOperation() == AttributeModifier.Operation.ADD_SCALAR)
                .toList()) {
            e += d * mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        for (AttributeModifier mod : modifiers
                .stream()
                .filter(a -> a.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                .toList()) {
            e *= 1.0 + mod.getAmount(); // Paper - destroy speed API - diff on change
        }

        return e;
    }

}
