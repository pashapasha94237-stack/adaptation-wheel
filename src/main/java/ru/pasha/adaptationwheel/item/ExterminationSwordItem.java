package ru.pasha.adaptationwheel.item;

import java.util.List;
import java.util.Locale;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import ru.pasha.adaptationwheel.core.SwordSettings;

/** A bandaged forearm blade. Raising it enables adapted parries, never generic shield immunity. */
public final class ExterminationSwordItem extends SwordItem {
    public ExterminationSwordItem() {
        super(Tiers.NETHERITE,5,-2.4F,new Item.Properties().fireResistant().rarity(Rarity.EPIC));
    }
    @Override public float getDamage(){return (float)(SwordSettings.current().baseDamage()-1);}
    @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if(slot!=EquipmentSlot.MAINHAND)return super.getDefaultAttributeModifiers(slot);
        return ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",SwordSettings.current().baseDamage()-1,AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Weapon modifier",-2.4,AttributeModifier.Operation.ADDITION));
    }
    /** Vanilla only refreshes equipment attributes after a stack change; configuration edits must refresh held swords too. */
    public static void refreshHeldDamage(LivingEntity wielder) {
        ItemStack held=wielder.getMainHandItem();
        if(!(held.getItem() instanceof ExterminationSwordItem))return;
        var attribute=wielder.getAttribute(Attributes.ATTACK_DAMAGE);if(attribute==null)return;
        // Respect explicit stack NBT and Forge attribute hooks rather than replacing all of the wielder's modifiers.
        var expected=held.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
            .filter(m->m.getId().equals(BASE_ATTACK_DAMAGE_UUID)).findFirst();
        if(expected.isEmpty())return;
        var old=attribute.getModifier(BASE_ATTACK_DAMAGE_UUID);var replacement=expected.get();
        if(old!=null && old.getAmount()==replacement.getAmount() && old.getOperation()==replacement.getOperation())return;
        attribute.removeModifier(BASE_ATTACK_DAMAGE_UUID);attribute.addTransientModifier(replacement);
    }
    /** Client only: the blade is drawn strapped to the forearm, with a guard pose while parrying. */
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(ru.pasha.adaptationwheel.client.ForearmBlade.EXTENSIONS);
    }
    /** NONE, not SPEAR: vanilla would pick the spear-throw pose before the guard pose of ForearmBlade. */
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(hand==InteractionHand.MAIN_HAND&&player.isShiftKeyDown()) {
            if(player instanceof net.minecraft.server.level.ServerPlayer server)
                ru.pasha.adaptationwheel.server.WorldSlash.toggle(server,stack);
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        if(hand==InteractionHand.MAIN_HAND&&ru.pasha.adaptationwheel.server.WorldSlash.selected(stack)) {
            if(player instanceof net.minecraft.server.level.ServerPlayer server)
                ru.pasha.adaptationwheel.server.WorldSlash.cast(server,stack,hand);
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        var settings=SwordSettings.current();
        boolean slash=ru.pasha.adaptationwheel.server.WorldSlash.selected(stack);
        String undead=String.format(Locale.ROOT,"%.2f",settings.undeadMultiplier()),chosen=String.format(Locale.ROOT,"%.2f",settings.cursedSpiritMultiplier());
        int listed=settings.specialMobs().size();
        // Short by default: mode, multipliers, keys. Shift shows the rest in short lines under their headings.
        boolean more=net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()&&ru.pasha.adaptationwheel.client.ItemTooltips.shiftDown();
        lines.add(Component.translatable(slash?"item.adaptationwheel.extermination_sword.mode_slash":"item.adaptationwheel.extermination_sword.mode_parry").withStyle(ChatFormatting.AQUA));
        if(!more) {
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword.multipliers",undead,chosen,listed).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(slash?"item.adaptationwheel.extermination_sword.keys_slash":"item.adaptationwheel.extermination_sword.keys_parry").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.translatable("item.adaptationwheel.extermination_sword.multipliers_plain",undead,chosen).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.adaptationwheel.extermination_sword.list",listed).withStyle(ChatFormatting.DARK_GRAY));
        // A mob with its own multiplier does not take the shared one above.
        if(!settings.mobMultipliers().isEmpty())
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword.own_multipliers",settings.mobMultipliers().size()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("item.adaptationwheel.extermination_sword.parry_title").withStyle(ChatFormatting.GOLD));
        for(String key:new String[]{"parry1","parry2","parry3"})
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword."+key).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.adaptationwheel.extermination_sword.slash_title").withStyle(ChatFormatting.GOLD));
        for(String key:new String[]{"slash1","slash2","slash3"})
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword."+key).withStyle(ChatFormatting.GRAY));
        for(String key:new String[]{"unlock1","unlock2"})
            lines.add(Component.translatable("item.adaptationwheel.extermination_sword."+key).withStyle(ChatFormatting.DARK_GRAY));
    }
}
