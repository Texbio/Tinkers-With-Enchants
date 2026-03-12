package t.tinkerswithenchants.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;

import java.util.Map;

/**
 * Merges TConstruct's modifier-reported enchantments with real NBT
 * enchantments so that no real enchantment is ever hidden.
 *
 * <h3>The problem</h3>
 * TConstruct overrides {@code getEnchantmentLevel} and
 * {@code getAllEnchantments} on all its item classes to route through
 * its modifier system.  While the modifier system DOES read real NBT
 * enchantments as a starting point, modifier hooks can adjust levels
 * downward or to zero — effectively canceling real enchantments that
 * we added via this mod.
 *
 * <h3>The fix</h3>
 * For each enchantment, we return the MAXIMUM of:
 * <ul>
 *   <li>The level TConstruct's modifier system reports (preserves
 *       modifier-added enchantments like Silk Touch from silky trait)</li>
 *   <li>The real NBT level (ensures our enchantments are never hidden)</li>
 * </ul>
 */
public final class EnchantMergeUtil {

    private EnchantMergeUtil() {}

    /**
     * Returns the effective level of a single enchantment, merging
     * TConstruct's modifier-reported level with the real NBT level.
     */
    public static int getMergedLevel(ItemStack stack, Enchantment enchantment) {
        // TConstruct's version: reads NBT, then lets modifiers adjust
        int modifierLevel = EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment);
        // Raw NBT level: what's actually stored on the item
        int nbtLevel = EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
        // Take the max so real enchantments are never reduced
        return Math.max(modifierLevel, nbtLevel);
    }

    /**
     * Returns all enchantments on the stack, merging TConstruct's
     * modifier-reported enchantments with real NBT enchantments.
     */
    public static Map<Enchantment, Integer> getMergedEnchantments(ItemStack stack) {
        // TConstruct's version: reads NBT, then lets modifiers adjust/add/remove
        Map<Enchantment, Integer> result = EnchantmentModifierHook.getAllEnchantments(stack);
        // Raw NBT enchantments
        Map<Enchantment, Integer> nbt = EnchantmentHelper.getEnchantments(stack);
        // Merge: for each NBT enchantment, ensure it's at least at NBT level
        for (Map.Entry<Enchantment, Integer> entry : nbt.entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return result;
    }
}
