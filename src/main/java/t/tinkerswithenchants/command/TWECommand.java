package t.tinkerswithenchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import t.tinkerswithenchants.util.TWEEnchantUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides {@code /twe tinkertype} command for debugging tool categorization.
 */
public class TWECommand {

    /** All ToolActions we check for categorization */
    private static final ToolAction[] CHECKED_ACTIONS = {
            ToolActions.SWORD_DIG, ToolActions.SWORD_SWEEP,
            ToolActions.AXE_DIG, ToolActions.AXE_STRIP,
            ToolActions.PICKAXE_DIG, ToolActions.SHOVEL_DIG,
            ToolActions.HOE_DIG, ToolActions.SHEARS_DIG,
            ToolActions.SHEARS_HARVEST, ToolActions.FISHING_ROD_CAST,
            ToolActions.SHIELD_BLOCK
    };

    /** TConstruct tags we care about */
    private static final String[] CHECKED_TAGS = {
            "tconstruct:modifiable",
            "tconstruct:modifiable/melee/primary",
            "tconstruct:modifiable/harvest/primary",
            "tconstruct:modifiable/ranged",
            "tconstruct:modifiable/ranged/bows",
            "tconstruct:modifiable/ranged/crossbows",
            "tconstruct:modifiable/ranged/longbows",
            "tconstruct:modifiable/fishing_rods",
            "tconstruct:modifiable/armor",
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("twe")
                        .then(Commands.literal("tinkertype")
                                .executes(TWECommand::showHeldTool)
                                .then(Commands.argument("option", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("all");
                                            builder.suggest("held");
                                            return builder.buildFuture();
                                        })
                                        .executes(TWECommand::handleOption)))
                        .then(Commands.literal("info")
                                .then(Commands.literal("enchantability")
                                        .executes(TWECommand::infoEnchantability))
                                .then(Commands.literal("unbreaking")
                                        .executes(TWECommand::infoUnbreaking))
                                .then(Commands.literal("sweep")
                                        .executes(TWECommand::infoSweep))
                                .then(Commands.literal("enchants")
                                        .executes(TWECommand::infoEnchants)))
        );
    }

    /**
     * /twe tinkertype — shows info about the held tool
     */
    private static int showHeldTool(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Hold a tool in your main hand"));
            return 0;
        }

        List<Component> lines = buildToolReport(stack);
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    /**
     * /twe tinkertype all — dumps all registered TConstruct items to file
     */
    private static int handleOption(CommandContext<CommandSourceStack> ctx) {
        String option = StringArgumentType.getString(ctx, "option");
        CommandSourceStack source = ctx.getSource();

        if ("all".equalsIgnoreCase(option)) {
            return dumpAll(source);
        }
        if ("held".equalsIgnoreCase(option)) {
            return showHeldTool(ctx);
        }

        source.sendFailure(Component.literal("Unknown option: " + option + ". Use 'held' or 'all'."));
        return 0;
    }

    private static int dumpAll(CommandSourceStack source) {
        try {
            File configDir = FMLPaths.CONFIGDIR.get().resolve("tinkerswithenchants").toFile();
            if (!configDir.exists()) configDir.mkdirs();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File outFile = new File(configDir, "tool_types_" + timestamp + ".txt");

            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                pw.println("=== Tinkers with Enchants — Tool Type Report ===");
                pw.println("Generated: " + LocalDateTime.now());
                pw.println();

                // ── Pre-compute: all enchantments by mod ──
                Map<String, List<String>> enchantsByMod = new TreeMap<>();
                for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
                    ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(ench);
                    if (rl != null) {
                        enchantsByMod.computeIfAbsent(rl.getNamespace(), k -> new ArrayList<>())
                                .add(rl.toString() + " [" + ench.category.name() + "]");
                    }
                }

                // ── Pre-compute: tool reports + track mapped enchants ──
                Set<Enchantment> mappedEnchants = new HashSet<>();
                List<String[]> toolReports = new ArrayList<>(); // [header, body]
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    if (!(item instanceof IModifiable)) continue;

                    ItemStack stack = new ItemStack(item);
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

                    StringBuilder sb = new StringBuilder();
                    sb.append("────────────────────────────────────────\n");
                    sb.append("Item: ").append(id).append("\n");
                    for (String line : buildToolReportText(stack)) {
                        sb.append("  ").append(line).append("\n");
                    }

                    for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
                        if (TWEEnchantUtil.canEnchant(stack, ench)) {
                            mappedEnchants.add(ench);
                        }
                    }
                    toolReports.add(new String[]{String.valueOf(id), sb.toString()});
                }

                // ── Pre-compute: unmapped enchantments ──
                List<String> unmapped = new ArrayList<>();
                for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
                    if (!mappedEnchants.contains(ench)) {
                        ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(ench);
                        if (rl != null) {
                            unmapped.add(rl + " [" + ench.category.name() + "]");
                        }
                    }
                }
                Collections.sort(unmapped);

                // ══════════ Write: Enchantments by mod ══════════
                pw.println("════════════════════════════════════════");
                pw.println("  ALL REGISTERED ENCHANTMENTS (by mod)");
                pw.println("════════════════════════════════════════");
                for (Map.Entry<String, List<String>> entry : enchantsByMod.entrySet()) {
                    pw.println("  " + entry.getKey() + ":");
                    Collections.sort(entry.getValue());
                    for (String e : entry.getValue()) {
                        pw.println("    " + e);
                    }
                }
                pw.println();

                // ══════════ Write: Unmapped enchantments ══════════
                pw.println("════════════════════════════════════════");
                pw.println("  UNMAPPED ENCHANTMENTS");
                pw.println("  (not compatible with any TC item)");
                pw.println("════════════════════════════════════════");
                if (unmapped.isEmpty()) {
                    pw.println("  (none — all enchantments mapped to at least one TC item)");
                } else {
                    for (String e : unmapped) {
                        pw.println("  " + e);
                    }
                }
                pw.println();

                // ══════════ Write: Tool reports ══════════
                for (String[] report : toolReports) {
                    pw.print(report[1]);
                    pw.println();
                }

                pw.println("=== Total: " + toolReports.size() + " TConstruct items, "
                        + mappedEnchants.size() + " mapped enchantments, "
                        + unmapped.size() + " unmapped ===");
            }

            String folderPath = configDir.getAbsolutePath();
            source.sendSuccess(() -> Component.literal("Dumped " + outFile.getName() + " to ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(folderPath)
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, folderPath)))),
                    true);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Builds a chat-formatted report for a single tool stack.
     */
    private static List<Component> buildToolReport(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

        lines.add(Component.literal("══ TWE Tool Report ══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Item: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(id)).withStyle(ChatFormatting.WHITE)));

        // Tool stats (if TConstruct tool)
        if (stack.getItem() instanceof IModifiable) {
            try {
                ToolStack tool = ToolStack.from(stack);
                lines.add(Component.literal("Durability: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(tool.getStats().getInt(ToolStats.DURABILITY)))
                                .withStyle(ChatFormatting.WHITE)));
                float attack = tool.getStats().get(ToolStats.ATTACK_DAMAGE);
                float speed = tool.getStats().get(ToolStats.ATTACK_SPEED);
                lines.add(Component.literal("Attack: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.1f dmg / %.1f speed", attack, speed))
                                .withStyle(ChatFormatting.WHITE)));
            } catch (Exception ignored) {}
        }

        // ToolActions
        List<String> actions = new ArrayList<>();
        for (ToolAction action : CHECKED_ACTIONS) {
            if (stack.canPerformAction(action)) {
                actions.add(action.name());
            }
        }
        lines.add(Component.literal("ToolActions: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(actions.isEmpty() ? "(none)" : String.join(", ", actions))
                        .withStyle(ChatFormatting.AQUA)));

        // Tags
        List<String> tags = new ArrayList<>();
        for (String tagName : CHECKED_TAGS) {

            TagKey<Item> tag = ItemTags.create(ResourceLocation.parse(tagName));
            if (stack.is(tag)) {
                tags.add(tagName.replace("tconstruct:", ""));
            }
        }
        lines.add(Component.literal("Tags: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tags.isEmpty() ? "(none)" : String.join(", ", tags))
                        .withStyle(ChatFormatting.YELLOW)));

        // NBT enchantments on the actual held item
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (!enchants.isEmpty()) {
            List<String> enchNames = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(e.getKey());
                enchNames.add(rl + " " + e.getValue());
            }
            lines.add(Component.literal("Enchantments: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", enchNames))
                            .withStyle(ChatFormatting.LIGHT_PURPLE)));
        }

        // TWE enchantment categories
        Set<EnchantmentCategory> cats = TWEEnchantUtil.getCategories(stack);
        List<String> catNames = new ArrayList<>();
        for (EnchantmentCategory cat : cats) {
            catNames.add(cat.name());
        }
        lines.add(Component.literal("TWE Categories: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(catNames.isEmpty() ? "(none)" : String.join(", ", catNames))
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));

        // Compatible enchantments
        List<String> compatible = new ArrayList<>();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (TWEEnchantUtil.canEnchant(stack, ench)) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(ench);
                compatible.add(rl != null ? rl.getPath() : "?");
            }
        }
        lines.add(Component.literal("Compatible Enchants: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(compatible.isEmpty() ? "(none)" : String.join(", ", compatible))
                        .withStyle(ChatFormatting.WHITE)));

        return lines;
    }

    /**
     * Builds a plain-text report for file dump.
     */
    private static List<String> buildToolReportText(ItemStack stack) {
        List<String> lines = new ArrayList<>();

        // ToolActions
        List<String> actions = new ArrayList<>();
        for (ToolAction action : CHECKED_ACTIONS) {
            if (stack.canPerformAction(action)) actions.add(action.name());
        }
        lines.add("ToolActions: " + (actions.isEmpty() ? "(none)" : String.join(", ", actions)));

        // Tags
        List<String> tags = new ArrayList<>();
        for (String tagName : CHECKED_TAGS) {

            TagKey<Item> tag = ItemTags.create(ResourceLocation.parse(tagName));
            if (stack.is(tag)) tags.add(tagName.replace("tconstruct:", ""));
        }
        lines.add("Tags: " + (tags.isEmpty() ? "(none)" : String.join(", ", tags)));

        // TWE categories
        Set<EnchantmentCategory> cats = TWEEnchantUtil.getCategories(stack);
        List<String> catNames = new ArrayList<>();
        for (EnchantmentCategory cat : cats) catNames.add(cat.name());
        lines.add("TWE Categories: " + (catNames.isEmpty() ? "(none)" : String.join(", ", catNames)));

        // Compatible enchantments
        List<String> compatible = new ArrayList<>();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (TWEEnchantUtil.canEnchant(stack, ench)) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(ench);
                compatible.add(rl != null ? rl.toString() : "?");
            }
        }
        lines.add("Compatible: " + (compatible.isEmpty() ? "(none)" : String.join(", ", compatible)));

        return lines;
    }

    // ── Info commands ───────────────────────────────────────────────────

    private static ServerPlayer requirePlayerWithTool(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return null;
        }
        if (player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold a tool in your main hand"));
            return null;
        }
        if (!(player.getMainHandItem().getItem() instanceof IModifiable)) {
            ctx.getSource().sendFailure(Component.literal("Not a TConstruct tool"));
            return null;
        }
        return player;
    }

    /**
     * /twe info enchantability — shows enchantability calculation breakdown
     */
    private static int infoEnchantability(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayerWithTool(ctx);
        if (player == null) return 0;

        CommandSourceStack source = ctx.getSource();
        ItemStack stack = player.getMainHandItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

        source.sendSuccess(() -> Component.literal("══ Enchantability Info ══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Item: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(id)).withStyle(ChatFormatting.WHITE)), false);

        try {
            ToolStack tool = ToolStack.from(stack);
            int durability = tool.getStats().getInt(ToolStats.DURABILITY);
            boolean unbreakable = tool.isUnbreakable();
            boolean canBeDepeleted = stack.getItem().canBeDepleted();

            source.sendSuccess(() -> Component.literal("Durability: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(durability)).withStyle(ChatFormatting.WHITE)), false);
            source.sendSuccess(() -> Component.literal("Unbreakable: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(unbreakable)).withStyle(
                            unbreakable ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Can be depleted: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(canBeDepeleted)).withStyle(
                            canBeDepeleted ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

            // Multiplier source
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            float nbtMultiplier = 0;
            if (tag != null && tag.contains("tic_multipliers")) {
                net.minecraft.nbt.CompoundTag multipliers = tag.getCompound("tic_multipliers");
                if (multipliers.contains("tconstruct:durability")) {
                    nbtMultiplier = multipliers.getFloat("tconstruct:durability");
                }
            }
            final float nbtMult = nbtMultiplier;

            if (nbtMult > 0) {
                source.sendSuccess(() -> Component.literal("Multiplier: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.2f", nbtMult)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" (from NBT)").withStyle(ChatFormatting.DARK_GRAY)), false);
            } else {
                Set<EnchantmentCategory> cats = TWEEnchantUtil.getCategories(stack);
                float estMult = 1.0f;
                if (cats.contains(EnchantmentCategory.ARMOR_CHEST)) estMult = 3.5f;
                else if (cats.contains(EnchantmentCategory.ARMOR_LEGS)) estMult = 3.0f;
                else if (cats.contains(EnchantmentCategory.ARMOR_HEAD)) estMult = 2.0f;
                else if (cats.contains(EnchantmentCategory.ARMOR_FEET)) estMult = 2.0f;
                final float est = estMult;
                source.sendSuccess(() -> Component.literal("Multiplier: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.2f", est)).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (estimated from category)").withStyle(ChatFormatting.DARK_GRAY)), false);
            }

            float effectiveMultiplier = nbtMult > 0 ? nbtMult : 1.0f;
            // Recalculate with category fallback like the actual code does
            if (nbtMult <= 0) {
                Set<EnchantmentCategory> cats = TWEEnchantUtil.getCategories(stack);
                if (cats.contains(EnchantmentCategory.ARMOR_CHEST)) effectiveMultiplier = 3.5f;
                else if (cats.contains(EnchantmentCategory.ARMOR_LEGS)) effectiveMultiplier = 3.0f;
                else if (cats.contains(EnchantmentCategory.ARMOR_HEAD)) effectiveMultiplier = 2.0f;
                else if (cats.contains(EnchantmentCategory.ARMOR_FEET)) effectiveMultiplier = 2.0f;
            }
            float baseDurability = durability / effectiveMultiplier;
            final float baseDur = baseDurability;

            source.sendSuccess(() -> Component.literal("Base durability: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.1f", baseDur)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(" (%d / %.2f)", durability, durability / baseDur))
                            .withStyle(ChatFormatting.DARK_GRAY)), false);

            // Gold check
            boolean hasGold = false;
            try {
                var materials = tool.getMaterials();
                if (materials.size() > 0) {
                    String path = materials.get(0).getId().getPath();
                    hasGold = path.contains("gold");
                }
            } catch (Exception ignored) {}
            final boolean gold = hasGold;

            if (gold) {
                source.sendSuccess(() -> Component.literal("Gold head: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("YES — minimum 22")
                                .withStyle(ChatFormatting.GOLD)), false);
            }

            // Materials list
            try {
                var materials = tool.getMaterials();
                List<String> matNames = new ArrayList<>();
                for (int i = 0; i < materials.size(); i++) {
                    matNames.add(materials.get(i).getId().toString());
                }
                source.sendSuccess(() -> Component.literal("Materials: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.join(", ", matNames))
                                .withStyle(ChatFormatting.WHITE)), false);
            } catch (Exception ignored) {}

            // Final result
            int computed = TWEEnchantUtil.computeEnchantability(stack);
            source.sendSuccess(() -> Component.literal("Enchantability: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(computed))
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    .append(Component.literal(vanillaReference(computed))
                            .withStyle(ChatFormatting.DARK_GRAY)), false);

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    /** Returns a vanilla comparison string for an enchantability value */
    private static String vanillaReference(int enchantability) {
        if (enchantability >= 22) return " (≥ gold)";
        if (enchantability >= 15) return " (≈ leather armor)";
        if (enchantability >= 10) return " (≈ iron/diamond)";
        if (enchantability >= 5)  return " (≈ stone)";
        return " (low)";
    }

    /**
     * /twe info unbreaking — shows Unbreaking enchant level on held tool
     */
    private static int infoUnbreaking(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayerWithTool(ctx);
        if (player == null) return 0;

        CommandSourceStack source = ctx.getSource();
        ItemStack stack = player.getMainHandItem();

        source.sendSuccess(() -> Component.literal("══ Unbreaking Info ══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        int nbtLevel = EnchantmentHelper.getTagEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, stack);
        boolean isArmor = stack.getItem() instanceof net.minecraft.world.item.ArmorItem;

        source.sendSuccess(() -> Component.literal("Unbreaking level (NBT): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(nbtLevel))
                        .withStyle(nbtLevel > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal("Is armor item: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(isArmor))
                        .withStyle(ChatFormatting.WHITE)), false);

        if (nbtLevel > 0) {
            float toolChance = 1f / (nbtLevel + 1);
            source.sendSuccess(() -> Component.literal("Damage chance (tools): ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.0f%%", toolChance * 100))
                            .withStyle(ChatFormatting.AQUA)), false);
            if (isArmor) {
                float armorChance = 0.6f + 0.4f * toolChance;
                source.sendSuccess(() -> Component.literal("Damage chance (armor): ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.0f%%", armorChance * 100))
                                .withStyle(ChatFormatting.AQUA)), false);
            }
            source.sendSuccess(() -> Component.literal("Effective lifetime: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("~%.1fx", (float)(nbtLevel + 1)))
                            .withStyle(ChatFormatting.GREEN)), false);
        }

        // Check if tool also has Reinforced modifier
        try {
            ToolStack tool = ToolStack.from(stack);
            for (var entry : tool.getModifierList()) {
                if (entry.getId().toString().contains("reinforced")) {
                    source.sendSuccess(() -> Component.literal("Reinforced modifier: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("level " + entry.getLevel())
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" (stacks with Unbreaking)")
                                    .withStyle(ChatFormatting.DARK_GRAY)), false);
                }
            }
        } catch (Exception ignored) {}

        return 1;
    }

    /**
     * /twe info sweep — shows Sweeping Edge info for held tool
     */
    private static int infoSweep(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayerWithTool(ctx);
        if (player == null) return 0;

        CommandSourceStack source = ctx.getSource();
        ItemStack stack = player.getMainHandItem();

        source.sendSuccess(() -> Component.literal("══ Sweeping Edge Info ══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        int sweepLevel = EnchantmentHelper.getTagEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, stack);
        boolean hasSwordDig = stack.canPerformAction(ToolActions.SWORD_DIG)
                || stack.canPerformAction(ToolActions.SWORD_SWEEP);
        boolean hasHoeDig = stack.canPerformAction(ToolActions.HOE_DIG);
        boolean isRanged = stack.is(ItemTags.create(ResourceLocation.parse("tconstruct:modifiable/ranged")));
        boolean canSweep = hasSwordDig && !hasHoeDig && !isRanged;

        source.sendSuccess(() -> Component.literal("Sweeping Edge (NBT): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(sweepLevel))
                        .withStyle(sweepLevel > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal("Has sword_dig: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(hasSwordDig))
                        .withStyle(hasSwordDig ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.literal("Has hoe_dig (blocker): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(hasHoeDig))
                        .withStyle(hasHoeDig ? ChatFormatting.RED : ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("Is ranged (blocker): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(isRanged))
                        .withStyle(isRanged ? ChatFormatting.RED : ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> Component.literal("Can receive Sweeping: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(canSweep))
                        .withStyle(canSweep ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)), false);

        if (sweepLevel > 0) {
            float sweepPercent = (float) sweepLevel / (sweepLevel + 1);
            source.sendSuccess(() -> Component.literal("Sweep damage %: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.0f%% of base damage + 1", sweepPercent * 100))
                            .withStyle(ChatFormatting.AQUA)), false);
        }

        // Check TConstruct sweep_percent from volatile data
        try {
            ToolStack tool = ToolStack.from(stack);
            float tcSweep = tool.getVolatileData().getFloat(
                    ResourceLocation.fromNamespaceAndPath("tconstruct", "sweep_percent"));
            if (tcSweep > 0) {
                source.sendSuccess(() -> Component.literal("TC sweep_percent: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.0f%%", tcSweep * 100))
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (from modifier)")
                                .withStyle(ChatFormatting.DARK_GRAY)), false);
            }
        } catch (Exception ignored) {}

        return 1;
    }

    /**
     * /twe info enchants — shows all enchantments (NBT + merged) on held tool
     */
    private static int infoEnchants(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayerWithTool(ctx);
        if (player == null) return 0;

        CommandSourceStack source = ctx.getSource();
        ItemStack stack = player.getMainHandItem();

        source.sendSuccess(() -> Component.literal("══ Enchantments Info ══")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Raw NBT enchantments
        Map<Enchantment, Integer> nbtEnchants = new LinkedHashMap<>();
        net.minecraft.nbt.ListTag nbtList = stack.getEnchantmentTags();
        for (int i = 0; i < nbtList.size(); i++) {
            net.minecraft.nbt.CompoundTag nbtTag = nbtList.getCompound(i);
            Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(
                    ResourceLocation.parse(nbtTag.getString("id")));
            if (ench != null) {
                nbtEnchants.put(ench, nbtTag.getInt("lvl"));
            }
        }

        if (nbtEnchants.isEmpty()) {
            source.sendSuccess(() -> Component.literal("NBT enchantments: (none)")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("NBT enchantments:")
                    .withStyle(ChatFormatting.GRAY), false);
            for (Map.Entry<Enchantment, Integer> e : nbtEnchants.entrySet()) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(e.getKey());
                source.sendSuccess(() -> Component.literal("  " + rl + " → " + e.getValue())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }

        // Merged enchantments (NBT + modifier)
        Map<Enchantment, Integer> merged = t.tinkerswithenchants.util.EnchantMergeUtil.getMergedEnchantments(stack);
        Map<Enchantment, Integer> modifierOnly = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> e : merged.entrySet()) {
            int nbtLevel = nbtEnchants.getOrDefault(e.getKey(), 0);
            if (e.getValue() > nbtLevel || !nbtEnchants.containsKey(e.getKey())) {
                modifierOnly.put(e.getKey(), e.getValue());
            }
        }

        if (!modifierOnly.isEmpty()) {
            source.sendSuccess(() -> Component.literal("From TConstruct modifiers:")
                    .withStyle(ChatFormatting.GRAY), false);
            for (Map.Entry<Enchantment, Integer> e : modifierOnly.entrySet()) {
                ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(e.getKey());
                int nbtLevel = nbtEnchants.getOrDefault(e.getKey(), 0);
                final String detail = nbtLevel > 0
                        ? " (modifier " + e.getValue() + ", NBT " + nbtLevel + ", merged → " + Math.max(e.getValue(), nbtLevel) + ")"
                        : " → " + e.getValue();
                source.sendSuccess(() -> Component.literal("  " + rl + detail)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }

        // Glint status
        boolean glint = stack.hasFoil();
        source.sendSuccess(() -> Component.literal("Shows glint: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(glint))
                        .withStyle(glint ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

        return 1;
    }
}
