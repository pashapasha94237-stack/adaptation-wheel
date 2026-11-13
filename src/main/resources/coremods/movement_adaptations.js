// Movement and world adaptations (MovementAdaptations): sticky blocks, portal wait time, water in the Nether.
// Every patch only inserts a static hook call; no original instruction is removed. A target that is not found
// (another transformer or OptiFine reshaped it) is left untouched, so vanilla behaviour stays.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes'),Node=Java.type('org.objectweb.asm.tree.AbstractInsnNode');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var H='ru/pasha/adaptationwheel/server/MovementAdaptations',ENTITY='net/minecraft/world/entity/Entity',PLAYER='net/minecraft/world/entity/player/Player';
 var VEC='net/minecraft/world/phys/Vec3',LEVEL='net/minecraft/world/level/Level',POS='net/minecraft/core/BlockPos',BUCKET='net/minecraft/world/item/BucketItem';
 function named(name,official,srg){return name===official||name===srg||name===A.mapMethod(srg);}
 function method(m,official,srg,desc){return m.desc===desc&&named(m.name,official,srg);}
 function each(m,visit){var all=m.instructions.toArray();for(var i=0;i<all.length;i++)visit(all[i]);}
 function before(m,n,nodes){var l=new List();for(var i=0;i<nodes.length;i++)l.add(nodes[i]);m.instructions.insertBefore(n,l);m.maxStack+=nodes.length+1;}
 function after(m,n,nodes){var l=new List();for(var i=0;i<nodes.length;i++)l.add(nodes[i]);m.instructions.insert(n,l);m.maxStack+=nodes.length+1;}
 // float getBlockSpeedFactor()/getBlockJumpFactor(): every FRETURN passes through hook(float, Entity). LivingEntity (Soul Speed)
 // and Player override these and call super, so patching Entity covers them and the Soul Speed case already returns 1.
 function floatFactor(node,official,srg,hook){
  for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(!method(m,official,srg,'()F'))continue;
   each(m,function(n){if(n.getOpcode()===O.FRETURN)before(m,n,[new Var(O.ALOAD,0),new Call(O.INVOKESTATIC,H,hook,'(FL'+ENTITY+';)F',false)]);});
  }
 }
 return {
  'movement_entity':{target:{type:'CLASS',name:'net.minecraft.world.entity.Entity'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    // makeStuckInBlock(BlockState,Vec3): replace the Vec3 argument (local 2) before anything reads it.
    if(method(m,'makeStuckInBlock','m_7601_','(Lnet/minecraft/world/level/block/state/BlockState;L'+VEC+';)V')){
     var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,2));p.add(new Call(O.INVOKESTATIC,H,'stuck','(L'+ENTITY+';L'+VEC+';)L'+VEC+';',false));p.add(new Var(O.ASTORE,2));
     m.instructions.insert(p);m.maxStack=Math.max(m.maxStack,2);
    }
   }
   floatFactor(node,'getBlockSpeedFactor','m_6041_','speed');
   floatFactor(node,'getBlockJumpFactor','m_20098_','jump');
   return node;
  }},
  'movement_player_portal':{target:{type:'CLASS',name:'net.minecraft.world.entity.player.Player'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(!method(m,'getPortalWaitTime','m_6078_','()I'))continue;
    each(m,function(n){if(n.getOpcode()===O.IRETURN)before(m,n,[new Var(O.ALOAD,0),new Call(O.INVOKESTATIC,H,'portalWait','(IL'+PLAYER+';)I',false)]);});
   }return node;
  }},
  // BucketItem.emptyContents(Player,Level,BlockPos,BlockHitResult,ItemStack): water evaporates in an ultra-warm dimension.
  // Forge asks FluidType.isVaporizedOnPlacement first (bucket with contents), vanilla's DimensionType.ultraWarm() after it.
  // Both results go through fizzles(boolean,Player,Level,BlockPos,BucketItem); locals 0..3 are this, player, level, pos.
  'movement_nether_water':{target:{type:'CLASS',name:'net.minecraft.world.item.BucketItem'},transformer:function(node){
   var desc='(L'+PLAYER+';L'+LEVEL+';L'+POS+';Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z';
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(m.name!=='emptyContents'||m.desc!==desc)continue;
    each(m,function(n){
     if(n.getType()!==Node.METHOD_INSN||n.getOpcode()!==O.INVOKEVIRTUAL)return;
     var warm=n.owner==='net/minecraft/world/level/dimension/DimensionType'&&n.desc==='()Z'&&(n.name==='ultraWarm'||n.name==='f_63857_'||n.name===A.mapMethod('f_63857_'));
     var vapor=n.owner==='net/minecraftforge/fluids/FluidType'&&n.name==='isVaporizedOnPlacement';
     if(warm||vapor)after(m,n,[new Var(O.ALOAD,1),new Var(O.ALOAD,2),new Var(O.ALOAD,3),new Var(O.ALOAD,0),
      new Call(O.INVOKESTATIC,H,'fizzles','(ZL'+PLAYER+';L'+LEVEL+';L'+POS+';L'+BUCKET+';)Z',false)]);
    });
   }return node;
  }}
 };
}
