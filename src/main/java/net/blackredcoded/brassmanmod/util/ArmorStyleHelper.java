package net.blackredcoded.brassmanmod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ArmorStyleHelper {

    public static final String BRASS = "brass";
    public static final String AQUA = "aqua";
    public static final String DARK_AQUA = "darkaqua";
    public static final String FLAMING = "flaming";

    // Safe getter methods instead of static fields
    private static LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static ItemStack getHelmet() {
        LocalPlayer player = getPlayer();
        return player != null ? player.getItemBySlot(EquipmentSlot.HEAD) : ItemStack.EMPTY;
    }

    public static ItemStack getChestplate() {
        LocalPlayer player = getPlayer();
        return player != null ? player.getItemBySlot(EquipmentSlot.CHEST) : ItemStack.EMPTY;
    }

    public static ItemStack getLeggings() {
        LocalPlayer player = getPlayer();
        return player != null ? player.getItemBySlot(EquipmentSlot.LEGS) : ItemStack.EMPTY;
    }

    public static ItemStack getBoots() {
        LocalPlayer player = getPlayer();
        return player != null ? player.getItemBySlot(EquipmentSlot.FEET) : ItemStack.EMPTY;
    }

    // For backwards compatibility with your existing code
    public static ItemStack helmet() { return getHelmet(); }
    public static ItemStack chestplate() { return getChestplate(); }
    public static ItemStack leggings() { return getLeggings(); }
    public static ItemStack boots() { return getBoots(); }

    public static String getArmorStyle(ItemStack stack) {
        if (stack.isEmpty()) return BRASS;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains("ArmorStyle") ? data.getString("ArmorStyle") : BRASS;
    }

    public static boolean hasArmorStyle(Player player, ItemStack armorItem, String armorStyle) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        String currentStyle = getArmorStyle(chestplate);
        return currentStyle.equalsIgnoreCase(armorStyle);
    }

    public static void setArmorStyle(ItemStack stack, String style) {
        if (stack.isEmpty()) return;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString("ArmorStyle", style);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
}
