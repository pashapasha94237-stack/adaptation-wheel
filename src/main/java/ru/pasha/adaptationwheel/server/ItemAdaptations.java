package ru.pasha.adaptationwheel.server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.items.IItemHandler;
import ru.pasha.adaptationwheel.MetaHooks;
import ru.pasha.adaptationwheel.core.MetaMechanic;

/**
 * Items that refuse or limit the wearer: broken tools are restored, the anvil price limit grows, a bow shoots without
 * arrows, vanilla cooldowns get shorter. The static hooks are called from coremods/item_adaptations.js on both sides.
 */
public final class ItemAdaptations {
    /** Share of the durability a broken item gets back at stages II, III and IV. */
    private static final int[] RESTORE={0,0,25,50,100};
    /** Cooldown cut in percent at stages I..IV. */
    private static final int[] CUT={0,15,30,40,50};
    private static final Map<ServerPlayer,Set<ItemStack>> LOW=new WeakHashMap<>();
    private static final Map<AnvilMenu,Integer> PRICES=new WeakHashMap<>();
    /** Set by the client: the local player owning these cooldowns, or null. The server side is found by reflection. */
    public static volatile Function<ItemCooldowns,Player> clientOwner=cooldowns->null;
    /** True on the client thread while ClientboundCooldownPacket is applied: the server already shortened that value. */
    private static final ThreadLocal<Boolean> FROM_SERVER=new ThreadLocal<>();
    private static Field cooldownOwner;
    private static boolean cooldownOwnerTried;
    private static Method curiosInventory,equippedCurios;
    private static boolean curiosTried;
    private ItemAdaptations(){}

    // ---- BROKEN_TOOL ------------------------------------------------------------------------------------------

    /** ItemStack.hurtAndBreak: called right after ItemStack.hurt; false cancels the break branch. */
    public static boolean afterHurt(boolean broken,ItemStack stack,LivingEntity entity){
        // An elytra never reaches the break branch: one durability point left is its broken state.
        if(!broken&&entity instanceof ServerPlayer p&&stack.getItem() instanceof ElytraItem&&!ElytraItem.isFlyEnabled(stack)){saveFromBreaking(stack,p);return false;}
        return broken&&!(entity instanceof ServerPlayer p&&saveFromBreaking(stack,p));
    }
    public static boolean saveFromBreaking(ItemStack stack,ServerPlayer p){
        try{
            var m=MetaMechanic.BROKEN_TOOL;if(!MetaServer.active(p,m))return false;
            MetaServer.attempt(p,m);
            int stage=MetaHooks.stage(p,m);if(stage<2)return false;
            int max=stack.getMaxDamage(),percent=RESTORE[stage];
            stack.setDamageValue(max-Math.max(1,Math.min(max,Math.round(max*percent/100F))));
            p.displayClientMessage(Component.translatable("meta.adaptationwheel.broken_tool.saved",stack.getHoverName(),percent).withStyle(ChatFormatting.GREEN),true);
            p.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.7F,1.4F);
            var warned=LOW.get(p);if(warned!=null)warned.remove(stack);
            return true;
        }catch(RuntimeException e){return false;}
    }
    private static boolean nearlyBroken(ItemStack stack){
        if(!stack.isDamageableItem())return false;int max=stack.getMaxDamage();
        return max>0&&(max-stack.getDamageValue())*10<=max;
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||p.tickCount%20!=0||!p.isAlive())return;
        if(!MetaServer.active(p,MetaMechanic.BROKEN_TOOL)||MetaHooks.stage(p,MetaMechanic.BROKEN_TOOL)<1)return;
        Set<ItemStack> warned=null;
        for(var slot:EquipmentSlot.values()){
            var stack=p.getItemBySlot(slot);if(stack.isEmpty())continue;
            if(warned==null)warned=LOW.computeIfAbsent(p,x->Collections.newSetFromMap(new WeakHashMap<>()));
            if(!nearlyBroken(stack)){warned.remove(stack);continue;}
            // Once per item until it is repaired (the check above forgets it again).
            if(warned.add(stack))p.displayClientMessage(Component.translatable("meta.adaptationwheel.broken_tool.low",stack.getHoverName(),(stack.getMaxDamage()-stack.getDamageValue())*100/stack.getMaxDamage()).withStyle(ChatFormatting.GOLD),true);
        }
    }

    // ---- ANVIL ------------------------------------------------------------------------------------------------

    /** Highest price the player can still take: vanilla's 40, then 50, 60 and no limit at stages II..IV. */
    public static int anvilLimit(Player player){
        int stage=MetaHooks.stage(player,MetaMechanic.ANVIL);
        return stage>=4?Integer.MAX_VALUE:stage==3?60:stage==2?50:40;
    }
    /**
     * AnvilMenu.createResult asks this where vanilla reads {@code instabuild} for "cost >= 40", so it is only reached
     * for a price of 40 or more. True keeps the result; the wearer still pays the whole price in levels.
     */
    public static boolean anvilAllows(boolean instabuild,AnvilMenu menu,Player player){
        if(instabuild)return true;
        try{
            int cost=menu.getCost();
            // One attempt per menu and price, not one per keystroke in the name field.
            if(player instanceof ServerPlayer p&&!Integer.valueOf(cost).equals(PRICES.put(menu,cost)))MetaServer.attempt(p,MetaMechanic.ANVIL);
            return cost<anvilLimit(player);
        }catch(RuntimeException e){return false;}
    }

    // ---- QUIVER -----------------------------------------------------------------------------------------------

    /**
     * Arrows the bow can still shoot: loose ones in the inventory plus those inside the item its next arrow comes from
     * (a Supplementaries quiver in the inventory, the off hand or a Curios slot). -1 when no number should be shown:
     * the next arrow comes from a source nobody can see into, another container with arrows may feed the bow once
     * these run out, Infinity keeps one of the arrows, or another mod's item fails.
     */
    private static int arrows(Player p,ItemStack bow){
        if(!(bow.getItem() instanceof ProjectileWeaponItem weapon))return -1;
        try{
            var wanted=weapon.getAllSupportedProjectiles();var next=p.getProjectile(bow);
            Predicate<ItemStack> endless=stack->stack.getItem() instanceof ArrowItem arrow&&arrow.isInfinite(stack,bow,p);
            // Nothing the bow counts as arrows (empty: a mod bow's own ammo), or one Infinity keeps: nothing to count down.
            if(!wanted.test(next)||endless.test(next))return -1;
            int n=0;boolean loose=false;var inventory=p.getInventory();var holders=new ArrayList<ItemStack>();
            for(int i=0;i<inventory.getContainerSize();i++){
                var stack=inventory.getItem(i);loose|=stack==next;
                if(!wanted.test(stack)){if(!stack.isEmpty())holders.add(stack);}
                else if(endless.test(stack))return -1; // Infinity keeps these once the arrows before them are gone
                else n+=stack.getCount();
            }
            // Curios first: a quiver is usually worn there.
            holders.addAll(0,curios(p));
            ItemStack source=null;
            if(!loose){
                for(var holder:holders)if(holds(holder,next)){source=holder;break;}
                if(source==null)return -1;
                int inside=inside(source,wanted,endless);if(inside<0)return -1;
                n+=inside;
            }
            // Few left, yet another container with arrows may take over when these run out: no count beats a low one.
            if(n<8)for(var holder:holders)if(holder!=source&&inside(holder,wanted,endless)!=0)return -1;
            return n;
        }catch(RuntimeException|LinkageError e){return -1;} // another mod's item or its inventory
    }
    /** The item's own inventory holds this very stack. */
    private static boolean holds(ItemStack holder,ItemStack stack){
        var handler=handler(holder);if(handler==null)return false;
        for(int i=0;i<handler.getSlots();i++)if(handler.getStackInSlot(i)==stack)return true;
        return false;
    }
    /** Matching arrows in the item's own inventory; 0 without one, -1 when Infinity keeps one of them. */
    private static int inside(ItemStack holder,Predicate<ItemStack> wanted,Predicate<ItemStack> endless){
        var handler=handler(holder);if(handler==null)return 0;
        int n=0;
        for(int i=0;i<handler.getSlots();i++){
            var stack=handler.getStackInSlot(i);if(!wanted.test(stack))continue;
            if(endless.test(stack))return -1;
            n+=stack.getCount();
        }
        return n;
    }
    private static IItemHandler handler(ItemStack holder){
        // Asked for its inventory, a never-opened Sophisticated Backpacks backpack gets a contents id and storage in the
        // world's data. It holds nothing yet, so it is left alone.
        if(BuiltInRegistries.ITEM.getKey(holder.getItem()).getNamespace().equals("sophisticatedbackpacks")
                &&(holder.getTag()==null||!holder.getTag().contains("contentsUuid")))return null;
        return holder.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }
    /** Items in the player's Curios slots; empty without Curios. */
    private static List<ItemStack> curios(Player p){
        if(!curiosTried){
            curiosTried=true;
            try{
                curiosInventory=Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory",LivingEntity.class);
                equippedCurios=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getEquippedCurios");
            }catch(ReflectiveOperationException|LinkageError e){curiosInventory=null;}
        }
        if(curiosInventory==null)return List.of();
        try{
            var inventory=((LazyOptional<?>)curiosInventory.invoke(null,p)).resolve().orElse(null);if(inventory==null)return List.of();
            var slots=(IItemHandler)equippedCurios.invoke(inventory);var out=new ArrayList<ItemStack>();
            for(int i=0;i<slots.getSlots();i++){var stack=slots.getStackInSlot(i);if(!stack.isEmpty())out.add(stack);}
            return out;
        }catch(ReflectiveOperationException|RuntimeException e){return List.of();}
    }
    /** Both sides: vanilla refuses to draw without ammo, the wearer's bow is nocked anyway from stage II. */
    @SubscribeEvent public static void nock(ArrowNockEvent event){
        var p=event.getEntity();if(p.getAbilities().instabuild||p.isSpectator())return;
        var server=p instanceof ServerPlayer sp?sp:null;int stage=MetaHooks.stage(p,MetaMechanic.QUIVER);
        if(event.hasAmmo()){
            if(server!=null&&stage>=1){int left=arrows(p,event.getBow());if(left>=0&&left<8)server.displayClientMessage(Component.translatable("meta.adaptationwheel.empty_quiver.left",left).withStyle(ChatFormatting.GOLD),true);}
            return;
        }
        // Vanilla does not ask about Infinity here: a bow with Infinity and no arrow cannot be drawn either.
        if(server!=null){MetaServer.attempt(server,MetaMechanic.QUIVER);if(stage>=1)server.displayClientMessage(Component.translatable("meta.adaptationwheel.empty_quiver.left",0).withStyle(ChatFormatting.RED),true);}
        if(stage>=2&&event.getAction()==null){p.startUsingItem(event.getHand());event.setAction(InteractionResultHolder.consume(event.getBow()));}
    }
    /** Server: a bow released with nothing to shoot fires an arrow out of the wheel's pocket, mirroring BowItem.releaseUsing. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void loose(ArrowLooseEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.getAbilities().instabuild||!(event.getBow().getItem() instanceof BowItem bowItem))return;
        int stage=MetaHooks.stage(p,MetaMechanic.QUIVER);if(stage<2)return;
        var bow=event.getBow();if(!p.getProjectile(bow).isEmpty())return;
        // Infinity makes vanilla's hasAmmo true without an arrow; that case is ours too.
        event.setCanceled(true);
        float power=BowItem.getPowerForTime(event.getCharge());if(power<.1F)return;
        var level=p.level();
        var arrow=((ArrowItem)Items.ARROW).createArrow(level,new ItemStack(Items.ARROW),p);
        arrow=bowItem.customArrow(arrow);
        if(stage==2)power*=.5F;
        arrow.shootFromRotation(p,p.getXRot(),p.getYRot(),0F,power*3F,1F);
        if(power==1F)arrow.setCritArrow(true);
        int powerLevel=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,bow);
        if(powerLevel>0)arrow.setBaseDamage(arrow.getBaseDamage()+powerLevel*.5D+.5D);
        int punch=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS,bow);
        if(punch>0)arrow.setKnockback(punch);
        if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS,bow)>0)arrow.setSecondsOnFire(100);
        bow.hurtAndBreak(stage==2?3:stage==3?2:1,p,x->x.broadcastBreakEvent(p.getUsedItemHand()));
        arrow.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;
        level.addFreshEntity(arrow);
        level.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,1F,1F/(level.getRandom().nextFloat()*.4F+1.2F)+power*.5F);
        p.awardStat(Stats.ITEM_USED.get(bowItem));
    }

    // ---- COOLDOWN ---------------------------------------------------------------------------------------------

    private static boolean vanilla(Item item){var id=BuiltInRegistries.ITEM.getKey(item);return id!=null&&"minecraft".equals(id.getNamespace());}
    /** MetaNetwork: the client saw the wearer press use on an item on cooldown; the claim is verified here. */
    public static void reportedCooldown(ServerPlayer p){
        if(!MetaServer.active(p,MetaMechanic.COOLDOWN))return;
        for(var hand:InteractionHand.values()){
            var item=p.getItemInHand(hand).getItem();
            if(item!=Items.AIR&&vanilla(item)&&p.getCooldowns().isOnCooldown(item)){MetaServer.attempt(p,MetaMechanic.COOLDOWN);return;}
        }
    }
    private static Player owner(ItemCooldowns cooldowns){
        if(cooldowns instanceof ServerItemCooldowns){
            if(!cooldownOwnerTried){
                cooldownOwnerTried=true;
                try{cooldownOwner=ObfuscationReflectionHelper.findField(ServerItemCooldowns.class,"f_43065_");}catch(RuntimeException e){cooldownOwner=null;}
            }
            try{return cooldownOwner==null?null:(Player)cooldownOwner.get(cooldowns);}catch(ReflectiveOperationException|RuntimeException e){return null;}
        }
        return clientOwner.apply(cooldowns);
    }
    /** ItemCooldowns.addCooldown head: the duration the wearer's own cooldown map will really use. */
    public static int cooldown(ItemCooldowns cooldowns,Item item,int ticks){
        try{
            if(Boolean.TRUE.equals(FROM_SERVER.get())){FROM_SERVER.remove();return ticks;}
            if(ticks<=1)return ticks;
            var owner=owner(cooldowns);if(owner==null)return ticks;
            int stage=MetaHooks.stage(owner,MetaMechanic.COOLDOWN);
            if(stage<1||!vanilla(item))return ticks;
            // In double: a mod's very long lock must not overflow into a 1-tick cooldown.
            return (int)Math.max(1L,Math.round(ticks*(100D-CUT[stage])/100D));
        }catch(RuntimeException e){return ticks;}
    }
    /** ClientPacketListener.handleItemCooldown brackets its addCooldown call: that duration comes from the server, already cut. */
    public static void packetBegin(){FROM_SERVER.set(Boolean.TRUE);}
    public static void packetEnd(){FROM_SERVER.remove();}

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)LOW.remove(p);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){LOW.clear();PRICES.clear();}
}
