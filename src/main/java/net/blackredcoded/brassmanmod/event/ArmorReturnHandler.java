package net.blackredcoded.brassmanmod.event;

import net.blackredcoded.brassmanmod.blockentity.AirCompressorBlockEntity;
import net.blackredcoded.brassmanmod.blockentity.BrassArmorStandBlockEntity;
import net.blackredcoded.brassmanmod.blocks.BrassArmorStandBaseBlock;
import net.blackredcoded.brassmanmod.entity.SentryArmorEntity;
import net.blackredcoded.brassmanmod.items.*;
import net.blackredcoded.brassmanmod.util.ArmorUpgradeHelper;
import net.blackredcoded.brassmanmod.util.CompressorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArmorReturnHandler {

    @SubscribeEvent
    public static void onItemDropped(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!isUpgradedBrassArmorPiece(stack)) return;

        String setName = BrassArmorStandBlockEntity.getSetName(stack);
        UUID owner = BrassArmorStandBlockEntity.getSetOwner(stack);

        System.out.println("[ArmorReturn] Armor dropped - SetName: '" + setName + "', Owner: " + owner);

        if (setName == null || setName.isEmpty() || owner == null) {
            System.out.println("[ArmorReturn] Missing setName or owner - skipping");
            return;
        }

        int upgradeStage = ArmorUpgradeHelper.getRemoteAssemblyLevel(stack);
        System.out.println("[ArmorReturn] Upgrade stage: " + upgradeStage);

        if (upgradeStage < 2) {
            System.out.println("[ArmorReturn] Not Stage 2+ - using fallback system");
            CompoundTag persistentData = itemEntity.getPersistentData();
            persistentData.putBoolean("BrassManReturning", true);
            persistentData.putString("BrassManSetName", setName);
            persistentData.putUUID("BrassManOwner", owner);
            persistentData.putInt("BrassManReturnDelay", 60);
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos targetStand = findTargetArmorStandGlobal(level, owner, setName);

        if (targetStand != null) {
            System.out.println("[ArmorReturn] Found target stand at: " + targetStand);
            double distance = Math.sqrt(itemEntity.position().distanceToSqr(
                    targetStand.getX() + 0.5,
                    targetStand.getY() + 1.0,
                    targetStand.getZ() + 0.5
            ));
            int delayTicks = Math.max(20, (int) (distance / 500.0 * 20));

            System.out.println("[ArmorReturn] Distance: " + distance + ", Delay: " + delayTicks + " ticks");
            scheduleArmorReturn(level, itemEntity, targetStand, delayTicks);
        } else {
            System.out.println("[ArmorReturn] No target stand found - using fallback system");
            CompoundTag persistentData = itemEntity.getPersistentData();
            persistentData.putBoolean("BrassManReturning", true);
            persistentData.putString("BrassManSetName", setName);
            persistentData.putUUID("BrassManOwner", owner);
            persistentData.putInt("BrassManReturnDelay", 60);
        }
    }

    // FIXED: Match compressor name directly, don't check armor stand contents
    private static BlockPos findTargetArmorStandGlobal(ServerLevel level, UUID ownerUUID, String setName) {
        CompressorRegistry registry = CompressorRegistry.get(level);
        Set<BlockPos> playerCompressors = registry.getPlayerCompressors(ownerUUID);

        System.out.println("[ArmorReturn] Searching for setName: '" + setName + "' for player: " + ownerUUID);
        System.out.println("[ArmorReturn] Found " + playerCompressors.size() + " compressors for player");

        for (BlockPos compressorPos : playerCompressors) {
            System.out.println("[ArmorReturn] Checking compressor at: " + compressorPos);

            BlockEntity be = level.getBlockEntity(compressorPos);
            if (!(be instanceof AirCompressorBlockEntity compressor)) {
                System.out.println("[ArmorReturn] Not an AirCompressor at: " + compressorPos);
                continue;
            }

            // Get compressor name
            String compressorName = compressor.getCustomName() != null ? compressor.getCustomName().getString() : "";
            System.out.println("[ArmorReturn] Compressor name: '" + compressorName + "'");

            // FIXED: Direct name matching - if compressor name matches setName
            if (setName.equals(compressorName)) {
                BlockPos standPos = compressorPos.above();
                BlockEntity standBE = level.getBlockEntity(standPos);
                System.out.println("[ArmorReturn] Checking armor stand at: " + standPos);

                if (standBE instanceof BrassArmorStandBlockEntity) {
                    System.out.println("[ArmorReturn] FOUND MATCH! Compressor '" + compressorName + "' matches setName '" + setName + "'");
                    return standPos; // Found matching compressor with armor stand above
                } else {
                    System.out.println("[ArmorReturn] Compressor matches but no armor stand above");
                }
            }
        }

        System.out.println("[ArmorReturn] No matching compressor found for setName: '" + setName + "'");
        return null;
    }

    private static void scheduleArmorReturn(ServerLevel level, ItemEntity itemEntity, BlockPos targetPos, int delayTicks) {
        System.out.println("[ArmorReturn] Scheduling return in " + delayTicks + " ticks");

        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + delayTicks, () -> {
            if (itemEntity.isRemoved()) {
                System.out.println("[ArmorReturn] Item was removed before teleportation");
                return;
            }

            System.out.println("[ArmorReturn] Teleporting item to: " + targetPos);

            itemEntity.teleportTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 1.2,
                    targetPos.getZ() + 0.5
            );

            itemEntity.setDeltaMovement(0, 0.3, 0);

            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 10, () -> {
                returnToArmorStand(level, itemEntity, targetPos);
            }));
        }));
    }

    // Keep all existing methods unchanged...
    public static void tickArmorReturns(ServerLevel level) {
        for (Entity itemEntity : level.getAllEntities()) {
            if (!(itemEntity instanceof ItemEntity item)) continue;

            CompoundTag data = item.getPersistentData();
            if (!data.getBoolean("BrassManReturning")) continue;

            int delay = data.getInt("BrassManReturnDelay");
            if (delay > 0) {
                data.putInt("BrassManReturnDelay", delay - 1);
                continue;
            }

            String setName = data.getString("BrassManSetName");
            UUID owner = data.getUUID("BrassManOwner");

            BlockPos targetStand = findTargetArmorStandGlobal(level, owner, setName);

            if (targetStand != null) {
                item.teleportTo(
                        targetStand.getX() + 0.5,
                        targetStand.getY() + 1.2,
                        targetStand.getZ() + 0.5
                );
                returnToArmorStand(level, item, targetStand);
            } else {
                BlockPos localStand = findMatchingArmorStand(level, item.blockPosition(), setName);
                if (localStand != null) {
                    returnToArmorStand(level, item, localStand);
                } else {
                    becomeSentry(level, item, setName);
                }
            }
        }
    }

    private static BlockPos findMatchingArmorStand(ServerLevel level, BlockPos searchPos, String setName) {
        for (BlockPos scanPos : BlockPos.betweenClosed(
                searchPos.offset(-64, -32, -64),
                searchPos.offset(64, 32, 64))) {

            if (level.getBlockEntity(scanPos) instanceof AirCompressorBlockEntity compressor) {
                String compressorName = compressor.getCustomName() != null ? compressor.getCustomName().getString() : "";
                if (setName.equals(compressorName)) {
                    BlockPos above = scanPos.above();
                    if (level.getBlockState(above).getBlock() instanceof BrassArmorStandBaseBlock) {
                        return above;
                    }
                }
            }
        }
        return null;
    }

    private static void returnToArmorStand(ServerLevel level, ItemEntity itemEntity, BlockPos standPos) {
        if (!(level.getBlockEntity(standPos) instanceof BrassArmorStandBlockEntity stand)) {
            System.out.println("[ArmorReturn] No armor stand at position when trying to return");
            return;
        }

        ItemStack stack = itemEntity.getItem();
        int slot = getArmorSlot(stack);

        System.out.println("[ArmorReturn] Trying to place armor in slot " + slot);

        if (slot >= 0 && stand.getArmor(slot).isEmpty()) {
            stand.setArmor(slot, stack.copy());
            stand.setChanged();
            itemEntity.discard();
            System.out.println("[ArmorReturn] Successfully placed armor on stand");
        } else {
            CompoundTag data = itemEntity.getPersistentData();
            System.out.println("[ArmorReturn] Slot occupied, becoming sentry");
            becomeSentry(level, itemEntity, data.getString("BrassManSetName"));
        }
    }

    // Keep all existing utility methods...
    private static void becomeSentry(ServerLevel level, ItemEntity itemEntity, String setName) {
        BlockPos pos = itemEntity.blockPosition();
        ItemStack helmet = ItemStack.EMPTY;
        ItemStack chestplate = ItemStack.EMPTY;
        ItemStack leggings = ItemStack.EMPTY;
        ItemStack boots = ItemStack.EMPTY;

        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(ItemEntity.class, itemEntity.getBoundingBox().inflate(5.0));
        for (ItemEntity nearby : nearbyItems) {
            CompoundTag nearbyData = nearby.getPersistentData();
            if (!nearbyData.getBoolean("BrassManReturning")) continue;
            if (!nearbyData.getString("BrassManSetName").equals(setName)) continue;

            ItemStack nearbyStack = nearby.getItem();
            if (nearbyStack.getItem() instanceof BrassManHelmetItem) {
                helmet = nearbyStack.copy();
                nearby.discard();
            } else if (nearbyStack.getItem() instanceof BrassManChestplateItem) {
                chestplate = nearbyStack.copy();
                nearby.discard();
            } else if (nearbyStack.getItem() instanceof BrassManLeggingsItem) {
                leggings = nearbyStack.copy();
                nearby.discard();
            } else if (nearbyStack.getItem() instanceof BrassManBootsItem) {
                boots = nearbyStack.copy();
                nearby.discard();
            }
        }

        ItemStack currentStack = itemEntity.getItem();
        if (currentStack.getItem() instanceof BrassManHelmetItem && helmet.isEmpty()) {
            helmet = currentStack.copy();
        } else if (currentStack.getItem() instanceof BrassManChestplateItem && chestplate.isEmpty()) {
            chestplate = currentStack.copy();
        } else if (currentStack.getItem() instanceof BrassManLeggingsItem && leggings.isEmpty()) {
            leggings = currentStack.copy();
        } else if (currentStack.getItem() instanceof BrassManBootsItem && boots.isEmpty()) {
            boots = currentStack.copy();
        }

        itemEntity.discard();

        SentryArmorEntity sentry = new SentryArmorEntity(level, pos, setName, helmet, chestplate, leggings, boots);
        level.addFreshEntity(sentry);
    }

    private static int getArmorSlot(ItemStack stack) {
        if (stack.getItem() instanceof BrassManHelmetItem) return 0;
        if (stack.getItem() instanceof BrassManChestplateItem) return 1;
        if (stack.getItem() instanceof BrassManLeggingsItem) return 2;
        if (stack.getItem() instanceof BrassManBootsItem) return 3;
        return -1;
    }

    private static boolean isUpgradedBrassArmorPiece(ItemStack stack) {
        return stack.getItem() instanceof BrassManHelmetItem ||
                stack.getItem() instanceof BrassManChestplateItem ||
                stack.getItem() instanceof BrassManLeggingsItem ||
                stack.getItem() instanceof BrassManBootsItem;
    }
}
