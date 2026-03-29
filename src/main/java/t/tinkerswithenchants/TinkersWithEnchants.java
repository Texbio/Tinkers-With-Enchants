package t.tinkerswithenchants;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import t.tinkerswithenchants.command.TWECommand;
import t.tinkerswithenchants.config.TWEConfig;

/**
 * Root class for <em>Tinkers with Enchants</em>.
 *
 * <h2>What this mod does</h2>
 * TConstruct deliberately prevents its tools from being enchanted via the
 * enchanting table or the vanilla anvil.  This mod lifts that restriction by
 * patching three low-level methods on TConstruct's base item classes:
 *
 * <ol>
 *   <li>{@code ModifiableItem.getEnchantmentValue()} – added as a new override
 *       by {@link t.tinkerswithenchants.mixin.ModifiableItemMixin} to return a
 *       configurable non-zero value, enabling the enchanting table's level
 *       cost calculation.  (Item's base implementation returns 0; TConstruct
 *       does not override it, so we add a fresh override.)</li>
 *   <li>{@code ModifiableItem.isBookEnchantable()} – patched by the same
 *       Mixin to return {@code true}, unblocking the vanilla anvil gate.</li>
 *   <li>{@code IForgeItem.canApplyAtEnchantingTable()} – overridden by the
 *       same Mixin to allow appropriate enchantments through the
 *       per-enchantment filter that the enchanting table and
 *       {@code EnchantmentHelper} apply.</li>
 * </ol>
 *
 * Anvil enchanting is additionally handled by
 * {@link t.tinkerswithenchants.events.AnvilEnchantHandler} via Forge's
 * {@code AnvilUpdateEvent}, which gives us full control over cost and output.
 *
 * <h2>Apotheosis compatibility</h2>
 * Apotheosis's {@code ApothEnchantmentMenu} and {@code ApothAnvilMenu} both
 * delegate to the same vanilla/Forge item-level hooks that we patch.  No
 * Apotheosis-specific code is required.
 *
 * <h2>Addon compatibility</h2>
 * Every TConstruct addon tool item (Tinkers' Things, etc.) either extends
 * {@code ModifiableItem} (covered by the Mixin) or implements
 * {@link slimeknights.tconstruct.library.tools.item.IModifiable} (covered by
 * the {@code IForgeItem} Mixin and the anvil event handler).  No per-addon
 * code is needed.
 *
 * <h2>Notes on grindstone / XP duplication</h2>
 * TConstruct applies some enchantments (Fortune, Silk Touch) <em>temporarily</em>
 * to the item stack during a block-break event, then restores the original
 * enchantment NBT afterwards.  Because they capture and restore the full
 * {@code Enchantments} tag, any enchantments we permanently add via this mod
 * are preserved across that cycle.  The grindstone can remove our enchantments
 * (giving XP in return) as normal – this is expected behaviour, not a dupe.
 */
@Mod(TinkersWithEnchants.MOD_ID)
public final class TinkersWithEnchants {

    public static final String MOD_ID = "tinkerswithenchants";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @SuppressWarnings("removal") // FMLJavaModLoadingContext.get() / ModLoadingContext.get() are
    public TinkersWithEnchants() { // deprecated for NeoForge/1.21+ but correct for Forge 1.20.1
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TWEConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TWEConfig.CLIENT_SPEC);

        // Register lifecycle events on the mod bus
        bus.addListener(this::commonSetup);

        // Register commands on the Forge bus
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        // Forge bus events are auto-registered via @Mod.EventBusSubscriber on
        // AnvilEnchantHandler – no explicit registration needed here.
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Tinkers with Enchants initialised.");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        TWECommand.register(event.getDispatcher());
    }
}
