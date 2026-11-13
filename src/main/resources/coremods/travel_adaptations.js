// Travel adaptations (server/TravelAdaptations): a Riptide trident asks the wheel whether its holder counts as wet and
// how strong the dash is; an Eye of Ender tells the wheel who threw it and asks whether it survives its fall. Each
// patch touches one call or field read that occurs exactly once in its vanilla method; if another transformer
// reshaped a method, nothing matches there and vanilla behaviour remains.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes'),Node=Java.type('org.objectweb.asm.tree.AbstractInsnNode');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var H='ru/pasha/adaptationwheel/server/TravelAdaptations';
 var PLAYER='net/minecraft/world/entity/player/Player',EYE='net/minecraft/world/entity/projectile/EyeOfEnder',POS='net/minecraft/core/BlockPos';
 var USE='(Lnet/minecraft/world/level/Level;L'+PLAYER+';Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;';
 function named(name,official,srg){return name===official||name===srg||name===A.mapMethod(srg);}
 function method(m,official,srg,desc){return m.desc===desc&&named(m.name,official,srg);}
 function invokes(n,owner,official,srg,desc){return n.getType()===Node.METHOD_INSN&&n.owner===owner&&n.desc===desc&&named(n.name,official,srg);}
 // The single instruction of m passing the test; null when there is none or more than one.
 function only(m,test){var all=m.instructions.toArray(),hit=null;for(var k=0;k<all.length;k++)if(test(all[k])){if(hit!==null)return null;hit=all[k];}return hit;}
 function wet(n){return invokes(n,PLAYER,'isInWaterOrRain','m_20070_','()Z');}
 // Same stack shape: the receiver becomes the hook's first argument.
 function swap(m,n,name,desc){m.instructions.set(n,new Call(O.INVOKESTATIC,H,name,desc,false));}
 return {
  // TridentItem.use / releaseUsing: player.isInWaterOrRain() of a Riptide trident becomes riptideUse / riptideRelease(player),
  // and the dash player.push(x, y, z) becomes riptidePush(player, x, y, z).
  'travel_riptide':{target:{type:'CLASS',name:'net.minecraft.world.item.TridentItem'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i),n;
    if(method(m,'use','m_7203_',USE)){
     n=only(m,wet);if(n!==null)swap(m,n,'riptideUse','(L'+PLAYER+';)Z');
    }else if(method(m,'releaseUsing','m_5551_','(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V')){
     n=only(m,wet);if(n!==null)swap(m,n,'riptideRelease','(L'+PLAYER+';)Z');
     n=only(m,function(x){return invokes(x,PLAYER,'push','m_5997_','(DDD)V');});if(n!==null)swap(m,n,'riptidePush','(L'+PLAYER+';DDD)V');
    }
   }return node;
  }},
  // EnderEyeItem.use: eye.signalTo(target) becomes signal(eye, target, player); the player is argument 2.
  'travel_ender_eye_throw':{target:{type:'CLASS',name:'net.minecraft.world.item.EnderEyeItem'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'use','m_7203_',USE))continue;
    var n=only(m,function(x){return invokes(x,EYE,'signalTo','m_36967_','(L'+POS+';)V');});if(n===null)continue;
    var l=new List();l.add(new Var(O.ALOAD,2));l.add(new Call(O.INVOKESTATIC,H,'signal','(L'+EYE+';L'+POS+';L'+PLAYER+';)V',false));
    m.instructions.insertBefore(n,l);m.instructions.remove(n);m.maxStack+=1;
   }return node;
  }},
  // EyeOfEnder.tick reads surviveAfterDeath once, when the eye ends: the value passes through eyeSurvives(value, eye).
  'travel_ender_eye_fall':{target:{type:'CLASS',name:'net.minecraft.world.entity.projectile.EyeOfEnder'},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);
    if(!method(m,'tick','m_8119_','()V'))continue;
    var n=only(m,function(x){return x.getOpcode()===O.GETFIELD&&x.owner===EYE&&x.desc==='Z'&&(x.name==='surviveAfterDeath'||x.name==='f_36954_'||x.name===A.mapField('f_36954_'));});
    if(n===null)continue;
    var l=new List();l.add(new Var(O.ALOAD,0));l.add(new Call(O.INVOKESTATIC,H,'eyeSurvives','(ZL'+EYE+';)Z',false));
    m.instructions.insert(n,l);m.maxStack+=1;
   }return node;
  }}
 };
}
