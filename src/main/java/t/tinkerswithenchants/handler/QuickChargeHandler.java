package t.tinkerswithenchants.handler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Applies vanilla Quick Charge enchantment to TConstruct crossbows.
 *
 * <p>TConstruct calculates draw time from its DRAW_SPEED stat, ignoring
 * vanilla Quick Charge. This handler fires when item use starts and
 * reduces the stored drawtime in TConstruct's persistent data by 5 ticks
 * per Quick Charge level (matching vanilla: QC I = 20, QC II = 15, QC III = 10).</p>
 *
 * <p>Uses {@code LivingEntityUseItemEvent.Start}, which fires after TConstruct's
 * {@code use()} has already called {@code startDrawing()} and stored the base
 * drawtime.</p>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuickChargeHandler {

    private static final ResourceLocation KEY_DRAWTIME =
            TConstruct.getResource("drawtime");

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        if (!stack.is(TinkerTags.Items.CROSSBOWS)) return;
        if (!stack.is(TinkerTags.Items.MODIFIABLE)) return;

        int quickCharge = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);
        if (quickCharge <= 0) return;

        ToolStack tool = ToolStack.from(stack);
        int drawtime = tool.getPersistentData().getInt(KEY_DRAWTIME);
        if (drawtime <= 0) return;

        // Vanilla: each level reduces by 5 ticks (from base 25)
        int reduced = Math.max(0, drawtime - (quickCharge * 5));
        tool.getPersistentData().putInt(KEY_DRAWTIME, reduced);
    }
}
