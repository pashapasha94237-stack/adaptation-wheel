function initializeCoreMod(){
 var O=Java.type('org.objectweb.asm.Opcodes'),A=Java.type('net.minecraftforge.coremod.api.ASMAPI');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode');
 var Frame=Java.type('org.objectweb.asm.tree.FrameNode'),Ldc=Java.type('org.objectweb.asm.tree.LdcInsnNode');
 var helper='ru/pasha/adaptationwheel/server/HazardCompatibility',living='Lnet/minecraft/world/entity/LivingEntity;';
 var defs=A.loadData('compat/mod-attacks.json').classes,out={};
 function guard(m,patch){var next=new Label();patch.add(new Jump(O.IFEQ,next));patch.add(new Insn(O.RETURN));patch.add(next);patch.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(patch);m.maxStack+=3;}
 function target(name,transform){out[name]={target:{type:'CLASS',name:name.replace(/\//g,'.')},transformer:transform};}
 target('com/github/alexthe666/iceandfire/entity/props/FrozenData',function(node){
  for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n),p=new List();
   if(m.name==='setFrozen'&&m.desc==='('+living+'I)V'){
    p.add(new Var(O.ALOAD,1));p.add(new Var(O.ILOAD,2));p.add(new Call(O.INVOKESTATIC,helper,'freezeDuration','('+living+'I)I',false));p.add(new Var(O.ISTORE,2));
    var next=new Label();p.add(new Var(O.ILOAD,2));p.add(new Call(O.INVOKESTATIC,helper,'freezeCanceled','(I)Z',false));p.add(new Jump(O.IFEQ,next));p.add(new Insn(O.RETURN));p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(p);m.maxStack+=2;
   }else if(m.name==='tickFrozen'&&m.desc==='('+living+')V'){
    p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,helper,'iceTick','(Ljava/lang/Object;'+living+')Z',false));guard(m,p);
   }
  }return node;
 });
 target('com/github/alexthe666/iceandfire/entity/props/SirenData',function(node){
  for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n);if(m.name!=='tickCharmed'||m.desc!=='('+living+')V')continue;
   var p=new List();p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,helper,'sirenTick','(Ljava/lang/Object;'+living+')Z',false));guard(m,p);
   for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext())if(ins.getOpcode()===O.INVOKEVIRTUAL&&(ins.name==='m_20256_'||ins.name==='setDeltaMovement')&&ins.desc==='(Lnet/minecraft/world/phys/Vec3;)V'){
    ins.setOpcode(O.INVOKESTATIC);ins.owner=helper;ins.name='sirenMotion';ins.desc='(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V';ins.itf=false;
   }
  }return node;
 });
 ['AHuntOnEntityTickUpdateProcedure','AHuntShadowOnEntityTickUpdateProcedure'].forEach(function(short){
  target('net/mcreator/anomalyrephased/procedures/'+short,function(node){
   for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n);
    for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
     if(ins.getOpcode()!==O.INVOKEVIRTUAL)continue;
     if((ins.name==='m_7292_'||ins.name==='addEffect')&&ins.desc==='(Lnet/minecraft/world/effect/MobEffectInstance;)Z'){
      ins.setOpcode(O.INVOKESTATIC);ins.owner=helper;ins.name='anomalyEffect';ins.desc='('+living+'Lnet/minecraft/world/effect/MobEffectInstance;)Z';ins.itf=false;
     }else if((ins.name==='m_6074_'||ins.name==='kill')&&ins.desc==='()V'&&m.name.indexOf('lambda$execute$')===0&&m.desc==='(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/LevelAccessor;DDD)V'){
      m.instructions.insertBefore(ins,new Var(O.ALOAD,0));ins.setOpcode(O.INVOKESTATIC);ins.owner=helper;ins.name='anomalyKill';ins.desc='(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V';ins.itf=false;m.maxStack++;
     }
    }
   }return node;
  });
 });
 target('com/bobmowzie/mowziesmobs/server/capability/FrozenCapability$FrozenCapabilityImp',function(node){
  for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n),p=new List();
   if(m.name==='addFreezeProgress'&&m.desc==='('+living+'F)V'){
    p.add(new Var(O.ALOAD,1));p.add(new Var(O.FLOAD,2));p.add(new Call(O.INVOKESTATIC,helper,'mowzieAmount','('+living+'F)F',false));p.add(new Var(O.FSTORE,2));m.instructions.insert(p);m.maxStack+=2;
   }else if(m.name==='onFreeze'&&m.desc==='('+living+')V'){
    p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,helper,'mowzieFreeze','('+living+')Z',false));guard(m,p);
   }else if(m.name==='tick'&&m.desc==='('+living+')V'){
    p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,helper,'mowzieTick','(Ljava/lang/Object;'+living+')Z',false));guard(m,p);
   }
  }return node;
 });
 target('weather2/weathersystem/storm/StormObject',function(node){
  for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n);
   if(m.name==='setVel'&&m.desc==='(Ljava/lang/Object;FFF)V'){
    var push=new List();push.add(new Var(O.ALOAD,1));push.add(new Var(O.FLOAD,2));push.add(new Var(O.FLOAD,3));push.add(new Var(O.FLOAD,4));
    push.add(new Call(O.INVOKESTATIC,helper,'tornadoPush','(Ljava/lang/Object;FFF)Z',false));guard(m,push);continue;
   }
   if(m.name!=='spinEntity'&&m.name!=='spinEntityv2')continue;
   for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
    if(ins.getOpcode()!==O.INVOKEVIRTUAL)continue;
    var vec=(ins.name==='m_20256_'||ins.name==='setDeltaMovement')&&ins.desc==='(Lnet/minecraft/world/phys/Vec3;)V';
    var xyz=(ins.name==='m_20334_'||ins.name==='setDeltaMovement')&&ins.desc==='(DDD)V';
    if(!vec&&!xyz)continue;ins.setOpcode(O.INVOKESTATIC);ins.owner=helper;ins.name=vec?'tornadoMotion':'tornadoMotion3';ins.desc=vec?'(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V':'(Lnet/minecraft/world/entity/Entity;DDD)V';ins.itf=false;
   }
  }return node;
 });
 ['OxygenApiImpl','TemperatureApiImpl'].forEach(function(short){var owner='earth/terrarium/adastra/common/systems/'+short;
  target(owner,function(node){var rules=defs[owner].methods;
   for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n),rule=rules[m.name+m.desc];
    if(!rule)continue;if(short==='OxygenApiImpl'&&m.name!=='entityTick')continue;if(short==='TemperatureApiImpl'&&m.name!=='burnEntity'&&m.name!=='freezeEntity')continue;
    var p=new List();p.add(new Var(O.ALOAD,short==='OxygenApiImpl'?2:1));p.add(new Ldc(rule.key));p.add(new Call(O.INVOKESTATIC,helper,'atmosphere','(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;)Z',false));guard(m,p);
   }return node;
  });
 });
 target('net/eternal_tales/init/EternalTalesModBiomes',function(node){
  for(var n=0;n<node.methods.size();n++){var m=node.methods.get(n);if(m.name!=='addSurfaceRule')continue;
   for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
    if(ins.getOpcode()===O.INVOKEINTERFACE&&ins.owner==='java/util/List'&&ins.name==='add'&&ins.desc==='(ILjava/lang/Object;)V'){
     ins.setOpcode(O.INVOKESTATIC);ins.owner=helper;ins.name='insertSurfaceRule';ins.desc='(Ljava/util/List;ILjava/lang/Object;)V';ins.itf=false;
    }
   }
  }return node;
 });
 return out;
}
