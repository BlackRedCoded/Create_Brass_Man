package net.blackredcoded.brassmanmod.event;

import net.blackredcoded.brassmanmod.BrassManMod;
import net.blackredcoded.brassmanmod.client.FlightHandler;
import net.blackredcoded.brassmanmod.config.FlightConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = BrassManMod.MOD_ID)
public class FlightFallDamageHandler {

    @SubscribeEvent
    public static void onFallDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageSource source = event.getSource();
        if (!source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return;

        // Get flight config
        FlightConfig.PlayerFlightData config = FlightConfig.get(player);

        // Check if player is in controlled flight mode
        boolean isActivelyFlying = FlightHandler.isFlying();
        boolean isActivelyHovering = FlightHandler.getFloatingTicks() > 0 && config.hoverEnabled;

        // Check if player is descending slowly (controlled descent)
        boolean isSlowDescent = player.getDeltaMovement().y > -0.8 && (config.flightEnabled || config.hoverEnabled);

        // Prevent ALL fall damage if in any controlled flight mode
        if (isActivelyFlying || isActivelyHovering || isSlowDescent) {
            event.setNewDamage(0.0f); // COMPLETELY prevent fall damage
        }
    }
}
