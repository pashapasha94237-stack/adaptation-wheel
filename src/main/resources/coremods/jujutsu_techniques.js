// Optional v51 target guards. Original mod files remain untouched; allies and casters retain original rules.
function initializeCoreMod(){
 var O=Java.type('org.objectweb.asm.Opcodes'),Type=Java.type('org.objectweb.asm.Type');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Ldc=Java.type('org.objectweb.asm.tree.LdcInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode');
 var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var helper='ru/pasha/adaptationwheel/server/JujutsuCompatibility',entity='Lnet/minecraft/world/entity/Entity;';
 var output={};
 function slots(method){var args=Type.getArgumentTypes(method.desc),slot=(method.access&O.ACC_STATIC)!==0?0:1,result=[];for(var n=0;n<args.length;n++){if(args[n].getClassName()==='net.minecraft.world.entity.Entity')result.push(slot);slot+=args[n].getSize();}return result;}
 function target(name,mode,id){output[name]={target:{type:'CLASS',name:'net.mcreator.jujutsucraft.procedures.'+name},transformer:function(node){
  for(var n=0;n<node.methods.size();n++){
   var m=node.methods.get(n);if(m.name!=='execute')continue;var es=slots(m);if(es.length<2)continue;
   var caster=es[es.length-2],victim=es[es.length-1];
   if(mode==='target' && Type.getReturnType(m.desc).getSort()===Type.BOOLEAN){
    for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext())if(ins.getOpcode()===O.IRETURN){
     var patch=new List();patch.add(new Var(O.ALOAD,caster));patch.add(new Var(O.ALOAD,victim));patch.add(new Insn(id==='domain'?O.ICONST_1:O.ICONST_0));
     patch.add(new Call(O.INVOKESTATIC,helper,'allowTarget','(Z'+entity+entity+'Z)Z',false));m.instructions.insertBefore(ins,patch);
    }m.maxStack+=3;
   }else if(mode==='projectile'&&Type.getReturnType(m.desc).getSort()===Type.VOID){
    // Entry capture is sufficient and does not alter the original owner/strength initialization.
    var patch=new List();patch.add(new Var(O.ALOAD,caster));patch.add(new Var(O.ALOAD,victim));patch.add(new Call(O.INVOKESTATIC,helper,'projectile','('+entity+entity+')V',false));m.instructions.insert(patch);m.maxStack=Math.max(2,m.maxStack);
   }else if(mode==='guard'&&Type.getReturnType(m.desc).getSort()===Type.VOID){
    var patch=new List(),proceed=new Label();patch.add(new Var(O.ALOAD,caster));patch.add(new Var(O.ALOAD,victim));patch.add(new Ldc(id));
    patch.add(new Call(O.INVOKESTATIC,helper,'skipTargetEffect','('+entity+entity+'Ljava/lang/String;)Z',false));patch.add(new Jump(O.IFEQ,proceed));patch.add(new Insn(O.RETURN));patch.add(proceed);patch.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(patch);m.maxStack=Math.max(3,m.maxStack);
   }
  }return node;
 }};}
 target('LogicAttackProcedure','target','');target('LogicAttackDomainProcedure','target','domain');
 target('SetRangedAmmoProcedure','projectile','');
 target('EffectProcedure','guard','');target('EffectCharactorProcedure','guard','');
 target('EffectCursedSpeechProcedure','guard','');
 target('EffectCursedSpeechExplodeProcedure','guard','305');target('EffectCursedSpeechGetCrushedProcedure','guard','306');
 target('EffectCursedSpeechCrumbleAwayProcedure','guard','307');target('EffectCursedSpeechDontMoveProcedure','guard','308');target('EffectCursedSpeechBlastAwayProcedure','guard','309');
 // Scope Infinity's hasEffect checks to the attacker; never strip the effect globally.
 function infinityQueries(name,sourceKind){output['infinity_'+name]={target:{type:'CLASS',name:'net.mcreator.jujutsucraft.procedures.'+name},transformer:function(node){
  for(var n=0;n<node.methods.size();n++){
   var m=node.methods.get(n);if(m.name!=='execute')continue;
   var args=Type.getArgumentTypes(m.desc),slot=(m.access&O.ACC_STATIC)!==0?0:1,source=-1;
   for(var a=0;a<args.length;a++){
    var className=args[a].getClassName();
    if(sourceKind==='damage'&&className==='net.minecraft.world.damagesource.DamageSource')source=slot;
    if(sourceKind==='caster'&&className==='net.minecraft.world.entity.Entity'&&source<0)source=slot;
    slot+=args[a].getSize();
   }
   if(source<0)continue;var changed=false;
   // A spatial cut already owns its fixed damage. JJC's normal-melee rewrite
   // would replace its source and reapply armor/resistance and weapon attributes.
   if(name==='WhenEntityAttacked2Procedure'){
    var cut=new List(),normal=new Label();cut.add(new Var(O.ALOAD,source));
    cut.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/WorldSlash','isAttack','(Lnet/minecraft/world/damagesource/DamageSource;)Z',false));
    cut.add(new Jump(O.IFEQ,normal));cut.add(new Insn(O.RETURN));cut.add(normal);cut.add(new Frame(O.F_SAME,0,null,0,null));
    m.instructions.insert(cut);m.maxStack=Math.max(1,m.maxStack);
   }
   for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
    if(ins.getOpcode()!==O.INVOKEVIRTUAL||(ins.name!=='m_21023_'&&ins.name!=='hasEffect')||ins.desc!=='(Lnet/minecraft/world/effect/MobEffect;)Z')continue;
    m.instructions.insertBefore(ins,new Var(O.ALOAD,source));ins.setOpcode(O.INVOKESTATIC);
    ins.owner='ru/pasha/adaptationwheel/server/InfinityAdaptation';ins.name=sourceKind==='damage'?'hasEffectForAttack':'hasEffectForCaster';
    ins.desc='(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffect;'+(sourceKind==='damage'?'Lnet/minecraft/world/damagesource/DamageSource;':entity)+')Z';ins.itf=false;changed=true;
   }if(changed)m.maxStack++;
  }return node;
 }};}
 infinityQueries('WhenEntityAttacked1Procedure','damage');infinityQueries('WhenEntityAttacked2Procedure','damage');
 infinityQueries('EffectConfilmCharactorProcedure','caster');infinityQueries('KnockbackProcedure','caster');
 output.infinity_field={target:{type:'CLASS',name:'net.mcreator.jujutsucraft.procedures.InfinityActiveTickProcedure'},transformer:function(node){
  for(var n=0;n<node.methods.size();n++){
   var m=node.methods.get(n);if(m.name!=='execute')continue;var es=slots(m);if(es.length!==1)continue;
   for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
    if((ins.name!=='m_6443_'&&ins.name!=='getEntitiesOfClass')||ins.desc!=='(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;')continue;
    m.instructions.insertBefore(ins,new Var(O.ALOAD,es[0]));ins.setOpcode(O.INVOKESTATIC);ins.owner='ru/pasha/adaptationwheel/server/InfinityAdaptation';ins.name='candidates';
    ins.desc='(Lnet/minecraft/world/level/LevelAccessor;Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;'+entity+')Ljava/util/List;';ins.itf=false;m.maxStack++;
   }
  }return node;
 }};
 return output;
}

