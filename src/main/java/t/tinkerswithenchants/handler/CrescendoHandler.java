package t.tinkerswithenchants.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implements Apotheosis's Crescendo of Bolts enchantment for TConstruct crossbows.
 *
 * <p>Crescendo gives the crossbow extra shots per charge, one per enchantment level.
 * Apotheosis hooks into vanilla's {@code CrossbowItem.use()}, but TConstruct uses
 * its own {@code ModifiableCrossbowItem} which never calls vanilla code.</p>
 *
 * <p>How it works:</p>
 * <ol>
 *   <li>When an arrow spawns from a TConstruct crossbow with Crescendo, we copy
 *       the loaded ammo NBT from the crossbow's persistent data (still present
 *       at this point — removal happens after arrow spawn).</li>
 *   <li>On the next tick, if shots remaining &lt; Crescendo level, we re-store
 *       the ammo NBT, effectively reloading the crossbow for free.</li>
 *   <li>Extra shot arrows get CREATIVE_ONLY pickup to prevent duplication.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CrescendoHandler {

    private static final ResourceLocation KEY_CROSSBOW_AMMO = TConstruct.getResource("crossbow_ammo");
    private static final ResourceLocation KEY_CRESCENDO_SHOTS = TConstruct.getResource("twe_crescendo_shots");

    /** Cached Apotheosis Crescendo enchantment reference */
    private static Enchantment cachedCrescendo;
    private static boolean crescendoResolved = false;

    /** Pending reloads: player UUID → saved ammo NBT to re-store next tick */
    private static final Map<UUID, CompoundTag> pendingReloads = new HashMap<>();

    /**
     * When an arrow spawns from a TConstruct crossbow with Crescendo,
     * capture the loaded ammo for potential re-storing.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        // Skip multishot clones
        if (arrow.getPersistentData().getBoolean("twe_multishot_clone")) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof Player player)) return;

        // Find TConstruct crossbow with Crescendo
        ItemStack weapon = findCrescendoCrossbow(player);
        if (weapon.isEmpty()) return;

        int crescendoLevel = getCrescendoLevel(weapon);
        if (crescendoLevel <= 0) return;

        // Already scheduled a reload for this shot? Don't duplicate
        if (pendingReloads.containsKey(player.getUUID())) return;

        ToolStack tool = ToolStack.from(weapon);
        ModDataNBT persistentData = tool.getPersistentData();

        // Read the loaded ammo (still present — removal happens after arrow spawn)
        CompoundTag ammoNbt = persistentData.get(KEY_CROSSBOW_AMMO,
                (tag, key) -> tag.getCompound(key));
        if (ammoNbt == null || ammoNbt.isEmpty()) return;

        // Read current shot count (0 on first shot)
        int shots = persistentData.get(KEY_CRESCENDO_SHOTS,
                (tag, key) -> tag.getInt(key));

        if (shots < crescendoLevel) {
            // Save a copy of the ammo for re-storing next tick
            pendingReloads.put(player.getUUID(), ammoNbt.copy());

            // Mark extra shot arrows as non-pickable (shots > 0 means bonus shot)
            if (shots > 0) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
        }
    }

    /**
     * On next tick, re-store the ammo in the crossbow and increment shot counter.
     * This runs AFTER fireCrossbow() has removed the ammo from persistent data.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.player.level().isClientSide()) return;

        CompoundTag savedAmmo = pendingReloads.remove(event.player.getUUID());
        if (savedAmmo == null) return;

        // Find the crossbow again
        ItemStack weapon = findCrescendoCrossbow(event.player);
        if (weapon.isEmpty()) return;

        int crescendoLevel = getCrescendoLevel(weapon);
        if (crescendoLevel <= 0) return;

        ToolStack tool = ToolStack.from(weapon);
        ModDataNBT persistentData = tool.getPersistentData();

        // Read and increment shot counter
        int shots = persistentData.get(KEY_CRESCENDO_SHOTS,
                (tag, key) -> tag.getInt(key));
        shots++;

        if (shots < crescendoLevel) {
            // More shots remaining — reload the crossbow
            persistentData.put(KEY_CROSSBOW_AMMO, savedAmmo);
            persistentData.putInt(KEY_CRESCENDO_SHOTS, shots);
        } else {
            // Final shot was already fired — clean up the counter
            persistentData.remove(KEY_CRESCENDO_SHOTS);
        }
    }

    /**
     * Finds a TConstruct crossbow with Crescendo in the player's hands.
     */
    private static ItemStack findCrescendoCrossbow(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ModifiableCrossbowItem
                    && stack.is(TinkerTags.Items.MODIFIABLE)
                    && getCrescendoLevel(stack) > 0) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Looks up Apotheosis's Crescendo enchantment level on the weapon.
     * Returns 0 if Apotheosis isn't installed or enchant not present.
     */
    @SuppressWarnings("removal")
    private static int getCrescendoLevel(ItemStack weapon) {
        if (!crescendoResolved) {
            crescendoResolved = true;
            cachedCrescendo = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation("apotheosis", "crescendo"));
        }
        if (cachedCrescendo == null) return 0;
        return EnchantmentHelper.getTagEnchantmentLevel(cachedCrescendo, weapon);
    }
}
