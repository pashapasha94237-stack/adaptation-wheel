// Unlimited stacks in the wearer's own inventory after the full inventory pressure adaptation (StackLimits).
// Each patch rewrites single calls where they stand; if another transformer reshaped a method, nothing matches there
// and vanilla limits remain. Counts above 127 also need the byte in item NBT and in item packets widened.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes'),Node=Java.type('org.objectweb.asm.tree.AbstractInsnNode');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Field=Java.type('org.objectweb.asm.tree.FieldInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
 var Label=Java.type('org.objectweb.asm.tree.LabelNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var H='ru/pasha/adaptationwheel/StackLimits',STACK='net/minecraft/world/item/ItemStack',SLOT='net/minecraft/world/inventory/Slot';
 var INV='net/minecraft/world/entity/player/Inventory',BUF='net/minecraft/network/FriendlyByteBuf',TAG='net/minecraft/nbt/CompoundTag';
 var LSTACK='L'+STACK+';',LSLOT='L'+SLOT+';';
 function named(name,official,srg){return name===official||name===srg||name===A.mapMethod(srg);}
 function method(m,official,srg,desc){return m.desc===desc&&named(m.name,official,srg);}
 function invokes(n,owner,official,srg,desc){return n.getType()===Node.METHOD_INSN&&n.owner===owner&&n.desc===desc&&named(n.name,official,srg);}
 function replace(m,n,nodes){var l=new List();for(var i=0;i<nodes.length;i++)l.add(nodes[i]);m.instructions.insertBefore(n,l);m.instructions.remove(n);m.maxStack+=2;}
 function each(m,visit){var all=m.instructions.toArray();for(var i=0;i<all.length;i++)visit(all[i],all,i);}
 return {
  'stack_limits_inventory':{target:{type:'CLASS',name:'net.minecraft.world.entity.player.Inventory'},transformer:function(node){
   // add / addResource / hasRemainingSpaceForItem / placeItemBackInInventory: both the item's limit and the inventory's 64.
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if((m.access&O.ACC_STATIC)!==0)continue;
    each(m,function(n){
     if(invokes(n,STACK,'getMaxStackSize','m_41741_','()I'))replace(m,n,[new Var(O.ALOAD,0),new Call(O.INVOKESTATIC,H,'item','('+LSTACK+'L'+INV+';)I',false)]);
     else if(invokes(n,INV,'getMaxStackSize','m_6893_','()I'))replace(m,n,[new Call(O.INVOKESTATIC,H,'container','(L'+INV+';)I',false)]);
    });
   }return node;
  }},
  'stack_limits_slot':{target:{type:'CLASS',name:'net.minecraft.world.inventory.Slot'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(!method(m,'getMaxStackSize','m_5866_','('+LSTACK+')I'))continue;
    var p=new List(),next=new Label();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));
    p.add(new Call(O.INVOKESTATIC,H,'lifted','('+LSLOT+LSTACK+')Z',false));p.add(new Jump(O.IFEQ,next));
    p.add(new Field(O.GETSTATIC,'ru/pasha/adaptationwheel/core/StackCount','LIMIT','I'));p.add(new Insn(O.IRETURN));
    p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(p);m.maxStack=Math.max(2,m.maxStack);
   }return node;
  }},
  'stack_limits_shift_click':{target:{type:'CLASS',name:'net.minecraft.world.inventory.AbstractContainerMenu'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(!method(m,'moveItemStackTo','m_38903_','('+LSTACK+'IIZ)Z'))continue;
    each(m,function(n){
     if(invokes(n,SLOT,'getMaxStackSize','m_6641_','()I'))replace(m,n,[new Var(O.ALOAD,1),new Call(O.INVOKESTATIC,H,'slotLimit','('+LSLOT+LSTACK+')I',false)]);
     else if(invokes(n,STACK,'getMaxStackSize','m_41741_','()I'))replace(m,n,[new Call(O.INVOKESTATIC,H,'unbounded','('+LSTACK+')I',false)]);
    });
   }return node;
  }},
  'stack_limits_item_nbt':{target:{type:'CLASS',name:'net.minecraft.world.item.ItemStack'},transformer:function(node){
   function count(all,i){for(var k=i-1;k>=0&&k>=i-8;k--)if(all[k].getType()===Node.LDC_INSN&&all[k].cst==='Count')return true;return false;}
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(method(m,'save','m_41739_','(L'+TAG+';)L'+TAG+';'))each(m,function(n,all,k){
     if(invokes(n,TAG,'putByte','m_128344_','(Ljava/lang/String;B)V')&&k>0&&all[k-1].getOpcode()===O.I2B&&count(all,k)){
      m.instructions.remove(all[k-1]);replace(m,n,[new Call(O.INVOKESTATIC,H,'putCount','(L'+TAG+';Ljava/lang/String;I)V',false)]);}
    });
    else if(m.name==='<init>'&&m.desc==='(L'+TAG+';)V')each(m,function(n,all,k){
     if(invokes(n,TAG,'getByte','m_128445_','(Ljava/lang/String;)B')&&count(all,k))replace(m,n,[new Call(O.INVOKEVIRTUAL,TAG,A.mapMethod('m_128451_'),'(Ljava/lang/String;)I',false)]);
    });
   }return node;
  }},
  'stack_limits_item_packets':{target:{type:'CLASS',name:'net.minecraft.network.FriendlyByteBuf'},transformer:function(node){
   // A VarInt below 128 is the very same single byte, so ordinary stacks keep vanilla's wire format.
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i),done=false;
    if(m.name==='writeItemStack'&&m.desc==='('+LSTACK+'Z)L'+BUF+';')each(m,function(n){
     if(!done&&invokes(n,BUF,'writeByte','writeByte','(I)Lio/netty/buffer/ByteBuf;')){done=true;replace(m,n,[new Call(O.INVOKEVIRTUAL,BUF,A.mapMethod('m_130130_'),'(I)L'+BUF+';',false)]);}
    });
    else if(method(m,'readItem','m_130267_','()'+LSTACK))each(m,function(n){
     if(!done&&invokes(n,BUF,'readByte','readByte','()B')){done=true;replace(m,n,[new Call(O.INVOKEVIRTUAL,BUF,A.mapMethod('m_130242_'),'()I',false)]);}
    });
   }return node;
  }},
  'stack_limits_creative':{target:{type:'CLASS',name:'net.minecraft.server.network.ServerGamePacketListenerImpl'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i),done=false;
    if(!method(m,'handleSetCreativeModeSlot','m_5964_','(Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;)V'))continue;
    each(m,function(n){
     if(!done&&n.getOpcode()===O.BIPUSH&&n.operand===64){done=true;
      replace(m,n,[new Var(O.ALOAD,0),new Field(O.GETFIELD,'net/minecraft/server/network/ServerGamePacketListenerImpl',A.mapField('f_9743_'),'Lnet/minecraft/server/level/ServerPlayer;'),
       new Call(O.INVOKESTATIC,H,'creativeLimit','(Lnet/minecraft/world/entity/player/Player;)I',false)]);}
    });
   }return node;
  }},
  'stack_limits_count_label':{target:{type:'CLASS',name:'net.minecraft.client.gui.GuiGraphics'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i),done=false;
    if(!method(m,'renderItemDecorations','m_280302_','(Lnet/minecraft/client/gui/Font;'+LSTACK+'IILjava/lang/String;)V'))continue;
    each(m,function(n){
     if(n.getOpcode()===O.INVOKESTATIC&&n.owner==='java/lang/String'&&n.name==='valueOf'&&n.desc==='(I)Ljava/lang/String;')
      replace(m,n,[new Call(O.INVOKESTATIC,H,'countText','(I)Ljava/lang/String;',false)]);
     // The first text drawn is the count; wide labels are drawn smaller (client.StackLabels).
     else if(!done&&invokes(n,'net/minecraft/client/gui/GuiGraphics','drawString','m_280056_','(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I')){done=true;
      replace(m,n,[new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/client/StackLabels','draw','(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I',false)]);}
    });
   }return node;
  }}
 };
}
