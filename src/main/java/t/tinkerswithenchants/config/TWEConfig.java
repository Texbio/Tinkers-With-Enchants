package t.tinkerswithenchants.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Tinkers with Enchants common config.
 *
 * Loaded on both client and server.  All booleans are fully reloadable by
 * Forge via /forge reload on newer builds.
 */
public final class TWEConfig {

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        var pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON      = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private TWEConfig() {}

    // -----------------------------------------------------------------------
    // Config schema
    // -----------------------------------------------------------------------

    public static final class Common {

        /** How "enchantable" TConstruct tools appear to the enchanting table.
         *
         *  Vanilla reference values:
         *    0  = TConstruct default (no enchanting)
         *    1  = stone
         *    5  = iron/diamond
         *    9  = gold (very high)
         *   14  = iron (balanced – our default)
         *   22  = enchanted golden apple level
         *
         *  A higher value means better enchantment rolls from the table. */
        public final ForgeConfigSpec.IntValue enchantability;

        /** Whether TConstruct tools can be enchanted via the enchanting table. */
        public final ForgeConfigSpec.BooleanValue allowEnchantingTable;

        /** Whether TConstruct tools can receive enchantments from enchanted books
         *  placed in an anvil. */
        public final ForgeConfigSpec.BooleanValue allowAnvil;

        /** When true, armor-only enchantments (Protection, Thorns, etc.) are
         *  excluded from the enchanting-table pool for tool items. Set to false
         *  to allow everything (might be useful for multi-tools). */
        public final ForgeConfigSpec.BooleanValue filterArmorEnchantments;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Tinkers with Enchants configuration").push("general");

            enchantability = builder
                    .comment(
                            "Enchantability value for all TConstruct tool items.",
                            "0 = disabled, 14 = iron (default), higher = better rolls.")
                    .defineInRange("enchantability", 14, 1, 100);

            allowEnchantingTable = builder
                    .comment("Allow TConstruct tools to be enchanted at an enchanting table.")
                    .define("allowEnchantingTable", true);

            allowAnvil = builder
                    .comment("Allow TConstruct tools to receive enchantments from enchanted books in an anvil.")
                    .define("allowAnvil", true);

            filterArmorEnchantments = builder
                    .comment(
                            "If true, armor-specific enchantments (Protection, Thorns, etc.) are",
                            "not offered for tool items at the enchanting table.",
                            "Does not affect anvil enchanting – all enchantments are always allowed there.")
                    .define("filterArmorEnchantments", true);

            builder.pop();
        }
    }
}
