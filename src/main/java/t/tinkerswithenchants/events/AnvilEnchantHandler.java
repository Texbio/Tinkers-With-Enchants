package t.tinkerswithenchants.events;

import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import t.tinkerswithenchants.TinkersWithEnchants;
import t.tinkerswithenchants.config.TWEConfig;
import t.tinkerswithenchants.util.TWEEnchantUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles TConstruct tool + enchanted book combinations in the anvil.
 *
 * <p>AnvilUpdateEvent fires before vanilla computes the output. Setting an
 * output here makes Forge use it directly, bypassing vanilla's
 * EnchantmentCategory check (which filters all enchantments for non-vanilla
 * tool types).
 *
 * <p>Enchantments are filtered by the tool's actual ToolActions via
 * {@link TWEEnchantUtil}, so only appropriate enchants are accepted —
 * e.g. Fortune on a pickaxe, Sharpness on a sword.
 *
 * <p>Apotheosis fires the same AnvilUpdateEvent from its custom AnvilMenu, so
 * this handler works unchanged under Apotheosis.
 */
@Mod.EventBusSubscriber(modid = TinkersWithEnchants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnvilEnchantHandler {

    private static final Logger LOGGER = LogManager.getLogger(TinkersWithEnchants.MOD_ID);

    private AnvilEnchantHandler() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left  = event.getLeft();
        ItemStack right = event.getRight();

        if (!(left.getItem() instanceof IModifiable)) return;
        if (!TWEConfig.COMMON.allowAnvil.get()) return;
        if (right.isEmpty() || !(right.getItem() instanceof EnchantedBookItem)) return;

        // Deserialize the book's ListTag into Map<Enchantment, Integer>
        Map<Enchantment, Integer> bookEnchants =
                EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(right));

        if (bookEnchants.isEmpty()) return;

        Map<Enchantment, Integer> outputEnchants = new HashMap<>(EnchantmentHelper.getEnchantments(left));

        int totalCost      = 0;
        boolean anyApplied = false;

        for (Map.Entry<Enchantment, Integer> entry : bookEnchants.entrySet()) {
            Enchantment ench      = entry.getKey();
            int         bookLevel = entry.getValue();

            // ── Category gate: only accept enchants valid for this tool type ──
            if (!TWEEnchantUtil.canEnchant(left, ench)) {
                LOGGER.debug("TWE: skipping {} — wrong category for {}",
                        ForgeRegistries.ENCHANTMENTS.getKey(ench),
                        left.getDisplayName().getString());
                totalCost++; // vanilla counts incompatible books as +1 XP penalty
                continue;
            }

            // ── Incompatibility gate: reject if conflicts with existing enchants ──
            boolean compatible = outputEnchants.keySet().stream()
                    .filter(e -> e != ench)
                    .allMatch(e -> e.isCompatibleWith(ench));
            if (!compatible) {
                LOGGER.debug("TWE: skipping {} — conflicts with existing enchants",
                        ForgeRegistries.ENCHANTMENTS.getKey(ench));
                totalCost++;
                continue;
            }

            // ── Level merge (same logic as vanilla anvil) ──
            int currentLevel = outputEnchants.getOrDefault(ench, 0);
            int finalLevel = (currentLevel == bookLevel)
                    ? Math.min(currentLevel + 1, ench.getMaxLevel())
                    : Math.max(currentLevel, bookLevel);

            outputEnchants.put(ench, finalLevel);

            // XP cost multiplier: rare enchants cost more (multiplier 4 for
            // treasure enchants, 2 for normal — consistent with vanilla anvil)
            int multiplier = ench.isTreasureOnly() ? 4 : 2;
            totalCost += finalLevel * multiplier;
            anyApplied = true;
        }

        if (!anyApplied) return;

        ItemStack output = left.copy();
        EnchantmentHelper.setEnchantments(outputEnchants, output);

        event.setOutput(output);
        event.setCost(Math.max(1, totalCost));
        event.setMaterialCost(1);

        LOGGER.debug("TWE: applied enchants to {} via anvil, cost={}",
                left.getDisplayName().getString(), event.getCost());
    }
}
