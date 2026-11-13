package ru.pasha.adaptationwheel.item;
import java.util.List;
import net.minecraft.item.*;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import ru.pasha.adaptationwheel.net.WheelNetwork;
import ru.pasha.adaptationwheel.server.AdaptationEvents;
public final class WheelItem extends ArmorItem {
 private static final IArmorMaterial MATERIAL=new IArmorMaterial(){
  public int getDurabilityForSlot(EquipmentSlotType slot){return 0;}
  public int getDefenseForSlot(EquipmentSlotType slot){return 0;}
  public int getEnchantmentValue(){return 0;}
  public SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_GOLD;}
  public Ingredient getRepairIngredient(){return Ingredient.EMPTY;}
  public String getName(){return "adaptationwheel:wheel";}
  public float getToughness(){return 0;}
  public float getKnockbackResistance(){return 0;}
 };
 public WheelItem(){super(MATERIAL,EquipmentSlotType.HEAD,new Item.Properties().stacksTo(1).tab(ItemGroup.TAB_TOOLS).fireResistant());}
 @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlotType slot,String type){return "adaptationwheel:textures/entity/blank.png";}
 @Override public ActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
  if(player.isShiftKeyDown()){if(player instanceof ServerPlayerEntity)WheelNetwork.openMenu((ServerPlayerEntity)player);return ActionResult.sidedSuccess(player.getItemInHand(hand),world.isClientSide);}
  ItemStack worn=player.getItemBySlot(EquipmentSlotType.HEAD);
  if(EnchantmentHelper.hasBindingCurse(worn)&&!player.isCreative())return ActionResult.fail(player.getItemInHand(hand));
  if(!world.isClientSide){player.setItemSlot(EquipmentSlotType.HEAD,player.getItemInHand(hand).copy());player.setItemInHand(hand,worn);
   world.playSound(null,player.blockPosition(),SoundEvents.ARMOR_EQUIP_GOLD,SoundCategory.PLAYERS,1,1);
   if(player instanceof ServerPlayerEntity)AdaptationEvents.saveAndSync((ServerPlayerEntity)player);
  }
  return ActionResult.sidedSuccess(player.getItemInHand(hand),world.isClientSide);
 }
 @Override public void appendHoverText(ItemStack stack,World world,List<ITextComponent> text,ITooltipFlag flag){text.add(new TranslationTextComponent("item.adaptationwheel.wheel.tooltip"));}
}
