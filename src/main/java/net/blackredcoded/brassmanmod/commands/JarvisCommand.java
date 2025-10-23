package net.blackredcoded.brassmanmod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.blackredcoded.brassmanmod.blockentity.AirCompressorBlockEntity;
import net.blackredcoded.brassmanmod.blockentity.BrassArmorStandBlockEntity;
import net.blackredcoded.brassmanmod.config.FlightConfig;
import net.blackredcoded.brassmanmod.entity.FlyingArmorPieceEntity;
import net.blackredcoded.brassmanmod.entity.FlyingSuitEntity;
import net.blackredcoded.brassmanmod.items.BrassManChestplateItem;
import net.blackredcoded.brassmanmod.items.BrassManHelmetItem;
import net.blackredcoded.brassmanmod.items.JarvisCommunicatorItem;
import net.blackredcoded.brassmanmod.util.ArmorUpgradeHelper;
import net.blackredcoded.brassmanmod.util.ChestArmorScanner;
import net.blackredcoded.brassmanmod.util.CompressorRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class JarvisCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jarvis")
                // NO .requires() - show to everyone but check access in methods
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null || !hasJarvisAccess(player)) {
                        player.sendSystemMessage(Component.literal("JARVIS: Access denied. Requires Brass Man helmet or JARVIS Communicator.")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    return showStatus(context);
                })
                .then(Commands.literal("call")
                        .executes(JarvisCommand::callSuit) // /jarvis call (nearest suit)
                        .then(Commands.argument("setName", StringArgumentType.greedyString())
                                .suggests(JarvisCommand::suggestSuitNames)
                                .executes(JarvisCommand::callNamedSuit))) // /jarvis call "Set Name"
                .then(Commands.literal("list")
                        .executes(JarvisCommand::listSuits) // /jarvis list (all suits, names only)
                        .then(Commands.argument("setName", StringArgumentType.greedyString())
                                .executes(JarvisCommand::listSuitDetails))) // /jarvis list "Set Name" (detailed info)
                .then(Commands.literal("flight")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(JarvisCommand::toggleFlight)))
                .then(Commands.literal("hover")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(JarvisCommand::toggleHover)))
                .then(Commands.literal("nightvision")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(JarvisCommand::toggleNightvision)))
                .then(Commands.literal("hud")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(JarvisCommand::toggleHud)))
                .then(Commands.literal("flightspeed")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(JarvisCommand::setFlightSpeed)))
                .then(Commands.literal("speedboost")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(JarvisCommand::setSpeedBoost)))
                .then(Commands.literal("jumpboost")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(JarvisCommand::setJumpBoost)))
                .then(Commands.literal("fallsave")
                        .then(Commands.literal("hover")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(JarvisCommand::setFallsaveHover)))
                        .then(Commands.literal("flight")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(JarvisCommand::setFallsaveFlight)))
                        // REMOVED: powertoair command
                        .then(Commands.literal("callsuit")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(JarvisCommand::setFallsaveCallSuit))))
                .then(Commands.literal("status")
                        .executes(JarvisCommand::showStatus))
        );
    }

    private static boolean hasJarvisAccess(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.getItem() instanceof BrassManHelmetItem ||
                helmet.getItem() instanceof JarvisCommunicatorItem;
    }

    private static CompletableFuture<Suggestions> suggestSuitNames(CommandContext<CommandSourceStack> context,
                                                                   SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture(); // Return empty suggestions if no player
        }

        // Check if player has JARVIS access (same check as in other commands)
        if (!JarvisCommunicatorItem.hasJarvis(player)) {
            return builder.buildFuture(); // No suggestions without JARVIS access
        }

        // Get all available suits using the SAME method as /jarvis list
        List<SuitInfo> suits = findPlayerSuits(player);

        // Add each suit name as a suggestion
        for (SuitInfo suit : suits) {
            if (suit.setName != null && !suit.setName.isEmpty()) {
                builder.suggest(suit.setName); // Add this suit name to suggestions
            }
        }

        return builder.buildFuture(); // Return the built suggestions
    }

    private static int toggleFlight(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.flightEnabled = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Flight mode " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int toggleHover(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.hoverEnabled = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Hover mode " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int toggleNightvision(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.nightvisionEnabled = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Night vision " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int toggleHud(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.hudEnabled = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: HUD display " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int setFlightSpeed(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        int percent = IntegerArgumentType.getInteger(context, "percent");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.flightSpeed = percent;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Flight speed set to " + percent + "%")
                .withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int setSpeedBoost(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        int percent = IntegerArgumentType.getInteger(context, "percent");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.speedBoost = percent;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Speed boost set to " + percent + "%")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int setJumpBoost(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        int percent = IntegerArgumentType.getInteger(context, "percent");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.jumpBoost = percent;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Jump boost set to " + percent + "%")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setFallsaveHover(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.fallsaveHover = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Fallsave hover " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    private static int setFallsaveFlight(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.fallsaveFlight = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Fallsave flight " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    // REMOVED: setFallsavePowerToAir method

    private static int setFallsaveCallSuit(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        FlightConfig.PlayerFlightData data = FlightConfig.get(player);
        data.fallsaveCallSuit = enabled;
        FlightConfig.save(player, data);

        player.sendSystemMessage(Component.literal("JARVIS: Fallsave auto-call suit " + (enabled ? "enabled" : "disabled"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return 1;
    }

    // Call nearest/best suit
    private static int callSuit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        // Check for Jarvis Communicator
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof JarvisCommunicatorItem)) {
            player.sendSystemMessage(Component.literal("You need a Jarvis Communicator to use this command!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Try armor stands first (Stage 1 and 2)
        boolean foundInStand = tryCallFromArmorStand(player, level, null);
        if (foundInStand) return 1;

        // Try chests (Stage 2 only)
        boolean foundInChest = tryCallFromChests(player, level, null);
        if (foundInChest) return 1;

        player.sendSystemMessage(Component.literal("No suit found nearby!")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    // Call specific named suit
    private static int callNamedSuit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        if (!JarvisCommunicatorItem.hasJarvis(player)) {
            player.sendSystemMessage(Component.literal("You need a JARVIS Communicator to use this command!").withStyle(ChatFormatting.RED));
            return 0;
        }

        String targetName = context.getArgument("setName", String.class);

        // Try armor stands first (Stage 1 and 2)
        boolean foundInStand = tryCallFromArmorStand(player, level, targetName);
        if (foundInStand) {
            return 1;
        }

        // Try chests (Stage 2 only)
        boolean foundInChest = tryCallFromChests(player, level, targetName);
        if (foundInChest) {
            return 1;
        }

        player.sendSystemMessage(Component.literal("JARVIS: No suit named \"" + targetName + "\" found, sir.").withStyle(ChatFormatting.RED));
        return 0;
    }

    // Find all suits owned by player
    private static List<SuitInfo> findPlayerSuits(ServerPlayer player) {
        List<SuitInfo> suits = new ArrayList<>();
        ServerLevel level = (ServerLevel) player.level();
        Set<BlockPos> playerCompressors = CompressorRegistry.getPlayerCompressors(player);

        for (BlockPos compressorPos : playerCompressors) {
            BlockEntity be = level.getBlockEntity(compressorPos);
            if (!(be instanceof AirCompressorBlockEntity compressor)) continue;

            // Check armor stand above
            BlockPos standPos = compressorPos.above();
            BlockEntity standBE = level.getBlockEntity(standPos);
            if (standBE instanceof BrassArmorStandBlockEntity armorStand) {
                ItemStack chestplate = armorStand.getArmor(1);
                if (chestplate.getItem() instanceof BrassManChestplateItem brass) {
                    int remoteLevel = ArmorUpgradeHelper.getRemoteAssemblyLevel(chestplate);
                    if (remoteLevel >= 1) {
                        int totalCharge = brass.air(chestplate) + brass.power(chestplate);
                        String setName = BrassArmorStandBlockEntity.getSetName(chestplate);
                        UUID owner = BrassArmorStandBlockEntity.getSetOwner(chestplate);

                        // Only include if owned by player
                        if (owner != null && owner.equals(player.getUUID())) {
                            ItemStack helmet = armorStand.getArmor(0);
                            ItemStack leggings = armorStand.getArmor(2);
                            ItemStack boots = armorStand.getArmor(3);
                            suits.add(new SuitInfo(standPos, armorStand, helmet, chestplate, leggings, boots, totalCharge, setName));
                        }
                    }
                }
            }
        }

        return suits;
    }

    // Spawn flying suit from armor stand
    private static void callSuitFromStand(ServerPlayer player, SuitInfo suit) {
        ServerLevel level = (ServerLevel) player.level();

        // Spawn flying suit entity
        FlyingSuitEntity flyingSuit = new FlyingSuitEntity(
                level, suit.pos, player,
                suit.helmet, suit.chestplate, suit.leggings, suit.boots
        );
        level.addFreshEntity(flyingSuit);

        // Remove armor from stand
        suit.armorStand.setArmor(0, ItemStack.EMPTY);
        suit.armorStand.setArmor(1, ItemStack.EMPTY);
        suit.armorStand.setArmor(2, ItemStack.EMPTY);
        suit.armorStand.setArmor(3, ItemStack.EMPTY);

        String message = suit.setName != null && !suit.setName.isEmpty()
                ? "JARVIS: Deploying \"" + suit.setName + "\", sir."
                : "JARVIS: Deploying suit, sir.";
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
    }

    // List all suits (names only)
    private static int listSuits(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (!JarvisCommunicatorItem.hasJarvis(player)) {
            player.sendSystemMessage(Component.literal("You need a JARVIS Communicator to use this command!").withStyle(ChatFormatting.RED));
            return 0;
        }

        List<SuitInfo> suits = findPlayerSuits(player);
        if (suits.isEmpty()) {
            player.sendSystemMessage(Component.literal("JARVIS: You have no suits available, sir.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("JARVIS: Your available suits:").withStyle(ChatFormatting.GOLD));
        for (SuitInfo suit : suits) {
            String name = suit.setName != null && !suit.setName.isEmpty() ? "\"" + suit.setName + "\"" : "Unnamed Suit";
            player.sendSystemMessage(Component.literal("  • " + name).withStyle(ChatFormatting.AQUA));
        }

        return 1;
    }

    // List detailed info about a specific suit
    private static int listSuitDetails(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (!JarvisCommunicatorItem.hasJarvis(player)) {
            player.sendSystemMessage(Component.literal("You need a JARVIS Communicator to use this command!").withStyle(ChatFormatting.RED));
            return 0;
        }

        String targetName = context.getArgument("setName", String.class);
        List<SuitInfo> suits = findPlayerSuits(player);

        // Find suit with matching name
        SuitInfo suit = suits.stream()
                .filter(s -> s.setName != null && s.setName.equalsIgnoreCase(targetName))
                .findFirst()
                .orElse(null);

        if (suit == null) {
            player.sendSystemMessage(Component.literal("JARVIS: No suit named \"" + targetName + "\" found, sir.").withStyle(ChatFormatting.RED));
            return 0;
        }

        // Get detailed info
        BrassManChestplateItem brass = (BrassManChestplateItem) suit.chestplate.getItem();
        int currentAir = brass.air(suit.chestplate);
        int maxAir = BrassManChestplateItem.getMaxAir(suit.chestplate);
        int airPercent = maxAir > 0 ? (currentAir * 100) / maxAir : 0;

        int currentPower = brass.power(suit.chestplate);
        int maxPower = BrassManChestplateItem.getMaxPower(suit.chestplate);
        int powerPercent = maxPower > 0 ? (currentPower * 100) / maxPower : 0;

        BlockPos pos = suit.pos;

        // Send detailed info
        player.sendSystemMessage(Component.literal("JARVIS: Suit \"" + suit.setName + "\" Status:").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal("  Location: [X: " + pos.getX() + ", Y: " + pos.getY() + ", Z: " + pos.getZ() + "]").withStyle(ChatFormatting.DARK_GREEN));
        player.sendSystemMessage(Component.literal("  Air: " + airPercent + "% (" + currentAir + "/" + maxAir + ")").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("  Power: " + powerPercent + "% (" + currentPower + "/" + maxPower + ")").withStyle(ChatFormatting.YELLOW));
        // Later you can add: player.sendSystemMessage(Component.literal("  Appearance: \"" + appearance + "\"").withStyle(ChatFormatting.LIGHT_PURPLE));

        return 1;
    }

    private static int callSuitWithChestSupport(CommandContext<CommandSourceStack> context, String suitName)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();

        // Check for Jarvis Communicator
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof JarvisCommunicatorItem)) {
            player.sendSystemMessage(Component.literal("You need a Jarvis Communicator to use this command!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // First, try to find in armor stands (existing logic)
        boolean foundInStand = tryCallFromArmorStand(player, level, suitName);

        if (foundInStand) {
            return 1;
        }

        // If not found in armor stands, try chests (Stage 2 only)
        boolean foundInChest = tryCallFromChests(player, level, suitName);

        if (foundInChest) {
            return 1;
        }

        player.sendSystemMessage(Component.literal("Could not find suit: " + suitName)
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static boolean tryCallFromArmorStand(ServerPlayer player, ServerLevel level, String suitName) {
        List<SuitInfo> suits = findPlayerSuits(player);

        if (suitName != null) {
            // Named call - find specific suit
            SuitInfo match = suits.stream()
                    .filter(s -> s.setName != null && s.setName.equalsIgnoreCase(suitName))
                    .findFirst()
                    .orElse(null);

            if (match != null) {
                callSuitFromStand(player, match);
                return true;
            }
        } else {
            // Un-named call - find nearest suit
            if (!suits.isEmpty()) {
                // Sort by distance and get nearest
                BlockPos playerPos = player.blockPosition();
                SuitInfo nearest = suits.stream()
                        .min((a, b) -> {
                            double distA = a.pos.distSqr(playerPos);
                            double distB = b.pos.distSqr(playerPos);
                            return Double.compare(distA, distB);
                        })
                        .orElse(null);

                if (nearest != null) {
                    callSuitFromStand(player, nearest);
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean tryCallFromChests(ServerPlayer player, ServerLevel level, String suitName) {
        ChestArmorScanner.SuitSet suitSet = ChestArmorScanner.findArmorInChests(
                level, player.blockPosition(), suitName, player.getUUID(), 64);

        if (!suitSet.hasAnyPiece()) {
            return false;
        }

        // Check if ANY piece is Stage 2
        boolean hasStage2 = false;
        ItemStack[] pieces = {
                suitSet.helmet != null ? suitSet.helmet.armor : ItemStack.EMPTY,
                suitSet.chestplate != null ? suitSet.chestplate.armor : ItemStack.EMPTY,
                suitSet.leggings != null ? suitSet.leggings.armor : ItemStack.EMPTY,
                suitSet.boots != null ? suitSet.boots.armor : ItemStack.EMPTY
        };

        for (ItemStack piece : pieces) {
            if (!piece.isEmpty()) {
                int stage = ArmorUpgradeHelper.getRemoteAssemblyLevel(piece);
                if (stage >= 2) {
                    hasStage2 = true;
                }
            }
        }

        if (!hasStage2) {
            player.sendSystemMessage(Component.literal("Suit must be upgraded to Stage 2 (3 stars) to call from chests!")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int piecesFlying = 0;

        if (suitSet.helmet != null) {
            FlyingArmorPieceEntity helmetEntity = new FlyingArmorPieceEntity(
                    level, suitSet.helmet.chestPos, player, suitSet.helmet.armor, EquipmentSlot.HEAD);
            level.addFreshEntity(helmetEntity);
            piecesFlying++;
        }

        if (suitSet.chestplate != null) {
            FlyingArmorPieceEntity chestEntity = new FlyingArmorPieceEntity(
                    level, suitSet.chestplate.chestPos, player, suitSet.chestplate.armor, EquipmentSlot.CHEST);
            level.addFreshEntity(chestEntity);
            piecesFlying++;
        }

        if (suitSet.leggings != null) {
            FlyingArmorPieceEntity legsEntity = new FlyingArmorPieceEntity(
                    level, suitSet.leggings.chestPos, player, suitSet.leggings.armor, EquipmentSlot.LEGS);
            level.addFreshEntity(legsEntity);
            piecesFlying++;
        }

        if (suitSet.boots != null) {
            FlyingArmorPieceEntity bootsEntity = new FlyingArmorPieceEntity(
                    level, suitSet.boots.chestPos, player, suitSet.boots.armor, EquipmentSlot.FEET);
            level.addFreshEntity(bootsEntity);
            piecesFlying++;
        }

        // REMOVE AFTER spawning entities
        ChestArmorScanner.removeArmorFromChests(level, suitSet);

        player.sendSystemMessage(Component.literal("Calling " + piecesFlying + " suit piece" + (piecesFlying > 1 ? "s" : "") + " from storage...")
                .withStyle(ChatFormatting.GOLD));

        return true;
    }

    // Helper record
    private record SuitInfo(BlockPos pos, BrassArmorStandBlockEntity armorStand,
                            ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots,
                            int totalCharge, String setName) {}


    private static int showStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || !hasJarvisAccess(player)) return 0;

        FlightConfig.PlayerFlightData data = FlightConfig.get(player);

        player.sendSystemMessage(Component.literal("━━━ JARVIS Status ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Flight: " + (data.flightEnabled ? "✓" : "✗"))
                .withStyle(data.flightEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Hover: " + (data.hoverEnabled ? "✓" : "✗"))
                .withStyle(data.hoverEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Night Vision: " + (data.nightvisionEnabled ? "✓" : "✗"))
                .withStyle(data.nightvisionEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("HUD Display: " + (data.hudEnabled ? "✓" : "✗"))
                .withStyle(data.hudEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Flight Speed: " + data.flightSpeed + "%").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Speed Boost: " + data.speedBoost + "%").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Jump Boost: " + data.jumpBoost + "%").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("━━━ Fallsave Settings ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Fallsave Hover: " + (data.fallsaveHover ? "✓" : "✗"))
                .withStyle(data.fallsaveHover ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Fallsave Flight: " + (data.fallsaveFlight ? "✓" : "✗"))
                .withStyle(data.fallsaveFlight ? ChatFormatting.GREEN : ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Fallsave Call Suit: " + (data.fallsaveCallSuit ? "✓" : "✗"))
                .withStyle(data.fallsaveCallSuit ? ChatFormatting.GREEN : ChatFormatting.RED));
        // REMOVED: Power-to-Air status line

        return 1;
    }
}