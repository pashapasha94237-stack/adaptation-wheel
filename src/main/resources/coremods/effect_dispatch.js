// Guard the periodic entry; keep original invocations for required mixins.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 return {periodic_effect_entry:{target:{type:'CLASS',name:'net.minecraft.world.effect.MobEffectInstance'},transformer:function(node){
  for(var i=0;i<node.methods.size();i++){
   var m=node.methods.get(i);
   if(m.desc!=='(Lnet/minecraft/world/entity/LivingEntity;)V'||(m.access&(O.ACC_STATIC|O.ACC_ABSTRACT|O.ACC_NATIVE))!==0)continue;
   var periodic=false;
   for(var n=m.instructions.getFirst();n!==null;n=n.getNext())
    if(n.getOpcode()===O.INVOKEVIRTUAL&&n.owner==='net/minecraft/world/effect/MobEffect'&&n.desc==='(Lnet/minecraft/world/entity/LivingEntity;I)V')periodic=true;
   if(!periodic)continue;
   var start=new List(),next=new Label();start.add(new Var(O.ALOAD,0));
   start.add(new Var(O.ALOAD,1));
   start.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/NegativeEffects','skipNativeInstance','(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/LivingEntity;)Z',false));
   start.add(new Jump(O.IFEQ,next));start.add(new Insn(O.RETURN));start.add(next);start.add(new Frame(O.F_SAME,0,null,0,null));
   m.instructions.insert(start);m.maxStack=Math.max(m.maxStack,2);
  }return node;
 }}};
}
