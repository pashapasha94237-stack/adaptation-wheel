package ru.pasha.adaptationwheel.server;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.*;
import ru.pasha.adaptationwheel.core.*;
import ru.pasha.adaptationwheel.net.WheelNetwork;
public final class AdaptationCommands {
 public static void register(CommandDispatcher<CommandSource> d){
  d.register(Commands.literal("adaptation").requires(s->s.getEntity() instanceof ServerPlayerEntity)
   .executes(c->menu(c.getSource())).then(Commands.literal("menu").executes(c->menu(c.getSource())))
   .then(Commands.literal("on").executes(c->toggle(c.getSource(),true))).then(Commands.literal("off").executes(c->toggle(c.getSource(),false)))
   .then(Commands.literal("reset").executes(c->reset(c.getSource()))).then(Commands.literal("status").executes(c->status(c.getSource()))));
 }
 private static int menu(CommandSource s)throws CommandSyntaxException{WheelNetwork.openMenu(s.getPlayerOrException());return 1;}
 private static int toggle(CommandSource s,boolean enabled)throws CommandSyntaxException{ServerPlayerEntity p=s.getPlayerOrException();AdaptationEvents.state(p).setEnabled(enabled);AdaptationEvents.discardQueuedDamage(p);AdaptationEvents.saveAndSync(p);s.sendSuccess(new TranslationTextComponent(enabled?"message.adaptationwheel.enabled":"message.adaptationwheel.disabled"),false);return 1;}
 private static int reset(CommandSource s)throws CommandSyntaxException{ServerPlayerEntity p=s.getPlayerOrException();AdaptationEvents.state(p).reset();AdaptationEvents.discardQueuedDamage(p);AdaptationEvents.saveAndSync(p);return 1;}
 private static int status(CommandSource s)throws CommandSyntaxException{for(AdaptationState.Snapshot e:AdaptationEvents.state(s.getPlayerOrException()).snapshots())s.sendSuccess(new TranslationTextComponent("gui.adaptationwheel.detail",DamageCategories.name(e.key()),e.reduction(),(int)Math.ceil(e.remaining()/20.0),String.format(java.util.Locale.ROOT,"%.2f",e.speed())),false);return 1;}
}