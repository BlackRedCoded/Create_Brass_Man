package net.blackredcoded.brassmanmod.client;

import net.blackredcoded.brassmanmod.BrassManMod;
import net.blackredcoded.brassmanmod.config.FlightConfig;
import net.blackredcoded.brassmanmod.items.BrassManChestplateItem;
import net.blackredcoded.brassmanmod.items.JarvisCommunicatorItem;
import net.blackredcoded.brassmanmod.network.ConsumeAirPacket;
import net.blackredcoded.brassmanmod.network.FallsavePacket;
import net.blackredcoded.brassmanmod.network.GrantAdvancementPacket;
import net.blackredcoded.brassmanmod.network.IcingStatePacket;
import net.blackredcoded.brassmanmod.util.ArmorStyleHelper;
import net.blackredcoded.brassmanmod.util.ArmorUpgradeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.material.Fluids;

@EventBusSubscriber(modid = BrassManMod.MOD_ID, value = Dist.CLIENT)
public class FlightHandler {
    private static ClientTickEvent.Post event;
    private static boolean isFlying = false;
    private static boolean fallsaveTriggered = false;
    private static boolean firstFlightGranted = false;
    private static boolean wasAbove500 = false;
    private static boolean hasGrantedIcingAchievement = false;
    private static int icingCooldownTicks = 0;
    private static int floatingTicks = 0;
    private static int airConsumeTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        FlightHandler.event = event;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.level().isClientSide) return;

        FlightConfig.PlayerFlightData config = FlightConfig.get(player);
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);

        // === FALLSAVE CHECK (BEFORE CHESTPLATE CHECK - WORKS WITH JUST JARVIS ACCESS) ===
        if (!player.onGround() && player.getDeltaMovement().y < -0.5) {
            if (!fallsaveTriggered && isFalling(player)) {
                if (JarvisCommunicatorItem.hasJarvis(player)) {
                    PacketDistributor.sendToServer(new FallsavePacket());
                    fallsaveTriggered = true;
                    player.displayClientMessage(
                            Component.literal("JARVIS: Fall detected! Emergency protocols engaged").withStyle(ChatFormatting.RED),
                            true
                    );
                }
            }
        } else if (player.onGround()) {
            fallsaveTriggered = false;
        }

        // === CHESTPLATE REQUIRED BEYOND THIS POINT ===
        if (!(chestplate.getItem() instanceof BrassManChestplateItem brass)) {
            stopFlying(player);
            return;
        }

        // REMOVED: All the aggressive fall damage prevention logic - now handled by FlightFallDamageHandler

        // === ICING PROBLEM LOGIC ===
        boolean isAbove500 = player.getY() >= 500;
        int upgradeStage = ArmorUpgradeHelper.getRemoteAssemblyLevel(chestplate);

        // Check if player is iced (above Y:500 with upgrade stage 0)
        boolean isIced = isAbove500 && upgradeStage == 0 && !ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getChestplate(), ArmorStyleHelper.AQUA);

        // Grant achievement when first entering icing zone
        if (isIced && !wasAbove500) {
            if (!hasGrantedIcingAchievement) {
                PacketDistributor.sendToServer(new GrantAdvancementPacket("brassmanmod:brass_man/icing_problem"));
                hasGrantedIcingAchievement = true;
            }

            // Send icing state to server
            PacketDistributor.sendToServer(new IcingStatePacket(true));
            icingCooldownTicks = 100; // 5 second cooldown before systems can restart (20 ticks = 1 sec)
        }

        // When leaving icing zone
        if (!isIced && wasAbove500) {
            PacketDistributor.sendToServer(new IcingStatePacket(false));
        }

        wasAbove500 = isAbove500;

        // Cooldown timer
        if (icingCooldownTicks > 0) {
            icingCooldownTicks--;
        }

        // Apply maximum powdered snow overlay effect when iced (140 = max freeze, damage threshold)
        if (isIced) {
            player.setTicksFrozen(140); // Maximum freeze overlay (same as when taking freeze damage)
        } else {
            // Clear frozen overlay when not iced
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 5)); // Quickly thaw
            }
        }

        // If iced, completely disable all suit functions and stop here
        if (isIced || icingCooldownTicks > 0) {
            // Force disable flight
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }

            // Disable HUD
            FlightConfig.PlayerFlightData data = FlightConfig.CLIENT_CONFIG;
            data.hudEnabled = false;

            // Clear any active flight movement
            if (player.getDeltaMovement().y > 0) {
                player.setDeltaMovement(player.getDeltaMovement().x,
                        Math.min(player.getDeltaMovement().y, -0.1),
                        player.getDeltaMovement().z);
            }

            return; // Stop all flight processing
        }

        FlightConfig.PlayerFlightData data = FlightConfig.CLIENT_CONFIG;
        if (!data.hudEnabled) {
            data.hudEnabled = true;
        }

        int airAmount = BrassManChestplateItem.getAir(chestplate);
        if (airAmount <= 0) {
            stopFlying(player);
            return;
        }

        boolean spacePressed = mc.options.keyJump.isDown();
        boolean shiftPressed = mc.options.keyShift.isDown();
        boolean isInAir = player.level().getBlockState(player.blockPosition().below()).isAir();
        boolean isInWater = player.isInWater();
        boolean isInLava = player.isInLava();

        int speedPercent = config.flightSpeed;
        int ticksPerAir;
        if (speedPercent <= 10) {
            ticksPerAir = 50;
        } else if (speedPercent <= 20) {
            ticksPerAir = 40;
        } else if (speedPercent <= 30) {
            ticksPerAir = 30;
        } else if (speedPercent <= 40) {
            ticksPerAir = 25;
        } else if (speedPercent <= 50) {
            ticksPerAir = 20;
        } else if (speedPercent <= 60) {
            ticksPerAir = 15;
        } else if (speedPercent <= 70) {
            ticksPerAir = 10;
        } else if (speedPercent <= 80) {
            ticksPerAir = 7;
        } else if (speedPercent <= 90) {
            ticksPerAir = 4;
        } else {
            ticksPerAir = 2;
        }

        // Ocean style allows water flight
        boolean canFlyInWater = isInWater && ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getChestplate(), ArmorStyleHelper.DARK_AQUA);
        // Flaming style allows lava flight
        boolean canFlyInLava = isInLava && ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getChestplate(), ArmorStyleHelper.FLAMING);
        boolean canFly = isInAir || canFlyInWater || canFlyInLava;

        // FLIGHT: Space pressed + flight enabled + MUST BE IN AIR
        if (spacePressed && config.flightEnabled && canFly) {
            if (!isFlying) {
                startFlying(player);
            }

            // REMOVED: player.fallDistance = 0; - now handled by FlightFallDamageHandler
            airConsumeTicks++;
            if (airConsumeTicks >= ticksPerAir) {
                PacketDistributor.sendToServer(new ConsumeAirPacket(1));
                airConsumeTicks = 0;
            }

            float speedMultiplier = speedPercent / 100.0f;
            Vec3 lookVector = player.getLookAngle();
            double baseSpeed = 0.225;
            double horizontalX = lookVector.x * baseSpeed * (0.5 + speedMultiplier);
            double verticalY = lookVector.y * baseSpeed * (0.5 + speedMultiplier) * 2.0;
            double horizontalZ = lookVector.z * baseSpeed * (0.5 + speedMultiplier);
            Vec3 movement = new Vec3(horizontalX, verticalY, horizontalZ);
            Vec3 currentMovement = player.getDeltaMovement();
            player.setDeltaMovement(
                    currentMovement.x * 0.8 + movement.x,
                    movement.y,
                    currentMovement.z * 0.8 + movement.z
            );

            player.setPose(Pose.STANDING);
            player.setSwimming(false);
            floatingTicks = 0;
            spawnParticles(player);
        }
        // HOVER: In air + hover enabled
        else if (isInAir && airAmount > 0 && config.hoverEnabled) {
            floatingTicks++;
            // REMOVED: player.fallDistance = 0; - now handled by FlightFallDamageHandler

            if (floatingTicks % 40 == 0) {
                PacketDistributor.sendToServer(new ConsumeAirPacket(1));
            }

            if (shiftPressed) {
                Vec3 currentMovement = player.getDeltaMovement();
                // FIXED: Made descending slower (half speed)
                double baseSink = -0.3; // Was -0.6
                double accelPerTick = -0.004; // Was -0.008
                double sinkSpeed = baseSink + (floatingTicks * accelPerTick);
                sinkSpeed = Math.max(sinkSpeed, -1.6); // Was -3.2
                player.setDeltaMovement(
                        currentMovement.x * 0.9,
                        sinkSpeed,
                        currentMovement.z * 0.9
                );
            } else {
                player.setDeltaMovement(
                        player.getDeltaMovement().x * 0.8,
                        player.getDeltaMovement().y * 0.05,
                        player.getDeltaMovement().z * 0.8
                );
            }

            player.setPose(Pose.STANDING);
            player.setSwimming(false);
            spawnParticles(player);
        } else {
            stopFlying(player);
        }
    }

    private static void spawnParticles(LocalPlayer player) {
        Vec3 pos = player.position();
        float yaw = (float) Math.toRadians(player.getYRot());
        double[][] relativeOffsets = {
                { -0.3, 0.1, 0.0 },
                { 0.3, 0.1, 0.0 },
                { -0.3, 1.2, 0.0 },
                { 0.3, 1.2, 0.0 }
        };

        for (double[] offset : relativeOffsets) {
            double rotatedX = offset[0] * Math.cos(yaw) - offset[2] * Math.sin(yaw);
            double rotatedZ = offset[0] * Math.sin(yaw) + offset[2] * Math.cos(yaw);
            player.level().addParticle(
                    ParticleTypes.CLOUD,
                    pos.x + rotatedX,
                    pos.y + offset[1],
                    pos.z + rotatedZ,
                    0.0, -0.05, 0.0
            );
        }
    }

    private static boolean isFalling(LocalPlayer player) {
        BlockPos pos = player.blockPosition();
        for (int i = 1; i <= 5; i++) {
            if (!player.level().getBlockState(pos.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void startFlying(LocalPlayer player) {
        if (!isFlying) {
            isFlying = true;
            airConsumeTicks = 0;
            player.setSwimming(false);

            // Grant "First Flight" advancement (only once)
            if (!firstFlightGranted) {
                PacketDistributor.sendToServer(new GrantAdvancementPacket("brassmanmod:brass_man/first_flight"));
                firstFlightGranted = true;
            }
        }
    }

    private static void stopFlying(LocalPlayer player) {
        if (isFlying) {
            isFlying = false;
            floatingTicks = 0;
            airConsumeTicks = 0;
            player.setPose(Pose.STANDING);
            player.setSwimming(false);
        }
    }

    public static ClientTickEvent.Post getEvent() {
        return event;
    }

    public static void setEvent(ClientTickEvent.Post event) {
        FlightHandler.event = event;
    }

    // Getter methods for external use (needed by FlightFallDamageHandler)
    public static boolean isFlying() {
        return isFlying;
    }

    public static int getFloatingTicks() {
        return floatingTicks;
    }
}
