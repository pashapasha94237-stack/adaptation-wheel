package ru.pasha.multimodqa;

import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import static ru.pasha.multimodqa.MultiModQa.*;

/** Sword of Extermination worn on the forearm: screenshots in first and third person, idle, swing and parry. */
final class ForearmQa {
 static int step,ticks;
 static void run(ServerPlayer p)throws Exception{
  reset(p);p.serverLevel().setDayTime(6000);
  var sword=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("adaptationwheel","extermination_sword")));
  p.setItemInHand(InteractionHand.MAIN_HAND,sword);
  p.connection.teleport(p.getX(),p.getY(),p.getZ(),0,0);
 }
 static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"forearm-"+name+".png",mc.getMainRenderTarget(),m->{});LogUtils.getLogger().info("MULTIMOD_QA FOREARM SHOT {}",name);}
 // The client releases a used item as soon as the use key is up, so the key is held like a player would.
 static void use(boolean on){var mc=Minecraft.getInstance();mc.options.keyUse.setDown(on);if(on)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);else mc.gameMode.releaseUsingItem(mc.player);}
 static boolean clientTick()throws Exception{
  var mc=Minecraft.getInstance();ticks++;if(ticks>400)throw new AssertionError("Forearm QA timed out at "+step);
  mc.player.setYRot(step==5||step==6?-50:0);mc.player.setXRot(step>=3?0:10);
  switch(step){
   case 0->{mc.options.setCameraType(CameraType.FIRST_PERSON);if(ticks<60)return false;shot("fp-idle");mc.player.swing(InteractionHand.MAIN_HAND);step=1;ticks=0;}
   case 1->{if(ticks<3)return false;shot("fp-swing");step=2;ticks=0;}
   case 2->{if(ticks==10)use(true);if(ticks<25)return false;check(mc.player.isUsingItem(),"The blade parry did not start");shot("fp-guard");use(false);step=3;ticks=0;}
   // Third person: no HUD (the fixture's 1000 HP hearts cover the player) and a narrow view so the arm is large.
   case 3->{mc.options.hideGui=true;mc.options.fov().set(40);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);if(ticks<20)return false;shot("tp-front");step=4;ticks=0;}
   case 4->{if(ticks==5)use(true);if(ticks<20)return false;shot("tp-front-guard");use(false);step=5;ticks=0;}
   case 5->{if(ticks<20)return false;shot("tp-quarter");step=6;ticks=0;}
   case 6->{if(ticks==5)use(true);if(ticks<20)return false;shot("tp-quarter-guard");use(false);step=7;ticks=0;}
   case 7->{mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);if(ticks<20)return false;shot("tp-back");mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;mc.options.fov().set(70);
    LogUtils.getLogger().info("MULTIMOD_QA FOREARM PASS: worn blade rendered in first and third person, idle, swing and parry");return true;}
   default->{}
  }
  return false;
 }
}
