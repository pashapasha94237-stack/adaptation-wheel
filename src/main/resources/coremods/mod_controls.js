function initializeCoreMod(){
 var O=Java.type('org.objectweb.asm.Opcodes'),A=Java.type('net.minecraftforge.coremod.api.ASMAPI');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var Label=Java.type('org.objectweb.asm.tree.LabelNode'),Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var H='ru/pasha/adaptationwheel/server/MobControlCompatibility',E='Lnet/minecraft/world/entity/Entity;',V='Lnet/minecraft/world/phys/Vec3;',out={};
 function target(owner,kind){out[owner]={target:{type:'CLASS',name:owner.replace(/\//g,'.')},transformer:function(node){
  for(var n=0;n<node.methods.size();n++){
   var m=node.methods.get(n),name=m.name;
   if(kind==='soul'&&name==='applyEffectToEntity'&&m.desc==='(Lnet/minecraft/world/entity/LivingEntity;)V'&&(m.access&O.ACC_ABSTRACT)===0){
    var p=new List(),next=new Label();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/ModAttackCompatibility','skipProjectileEffect','('+E+'Lnet/minecraft/world/entity/LivingEntity;)Z',false));p.add(new Jump(O.IFEQ,next));p.add(new Insn(O.RETURN));p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(p);m.maxStack+=2;
   }
   if(kind==='krampus'&&name==='execute'&&m.desc==='(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD'+E+E+')V'){
    var p=new List(),next=new Label();p.add(new Var(O.ALOAD,8));p.add(new Var(O.ALOAD,9));p.add(new Call(O.INVOKESTATIC,H,'krampus','('+E+E+')Z',false));p.add(new Jump(O.IFEQ,next));p.add(new Insn(O.RETURN));p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(p);m.maxStack+=2;
   }
   if(kind==='snake'&&name==='setStrangling'&&m.desc==='(Z)V'){
    var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ILOAD,1));p.add(new Call(O.INVOKESTATIC,H,'strangling','('+E+'Z)Z',false));p.add(new Var(O.ISTORE,1));m.instructions.insert(p);m.maxStack+=2;
   }
   if(kind==='forsaken'&&name==='setHeldMobId'&&m.desc==='(I)V'){
    var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ILOAD,1));p.add(new Call(O.INVOKESTATIC,H,'heldId','('+E+'I)I',false));p.add(new Var(O.ISTORE,1));m.instructions.insert(p);m.maxStack+=2;
   }
   var tick=(name==='m_8119_'||name==='tick'||name===A.mapMethod('m_8119_'))&&m.desc==='()V';
   if(tick&&(kind==='snake'||kind==='forsaken')){var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Call(O.INVOKESTATIC,H,kind==='snake'?'snakeTick':'forsakenTick','('+E+')V',false));m.instructions.insert(p);m.maxStack++;}
   for(var i=m.instructions.getFirst();i!==null;i=i.getNext()){
    if(kind==='aoacharge'&&name==='tick'&&i.getOpcode()===O.INVOKESTATIC&&i.owner==='net/tslat/aoa3/util/EntityUtil'&&i.name==='pushEntityAway'&&i.desc==='('+E+E+'F)V'){
     i.owner=H;i.name='aoaChargePush';
    }else if(kind==='guardian'&&name==='collideWithEntities'&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_5997_'||i.name==='push'||i.name===A.mapMethod('m_5997_'))&&i.desc==='(DDD)V'){
     m.instructions.insertBefore(i,new Var(O.ALOAD,0));i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name='guardianPush';i.desc='('+E+'DDD'+E+')V';i.itf=false;m.maxStack++;
    }else if(kind==='hydra'&&name==='executeAttacks'&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_45933_'||i.name==='getEntities'||i.name===A.mapMethod('m_45933_'))&&i.desc==='('+E+'Lnet/minecraft/world/phys/AABB;)Ljava/util/List;'){
     i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name='hydraBiteTargets';i.desc='(Lnet/minecraft/world/level/Level;'+E+'Lnet/minecraft/world/phys/AABB;)Ljava/util/List;';i.itf=false;
    }else if(kind==='wave'&&name==='attackEntities'&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_147240_'||i.name==='knockback')&&i.desc==='(DDD)V'){
     m.instructions.insertBefore(i,new Var(O.ALOAD,0));i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name='waveKnockback';i.desc='(Lnet/minecraft/world/entity/LivingEntity;DDD'+E+')V';i.itf=false;m.maxStack++;
    }else if(kind==='capture'&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_7998_'||i.name==='startRiding')&&i.desc==='('+E+'Z)Z'){
     i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name='capture';i.desc='('+E+E+'Z)Z';i.itf=false;
    }else if(kind==='capture'&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_8127_'||i.name==='stopRiding')&&i.desc==='()V'&&(node.name.indexOf('PinchBeetle')>=0||node.name.indexOf('ThrowRiderGoal')>=0)){
     // Only the snatching path, never native release/creative escape paths.
     if((node.name.indexOf('PinchBeetle')>=0&&(name==='m_7327_'||name===A.mapMethod('m_7327_')))||node.name.indexOf('ThrowRiderGoal')>=0&&(name==='m_6739_'||name===A.mapMethod('m_6739_'))){
      m.instructions.insertBefore(i,new Var(O.ALOAD,0));i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name='captureDismount';i.desc='('+E+'Ljava/lang/Object;)V';i.itf=false;m.maxStack++;
     }
    }else if(tick&&(kind==='snake'||kind==='forsaken')&&i.getOpcode()===O.INVOKEVIRTUAL&&(i.name==='m_20256_'||i.name==='setDeltaMovement')&&i.desc==='('+V+')V'){
     i.setOpcode(O.INVOKESTATIC);i.owner=H;i.name=kind==='snake'?'snakeMotion':'forsakenMotion';i.desc='('+E+V+')V';i.itf=false;
    }else if(kind==='gorgon'&&(name==='m_8107_'||name==='aiStep'||name===A.mapMethod('m_8107_'))&&i.getOpcode()===O.INVOKESTATIC&&i.owner===node.name&&i.name==='isEntityLookingAt'){
     i.owner=H;i.name='gorgonLooking';
    }
   }
  }return node;
 }};}
 target('com/github/alexthe666/alexsmobs/entity/EntityCrocodile','capture');
 target('twilightforest/entity/monster/PinchBeetle','capture');
 target('twilightforest/entity/ai/goal/ThrowRiderGoal','capture');
 target('com/github/alexthe666/alexsmobs/entity/EntityAnaconda','snake');
 target('com/github/alexmodguy/alexscaves/server/entity/living/ForsakenEntity','forsaken');
 target('com/github/alexthe666/iceandfire/entity/EntityGorgon','gorgon');
 target('net/mcreator/borninchaosv/procedures/KrampusAttackProcedure','krampus');
 target('com/github/alexmodguy/alexscaves/server/entity/item/WaveEntity','wave');
 target('twilightforest/entity/boss/HydraHeadContainer','hydra');
 target('com/brandon3055/draconicevolution/entity/guardian/DraconicGuardianEntity','guardian');
 target('net/tslat/aoa3/content/entity/brain/task/custom/ChargeAttack','aoacharge');
 var projectiles=A.loadData('compat/mod-attacks.json').projectiles;
 Object.keys(projectiles).forEach(function(id){if(id.indexOf('sculkhorde:soul_')===0)target(projectiles[id].entity,'soul');});
 return out;
}
