package t.tinkerswithenchants.handler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.tconstruct.common.TinkerTags;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Applies vanilla ranged and fishing enchantments to TConstruct projectiles.
 *
 * <p>Covers: Power, Flame, Punch, Piercing, Infinity, Lure, Luck of the Sea.</p>
 *
 * <p>Fishing fields ({@code luck}, {@code lureSpeed}) on {@link FishingHook}
 * are {@code private final}, so normal reflection cannot modify them in Java 17.
 * We use {@link sun.misc.Unsafe} to bypass the final restriction.</p>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProjectileEnchantHandler {

    private static final Logger LOGGER = LogManager.getLogger("TinkersWithEnchants");

    // Unsafe + field offsets for FishingHook's final int fields
    private static Unsafe theUnsafe;
    private static long fishLuckOffset = -1;
    private static long fishLureOffset = -1;
    private static boolean fishFieldsResolved = false;
    private static boolean fishFieldsFailed = false;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (entity instanceof AbstractArrow arrow) {
            handleArrow(arrow);
        } else if (entity instanceof FishingHook hook) {
            handleFishingHook(hook);
        }
    }

    /**
     * Applies Power, Flame, Punch, Piercing, and Infinity to arrows.
     */
    private static void handleArrow(AbstractArrow arrow) {
        // Skip multishot clones — they already inherit all effects
        if (arrow.getPersistentData().getBoolean("twe_multishot_clone")) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        ItemStack weapon = findRangedWeapon(shooter);
        if (weapon.isEmpty()) return;

        // Power
        int power = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.POWER_ARROWS, weapon);
        if (power > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        }

        // Flame
        int flame = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FLAMING_ARROWS, weapon);
        if (flame > 0 && !arrow.isOnFire()) {
            arrow.setSecondsOnFire(100);
        }

        // Punch
        int punch = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.PUNCH_ARROWS, weapon);
        if (punch > 0) {
            arrow.setKnockback(punch);
        }

        // Piercing
        int piercing = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.PIERCING, weapon);
        if (piercing > 0) {
            arrow.setPierceLevel((byte) piercing);
        }

        // Infinity / Endless Quiver — prevent pickup AND restore consumed ammo
        int infinity = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon);
        boolean hasEndlessQuiver = getEndlessQuiverLevel(weapon) > 0;

        if ((infinity > 0 || hasEndlessQuiver) && shooter instanceof Player player) {
            boolean isClone = arrow.getPersistentData().getBoolean("twe_multishot_clone");

            if (infinity > 0) {
                // Vanilla Infinity: standard arrows only
                ItemStack ammoType = player.getProjectile(weapon);
                boolean isStandardArrow = !ammoType.isEmpty() && ammoType.is(Items.ARROW);

                if (isStandardArrow) {
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    if (!isClone && !player.getAbilities().instabuild) {
                        player.getInventory().add(new ItemStack(Items.ARROW));
                    }
                }
            }

            if (hasEndlessQuiver) {
                // Apotheosis Endless Quiver: ALL arrow types
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                if (!isClone && !player.getAbilities().instabuild) {
                    // Give back whatever arrow type was consumed
                    ItemStack ammoType = player.getProjectile(weapon);
                    if (!ammoType.isEmpty()) {
                        player.getInventory().add(ammoType.copyWithCount(1));
                    } else {
                        // Fallback: standard arrow
                        player.getInventory().add(new ItemStack(Items.ARROW));
                    }
                }
            }
        }
    }

    /**
     * Applies Lure and Luck of the Sea to TConstruct fishing hooks.
     * Uses {@link Unsafe} because {@code luck} and {@code lureSpeed} are final.
     */
    private static void handleFishingHook(FishingHook hook) {
        if (fishFieldsFailed) return;

        Entity owner = hook.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        ItemStack weapon = findFishingRod(shooter);
        if (weapon.isEmpty()) return;

        int lure = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FISHING_SPEED, weapon);
        int luck = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.FISHING_LUCK, weapon);
        if (lure <= 0 && luck <= 0) return;

        try {
            resolveFishingFields();
            if (fishFieldsFailed) return;

            if (luck > 0) {
                int current = theUnsafe.getInt(hook, fishLuckOffset);
                theUnsafe.putInt(hook, fishLuckOffset, current + luck);
            }
            if (lure > 0) {
                int current = theUnsafe.getInt(hook, fishLureOffset);
                theUnsafe.putInt(hook, fishLureOffset, current + lure);
            }
        } catch (Exception e) {
            LOGGER.warn("TWE: Failed to apply fishing enchantments", e);
            fishFieldsFailed = true;
        }
    }

    /**
     * Resolves Unsafe instance and field offsets for FishingHook's final fields.
     */
    private static void resolveFishingFields() {
        if (fishFieldsResolved) return;
        fishFieldsResolved = true;

        try {
            // Get Unsafe instance
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            theUnsafe = (Unsafe) unsafeField.get(null);

            // Strategy 1: Mojang mapped names
            try {
                Field luckField = FishingHook.class.getDeclaredField("luck");
                Field lureField = FishingHook.class.getDeclaredField("lureSpeed");
                fishLuckOffset = theUnsafe.objectFieldOffset(luckField);
                fishLureOffset = theUnsafe.objectFieldOffset(lureField);
                LOGGER.debug("TWE: Resolved fishing fields via Mojang names (luck={}, lure={})",
                        fishLuckOffset, fishLureOffset);
                return;
            } catch (NoSuchFieldException ignored) {}

            // Strategy 2: scan for last two private non-static final int fields
            Field lastFinalInt = null;
            Field secondLastFinalInt = null;
            for (Field f : FishingHook.class.getDeclaredFields()) {
                if (f.getType() == int.class
                        && java.lang.reflect.Modifier.isPrivate(f.getModifiers())
                        && java.lang.reflect.Modifier.isFinal(f.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    secondLastFinalInt = lastFinalInt;
                    lastFinalInt = f;
                }
            }
            if (secondLastFinalInt != null && lastFinalInt != null) {
                fishLuckOffset = theUnsafe.objectFieldOffset(secondLastFinalInt);
                fishLureOffset = theUnsafe.objectFieldOffset(lastFinalInt);
                LOGGER.debug("TWE: Resolved fishing fields via scan: {} (luck), {} (lure)",
                        secondLastFinalInt.getName(), lastFinalInt.getName());
                return;
            }

            LOGGER.error("TWE: Could not locate FishingHook luck/lure fields");
            fishFieldsFailed = true;
        } catch (Exception e) {
            LOGGER.error("TWE: Failed to initialize Unsafe for fishing fields", e);
            fishFieldsFailed = true;
        }
    }

    private static ItemStack findRangedWeapon(LivingEntity shooter) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (mainHand.is(TinkerTags.Items.MODIFIABLE) && mainHand.is(TinkerTags.Items.RANGED)) {
            return mainHand;
        }
        ItemStack offHand = shooter.getOffhandItem();
        if (offHand.is(TinkerTags.Items.MODIFIABLE) && offHand.is(TinkerTags.Items.RANGED)) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findFishingRod(LivingEntity shooter) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (mainHand.is(TinkerTags.Items.MODIFIABLE)
                && mainHand.canPerformAction(ToolActions.FISHING_ROD_CAST)) {
            return mainHand;
        }
        ItemStack offHand = shooter.getOffhandItem();
        if (offHand.is(TinkerTags.Items.MODIFIABLE)
                && offHand.canPerformAction(ToolActions.FISHING_ROD_CAST)) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    // ── Apotheosis: Endless Quiver ──────────────────────────────────────
    private static Enchantment cachedEndlessQuiver;
    private static boolean endlessQuiverResolved = false;

    /**
     * Looks up Apotheosis's Endless Quiver enchantment level on the weapon.
     * Returns 0 if Apotheosis isn't installed or enchant not present.
     */
    @SuppressWarnings("removal")
    private static int getEndlessQuiverLevel(ItemStack weapon) {
        if (!endlessQuiverResolved) {
            endlessQuiverResolved = true;
            cachedEndlessQuiver = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation("apotheosis", "endless_quiver"));
        }
        if (cachedEndlessQuiver == null) return 0;
        return EnchantmentHelper.getTagEnchantmentLevel(cachedEndlessQuiver, weapon);
    }
}
