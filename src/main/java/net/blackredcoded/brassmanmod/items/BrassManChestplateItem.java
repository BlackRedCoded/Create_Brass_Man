package net.blackredcoded.brassmanmod.items;

import net.blackredcoded.brassmanmod.blockentity.BrassArmorStandBlockEntity;
import net.blackredcoded.brassmanmod.client.renderer.BrassArmorRenderer;
import net.blackredcoded.brassmanmod.registry.ModArmorMaterials;
import net.blackredcoded.brassmanmod.util.ArmorUpgradeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.blackredcoded.brassmanmod.util.ArmorStyleHelper;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class BrassManChestplateItem extends ArmorItem {

    public static final int BASE_MAX_AIR = 12_000;
    public static final int BASE_MAX_POWER = 1_000;
    public static final int MAX_AIR = BASE_MAX_AIR;
    public static final int MAX_POWER = BASE_MAX_POWER;

    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide() && entity instanceof Player player) {
            if (player.getItemBySlot(EquipmentSlot.CHEST).equals(stack)) {
                applyStyleEffects(player);
            }
        }

        if (entity instanceof Player player && !level.isClientSide) {
            String armorStyle = ArmorStyleHelper.getArmorStyle(stack);

            // Flaming armor takes damage in water
            if (armorStyle.equals(ArmorStyleHelper.FLAMING)) {
                if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
                    // Damage armor every second (20 ticks)
                    if (level.getGameTime() % 20 == 0) {
                        stack.hurtAndBreak(1, player, EquipmentSlot.CHEST);

                        // Optional: Steam particles/sound effect
                        level.playSound(null, player.blockPosition(),
                                SoundEvents.LAVA_EXTINGUISH,
                                SoundSource.PLAYERS, 0.5f, 1.0f);
                    }
                }
            }
        }
    }

    public BrassManChestplateItem(Properties props) {
        super(ModArmorMaterials.BRASS_MAN, Type.CHESTPLATE,
                props.durability(Type.CHESTPLATE.getDurability(15)).rarity(Rarity.UNCOMMON));
    }

    public static int getMaxAir(ItemStack stack) {
        return BASE_MAX_AIR + ArmorUpgradeHelper.getAirBonus(stack);
    }

    public static int getMaxPower(ItemStack stack) {
        return BASE_MAX_POWER + ArmorUpgradeHelper.getPowerBonus(stack);
    }

    public int air(ItemStack s) {
        CustomData cd = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return cd.copyTag().getInt("BrassAir");
    }

    public void setAir(ItemStack s, int v) {
        CustomData cd = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag t = cd.copyTag();
        int existingPower = t.getInt("BrassPower");
        t.putInt("BrassAir", Math.max(0, Math.min(v, getMaxAir(s))));
        t.putInt("BrassPower", existingPower);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public int power(ItemStack s) {
        CustomData cd = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return cd.copyTag().getInt("BrassPower");
    }

    public void setPower(ItemStack s, int v) {
        CustomData cd = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag t = cd.copyTag();
        int existingAir = t.getInt("BrassAir");
        t.putInt("BrassPower", Math.max(0, Math.min(v, getMaxPower(s))));
        t.putInt("BrassAir", existingAir);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public void setAirAndPower(ItemStack s, int air, int power) {
        CustomData cd = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag t = cd.copyTag();
        t.putInt("BrassAir", Math.max(0, Math.min(air, getMaxAir(s))));
        t.putInt("BrassPower", Math.max(0, Math.min(power, getMaxPower(s))));
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public boolean consume(ItemStack s, int baseAmount) {
        float multiplier = ArmorUpgradeHelper.getAirEfficiencyMultiplier(s);
        int actualCost = Math.max(1, (int)(baseAmount * multiplier));
        int cur = air(s);
        if (cur < actualCost) return false;
        setAir(s, cur - actualCost);
        return true;
    }

    public boolean consumePower(ItemStack s, int baseAmount) {
        float multiplier = ArmorUpgradeHelper.getPowerEfficiencyMultiplier(s);
        int actualCost = Math.max(1, (int)(baseAmount * multiplier));
        int cur = power(s);
        if (cur < actualCost) return false;
        setPower(s, cur - actualCost);
        return true;
    }

    public static int getAir(ItemStack stack) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return cd.copyTag().getInt("BrassAir");
    }

    public static void consumeAir(ItemStack stack, int amount) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
        int currentAir = tag.getInt("BrassAir");
        int currentPower = tag.getInt("BrassPower");
        float airDebt = tag.getFloat("AirDebt");

        float multiplier = ArmorUpgradeHelper.getAirEfficiencyMultiplier(stack);
        float exactCost = amount * multiplier;

        airDebt += exactCost;

        int wholeUnitsToConsume = (int) airDebt;
        if (wholeUnitsToConsume > 0) {
            currentAir = Math.max(0, currentAir - wholeUnitsToConsume);
            airDebt -= wholeUnitsToConsume;
        }

        tag.putInt("BrassAir", currentAir);
        tag.putInt("BrassPower", currentPower);
        tag.putFloat("AirDebt", airDebt);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int air = air(s);
        int power = power(s);
        int maxAir = getMaxAir(s);
        int maxPower = getMaxPower(s);
        ChatFormatting airColor = maxAir > BASE_MAX_AIR ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        ChatFormatting powerColor = maxPower > BASE_MAX_POWER ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

        // Show set name if it exists
        String setName = BrassArmorStandBlockEntity.getSetName(s);
        if (setName != null && !setName.isEmpty()) {
            tooltip.add(Component.literal("Set: " + setName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        tooltip.add(Component.literal("Air: %d / %d".formatted(air, maxAir)).withStyle(airColor));
        tooltip.add(Component.literal("Power: %d / %d".formatted(power, maxPower)).withStyle(powerColor));

        // NEW: Show Remote Assembly Level (stars) - ALWAYS shown, starting at 1 star
        int remoteLevel = ArmorUpgradeHelper.getRemoteAssemblyLevel(s);
        int displayStars = remoteLevel + 1; // 0 -> 1 star, 1 -> 2 stars, 2 -> 3 stars

        tooltip.add(Component.literal(""));

        String stars;
        ChatFormatting starColor;
        String levelText;

        if (displayStars == 1) {
            stars = "⭐";
            starColor = ChatFormatting.WHITE;
            levelText = "Base Configuration";
        } else if (displayStars == 2) {
            stars = "⭐⭐";
            starColor = ChatFormatting.AQUA;
            levelText = "Remote Assembly (MK 7)";
        } else { // 3 stars
            stars = "⭐⭐⭐";
            starColor = ChatFormatting.GOLD;
            levelText = "Field Assembly (MK 42)";
        }

        tooltip.add(Component.literal(stars + " " + levelText).withStyle(starColor, ChatFormatting.BOLD));

        if (displayStars == 1) {
            tooltip.add(Component.literal("Manual suit equipping only").withStyle(ChatFormatting.GRAY));
        } else if (displayStars == 2) {
            tooltip.add(Component.literal("Can call suit from Armor Stands").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal("Can call suit from anywhere!").withStyle(ChatFormatting.GOLD));
        }

        if (ArmorUpgradeHelper.hasUpgrades(s)) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("━━━ Installed Upgrades ━━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            int powerCells = ArmorUpgradeHelper.getUpgradeCount(s, ArmorUpgradeHelper.POWER_CELL);
            if (powerCells > 0) {
                int powerBonus = ArmorUpgradeHelper.getPowerBonus(s);
                tooltip.add(Component.literal("⚡ Power Cells: " + powerCells + "/" + ArmorUpgradeHelper.MAX_POWER_CELLS
                        + " (+" + (powerCells * 10) + "% = +" + powerBonus + " power)").withStyle(ChatFormatting.YELLOW));
            }

            int airTanks = ArmorUpgradeHelper.getUpgradeCount(s, ArmorUpgradeHelper.AIR_TANK);
            if (airTanks > 0) {
                tooltip.add(Component.literal("🌀 Air Tanks: " + airTanks + "/" + ArmorUpgradeHelper.MAX_AIR_TANKS
                        + " (+" + (airTanks * 3000) + " air)").withStyle(ChatFormatting.AQUA));
            }

            int airEff = ArmorUpgradeHelper.getUpgradeCount(s, ArmorUpgradeHelper.AIR_EFFICIENCY);
            if (airEff > 0) {
                tooltip.add(Component.literal("💨 Air Efficiency: " + airEff + "/" + ArmorUpgradeHelper.MAX_AIR_EFFICIENCY
                        + " (-" + (airEff * 10) + "% air use)").withStyle(ChatFormatting.AQUA));
            }

            int powerEff = ArmorUpgradeHelper.getUpgradeCount(s, ArmorUpgradeHelper.POWER_EFFICIENCY);
            if (powerEff > 0) {
                tooltip.add(Component.literal("⚙ Power Efficiency: " + powerEff + "/" + ArmorUpgradeHelper.MAX_POWER_EFFICIENCY
                        + " (-" + (powerEff * 10) + "% power use)").withStyle(ChatFormatting.GOLD));
            }

            int total = ArmorUpgradeHelper.getTotalUpgradeCount(s);
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Upgrade Slots: " + total + "/" + ArmorUpgradeHelper.MAX_TOTAL_UPGRADES)
                    .withStyle(total >= ArmorUpgradeHelper.MAX_TOTAL_UPGRADES ? ChatFormatting.RED : ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("⬆ No Upgrades Installed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("━━━ Information ━━━").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Wear to power Brass Man suit").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.literal("Recharge at Brass Armor Stand").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("Upgrade at Modification Station").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Hold Shift for more info").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private void applyStyleEffects(Player player) {
        boolean fullFlamingArmor;

        fullFlamingArmor = ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getBoots(), ArmorStyleHelper.FLAMING) &&
                ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getLeggings(), ArmorStyleHelper.FLAMING) &&
                ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getChestplate(), ArmorStyleHelper.FLAMING) &&
                ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getHelmet(), ArmorStyleHelper.FLAMING);

        // Iced/Aqua Style: Prevent freezing completely
        if (ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getChestplate(), ArmorStyleHelper.AQUA)) {
            // Remove freezing damage by resetting frozen ticks
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }
            // Also prevent powder snow freezing
            player.setIsInPowderSnow(false);
        }

        // Ocean Style: Water Breathing + Night Vision underwater
        if (ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getHelmet(), ArmorStyleHelper.DARK_AQUA)) {
            if (player.isInWater()) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20, 0, false, false));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WATER_BREATHING, 20, 0, false, false));
            }
        }

        // Flaming Style: Night Vision if player has helmet
        if (ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getHelmet(), ArmorStyleHelper.FLAMING) && player.isInLava()) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20, 0, false, false));
        }
        // Flaming Style: Fire Resistance if player has full set
        if (fullFlamingArmor && player.isOnFire()) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20, 0, false, false));
        }
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return BrassArmorRenderer.getArmorTexture(stack, slot, innerModel);
    }

}
