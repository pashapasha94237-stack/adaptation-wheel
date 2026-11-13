// These harmful instant overrides mutate cold/casting state before any ordinary hurt()
// callback. Guard their entries; retain every original native invocation for other mixins.
// Attribute application/removal, expiry and useful/neutral callbacks are not transformed.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var owners=[
  'com.mna.effects.harmful.EffectInstantChill',
  'com.mna.effects.harmful.EffectManaBurn',
  'net.dakotapride.garnished.effect.FreezingMobEffect',
  'com.yungnickyoung.minecraft.yungscavebiomes.effect.FrostMobEffect'
 ],out={};
 owners.forEach(function(owner){out[owner]={target:{type:'CLASS',name:owner},transformer:function(node){
  for(var i=0;i<node.methods.size();i++){
   var m=node.methods.get(i);
   if(m.desc!=='(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V'||
      (m.name!=='m_19461_'&&m.name!=='applyInstantenousEffect'&&m.name!==A.mapMethod('m_19461_'))||
      (m.access&(O.ACC_STATIC|O.ACC_ABSTRACT|O.ACC_NATIVE))!==0)continue;
   var patch=new List(),next=new Label();patch.add(new Var(O.ALOAD,0));patch.add(new Var(O.ALOAD,3));patch.add(new Var(O.ILOAD,4));
   patch.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/ExtraEffects','skipInstant','(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;I)Z',false));
   patch.add(new Jump(O.IFEQ,next));patch.add(new Insn(O.RETURN));patch.add(next);patch.add(new Frame(O.F_SAME,0,null,0,null));
   m.instructions.insert(patch);m.maxStack=Math.max(m.maxStack,3);
  }return node;
 }};});return out;
}
