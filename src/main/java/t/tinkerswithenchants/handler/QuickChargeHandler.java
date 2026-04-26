package t.tinkerswithenchants.handler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Applies vanilla Quick Charge enchantment to TConstruct ranged tools that
 * use the load-and-fire mechanic.
 *
 * <p>TConstruct calculates draw time from its DRAW_SPEED stat, ignoring
 * vanilla Quick Charge. This handler fires when item use starts and
 * reduces the stored drawtime in TConstruct's persistent data by 5 ticks
 * per Quick Charge level (matching vanilla: QC I = 20, QC II = 15, QC III = 10).</p>
 *
 * <h3>Detection (with version compatibility)</h3>
 * <ol>
 *   <li><b>Modern path (TC 3.10+):</b> the
 *       {@code tconstruct:modifiable/ranged/quick_charge} tag exists and
 *       includes crossbows + swasher + addon launchers that opt in. We
 *       check this tag first — when present, it's authoritative.</li>
 *   <li><b>Legacy fallback (TC 3.9.x):</b> the quick_charge tag does NOT
 *       exist in the datapack. We fall back to checking the crossbows tag
 *       (covers crossbow + war_pick) plus a hardcoded swasher item id
 *       (the only base TC launcher that uses load-and-fire but isn't in
 *       the crossbows tag).</li>
 * </ol>
 * The tags are built locally via {@link ItemTags#create} rather than
 * referencing {@code TinkerTags.Items.RANGED_QUICK_CHARGE} directly,
 * because that Java constant doesn't exist in TC 3.9.x.
 *
 * <p>Uses {@code LivingEntityUseItemEvent.Start}, which fires after TConstruct's
 * {@code use()} has already called {@code startDrawing()} (or
 * {@code startUsingWithDrawtime} for the swasher) and stored the base
 * drawtime.</p>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuickChargeHandler {

    private static final ResourceLocation KEY_DRAWTIME =
            TConstruct.getResource("drawtime");

    /** TC 3.10+ tag — empty in 3.9 datapacks, so {@code stack.is} returns
     *  false there and we fall through to the legacy detection. */
    private static final TagKey<Item> TAG_RANGED_QUICK_CHARGE =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("tconstruct", "modifiable/ranged/quick_charge"));

    /** Always-present subtag (since TC 3.9). Covers crossbow + war_pick. */
    private static final TagKey<Item> TAG_CROSSBOWS =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("tconstruct", "modifiable/ranged/crossbows"));

    /** Hardcoded for TC 3.9 compat — swasher is the only base TC launcher
     *  that uses load-and-fire but is not in the crossbows tag. */
    private static final ResourceLocation SWASHER_ID =
            ResourceLocation.fromNamespaceAndPath("tconstruct", "swasher");

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        if (!stack.is(TinkerTags.Items.MODIFIABLE)) return;
        if (!isQuickChargeable(stack)) return;

        int quickCharge = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);
        if (quickCharge <= 0) return;

        ToolStack tool = ToolStack.from(stack);
        int drawtime = tool.getPersistentData().getInt(KEY_DRAWTIME);
        if (drawtime <= 0) return;

        // Vanilla: each level reduces by 5 ticks (from base 25)
        int reduced = Math.max(0, drawtime - (quickCharge * 5));
        tool.getPersistentData().putInt(KEY_DRAWTIME, reduced);
    }

    /**
     * Two-path detection so we work on both modern (3.10+) and legacy
     * (3.9.x) TC versions. The modern tag is empty in 3.9 datapacks, so
     * the first check naturally falls through there and we hit the
     * legacy path.
     */
    private static boolean isQuickChargeable(ItemStack stack) {
        // Modern: TC 3.10+ defines this tag with crossbows + swasher + addons
        if (stack.is(TAG_RANGED_QUICK_CHARGE)) return true;
        // Legacy: explicit crossbows + the one base TC launcher missing from it
        if (stack.is(TAG_CROSSBOWS)) return true;
        return SWASHER_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
}
