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
        if (!TWEConfig.COMMON.allowEnchantingTable.get()
                && !TWEConfig.COMMON.allowAnvil.get()) return 0;
        return TWEConfig.COMMON.enchantability.get();
    }

    /** @author TinkersWithEnchants @reason enable enchanting table */
    @Overwrite
    public boolean isEnchantable(ItemStack stack) {
        return TWEConfig.COMMON.allowEnchantingTable.get()
                || TWEConfig.COMMON.allowAnvil.get();
    }

    /** @author TinkersWithEnchants @reason enable anvil enchanting */
    @Overwrite(remap = false)
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return TWEConfig.COMMON.allowAnvil.get();
    }

    /** @author TinkersWithEnchants @reason allow appropriate enchantments */
    @Overwrite(remap = false)
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (!TWEConfig.COMMON.allowEnchantingTable.get()) return enchantment.isCurse();
        return enchantment.isCurse() || TWEEnchantUtil.canEnchant(stack, enchantment);
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
        return ModifierUtil.checkVolatileFlag(stack, IModifiable.SHINY) || stack.isEnchanted();
    }
}
