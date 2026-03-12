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
                                            return builder.buildFuture();
                                        })
                                        .executes(TWECommand::handleOption)))
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

        source.sendFailure(Component.literal("Unknown option: " + option + ". Use 'all'."));
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

                int count = 0;
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    if (!(item instanceof IModifiable)) continue;

                    ItemStack stack = new ItemStack(item);
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

                    pw.println("────────────────────────────────────────");
                    pw.println("Item: " + id);

                    List<String> textLines = buildToolReportText(stack);
                    for (String line : textLines) {
                        pw.println("  " + line);
                    }
                    pw.println();
                    count++;
                }

                pw.println("=== Total: " + count + " TConstruct items ===");
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
            @SuppressWarnings("removal")
            TagKey<Item> tag = ItemTags.create(new ResourceLocation(tagName));
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
    @SuppressWarnings("removal")
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
            @SuppressWarnings("removal")
            TagKey<Item> tag = ItemTags.create(new ResourceLocation(tagName));
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
}
