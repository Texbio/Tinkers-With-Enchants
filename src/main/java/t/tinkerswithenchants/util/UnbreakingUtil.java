package t.tinkerswithenchants.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Applies vanilla Unbreaking probability to a damage amount.
 *
 * <p>TConstruct completely bypasses vanilla's {@code ItemStack.hurt()},
 * routing all damage through {@code ToolDamageUtil.damage()} instead.
 * Vanilla Unbreaking is normally checked inside {@code ItemStack.hurt()},
 * so it never fires for TConstruct tools.
 *
 * <p>Vanilla formula:
 * <ul>
 *   <li>Tools: each damage point has {@code 1/(level+1)} chance to apply
 *       (Unbreaking III = 25% chance per point → ~4x lifetime)</li>
 *   <li>Armor: 60% base chance to always take damage, then same formula
 *       for the remaining 40%</li>
 * </ul>
 */
public final class UnbreakingUtil {

    private UnbreakingUtil() {}

    /**
     * Reduces damage amount based on Unbreaking enchantment level.
     *
     * @param stack  The tool/armor stack to check for Unbreaking
     * @param amount Original damage amount
     * @param random Random source for probability rolls
     * @return Reduced damage amount (may be 0)
     */
    public static int applyUnbreaking(ItemStack stack, int amount, RandomSource random) {
        int unbreakingLevel = EnchantmentHelper.getTagEnchantmentLevel(
                Enchantments.UNBREAKING, stack);
        if (unbreakingLevel <= 0) {
            return amount;
        }

        boolean isArmor = stack.getItem() instanceof ArmorItem;
        int surviving = 0;
        for (int i = 0; i < amount; i++) {
            if (isArmor) {
                // Armor: 60% base chance to always take damage,
                // 40% chance to roll the normal formula
                if (random.nextFloat() < 0.6f) {
                    surviving++;
                    continue;
                }
            }
            // 1/(level+1) chance damage applies
            if (random.nextInt(unbreakingLevel + 1) == 0) {
                surviving++;
            }
        }
        return surviving;
    }
}
