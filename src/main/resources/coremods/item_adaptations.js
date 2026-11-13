// Item adaptations (server/ItemAdaptations, client/ItemAdaptationsClient): a broken item can be restored, the anvil price
// limit follows the wheel, vanilla cooldowns get shorter. Every patch only inserts a hook call next to one uniquely
// identified instruction and keeps all original ones; if another transformer reshaped a method, nothing matches there
// and vanilla behaviour remains.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes'),Node=Java.type('org.objectweb.asm.tree.AbstractInsnNode');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Field=Java.type('org.objectweb.asm.tree.FieldInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var H='ru/pasha/adaptationwheel/server/ItemAdaptations',C='ru/pasha/adaptationwheel/client/ItemAdaptationsClient';
 var STACK='net/minecraft/world/item/ItemStack',COOLDOWNS='net/minecraft/world/item/ItemCooldowns',ABILITIES='net/minecraft/world/entity/player/Abilities';
 var COMPONENT='Lnet/minecraft/network/chat/Component;';
 function named(name,official,srg){return name===official||name===srg||name===A.mapMethod(srg);}
 function method(m,official,srg,desc){return m.desc===desc&&named(m.name,official,srg);}
 function invokes(n,owner,official,srg,desc){return n.getType()===Node.METHOD_INSN&&n.owner===owner&&n.desc===desc&&named(n.name,official,srg);}
 function fieldNamed(n,official,srg){return n.name===official||n.name===srg||n.name===A.mapField(srg);}
 function instabuild(n){return n.getOpcode()===O.GETFIELD&&n.owner===ABILITIES&&fieldNamed(n,'instabuild','f_35937_');}
 function real(m){var all=m.instructions.toArray(),out=[];for(var i=0;i<all.length;i++)if(all[i].getOpcode()>=0)out.push(all[i]);return out;}
 // "cost >= 40 && !...abilities.instabuild": bipush 40, if_icmplt, then the instabuild read a few instructions later.
 // Calls visit(instabuildNode, instructionsBetween, priceRead) once per match; the vanilla method has exactly one.
 function afterPriceCheck(m,visit){
  var r=real(m);
  for(var k=1;k+1<r.length;k++){
   if(r[k].getOpcode()===O.BIPUSH&&r[k].operand===40&&r[k+1].getOpcode()===O.IF_ICMPLT){
    for(var j=k+2;j<r.length&&j<=k+7;j++)if(instabuild(r[j])){visit(r[j],r.slice(k+2,j),r[k-1]);break;}
   }
  }
 }
 return {
  // Right after ItemStack.hurt(...) in hurtAndBreak: its boolean ("the item broke") passes through a hook that may turn it false.
  'item_adaptations_break':{target:{type:'CLASS',name:'net.minecraft.world.item.ItemStack'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'hurtAndBreak','m_41622_','(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V'))continue;
    var done=false,all=m.instructions.toArray();
    for(var k=0;k<all.length&&!done;k++){var n=all[k];
     if(invokes(n,STACK,'hurt','m_220157_','(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z')){
      done=true;var l=new List();l.add(new Var(O.ALOAD,0));l.add(new Var(O.ALOAD,2));
      l.add(new Call(O.INVOKESTATIC,H,'afterHurt','(ZL'+STACK+';Lnet/minecraft/world/entity/LivingEntity;)Z',false));
      m.instructions.insert(n,l);m.maxStack+=2;
     }
    }
   }return node;
  }},
  // The price check of the result slot: the instabuild boolean also goes through anvilAllows(boolean, menu, player).
  'item_adaptations_anvil_menu':{target:{type:'CLASS',name:'net.minecraft.world.inventory.AnvilMenu'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'createResult','m_6640_','()V'))continue;
    afterPriceCheck(m,function(n,between){
     var playerRead=null;
     for(var w=0;w<between.length;w++)if(between[w].getOpcode()===O.GETFIELD&&between[w].desc==='Lnet/minecraft/world/entity/player/Player;'&&fieldNamed(between[w],'player','f_39771_'))playerRead=between[w];
     if(playerRead===null)return;
     var l=new List();l.add(new Var(O.ALOAD,0));l.add(new Var(O.ALOAD,0));l.add(new Field(O.GETFIELD,playerRead.owner,playerRead.name,playerRead.desc));
     l.add(new Call(O.INVOKESTATIC,H,'anvilAllows','(ZLnet/minecraft/world/inventory/AnvilMenu;Lnet/minecraft/world/entity/player/Player;)Z',false));
     m.instructions.insert(n,l);m.maxStack+=3;
    });
   }return node;
  }},
  // Client screen: the same price check decides the label, and the "Too Expensive!" text can become one with the price.
  'item_adaptations_anvil_screen':{target:{type:'CLASS',name:'net.minecraft.client.gui.screens.inventory.AnvilScreen'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'renderLabels','m_280003_','(Lnet/minecraft/client/gui/GuiGraphics;II)V'))continue;
    afterPriceCheck(m,function(n){
     var l=new List();l.add(new Call(O.INVOKESTATIC,C,'screenAllows','(Z)Z',false));m.instructions.insert(n,l);m.maxStack=Math.max(m.maxStack,2);
    });
    var all=m.instructions.toArray();
    for(var k=0;k<all.length;k++){var n=all[k];
     if(n.getOpcode()===O.GETSTATIC&&n.owner==='net/minecraft/client/gui/screens/inventory/AnvilScreen'&&n.desc===COMPONENT&&fieldNamed(n,'TOO_EXPENSIVE_TEXT','f_97870_')){
      var l=new List();l.add(new Call(O.INVOKESTATIC,C,'label','('+COMPONENT+')'+COMPONENT,false));m.instructions.insert(n,l);break;
     }
    }
   }return node;
  }},
  // Sophisticated Backpacks: the anvil upgrade tab draws its own price line over the AnvilMenu it wraps (whose
  // createResult is patched above). Same verdict and label as AnvilScreen, for the price the tab keeps in a local.
  'item_adaptations_backpack_anvil':{target:{type:'CLASS',name:'net.p3pp3rf1y.sophisticatedbackpacks.upgrades.anvil.AnvilUpgradeTab'},transformer:function(node){
   var owner='net/p3pp3rf1y/sophisticatedbackpacks/upgrades/anvil/AnvilUpgradeTab';
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(m.name!=='renderCost'||m.desc!=='(Lnet/minecraft/client/gui/GuiGraphics;II)V')continue;
    var cost=-1;
    afterPriceCheck(m,function(n,between,price){
     if(price.getOpcode()!==O.ILOAD)return;cost=price.var;
     var l=new List();l.add(new Var(O.ILOAD,cost));l.add(new Call(O.INVOKESTATIC,C,'screenAllows','(ZI)Z',false));m.instructions.insert(n,l);m.maxStack=Math.max(m.maxStack,2);
    });
    if(cost<0)continue;
    var all=m.instructions.toArray();
    for(var k=0;k<all.length;k++){var n=all[k];
     if(n.getOpcode()===O.GETSTATIC&&n.owner===owner&&n.name==='TOO_EXPENSIVE_TEXT'&&n.desc===COMPONENT){
      var l=new List();l.add(new Var(O.ILOAD,cost));l.add(new Call(O.INVOKESTATIC,C,'label','('+COMPONENT+'I)'+COMPONENT,false));m.instructions.insert(n,l);m.maxStack=Math.max(m.maxStack,2);break;
     }
    }
   }return node;
  }},
  // Head of ItemCooldowns.addCooldown(Item, int): the duration argument is replaced by the shortened one.
  'item_adaptations_cooldown':{target:{type:'CLASS',name:'net.minecraft.world.item.ItemCooldowns'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'addCooldown','m_41524_','(Lnet/minecraft/world/item/Item;I)V'))continue;
    var l=new List();l.add(new Var(O.ALOAD,0));l.add(new Var(O.ALOAD,1));l.add(new Var(O.ILOAD,2));
    l.add(new Call(O.INVOKESTATIC,H,'cooldown','(L'+COOLDOWNS+';Lnet/minecraft/world/item/Item;I)I',false));l.add(new Var(O.ISTORE,2));
    m.instructions.insert(l);m.maxStack=Math.max(m.maxStack,4);
   }return node;
  }},
  // The server sends the already shortened duration; the client's own addCooldown for that packet must not cut it again.
  'item_adaptations_cooldown_packet':{target:{type:'CLASS',name:'net.minecraft.client.multiplayer.ClientPacketListener'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'handleItemCooldown','m_7701_','(Lnet/minecraft/network/protocol/game/ClientboundCooldownPacket;)V'))continue;
    var all=m.instructions.toArray();
    for(var k=0;k<all.length;k++){var n=all[k];
     if(invokes(n,COOLDOWNS,'addCooldown','m_41524_','(Lnet/minecraft/world/item/Item;I)V')){
      var before=new List(),after=new List();
      before.add(new Call(O.INVOKESTATIC,H,'packetBegin','()V',false));after.add(new Call(O.INVOKESTATIC,H,'packetEnd','()V',false));
      m.instructions.insertBefore(n,before);m.instructions.insert(n,after);
     }
    }
   }return node;
  }}
 };
}
