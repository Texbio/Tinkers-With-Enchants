package t.tinkerswithenchants.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import t.tinkerswithenchants.config.TWEConfig;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maps TConstruct tools to vanilla enchantment categories.
 *
 * <h3>Primary: Forge ToolActions</h3>
 * TConstruct registers ToolActions in each tool's JSON definition,
 * and some modifiers add ToolActions dynamically:
 * <ul>
 *   <li>{@code sword_dig}       → WEAPON (sword, cleaver, dagger, swasher)</li>
 *   <li>{@code axe_dig/strip}   → WEAPON + DIGGER (hand axe, broad axe, mattock)</li>
 *   <li>{@code pickaxe_dig}     → DIGGER (pickaxe, pickadze, sledge hammer, vein hammer)</li>
 *   <li>{@code shovel_dig}      → DIGGER (excavator, mattock, pickadze)</li>
 *   <li>{@code hoe_dig}         → DIGGER (kama, dagger)</li>
 *   <li>{@code shears_dig/harvest} → DIGGER (from silky_shears modifier trait)</li>
 *   <li>{@code fishing_rod_cast}→ FISHING_ROD (from FishingModule modifier)</li>
 *   <li>{@code sword_sweep}     → WEAPON (vanilla compat for addons)</li>
 * </ul>
 * Note: TConstruct uses {@code sword_dig}, NOT vanilla's {@code sword_sweep}.
 *
 * <h3>Fallback: TConstruct tags</h3>
 * Some tools register no ToolActions (scythe, battlesign, melting_pan):
 * <ul>
 *   <li>{@code tconstruct:modifiable/melee/primary}    → WEAPON</li>
 *   <li>{@code tconstruct:modifiable/harvest/primary}   → DIGGER</li>
 *   <li>{@code tconstruct:modifiable/fishing_rods}      → FISHING_ROD</li>
 * </ul>
 *
 * <h3>Ranged: instanceof detection</h3>
 * {@code ModifiableBowItem} → BOW, {@code ModifiableCrossbowItem} → CROSSBOW.
 * Tags are NOT used for ranged because {@code modifiable/ranged} is too broad.
 *
 * <h3>Addon compatibility</h3>
 * This is fully automatic. Addons that register ToolActions in their
 * tool definition JSON get correct mapping. Addons that add items to
 * TConstruct's tags get caught by the fallback. No hardcoded item IDs.
 */
@SuppressWarnings("removal")
public final class TWEEnchantUtil {

    private TWEEnchantUtil() {}

    // TConstruct's sword ToolAction (NOT vanilla's sword_sweep)
    private static final ToolAction SWORD_DIG = ToolAction.get("sword_dig");

    // Fallback tags
    private static final TagKey<Item> TAG_MELEE_PRIMARY =
            ItemTags.create(new ResourceLocation("tconstruct", "modifiable/melee/primary"));
    private static final TagKey<Item> TAG_HARVEST_PRIMARY =
            ItemTags.create(new ResourceLocation("tconstruct", "modifiable/harvest/primary"));
    private static final TagKey<Item> TAG_MODIFIABLE =
            ItemTags.create(new ResourceLocation("tconstruct", "modifiable"));
    private static final TagKey<Item> TAG_FISHING_ROD =
            ItemTags.create(new ResourceLocation("tconstruct", "modifiable/fishing_rods"));
    private static final TagKey<Item> TAG_RANGED =
            ItemTags.create(new ResourceLocation("tconstruct", "modifiable/ranged"));

    public static Set<EnchantmentCategory> getCategories(ItemStack stack) {
        if (!(stack.getItem() instanceof IModifiable)) return Set.of();

        Set<EnchantmentCategory> cats = EnumSet.noneOf(EnchantmentCategory.class);

        // ── Armor (slot-based) ─────────────────────────────────────────────
        if (stack.getItem() instanceof ModifiableArmorItem armor) {
            cats.add(EnchantmentCategory.ARMOR);
            cats.add(EnchantmentCategory.WEARABLE);
            switch (armor.getEquipmentSlot()) {
                case HEAD  -> cats.add(EnchantmentCategory.ARMOR_HEAD);
                case CHEST -> cats.add(EnchantmentCategory.ARMOR_CHEST);
                case LEGS  -> cats.add(EnchantmentCategory.ARMOR_LEGS);
                case FEET  -> cats.add(EnchantmentCategory.ARMOR_FEET);
                default    -> {}
            }
        }

        // ── ToolAction-based detection (primary) ───────────────────────────
        // TConstruct registers these in each tool's JSON definition.
        // Modifier-added ToolActions (shears, fishing) are also detected.

        // sword_dig = TConstruct sword-type (sword, cleaver, dagger, swasher)
        // sword_sweep = vanilla sword action (for addon compatibility)
        if (stack.canPerformAction(SWORD_DIG)
                || stack.canPerformAction(ToolActions.SWORD_SWEEP)) {
            cats.add(EnchantmentCategory.WEAPON);
        }

        // axe_dig = axe-type (hand axe, broad axe, mattock, minotaur axe)
        // Vanilla axes get both WEAPON + DIGGER
        if (stack.canPerformAction(ToolActions.AXE_DIG)
                || stack.canPerformAction(ToolActions.AXE_STRIP)) {
            cats.add(EnchantmentCategory.WEAPON);
            cats.add(EnchantmentCategory.DIGGER);
        }

        // pickaxe/shovel/hoe dig = mining tools
        if (stack.canPerformAction(ToolActions.PICKAXE_DIG)
                || stack.canPerformAction(ToolActions.SHOVEL_DIG)
                || stack.canPerformAction(ToolActions.HOE_DIG)) {
            cats.add(EnchantmentCategory.DIGGER);
        }

        // shears = harvest tools (added dynamically by silky_shears modifier)
        // Vanilla shears use DIGGER category (Efficiency, Silk Touch)
        if (stack.canPerformAction(ToolActions.SHEARS_DIG)
                || stack.canPerformAction(ToolActions.SHEARS_HARVEST)) {
            cats.add(EnchantmentCategory.DIGGER);
        }

        // fishing_rod_cast = fishing rod (added by FishingModule modifier)
        // Vanilla fishing rods use FISHING_ROD category (Luck of the Sea, Lure)
        if (stack.canPerformAction(ToolActions.FISHING_ROD_CAST)) {
            cats.add(EnchantmentCategory.FISHING_ROD);
        }

        // ── Tag-based fallback ─────────────────────────────────────────────
        // For tools with NO ToolActions (scythe, battlesign, melting_pan, etc.)
        // OR tools that need WEAPON from melee/primary even though they have
        // a DIGGER ToolAction (sledge hammer, vein hammer).
        // Always check melee/primary for WEAPON (regardless of existing cats).
        if (stack.is(TAG_MELEE_PRIMARY) && !cats.contains(EnchantmentCategory.WEAPON)) {
            cats.add(EnchantmentCategory.WEAPON);
        }
        // Only add DIGGER from harvest tag if no ToolAction already gave it
        if (stack.is(TAG_HARVEST_PRIMARY) && !cats.contains(EnchantmentCategory.DIGGER)) {
            cats.add(EnchantmentCategory.DIGGER);
        }

        // Fishing rod tag fallback (fishing_rod has no base ToolActions,
        // gets fishing_rod_cast from its FishingModule modifier trait)
        if (stack.is(TAG_FISHING_ROD)) {
            cats.add(EnchantmentCategory.FISHING_ROD);
        }

        // ── Ranged: use instanceof, NOT tags ────────────────────────────────
        // TConstruct's "modifiable/ranged" tag is too broad — includes staffs,
        // javelins, swashers, fishing rods. Only actual bow/crossbow classes
        // should get bow/crossbow enchantments.
        if (stack.getItem() instanceof ModifiableCrossbowItem) {
            cats.add(EnchantmentCategory.CROSSBOW);
        } else if (stack.getItem() instanceof ModifiableBowItem) {
            cats.add(EnchantmentCategory.BOW);
        }

        // ── Universal enchants (Mending, Unbreaking, Curse of Vanishing) ──
        if (!cats.isEmpty() || stack.is(TAG_MODIFIABLE)) {
            cats.add(EnchantmentCategory.BREAKABLE);
            cats.add(EnchantmentCategory.VANISHABLE);
        }

        return cats;
    }

    public static boolean canEnchant(ItemStack stack, Enchantment enchantment) {
        Set<EnchantmentCategory> categories = getCategories(stack);
        if (categories.isEmpty()) return false;

        // Filter armor enchants on non-armor items if configured
        if (TWEConfig.COMMON.filterArmorEnchantments.get()
                && !(stack.getItem() instanceof ModifiableArmorItem)) {
            if (isArmorCategory(enchantment.category)) return false;
        }

        // Sweeping Edge: only on tools with the sweep_melee module (sword, cleaver).
        // Detection: has sword_dig but NOT hoe_dig (dagger) and NOT in ranged tag (swasher).
        // Axes are already blocked (no sword_dig). Addon tools with sweep_melee
        // should also register sword_dig, so this works for them too.
        if (enchantment == net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE) {
            boolean hasSwordDig = stack.canPerformAction(SWORD_DIG)
                    || stack.canPerformAction(ToolActions.SWORD_SWEEP);
            boolean hasSweep = hasSwordDig
                    && !stack.canPerformAction(ToolActions.HOE_DIG)
                    && !stack.is(TAG_RANGED);
            if (!hasSweep) {
                return false;
            }
        }

        // Infinity only on bows, not crossbows (matches vanilla behavior)
        if (enchantment == net.minecraft.world.item.enchantment.Enchantments.INFINITY_ARROWS) {
            if (!(stack.getItem() instanceof ModifiableBowItem)) {
                return false;
            }
        }

        // Quick Charge only on crossbows
        if (enchantment == net.minecraft.world.item.enchantment.Enchantments.QUICK_CHARGE) {
            if (!(stack.getItem() instanceof ModifiableCrossbowItem)) {
                return false;
            }
        }

        for (EnchantmentCategory cat : categories) {
            if (enchantment.category == cat) return true;
        }

        // ── Custom category fallback ─────────────────────────────────────
        // Mods like Min's Enchantments register custom EnchantmentCategories
        // with instanceof checks against vanilla classes (e.g. ProjectileWeaponItem).
        // TConstruct tools don't extend those classes, so the check fails.
        //
        // Fix: probe the custom category with vanilla items to figure out what
        // it represents, then match against our TConstruct tool types.
        // This is generic — works with ANY mod's custom categories.
        EnchantmentCategory ec = enchantment.category;
        if (probeCategory(ec, stack, categories)) return true;

        return false;
    }

    /**
     * Probes a custom {@link EnchantmentCategory} by testing it against vanilla
     * reference items. If it matches a vanilla type that corresponds to one of
     * our mapped categories, return true.
     */
    private static boolean probeCategory(EnchantmentCategory ec, ItemStack stack, Set<EnchantmentCategory> categories) {
        // Only probe if it's a non-standard category we don't already handle
        if (isStandardCategory(ec)) return false;

        try {
            // Probe with vanilla items → map to our categories
            if (categories.contains(EnchantmentCategory.WEAPON)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_SWORD)) return true;
            }
            if (categories.contains(EnchantmentCategory.DIGGER)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_PICKAXE)) return true;
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_AXE)) return true;
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_SHOVEL)) return true;
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_HOE)) return true;
            }
            if (categories.contains(EnchantmentCategory.BOW)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.BOW)) return true;
            }
            if (categories.contains(EnchantmentCategory.CROSSBOW)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.CROSSBOW)) return true;
            }
            if (categories.contains(EnchantmentCategory.FISHING_ROD)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.FISHING_ROD)) return true;
            }
            if (categories.contains(EnchantmentCategory.ARMOR)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)) return true;
            }
            if (categories.contains(EnchantmentCategory.ARMOR_HEAD)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_HELMET)) return true;
            }
            if (categories.contains(EnchantmentCategory.ARMOR_CHEST)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)) return true;
            }
            if (categories.contains(EnchantmentCategory.ARMOR_LEGS)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_LEGGINGS)) return true;
            }
            if (categories.contains(EnchantmentCategory.ARMOR_FEET)) {
                if (ec.canEnchant(net.minecraft.world.item.Items.DIAMOND_BOOTS)) return true;
            }
            // Shield probing — TConstruct shields can block but don't extend ShieldItem.
            // Only match if this item has no weapon/digger/bow/crossbow/fishing categories
            // (i.e. it's likely a shield or purely defensive item).
            if (categories.contains(EnchantmentCategory.WEARABLE)
                    || (categories.size() <= 2 && categories.contains(EnchantmentCategory.BREAKABLE))) {
                if (ec.canEnchant(net.minecraft.world.item.Items.SHIELD)) return true;
            }
        } catch (Exception ignored) {
            // Safety net — custom canEnchant predicates could throw
        }
        return false;
    }

    private static boolean isStandardCategory(EnchantmentCategory cat) {
        return cat == EnchantmentCategory.ARMOR
                || cat == EnchantmentCategory.ARMOR_FEET
                || cat == EnchantmentCategory.ARMOR_LEGS
                || cat == EnchantmentCategory.ARMOR_CHEST
                || cat == EnchantmentCategory.ARMOR_HEAD
                || cat == EnchantmentCategory.WEAPON
                || cat == EnchantmentCategory.DIGGER
                || cat == EnchantmentCategory.BOW
                || cat == EnchantmentCategory.CROSSBOW
                || cat == EnchantmentCategory.FISHING_ROD
                || cat == EnchantmentCategory.BREAKABLE
                || cat == EnchantmentCategory.VANISHABLE
                || cat == EnchantmentCategory.WEARABLE
                || cat == EnchantmentCategory.TRIDENT;
    }

    private static boolean isArmorCategory(EnchantmentCategory cat) {
        return cat == EnchantmentCategory.ARMOR
                || cat == EnchantmentCategory.ARMOR_HEAD
                || cat == EnchantmentCategory.ARMOR_CHEST
                || cat == EnchantmentCategory.ARMOR_LEGS
                || cat == EnchantmentCategory.ARMOR_FEET;
    }
}
