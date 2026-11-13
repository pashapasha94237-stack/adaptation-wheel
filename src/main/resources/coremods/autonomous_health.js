// Exact Forge 1.20.1 native health sinks, not a list of optional mod callers.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),ArrayList=Java.type('java.util.ArrayList');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode');
 var Frame=Java.type('org.objectweb.asm.tree.FrameNode'),Catch=Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
 var helper='ru/pasha/adaptationwheel/server/AutonomousHealthGuard',out={};
 function wrap(m,owner,tracked,arity){
  var start=new Label(),end=new Label(),handler=new Label(),enter=new List();
  enter.add(new Var(O.ALOAD,0));
  if(tracked){enter.add(new Var(O.ALOAD,1));enter.add(new Var(O.ALOAD,2));enter.add(new Call(O.INVOKESTATIC,helper,'beforeTrackedHealth','(Lnet/minecraft/network/syncher/SynchedEntityData;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)Ljava/lang/Object;',false));enter.add(new Var(O.ASTORE,2));}
  else{enter.add(new Var(O.FLOAD,1));enter.add(new Call(O.INVOKESTATIC,helper,'beforeSetHealth','(Lnet/minecraft/world/entity/LivingEntity;F)F',false));enter.add(new Var(O.FSTORE,1));}
  var descriptor=tracked?'(Lnet/minecraft/network/syncher/SynchedEntityData;)V':'(Lnet/minecraft/world/entity/LivingEntity;)V';
  for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext())if(ins.getOpcode()===O.RETURN){var leave=new List();leave.add(new Var(O.ALOAD,0));leave.add(new Call(O.INVOKESTATIC,helper,tracked?'afterTrackedHealth':'afterSetHealth',descriptor,false));m.instructions.insertBefore(ins,leave);}
  enter.add(start);m.instructions.insert(enter);m.instructions.add(end);m.instructions.add(handler);
  var locals=new ArrayList();locals.add(owner);if(tracked){locals.add('net/minecraft/network/syncher/EntityDataAccessor');locals.add('java/lang/Object');if(arity!==2)locals.add(O.INTEGER);}else locals.add(O.FLOAT);
  var stack=new ArrayList();stack.add('java/lang/Throwable');m.instructions.add(new Frame(O.F_FULL,locals.size(),locals.toArray(),1,stack.toArray()));
  var slot=m.maxLocals;m.instructions.add(new Var(O.ASTORE,slot));m.instructions.add(new Var(O.ALOAD,0));m.instructions.add(new Call(O.INVOKESTATIC,helper,tracked?'failedTrackedHealth':'failedSetHealth',descriptor,false));m.instructions.add(new Var(O.ALOAD,slot));m.instructions.add(new Insn(O.ATHROW));
  m.tryCatchBlocks.add(new Catch(start,end,handler,'java/lang/Throwable'));m.maxLocals++;m.maxStack=Math.max(m.maxStack,3);
 }
 out['living_health_sink']={target:{type:'CLASS',name:'net.minecraft.world.entity.LivingEntity'},transformer:function(node){for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if((m.name==='setHealth'||m.name==='m_21153_'||m.name===A.mapMethod('m_21153_'))&&m.desc==='(F)V')wrap(m,'net/minecraft/world/entity/LivingEntity',false);}return node;}};
 out['tracked_health_sink']={target:{type:'CLASS',name:'net.minecraft.network.syncher.SynchedEntityData'},transformer:function(node){for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if((m.name==='set'||m.name==='m_276349_'||m.name===A.mapMethod('m_276349_'))&&m.desc==='(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V')wrap(m,'net/minecraft/network/syncher/SynchedEntityData',true);}return node;}};
 // First Aid 1.1 intercepts the two-argument setter BEFORE forwarding into the
 // vanilla parent. Guard its real body-model entry point, without linking Java
 // against optional First Aid classes or changing private limb fields.
 out['firstaid_health_adapter']={target:{type:'CLASS',name:'ichttt.mods.firstaid.common.SynchedEntityDataWrapper'},transformer:function(node){for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if((m.name==='set'||m.name==='m_135381_'||m.name===A.mapMethod('m_135381_'))&&m.desc==='(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V')wrap(m,'ichttt/mods/firstaid/common/SynchedEntityDataWrapper',true,2);}return node;}};
 return out;
}
