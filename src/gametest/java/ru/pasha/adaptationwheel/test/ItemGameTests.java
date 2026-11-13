package ru.pasha.adaptationwheel.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingGetProjectileEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.items.ItemStackHandler;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.server.*;

/** Broken tools, anvil limit, empty quiver and item cooldowns. Coremods are active in the GameTest server. */
@GameTestHolder(AdaptationWheel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemGameTests {
    // ---- BROKEN_TOOL ----
    @GameTest(template="empty",timeoutTicks=60)
    public static void brokenToolBreaksUntilTheSecondStage(GameTestHelper h) {
        var plain=player(h,null);var tool=nearlyBroken();
        check(h,breakIt(plain,tool),"An item at its last point of durability did not break");
        check(h,AdaptationEvents.state(plain).pending().containsKey(MetaMechanic.BROKEN_TOOL.key),"A broken item did not start the adaptation");
        var first=player(h,null);learn(first,MetaMechanic.BROKEN_TOOL,1);var other=nearlyBroken();
        check(h,breakIt(first,other),"Stage one already saved a broken item");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brokenToolIsRestoredPerStage(GameTestHelper h) {
        int[] percent={0,0,25,50,100};
        for(int stage=2;stage<=4;stage++){
            var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.BROKEN_TOOL,stage);var tool=nearlyBroken();
            check(h,!breakIt(p,tool)&&tool.getCount()==1,"Stage "+stage+" let the item break");
            int max=tool.getMaxDamage(),left=max-tool.getDamageValue();
            check(h,Math.abs(left-max*percent[stage]/100.0)<=1,"Stage "+stage+" restored "+left+" of "+max+", wanted "+percent[stage]+"%");
            if(stage==4)check(h,tool.getDamageValue()==0,"Stage four did not restore the item completely");
            check(h,said(sent,"meta.adaptationwheel.broken_tool.saved"),"Stage "+stage+" showed no message");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brokenToolRestoreStopsWhenSwitchedOff(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.BROKEN_TOOL,4);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.BROKEN_TOOL.key,false);
        check(h,breakIt(p,nearlyBroken()),"A disabled adaptation still saved a broken item");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.BROKEN_TOOL.key,true);
        check(h,!breakIt(p,nearlyBroken()),"Enabling the entry again did not restore it");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,breakIt(p,nearlyBroken()),"An item broke under a removed wheel");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void armourIsRestoredToo(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.BROKEN_TOOL,3);
        var boots=new ItemStack(Items.IRON_BOOTS);boots.setDamageValue(boots.getMaxDamage()-1);
        boots.hurtAndBreak(1,p,x->{});
        check(h,!boots.isEmpty()&&boots.getMaxDamage()-boots.getDamageValue()>=boots.getMaxDamage()/2-1,"Armour was not restored at stage three");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brokenElytraIsRestored(GameTestHelper h) {
        // Vanilla stops an elytra one point before breaking; that point is its broken state.
        var plain=player(h,null);var worn=new ItemStack(Items.ELYTRA);worn.setDamageValue(worn.getMaxDamage()-2);
        worn.hurtAndBreak(1,plain,x->{});
        check(h,!worn.isEmpty()&&!ElytraItem.isFlyEnabled(worn),"Vanilla elytra did not stop at its last point");
        check(h,AdaptationEvents.state(plain).pending().containsKey(MetaMechanic.BROKEN_TOOL.key),"A worn-out elytra did not start the adaptation");
        var p=player(h,null);learn(p,MetaMechanic.BROKEN_TOOL,4);var elytra=new ItemStack(Items.ELYTRA);elytra.setDamageValue(elytra.getMaxDamage()-2);
        elytra.hurtAndBreak(1,p,x->{});
        check(h,elytra.getDamageValue()==0&&ElytraItem.isFlyEnabled(elytra),"Stage four did not restore a worn-out elytra: damage "+elytra.getDamageValue());
        var fine=new ItemStack(Items.ELYTRA);fine.setDamageValue(10);fine.hurtAndBreak(1,p,x->{});
        check(h,fine.getDamageValue()==11,"A healthy elytra was touched");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void brokenToolWarnsOncePerItem(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.BROKEN_TOOL,1);
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);p.setItemInHand(InteractionHand.MAIN_HAND,tool);
        for(int i=0;i<20;i++)tick(p);check(h,!said(sent,"meta.adaptationwheel.broken_tool.low"),"A fresh tool triggered the warning");
        tool.setDamageValue(tool.getMaxDamage()-tool.getMaxDamage()/10);
        for(int i=0;i<20;i++)tick(p);check(h,said(sent,"meta.adaptationwheel.broken_tool.low"),"A tool at ten percent did not trigger the warning");
        sent.clear();for(int i=0;i<40;i++)tick(p);check(h,!said(sent,"meta.adaptationwheel.broken_tool.low"),"The warning repeated for the same item");
        tool.setDamageValue(0);for(int i=0;i<20;i++)tick(p);
        tool.setDamageValue(tool.getMaxDamage()-2);for(int i=0;i<20;i++)tick(p);
        check(h,said(sent,"meta.adaptationwheel.broken_tool.low"),"A repaired and worn-down tool did not warn again");
        h.succeed();
    }

    // ---- ANVIL ----
    @GameTest(template="empty",timeoutTicks=100)
    public static void anvilLimitGrowsWithTheStage(GameTestHelper h) {
        var vanilla=player(h,null);
        check(h,!offers(vanilla,45),"Vanilla price 46 was offered without the adaptation");
        check(h,AdaptationEvents.state(vanilla).pending().containsKey(MetaMechanic.ANVIL.key),"A too expensive result did not start the adaptation");
        var cheap=player(h,null);offers(cheap,10);
        check(h,!AdaptationEvents.state(cheap).pending().containsKey(MetaMechanic.ANVIL.key),"A cheap result started the adaptation");
        var one=player(h,null);learn(one,MetaMechanic.ANVIL,1);check(h,!offers(one,45),"Stage one already raised the limit");
        var two=player(h,null);learn(two,MetaMechanic.ANVIL,2);
        check(h,offers(two,45)&&!offers(two,55),"Stage two limit is not 50");
        var three=player(h,null);learn(three,MetaMechanic.ANVIL,3);
        check(h,offers(three,55)&&!offers(three,70),"Stage three limit is not 60");
        var four=player(h,null);learn(four,MetaMechanic.ANVIL,4);
        check(h,offers(four,70)&&offers(four,200),"Stage four still has a limit");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void anvilStillChargesTheFullPrice(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.ANVIL,4);var menu=anvil(p,70);int cost=menu.getCost();
        check(h,cost>=71&&!menu.getSlot(2).getItem().isEmpty(),"No expensive result: "+cost);
        check(h,!menu.getSlot(2).mayPickup(p),"The result was free to take without levels");
        p.giveExperienceLevels(cost+5);int before=p.experienceLevel;
        check(h,menu.getSlot(2).mayPickup(p),"The result could not be taken with enough levels");
        menu.getSlot(2).onTake(p,menu.getSlot(2).getItem());
        check(h,p.experienceLevel==before-cost,"The wearer paid "+(before-p.experienceLevel)+" levels instead of "+cost);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void anvilLimitStopsWhenSwitchedOff(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.ANVIL,4);check(h,offers(p,70),"Stage four did not raise the limit");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.ANVIL.key,false);
        check(h,!offers(p,45),"A disabled entry still raised the limit");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.ANVIL.key,true);check(h,offers(p,70),"Re-enabling did not restore the limit");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(h,!offers(p,45),"A removed wheel still raised the limit");
        h.succeed();
    }

    // ---- QUIVER ----
    @GameTest(template="empty",timeoutTicks=100)
    public static void emptyBowRefusesUntilStageTwo(GameTestHelper h) {
        var p=player(h,null);var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
        check(h,bow.use(p.level(),p,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL,"A bow was drawn without arrows");
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.QUIVER.key),"Drawing without arrows did not start the adaptation");
        var one=player(h,null);learn(one,MetaMechanic.QUIVER,1);one.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
        check(h,one.getItemInHand(InteractionHand.MAIN_HAND).use(one.level(),one,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL&&!one.isUsingItem(),"Stage one drew an empty bow");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void emptyBowShootsFromStageTwo(GameTestHelper h) {
        int[] wear={0,0,3,2,1};
        for(int stage=2;stage<=4;stage++){
            var p=player(h,null);learn(p,MetaMechanic.QUIVER,stage);var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
            var result=bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,result.getResult()==InteractionResult.CONSUME&&p.isUsingItem(),"Stage "+stage+" did not draw the empty bow");
            bow.releaseUsing(p.level(),p,72000-25);p.stopUsingItem();
            var arrows=arrowsOf(h,p);
            check(h,arrows.size()==1,"Stage "+stage+" shot "+arrows.size()+" arrows");
            var arrow=arrows.get(0);
            check(h,arrow.pickup==AbstractArrow.Pickup.CREATIVE_ONLY,"An arrow from nothing could be picked up");
            check(h,arrow.isCritArrow()==(stage>=3),"Stage "+stage+" power is wrong (crit="+arrow.isCritArrow()+")");
            check(h,bow.getDamageValue()==wear[stage],"Stage "+stage+" cost "+bow.getDamageValue()+" durability");
            arrow.discard();
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void arrowFromNothingKeepsEnchantments(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.QUIVER,4);var bow=new ItemStack(Items.BOW);
        bow.enchant(Enchantments.POWER_ARROWS,2);bow.enchant(Enchantments.PUNCH_ARROWS,1);bow.enchant(Enchantments.FLAMING_ARROWS,1);
        p.setItemInHand(InteractionHand.MAIN_HAND,bow);bow.use(p.level(),p,InteractionHand.MAIN_HAND);bow.releaseUsing(p.level(),p,72000-25);p.stopUsingItem();
        var arrows=arrowsOf(h,p);check(h,arrows.size()==1,"No arrow was shot");
        var arrow=arrows.get(0);
        check(h,Math.abs(arrow.getBaseDamage()-3.5)<1e-6,"Power was not applied: "+arrow.getBaseDamage());
        check(h,arrow.getRemainingFireTicks()>0,"Flame was not applied");
        arrow.discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void bowWithArrowsStaysVanilla(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.QUIVER,4);var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
        p.getInventory().add(new ItemStack(Items.ARROW,5));
        bow.use(p.level(),p,InteractionHand.MAIN_HAND);bow.releaseUsing(p.level(),p,72000-25);p.stopUsingItem();
        var arrows=arrowsOf(h,p);check(h,arrows.size()==1,"Wrong number of arrows");
        check(h,arrows.get(0).pickup==AbstractArrow.Pickup.ALLOWED,"A real arrow became unpickable");
        check(h,bow.getDamageValue()==1,"A real shot costs one durability");
        check(h,countArrows(p)==4,"A real arrow was not used up: "+countArrows(p));
        arrows.get(0).discard();h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void quiverStopsWhenSwitchedOff(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.QUIVER,4);var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.QUIVER.key,false);
        check(h,bow.use(p.level(),p,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL,"A disabled entry drew an empty bow");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.QUIVER.key,true);
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        check(h,bow.use(p.level(),p,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL,"A removed wheel drew an empty bow");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lowArrowCountIsShownFromStageOne(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.QUIVER,1);
        var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);p.getInventory().add(new ItemStack(Items.ARROW,3));
        bow.use(p.level(),p,InteractionHand.MAIN_HAND);
        check(h,said(sent,"meta.adaptationwheel.empty_quiver.left","3"),"The arrow count was not shown");
        p.stopUsingItem();sent.clear();p.getInventory().add(new ItemStack(Items.ARROW,60));
        bow.use(p.level(),p,InteractionHand.MAIN_HAND);
        check(h,!said(sent,"meta.adaptationwheel.empty_quiver.left"),"A full quiver was reported");
        p.stopUsingItem();
        // The edge of "few": 7 arrows are counted, 8 are not.
        var edge=new ArrayList<Packet<?>>();var seven=player(h,edge);learn(seven,MetaMechanic.QUIVER,1);
        var other=new ItemStack(Items.BOW);seven.setItemInHand(InteractionHand.MAIN_HAND,other);seven.getInventory().add(new ItemStack(Items.ARROW,7));
        other.use(seven.level(),seven,InteractionHand.MAIN_HAND);
        check(h,said(edge,"meta.adaptationwheel.empty_quiver.left","7"),"Seven arrows were not counted");
        seven.stopUsingItem();edge.clear();seven.getInventory().add(new ItemStack(Items.ARROW,1));
        other.use(seven.level(),seven,InteractionHand.MAIN_HAND);
        check(h,!said(edge,"meta.adaptationwheel.empty_quiver.left"),"Eight arrows were reported as few");
        seven.stopUsingItem();
        // Before stage one nothing is said.
        var quiet=new ArrayList<Packet<?>>();var fresh=player(h,quiet);
        var plain=new ItemStack(Items.BOW);fresh.setItemInHand(InteractionHand.MAIN_HAND,plain);fresh.getInventory().add(new ItemStack(Items.ARROW,3));
        plain.use(fresh.level(),fresh,InteractionHand.MAIN_HAND);
        check(h,fresh.isUsingItem(),"The bow was not drawn before stage one");
        check(h,!said(quiet,"meta.adaptationwheel.empty_quiver.left"),"The arrow count was shown before stage one");
        fresh.stopUsingItem();
        // An Infinity bow never uses the arrow up: nothing to warn about.
        var endless=new ArrayList<Packet<?>>();var q=player(h,endless);learn(q,MetaMechanic.QUIVER,1);
        var infinite=new ItemStack(Items.BOW);infinite.enchant(Enchantments.INFINITY_ARROWS,1);
        q.setItemInHand(InteractionHand.MAIN_HAND,infinite);q.getInventory().add(new ItemStack(Items.ARROW,1));
        infinite.use(q.level(),q,InteractionHand.MAIN_HAND);
        check(h,q.isUsingItem(),"The Infinity bow was not drawn");
        check(h,!said(endless,"meta.adaptationwheel.empty_quiver.left"),"An Infinity bow was warned about running out of arrows");
        q.stopUsingItem();endless.clear();
        // A spectral arrow goes first and is used up, but Infinity keeps the plain ones after it.
        q.getInventory().setItem(1,new ItemStack(Items.SPECTRAL_ARROW,1));q.getInventory().setItem(2,new ItemStack(Items.ARROW,3));
        infinite.use(q.level(),q,InteractionHand.MAIN_HAND);
        check(h,q.isUsingItem(),"The Infinity bow was not drawn with a spectral arrow");
        check(h,!said(endless,"meta.adaptationwheel.empty_quiver.left"),"An Infinity bow was warned about plain arrows it never runs out of");
        q.stopUsingItem();
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void arrowsInsideAQuiverAreCounted(GameTestHelper h) {
        var sent=new ArrayList<Packet<?>>();var p=player(h,sent);learn(p,MetaMechanic.QUIVER,1);
        var bow=new ItemStack(Items.BOW);p.setItemInHand(InteractionHand.MAIN_HAND,bow);
        // Like Supplementaries' quiver: an item with its own inventory, whose arrow the bow is handed.
        var pouch=new ItemStackHandler(2);pouch.setStackInSlot(0,new ItemStack(Items.ARROW,5));
        // Item stacks gather capabilities lazily, on the first query, so the stack is recognised by identity.
        var quiver=new ItemStack(Items.BUNDLE);
        Consumer<AttachCapabilitiesEvent<ItemStack>> attach=e->{if(e.getObject()==quiver)e.addCapability(new ResourceLocation(AdaptationWheel.MOD_ID,"test_quiver"),new ICapabilityProvider(){
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability,LazyOptional.of(()->pouch));}
        });};
        ItemStack[] fed={null}; // handed instead of the quiver's first arrow when set
        Consumer<LivingGetProjectileEvent> feed=e->{if(e.getEntity()==p&&e.getProjectileItemStack().isEmpty())e.setProjectileItemStack(fed[0]!=null?fed[0]:pouch.getStackInSlot(0));};
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class,attach);MinecraftForge.EVENT_BUS.addListener(feed);
        try{
            // Set straight into a slot: Inventory.add would store a copy, built without this capability.
            p.getInventory().setItem(9,quiver);
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,p.isUsingItem(),"The bow was not drawn with arrows in the quiver");
            check(h,said(sent,"meta.adaptationwheel.empty_quiver.left","5"),"The arrows inside the quiver were not counted");
            p.stopUsingItem();sent.clear();p.getInventory().setItem(9,ItemStack.EMPTY);
            // The same arrow from a source nobody can see into: no count rather than a wrong one.
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,p.isUsingItem(),"The bow was not drawn with an arrow from elsewhere");
            check(h,!said(sent,"meta.adaptationwheel.empty_quiver.left"),"An arrow count was shown for a source that cannot be counted");
            p.stopUsingItem();sent.clear();
            // Loose arrows go first and the quiver takes over when they run out: counting only the loose ones would be wrong.
            p.getInventory().setItem(9,quiver);p.getInventory().setItem(1,new ItemStack(Items.ARROW,3));
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,p.isUsingItem(),"The bow was not drawn with loose arrows");
            check(h,!said(sent,"meta.adaptationwheel.empty_quiver.left"),"Only the loose arrows were counted while a quiver with arrows waited");
            p.stopUsingItem();sent.clear();
            // The same loose arrows next to an empty quiver: now the count is exact.
            pouch.setStackInSlot(0,ItemStack.EMPTY);
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,said(sent,"meta.adaptationwheel.empty_quiver.left","3"),"Loose arrows next to an empty quiver were not counted");
            p.stopUsingItem();sent.clear();p.getInventory().setItem(1,ItemStack.EMPTY);
            // Every arrow in the quiver counts, whatever kind comes first...
            pouch.setStackInSlot(0,new ItemStack(Items.SPECTRAL_ARROW,1));pouch.setStackInSlot(1,new ItemStack(Items.ARROW,3));
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,said(sent,"meta.adaptationwheel.empty_quiver.left","4"),"Mixed arrows inside the quiver were not counted");
            p.stopUsingItem();sent.clear();
            // ...but with Infinity the plain ones after the spectral arrow are never used up.
            var infinite=new ItemStack(Items.BOW);infinite.enchant(Enchantments.INFINITY_ARROWS,1);p.setItemInHand(InteractionHand.MAIN_HAND,infinite);
            infinite.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,p.isUsingItem(),"The Infinity bow was not drawn from the quiver");
            check(h,!said(sent,"meta.adaptationwheel.empty_quiver.left"),"An Infinity bow was warned about the plain arrows in its quiver");
            p.stopUsingItem();sent.clear();
            // Handed something the bow does not count as an arrow (a mod bow's own ammo): no count, not "0".
            p.setItemInHand(InteractionHand.MAIN_HAND,bow);p.getInventory().setItem(9,ItemStack.EMPTY);
            fed[0]=new ItemStack(Items.FIREWORK_ROCKET);p.getInventory().setItem(5,fed[0]);
            bow.use(p.level(),p,InteractionHand.MAIN_HAND);
            check(h,p.isUsingItem(),"The bow was not drawn with what it was handed");
            check(h,!said(sent,"meta.adaptationwheel.empty_quiver.left"),"An arrow count was shown for ammo that is not arrows");
            p.stopUsingItem();
        }finally{MinecraftForge.EVENT_BUS.unregister(attach);MinecraftForge.EVENT_BUS.unregister(feed);}
        h.succeed();
    }

    // ---- COOLDOWN ----
    @GameTest(template="empty",timeoutTicks=100)
    public static void vanillaCooldownsGetShorterPerStage(GameTestHelper h) {
        int[] cut={0,15,30,40,50};
        for(int stage=0;stage<=4;stage++){
            var p=player(h,null);if(stage>0)learn(p,MetaMechanic.COOLDOWN,stage);
            var cooldowns=p.getCooldowns();cooldowns.addCooldown(Items.ENDER_PEARL,200);
            int expected=Math.round(200*(100-cut[stage])/100F);
            for(int i=0;i<expected-1;i++)cooldowns.tick();
            check(h,cooldowns.isOnCooldown(Items.ENDER_PEARL),"Stage "+stage+" cooldown ended early (wanted "+expected+" ticks)");
            cooldowns.tick();
            check(h,!cooldowns.isOnCooldown(Items.ENDER_PEARL),"Stage "+stage+" cooldown lasted longer than "+expected+" ticks");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void hugeCooldownIsCutWithoutOverflow(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.COOLDOWN,1);var cooldowns=p.getCooldowns();
        cooldowns.addCooldown(Items.ENDER_PEARL,1_000_000_000);for(int i=0;i<1000;i++)cooldowns.tick();
        check(h,cooldowns.isOnCooldown(Items.ENDER_PEARL)&&cooldowns.getCooldownPercent(Items.ENDER_PEARL,0)>.99F,"A mod's very long lock became a short cooldown");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void foreignAndDisabledCooldownsStayFull(GameTestHelper h) {
        var p=player(h,null);learn(p,MetaMechanic.COOLDOWN,4);var cooldowns=p.getCooldowns();
        var foreign=AdaptationWheel.WHEEL.get();cooldowns.addCooldown(foreign,200);
        for(int i=0;i<199;i++)cooldowns.tick();
        check(h,cooldowns.isOnCooldown(foreign),"A foreign item's cooldown was shortened");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.COOLDOWN.key,false);
        cooldowns.addCooldown(Items.CHORUS_FRUIT,100);for(int i=0;i<99;i++)cooldowns.tick();
        check(h,cooldowns.isOnCooldown(Items.CHORUS_FRUIT),"A disabled entry still shortened the cooldown");
        AdaptationEvents.state(p).setCategoryEnabled(MetaMechanic.COOLDOWN.key,true);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        cooldowns.addCooldown(Items.ENDER_PEARL,100);for(int i=0;i<99;i++)cooldowns.tick();
        check(h,cooldowns.isOnCooldown(Items.ENDER_PEARL),"A removed wheel still shortened the cooldown");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void shieldCooldownIsHalved(GameTestHelper h) {
        // Player.disableShield adds this very cooldown, but only by chance; the 100 ticks are vanilla's.
        var p=player(h,null);learn(p,MetaMechanic.COOLDOWN,4);
        p.getCooldowns().addCooldown(Items.SHIELD,100);
        for(int i=0;i<49;i++)p.getCooldowns().tick();
        check(h,p.getCooldowns().isOnCooldown(Items.SHIELD),"The shield cooldown ended early");
        p.getCooldowns().tick();
        check(h,!p.getCooldowns().isOnCooldown(Items.SHIELD),"The shield cooldown was not halved");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void reportedCooldownIsVerifiedByTheServer(GameTestHelper h) {
        var p=player(h,null);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL));
        ItemAdaptations.reportedCooldown(p);
        check(h,!AdaptationEvents.state(p).pending().containsKey(MetaMechanic.COOLDOWN.key),"A claim without a cooldown started the adaptation");
        p.getCooldowns().addCooldown(Items.ENDER_PEARL,100);
        ItemAdaptations.reportedCooldown(p);
        check(h,AdaptationEvents.state(p).pending().containsKey(MetaMechanic.COOLDOWN.key),"A real use during a cooldown did not start the adaptation");
        var foreign=player(h,null);foreign.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AdaptationWheel.WHEEL.get()));
        foreign.getCooldowns().addCooldown(AdaptationWheel.WHEEL.get(),100);ItemAdaptations.reportedCooldown(foreign);
        check(h,!AdaptationEvents.state(foreign).pending().containsKey(MetaMechanic.COOLDOWN.key),"A foreign item counted");
        h.succeed();
    }

    // ---- helpers ----
    private static ItemStack nearlyBroken(){var stack=new ItemStack(Items.IRON_PICKAXE);stack.setDamageValue(stack.getMaxDamage()-1);return stack;}
    private static boolean breakIt(ServerPlayer p,ItemStack stack){stack.hurtAndBreak(1,p,x->{});return stack.isEmpty();}
    private static AnvilMenu anvil(ServerPlayer p,int repairCost){
        var menu=new AnvilMenu(7,p.getInventory());var tool=new ItemStack(Items.IRON_PICKAXE);tool.setRepairCost(repairCost);
        menu.getSlot(0).set(tool);menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.BLOCK_EFFICIENCY,1)));
        return menu;
    }
    private static boolean offers(ServerPlayer p,int repairCost){return !anvil(p,repairCost).getSlot(2).getItem().isEmpty();}
    private static List<AbstractArrow> arrowsOf(GameTestHelper h,ServerPlayer p){
        return h.getLevel().getEntitiesOfClass(AbstractArrow.class,new AABB(p.blockPosition()).inflate(6),a->a.getOwner()==p);
    }
    private static int countArrows(ServerPlayer p){int n=0;for(var stack:p.getInventory().items)if(stack.is(Items.ARROW))n+=stack.getCount();return n;}
    private static boolean said(List<Packet<?>> sent,String key){return said(sent,key,null);}
    private static boolean said(List<Packet<?>> sent,String key,String firstArgument){
        for(var packet:sent)if(packet instanceof ClientboundSystemChatPacket chat&&chat.overlay()&&chat.content().getContents() instanceof TranslatableContents text&&text.getKey().equals(key)
                &&(firstArgument==null||text.getArgs().length>0&&String.valueOf(text.getArgs()[0]).equals(firstArgument)))return true;
        return false;
    }
    private static void learn(ServerPlayer p,MetaMechanic m,int stage){
        AdaptationEvents.state(p).hit(m.key,false,AdaptationProfile.ENVIRONMENT);
        int guard=40000;while(AdaptationEvents.state(p).stage(m.key)<stage&&guard-->0)AdaptationEvents.state(p).tick();
        if(guard<=0)throw new AssertionError("Timer did not finish "+m.key);
    }
    private static void check(GameTestHelper h,boolean value,String message){if(!value)h.fail(message);}
    private static void tick(ServerPlayer p){p.tickCount++;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    /** Packets for the player (system chat is what the action bar uses) go to {@code sent} when it is not null. */
    private static ServerPlayer player(GameTestHelper h,List<Packet<?>> sent){
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ItemTest"));
        var connection=new Connection(PacketFlow.SERVERBOUND);
        p.connection=sent==null?new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p):new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p){
            @Override public void send(Packet<?> packet){sent.add(packet);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){sent.add(packet);}
        };
        for(int i=0;i<60;i++)p.tick(); // Expire vanilla login protection before exercising real damage.
        p.setPos(0,100,0);p.setHealth(20);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AdaptationWheel.WHEEL.get()));
        if(sent!=null)sent.clear();
        return p;
    }
}
