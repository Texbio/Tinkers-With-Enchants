package t.tinkerswithenchants.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import t.tinkerswithenchants.config.TWEConfig;
import t.tinkerswithenchants.util.EnchantMergeUtil;
import t.tinkerswithenchants.util.TWEEnchantUtil;
import java.util.Map;

/** Patches ModifiableItem (tools: swords, axes, picks, etc.) */
@Mixin(ModifiableItem.class)
public abstract class ModifiableItemMixin extends TieredItem {

    private ModifiableItemMixin(Tier tier, Properties props) { super(tier, props); }

    @Override
    public int getEnchantmentValue() {
        return TWEEnchantUtil.getCachedEnchantability();
    }

    /** Stack-aware enchantability for Forge code paths */
    public int getEnchantmentValue(ItemStack stack) {
        return TWEEnchantUtil.computeEnchantability(stack);
    }

    /** @author TinkersWithEnchants @reason enable enchanting table */
    @Overwrite
    public boolean isEnchantable(ItemStack stack) {
        TWEEnchantUtil.cacheEnchantability(stack);
        return true;
    }

    /** @author TinkersWithEnchants @reason enable anvil enchanting */
    @Overwrite(remap = false)
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    /** @author TinkersWithEnchants @reason allow appropriate enchantments */
    @Overwrite(remap = false)
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return TWEEnchantUtil.canEnchant(stack, enchantment);
    }

    /** @author TinkersWithEnchants @reason ensure real NBT enchantments are never hidden by modifier hooks */
    @Overwrite(remap = false)
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        return EnchantMergeUtil.getMergedLevel(stack, enchantment);
    }

    /** @author TinkersWithEnchants @reason ensure real NBT enchantments are never hidden by modifier hooks */
    @Overwrite(remap = false)
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        return EnchantMergeUtil.getMergedEnchantments(stack);
    }

    /** @author TinkersWithEnchants @reason show enchant glint when item has real enchantments */
    @Overwrite
    public boolean isFoil(ItemStack stack) {
        if (!TWEConfig.CLIENT.showGlint.get()) {
            return ModifierUtil.checkVolatileFlag(stack, IModifiable.SHINY);
        }
        return ModifierUtil.checkVolatileFlag(stack, IModifiable.SHINY) || stack.isEnchanted();
    }
}
