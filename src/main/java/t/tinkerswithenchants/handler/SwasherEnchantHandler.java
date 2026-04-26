package t.tinkerswithenchants.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.tools.entity.FluidEffectProjectile;

/**
 * Applies vanilla crossbow enchantments to TConstruct fluid-launcher
 * projectiles (the swasher's "spit" projectiles, plus any addon tool
 * that fires {@link FluidEffectProjectile}).
 *
 * <p>{@link FluidEffectProjectile} extends {@code Projectile} (not
 * {@code AbstractArrow}) and the {@code SpittingModule} that fires it does
 * not consult vanilla enchantment levels, so Multishot, Piercing, and
 * similar enchants would otherwise be cosmetic. This handler intercepts
 * spawn events and applies the missing effects.</p>
 *
 * <ul>
 *   <li><b>Multishot</b>: spawns 2 angular clones per Multishot level,
 *       matching the existing {@link MultishotHandler} pattern for arrows
 *       (clone full state via NBT, rotate velocity ±10° per pair, mark
 *       clones to prevent recursion).</li>
 *   <li><b>Piercing</b>: boosts the projectile's fluid amount via a
 *       multiplier ({@code amount × (1 + level)}) by mutating the
 *       FluidStack reference returned by {@code getFluid()} in place.
 *       This bypasses Mojang's {@link net.minecraft.network.syncher.SynchedEntityData#set}
 *       equality short-circuit, which silently drops {@code setFluid}
 *       calls when Forge's {@code FluidStack.equals} doesn't see the
 *       amount difference. The vanilla swasher's pierce-via-fluid-budget
 *       mechanic then naturally extends pierce count, matching vanilla
 *       Piercing semantics (Piercing N pierces N+1 entities) at full
 *       effect scale.</li>
 * </ul>
 *
 * <p>Quick Charge is handled in {@link QuickChargeHandler} via the
 * RANGED_QUICK_CHARGE tag (which the swasher is in).</p>
 */
@Mod.EventBusSubscriber(modid = "tinkerswithenchants", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SwasherEnchantHandler {

    /** Same tag used by {@link MultishotHandler} so other handlers
     *  (and re-entry into this one) skip clones. */
    private static final String CLONE_TAG = "twe_multishot_clone";

    private static final Logger LOGGER = LogManager.getLogger("TinkersWithEnchants");

    /** Diagnostic switch — flip to {@code true} to enable detailed
     *  TWE-DIAG logging for every spit projectile (spawn boost, multi-hit
     *  pre-tick pass, per-tick lifecycle, impacts, removal). Useful when
     *  debugging Piercing/Multishot behavior. Off in normal builds — the
     *  short-circuited log calls JIT-eliminate to nothing. */
    private static final boolean DIAG_PIERCING = false;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof FluidEffectProjectile spit)) return;

        // Track active spit count for the multi-hit pre-tick pass throttle.
        // Counts ALL spits (including multishot clones) since each one
        // independently runs its own per-tick entity scan when multi-hit
        // is enabled.
        ACTIVE_SPIT_COUNT.incrementAndGet();

        // Skip clones to prevent recursive multishot and double-piercing.
        CompoundTag projTag = spit.getPersistentData();
        if (projTag.getBoolean(CLONE_TAG)) return;

        // Find the firing weapon. We're already filtered to FluidEffectProjectile
        // spawns, which means the shooter just fired a fluid launcher — so any
        // TC item in their hand carrying the relevant enchant is the firer
        // (same pattern as MultishotHandler for arrows). This avoids needing
        // a LAUNCHERS tag, which doesn't exist in TC 3.9 datapacks.
        Entity owner = spit.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        // ── Piercing: boost fluid budget for more pierces ───────────────
        // Multiplier formula: fluid.amount × (1 + piercing). Matches vanilla
        // Piercing semantics where Piercing N pierces N+1 entities, and keeps
        // each pierce at full effect scale.
        //
        // IMPORTANT: we MUTATE the FluidStack in place rather than copying
        // and calling setFluid. Mojang's SynchedEntityData.set() short-
        // circuits when ObjectUtils.notEqual returns false, and Forge's
        // FluidStack.equals does not strictly compare amount — so a boosted
        // copy with the same fluid type would be considered equal to the
        // original and silently dropped. Since getFluid returns the stored
        // reference directly, mutating its amount changes the stored value
        // without going through that check. Server-side hit logic reads
        // through getFluid so it sees the boosted amount immediately.
        ItemStack piercingWeapon = findWeaponWith(shooter, Enchantments.PIERCING);
        if (DIAG_PIERCING) {
            LOGGER.info("TWE-DIAG spit spawned: shooter={}, mainHand={}, offHand={}",
                    shooter.getName().getString(),
                    shooter.getMainHandItem().getItem(),
                    shooter.getOffhandItem().getItem());
            LOGGER.info("TWE-DIAG piercingWeapon empty? {}", piercingWeapon.isEmpty());
        }
        if (!piercingWeapon.isEmpty()) {
            int piercing = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.PIERCING, piercingWeapon);
            if (DIAG_PIERCING) {
                LOGGER.info("TWE-DIAG piercing level read from weapon: {}", piercing);
            }
            if (piercing > 0) {
                FluidStack fluid = spit.getFluid();
                if (DIAG_PIERCING) {
                    LOGGER.info("TWE-DIAG spit fluid before boost: {} mB of {}",
                            fluid.getAmount(),
                            fluid.isEmpty() ? "(empty)" : fluid.getFluid());
                }
                if (!fluid.isEmpty()) {
                    int newAmount = fluid.getAmount() * (1 + piercing);
                    fluid.setAmount(newAmount);
                    // No setFluid call — fluid is the actual stored reference,
                    // mutating its amount mutates the entity's data directly.
                    if (DIAG_PIERCING) {
                        FluidStack readback = spit.getFluid();
                        LOGGER.info("TWE-DIAG boost applied (in-place): target={} mB, readback={} mB",
                                newAmount, readback.getAmount());
                    }
                }
            }
        }

        // ── Multishot: spawn angular clones ─────────────────────────────
        ItemStack multishotWeapon = findWeaponWith(shooter, Enchantments.MULTISHOT);
        if (multishotWeapon.isEmpty()) return;
        int multishot = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.MULTISHOT, multishotWeapon);
        if (multishot <= 0) return;

        // Save full state so clones inherit fluid (now Piercing-boosted),
        // power, knockback, and any modifier capabilities.
        CompoundTag originalNbt = new CompoundTag();
        spit.save(originalNbt);

        Level level = event.getLevel();
        for (int i = 0; i < multishot; i++) {
            float angleOffset = 10.0F * (i + 1);
            spawnClone(level, spit, originalNbt, owner, -angleOffset);
            spawnClone(level, spit, originalNbt, owner, angleOffset);
        }
    }

    /**
     * Creates a clone of the original spit projectile from its saved NBT,
     * rotates its velocity by the given angle, and spawns it.
     */
    private static void spawnClone(Level level, FluidEffectProjectile original,
                                   CompoundTag originalNbt, Entity owner, float angleDegrees) {
        EntityType<?> type = original.getType();
        Entity rawClone = type.create(level);
        if (!(rawClone instanceof FluidEffectProjectile clone)) return;

        // Load full state from original (fluid, power, knockback, capabilities)
        clone.load(originalNbt);

        // Generate new UUID so the clone is a distinct entity
        clone.setUUID(Mth.createInsecureUUID());

        // Mark as clone to prevent recursive cloning
        clone.getPersistentData().putBoolean(CLONE_TAG, true);

        // Set position to same as original
        clone.setPos(original.getX(), original.getY(), original.getZ());

        // Rotate the velocity vector around the Y axis (matches MultishotHandler)
        Vec3 velocity = original.getDeltaMovement();
        double angleRad = Math.toRadians(angleDegrees);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double newX = velocity.x * cos - velocity.z * sin;
        double newZ = velocity.x * sin + velocity.z * cos;
        clone.setDeltaMovement(newX, velocity.y, newZ);

        // Preserve owner for kill credit, advancements, stats
        clone.setOwner(owner);

        level.addFreshEntity(clone);
    }

    /**
     * Finds a TC item in the shooter's hands that carries the given
     * enchantment. We don't need to verify it's a launcher — the calling
     * event is already filtered to {@code FluidEffectProjectile} spawns,
     * so the shooter just fired a fluid launcher by definition.
     *
     * <p>Mirrors {@link MultishotHandler#findMultishotWeapon} but
     * parameterized by enchantment so we can use it for both Multishot
     * and Piercing without duplicating the hand-walk.</p>
     *
     * <p>Avoids any tag checks beyond {@code MODIFIABLE} (which has been
     * in TC since the 3.x line started), so this works on TC 3.9 where
     * the {@code modifiable/ranged/launcher} tag doesn't exist.</p>
     */
    private static ItemStack findWeaponWith(LivingEntity shooter, Enchantment enchant) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (mainHand.is(TinkerTags.Items.MODIFIABLE)
                && EnchantmentHelper.getTagEnchantmentLevel(enchant, mainHand) > 0) {
            return mainHand;
        }
        ItemStack offHand = shooter.getOffhandItem();
        if (offHand.is(TinkerTags.Items.MODIFIABLE)
                && EnchantmentHelper.getTagEnchantmentLevel(enchant, offHand) > 0) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    /** NBT key on the spit projectile holding the list of entity IDs it has
     *  already pierced. Prevents re-hits while the projectile is still
     *  inside the bounding box of an entity it just hit, and also gates
     *  the multi-hit mixin's iteration. Public so the mixin can read it. */
    public static final String PIERCED_TAG = "twe_pierced_ids";

    /** Number of {@link FluidEffectProjectile} entities currently alive on
     *  the server side, across all levels. Used by the multi-hit mixin to
     *  fall back to vanilla single-hit-per-tick behavior when the world is
     *  saturated with spits (mob farms, dispenser arrays, multishot spam),
     *  preventing the per-projectile entity scan from compounding into
     *  visible TPS loss. */
    public static final java.util.concurrent.atomic.AtomicInteger ACTIVE_SPIT_COUNT
            = new java.util.concurrent.atomic.AtomicInteger(0);

    /** When the active-spit count exceeds this number, the multi-hit mixin
     *  becomes a no-op for that tick and projectiles fall back to vanilla
     *  one-entity-per-tick hits. Chosen empirically to allow normal
     *  gameplay (player rapid-fire, modest multishot) at full quality
     *  while degrading gracefully under farm-scale loads. Tune via this
     *  constant if needed; no config UI for now. */
    public static final int MULTIHIT_THRESHOLD = 64;

    /**
     * Tracks pierced entities on every {@link FluidEffectProjectile} so
     * that once the spit has hit an entity, the projectile's velocity can
     * carry it past without re-hitting the same target every tick.
     *
     * <p>Without this, even with our Piercing fluid boost, the projectile
     * gets stuck inside mob #1 — vanilla {@code AbstractArrow} avoids this
     * via {@code piercingIgnoreEntityIds}, but {@code FluidEffectProjectile}
     * extends raw {@code Projectile} and inherits no such tracking.</p>
     *
     * <p>This runs for ALL fluid spits, not just piercing-enchanted ones.
     * For non-piercing spits the existing fluid-shrink-on-hit mechanic
     * discards the projectile after the first hit anyway, so this listener
     * is a no-op in that case.</p>
     *
     * <p>Block hits are ignored — they always discard the projectile via
     * onHitBlock, which is correct behavior.</p>
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof FluidEffectProjectile spit)) return;

        // Diagnostic: log every impact, including block hits
        if (DIAG_PIERCING) {
            String side = spit.level().isClientSide() ? "CLIENT" : "SERVER";
            String targetDesc;
            if (event.getRayTraceResult() instanceof EntityHitResult ehr) {
                Entity t = ehr.getEntity();
                targetDesc = "ENTITY id=" + t.getId() + " type=" + t.getType().getDescriptionId();
            } else {
                targetDesc = "BLOCK at " + event.getRayTraceResult().getLocation();
            }
            FluidStack fluidNow = spit.getFluid();
            int spitId = spit.getId();
            LOGGER.info("TWE-DIAG [{}] impact: spitId={}, target={}, fluid={}mB, pos={}",
                    side, spitId, targetDesc, fluidNow.getAmount(), spit.position());
        }

        if (!(event.getRayTraceResult() instanceof EntityHitResult ehr)) return;

        Entity target = ehr.getEntity();
        int targetId = target.getId();
        CompoundTag data = spit.getPersistentData();
        ListTag pierced = data.getList(PIERCED_TAG, Tag.TAG_INT);

        // Already pierced? Skip the entity hit so the projectile can move past.
        // We use SKIP_ENTITY (not the deprecated setCanceled, which routes to
        // STOP_AT_CURRENT_PROGRESS — that means "stop the projectile here as if
        // it stuck", the opposite of what we want for piercing).
        for (int i = 0; i < pierced.size(); i++) {
            if (pierced.getInt(i) == targetId) {
                if (DIAG_PIERCING) {
                    LOGGER.info("TWE-DIAG impact CANCELED — entity {} already in pierced list (size={})",
                            targetId, pierced.size());
                }
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
                return;
            }
        }

        // First hit on this entity — record it and let the impact proceed
        // normally. The fluid effect will apply, fluid will shrink, and on
        // subsequent ticks while the projectile is still inside this entity's
        // hitbox, we'll cancel the duplicate impacts.
        pierced.add(IntTag.valueOf(targetId));
        data.put(PIERCED_TAG, pierced);
        if (DIAG_PIERCING) {
            LOGGER.info("TWE-DIAG impact ALLOWED — added entity {} to pierced list (size now={})",
                    targetId, pierced.size());
        }
    }

    /**
     * Diagnostic: log when a {@link FluidEffectProjectile} is removed from
     * the level so we can see whether it discarded after a hit, fell out of
     * the world, or otherwise vanished. Pure logging, no behavior change.
     */
    @SubscribeEvent
    public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof FluidEffectProjectile spit)) return;

        // Decrement the active-spit counter on the server side only, mirroring
        // the increment in onEntityJoinLevel. (Client-side mirrors don't count.)
        if (!event.getLevel().isClientSide()) {
            ACTIVE_SPIT_COUNT.decrementAndGet();
        }

        if (!DIAG_PIERCING) return;
        FluidStack fluid = spit.getFluid();
        String side = event.getLevel().isClientSide() ? "CLIENT" : "SERVER";
        LOGGER.info("TWE-DIAG [{}] spit removed: id={}, removalReason={}, fluid={}mB, pos={}, age={}",
                side,
                spit.getId(),
                spit.getRemovalReason() == null ? "null" : spit.getRemovalReason().name(),
                fluid.isEmpty() ? 0 : fluid.getAmount(),
                spit.position(),
                spit.tickCount);
    }

    /**
     * Multi-hit pre-tick pass for fluid-spit projectiles. Runs on
     * {@link net.minecraftforge.event.TickEvent.Phase#START START} of each
     * server level tick — <em>before</em> any entity in that level ticks —
     * which lets us iterate the projectile's upcoming move segment and
     * apply fluid effects to every entity in path order, not just the
     * closest one (which is all the natural per-tick hit detection finds).
     *
     * <p>Skipped entities go into the projectile's pierced list, so when
     * the natural tick fires {@link net.minecraftforge.event.entity.ProjectileImpactEvent}
     * for the closest entity, our impact-event listener sees it as already
     * pierced and cancels via {@code SKIP_ENTITY}, and the projectile
     * naturally moves past. The closest entity is intentionally left for
     * the natural tick to handle so that knockback and other side effects
     * apply on at least one entity per shot.</p>
     *
     * <h3>Throttle</h3>
     * Skipped when active spit count exceeds
     * {@link #MULTIHIT_THRESHOLD} — the world reverts to vanilla one-hit-
     * per-tick behavior under farm-scale spit loads, preventing
     * O(spits × entities-in-segment) work from compounding into TPS loss.
     *
     * <h3>Why this lives in an event instead of a Mixin</h3>
     * A Mixin into {@code FluidEffectProjectile.tick} would let us inject
     * directly into the per-projectile per-tick loop, but bytecode-level
     * targeting of inherited methods on third-party classes proved fragile
     * across mappings configurations. A pre-tick event handler runs on the
     * same game tick at slightly different timing and produces the same
     * net result with no bytecode patching.
     */
    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        if (!(event.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        // Throttle: when the world is saturated with spits (mob farms,
        // dispenser arrays, multishot spam), skip multi-hit entirely and
        // let projectiles fall back to vanilla single-hit-per-tick.
        if (ACTIVE_SPIT_COUNT.get() > MULTIHIT_THRESHOLD) return;

        for (Entity e : serverLevel.getAllEntities()) {
            if (!(e instanceof FluidEffectProjectile spit)) continue;
            if (spit.isRemoved()) continue;

            FluidStack fluid = spit.getFluid();
            if (fluid.isEmpty()) continue;

            Vec3 startPos = spit.position();
            Vec3 velocity = spit.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0e-6) continue;
            Vec3 endPos = startPos.add(velocity);

            // Build pierced-id set once
            CompoundTag data = spit.getPersistentData();
            ListTag pierced = data.getList(PIERCED_TAG, Tag.TAG_INT);
            java.util.Set<Integer> piercedIds = new java.util.HashSet<>(pierced.size() * 2);
            for (int i = 0; i < pierced.size(); i++) {
                piercedIds.add(pierced.getInt(i));
            }

            // Find candidate entities in segment AABB
            Entity ownerEntity = spit.getOwner();
            net.minecraft.world.phys.AABB box = spit.getBoundingBox().expandTowards(velocity).inflate(1.0);
            java.util.List<net.minecraft.world.phys.EntityHitResult> hits = new java.util.ArrayList<>();
            for (Entity candidate : serverLevel.getEntities(spit, box)) {
                if (candidate == ownerEntity) continue;
                if (!candidate.isAlive() || !candidate.isPickable() || candidate.isSpectator()) continue;
                if (piercedIds.contains(candidate.getId())) continue;

                net.minecraft.world.phys.AABB entityBox = candidate.getBoundingBox().inflate(0.3);
                java.util.Optional<Vec3> intersection = entityBox.clip(startPos, endPos);
                if (intersection.isEmpty()) continue;
                hits.add(new net.minecraft.world.phys.EntityHitResult(candidate, intersection.get()));
            }

            if (hits.size() < 2) continue;  // Only one entity → natural tick handles it

            // Sort by distance from spit's start so consumption order is correct
            hits.sort(java.util.Comparator.comparingDouble(h -> h.getLocation().distanceToSqr(startPos)));

            // Skip the FIRST (closest) entity — let the natural tick hit
            // it so that onHitEntity's full logic (knockback + fluid effect)
            // runs at least once per shot. We process the rest manually.
            slimeknights.tconstruct.library.modifiers.fluid.FluidEffects recipe =
                slimeknights.tconstruct.library.modifiers.fluid.FluidEffectManager.INSTANCE.find(fluid.getFluid());
            if (!recipe.hasEntityEffects()) continue;

            float power = readPower(spit);
            int additionalApplied = 0;
            for (int i = 1; i < hits.size(); i++) {
                if (spit.isRemoved()) break;
                FluidStack current = spit.getFluid();
                if (current.isEmpty()) break;

                net.minecraft.world.phys.EntityHitResult hit = hits.get(i);

                // Pre-add to pierced list so the natural tick (and the
                // impact-event listener) skip duplicate hits on this entity.
                pierced.add(IntTag.valueOf(hit.getEntity().getId()));

                LivingEntity ownerLiving = ownerEntity instanceof LivingEntity le ? le : null;
                @SuppressWarnings("removal")
                slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext.Entity context =
                    new slimeknights.tconstruct.library.modifiers.fluid.FluidEffectContext.Entity(
                        serverLevel, ownerLiving, spit, hit.getEntity(), hit.getLocation()
                    );
                int consumed = recipe.applyToEntity(current, power, context,
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);

                int remaining = current.getAmount() - consumed;
                if (remaining <= 0) {
                    current.setAmount(0);
                    spit.discard();
                    break;
                } else {
                    current.setAmount(remaining);
                }
                additionalApplied++;
            }

            if (additionalApplied > 0) {
                data.put(PIERCED_TAG, pierced);
                if (DIAG_PIERCING) {
                    LOGGER.info("TWE-DIAG [SERVER] multi-hit pre-tick: spitId={}, additional pierces={}, fluid after={}mB",
                            spit.getId(), additionalApplied,
                            spit.getFluid().isEmpty() ? 0 : spit.getFluid().getAmount());
                }
            }
        }

        // Per-tick lifecycle diagnostic (kept from previous build, shifted
        // to START phase to share the same iteration).
        if (DIAG_PIERCING) {
            for (Entity e : serverLevel.getAllEntities()) {
                if (!(e instanceof FluidEffectProjectile spit)) continue;
                FluidStack fluid = spit.getFluid();
                Vec3 vel = spit.getDeltaMovement();
                int piercedCount = spit.getPersistentData().getList(PIERCED_TAG, Tag.TAG_INT).size();
                LOGGER.info("TWE-DIAG [SERVER] tick spit id={}, age={}, pos={}, vel=({},{},{}), |vel|={}, fluid={}mB, pierced={}",
                        spit.getId(),
                        spit.tickCount,
                        spit.position(),
                        String.format("%.3f", vel.x), String.format("%.3f", vel.y), String.format("%.3f", vel.z),
                        String.format("%.3f", vel.length()),
                        fluid.isEmpty() ? 0 : fluid.getAmount(),
                        piercedCount);
            }
        }
    }

    /**
     * Read the projectile's {@code power} field via reflection. Avoids a
     * Mixin-style Shadow which has been flaky in this project's mapping
     * configuration. Falls back to 1.0f on any failure (matches default).
     */
    private static float readPower(FluidEffectProjectile spit) {
        try {
            java.lang.reflect.Field f = FluidEffectProjectile.class.getDeclaredField("power");
            f.setAccessible(true);
            return f.getFloat(spit);
        } catch (Exception ex) {
            return 1.0f;
        }
    }
}
