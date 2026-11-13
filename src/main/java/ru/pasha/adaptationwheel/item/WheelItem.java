package ru.pasha.adaptationwheel.item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import java.util.List;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.AdaptationEvents;

/** Zero-armour head equipment; an invisible ordinary armour layer leaves only the wheel visible. */
public final class WheelItem extends ArmorItem {
    private static final ArmorMaterial MATERIAL=new ArmorMaterial() {
        public int getDurabilityForType(ArmorItem.Type type) { return 0; }
        public int getDefenseForType(ArmorItem.Type type) { return 0; }
        public int getEnchantmentValue() { return 0; }
        public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_GOLD; }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        public String getName() { return "adaptationwheel:wheel"; }
        public float getToughness() { return 0; }
        public float getKnockbackResistance() { return 0; }
    };
    public WheelItem() { super(MATERIAL,ArmorItem.Type.HELMET,new Item.Properties().stacksTo(1).fireResistant()); }
    /**
     * No modifiers at all: ArmorItem adds armour 0 and toughness 0, which vanilla hides line by line but still heads
     * with an empty «Когда надето на голову:» in the tooltip.
     */
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return com.google.common.collect.ImmutableMultimap.of();
    }
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type) {
        return "adaptationwheel:textures/entity/blank.png";
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) WheelNetwork.openMenu(serverPlayer);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        ItemStack worn=player.getItemBySlot(EquipmentSlot.HEAD);
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(worn) && !player.isCreative())
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (!level.isClientSide) {
            ItemStack wheel=player.getItemInHand(hand).copy();
            player.setItemSlot(EquipmentSlot.HEAD,wheel);
            player.setItemInHand(hand,worn);
            level.playSound(null,player.blockPosition(),SoundEvents.ARMOR_EQUIP_GOLD,SoundSource.PLAYERS,1,1);
            if (player instanceof ServerPlayer serverPlayer) AdaptationEvents.saveAndSync(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public boolean isEnderMask(ItemStack stack,Player player,net.minecraft.world.entity.monster.EnderMan enderman) {
        return ru.pasha.adaptationwheel.server.MobAdaptations.isEnderMask(player,enderman);
    }
    @Override public boolean makesPiglinsNeutral(ItemStack stack,LivingEntity wearer) {
        return ru.pasha.adaptationwheel.server.MobAdaptations.makesPiglinsNeutral(wearer);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flag) {
        text.add(Component.translatable("item.adaptationwheel.wheel.tooltip.what").withStyle(net.minecraft.ChatFormatting.GRAY));
        text.add(Component.translatable("item.adaptationwheel.wheel.tooltip.what2").withStyle(net.minecraft.ChatFormatting.GRAY));
        // The player's adaptations and the bound menu key exist only on the client.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) ru.pasha.adaptationwheel.client.ItemTooltips.wheel(text);
        else text.add(Component.translatable("item.adaptationwheel.wheel.tooltip.keys_unbound").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
