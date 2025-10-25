package net.blackredcoded.brassmanmod.event;

import net.blackredcoded.brassmanmod.BrassManMod;
import net.blackredcoded.brassmanmod.client.FlightHandler;
import net.blackredcoded.brassmanmod.config.FlightConfig;
import net.blackredcoded.brassmanmod.items.BrassManChestplateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = BrassManMod.MOD_ID, value = Dist.CLIENT)
public class FlightFallDamageHandler {

    // Track when each player last stopped flying (for grace period)
    private static final Map<UUID, Long> lastFlightStopTime = new HashMap<>();
    private static final long GRACE_PERIOD_MS = 500; // 0.5 seconds in milliseconds

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!event.getSource().is(DamageTypes.FALL)) {
            return;
        }

        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chestplate.getItem() instanceof BrassManChestplateItem)) {
            return;
        }

        FlightConfig.PlayerFlightData config = FlightConfig.get(player);

        // Check current flight states
        boolean isActivelyFlying = FlightHandler.isFlying();
        boolean isHovering = FlightHandler.getFloatingTicks() > 0 && config.hoverEnabled;

        // Check if space is currently pressed (active flying)
        boolean spacePressed = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                spacePressed = mc.options.keyJump.isDown();
            }
        } catch (Exception ignored) {
            // Fallback to false if can't check
        }

        // Update grace period tracking
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();

        // If player WAS flying but now isn't, record the stop time
        if (!isActivelyFlying && lastFlightStopTime.containsKey(playerId)) {
            // Check if we're within grace period
            long timeSinceStop = currentTime - lastFlightStopTime.get(playerId);
            boolean withinGracePeriod = timeSinceStop <= GRACE_PERIOD_MS;

            if (!withinGracePeriod) {
                // Grace period expired, remove from tracking
                lastFlightStopTime.remove(playerId);
            }
        }

        // Record when flight stops (for next time)
        if (!isActivelyFlying && FlightHandler.wasRecentlyFlying()) {
            lastFlightStopTime.put(playerId, currentTime);
        }

        // Check if we should prevent fall damage
        boolean shouldPreventDamage = false;

        if (isActivelyFlying && spacePressed && config.flightEnabled) {
            shouldPreventDamage = true; // Active flight
        } else if (isHovering) {
            shouldPreventDamage = true; // Hover mode (includes sinking)
        } else {
            // Check grace period - if player stopped flying recently, still protect
            if (lastFlightStopTime.containsKey(playerId)) {
                long timeSinceStop = currentTime - lastFlightStopTime.get(playerId);
                if (timeSinceStop <= GRACE_PERIOD_MS) {
                    shouldPreventDamage = true; // Within 0.5 second grace period
                    System.out.println("[FlightFallDamage] Grace period protection: " + timeSinceStop + "ms since flight stop");
                }
            }
        }

        if (shouldPreventDamage) {
            event.setCanceled(true); // Prevent fall damage
        }
    }

    // Clean up old entries periodically
    public static void cleanupGracePeriod() {
        long currentTime = System.currentTimeMillis();
        lastFlightStopTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > GRACE_PERIOD_MS * 2); // Remove entries older than 1 second
    }
}
