package net.blackredcoded.brassmanmod.items;

import net.blackredcoded.brassmanmod.blockentity.BrassArmorStandBlockEntity;
import net.blackredcoded.brassmanmod.client.renderer.BrassArmorRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.blackredcoded.brassmanmod.util.ArmorStyleHelper;

import java.util.List;

public class BrassManBootsItem extends ArmorItem {

    public BrassManBootsItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.BOOTS, properties.durability(Type.BOOTS.getDurability(15)).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (entity instanceof Player player && !level.isClientSide) {
            String armorStyle = ArmorStyleHelper.getArmorStyle(stack);

            // Flaming armor takes damage in water
            if (armorStyle.equals(ArmorStyleHelper.FLAMING)) {
                if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
                    // Damage armor every second (20 ticks)
                    if (level.getGameTime() % 20 == 0) {
                        stack.hurtAndBreak(1, player, EquipmentSlot.CHEST);
                    }
                }
            }
        }
    }


    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Show set name if it exists
        String setName = BrassArmorStandBlockEntity.getSetName(stack);
        if (setName != null && !setName.isEmpty()) {
            tooltip.add(Component.literal("Set: " + setName).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.literal("Shock Absorbing Boots").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal("Absorbs first 10 HP of fall damage").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Power Cost: 2 per HP absorbed").withStyle(ChatFormatting.YELLOW));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Hydraulic shock absorbers convert").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("kinetic energy into heat using power").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for details").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return BrassArmorRenderer.getArmorTexture(stack, slot, innerModel);
    }

    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, net.minecraft.world.entity.LivingEntity wearer) {
        if (wearer instanceof Player player) {
            return ArmorStyleHelper.hasArmorStyle(player, ArmorStyleHelper.getBoots(), ArmorStyleHelper.AQUA);
        }
        return false;
    }
}