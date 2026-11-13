// Scope complete hurt methods while retaining every original invocation for other mixins.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),ArrayList=Java.type('java.util.ArrayList');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode');
 var Frame=Java.type('org.objectweb.asm.tree.FrameNode'),Catch=Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
 var helper='ru/pasha/adaptationwheel/server/ModAttackCompatibility',out={};
 function initial(owner){var l=new ArrayList();l.add(owner);l.add('net/minecraft/world/damagesource/DamageSource');l.add(O.FLOAT);return l;}
 function padded(locals,slot){var result=new ArrayList(locals),width=0;for(var n=0;n<locals.size();n++){var type=locals.get(n);width+=type==O.LONG||type==O.DOUBLE?2:1;}while(width<slot){result.add(O.TOP);width++;}result.add('java/lang/Object');return result;}
 ['net/minecraft/world/entity/LivingEntity','net/minecraft/world/entity/player/Player','net/minecraft/server/level/ServerPlayer'].forEach(function(owner){
  out[owner]={target:{type:'CLASS',name:owner.replace(/\//g,'.')},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){
    var m=node.methods.get(i);if((m.name!=='m_6469_'&&m.name!=='hurt'&&m.name!==A.mapMethod('m_6469_'))||m.desc!=='(Lnet/minecraft/world/damagesource/DamageSource;F)Z')continue;
    var slot=m.maxLocals,locals=initial(owner);
    // Expand compressed frames using ORIGINAL locals, then append the initialized token.
    for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
     if(ins instanceof Frame){
      if(ins.type===O.F_FULL||ins.type===O.F_NEW)locals=new ArrayList(ins.local);
      else if(ins.type===O.F_APPEND)locals.addAll(ins.local);
      else if(ins.type===O.F_CHOP)locals=new ArrayList(locals.subList(0,locals.size()-ins.local.size()));
      var stack=ins.stack===null?new ArrayList():new ArrayList(ins.stack);
      ins.type=O.F_FULL;ins.local=padded(locals,slot);ins.stack=stack;
     }
     if(ins.getOpcode()===O.IRETURN){var leave=new List();leave.add(new Var(O.ALOAD,slot));leave.add(new Call(O.INVOKESTATIC,helper,'endHurt','(Ljava/lang/Object;)V',false));m.instructions.insertBefore(ins,leave);}
    }
    var start=new Label(),end=new Label(),handler=new Label(),enter=new List();
    enter.add(new Var(O.ALOAD,0));enter.add(new Var(O.ALOAD,1));enter.add(new Var(O.FLOAD,2));
    enter.add(new Call(O.INVOKESTATIC,helper,'beginHurt','(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)Ljava/lang/Object;',false));enter.add(new Var(O.ASTORE,slot));enter.add(start);m.instructions.insert(enter);
    m.instructions.add(end);m.instructions.add(handler);
    var handlerLocals=padded(initial(owner),slot),exceptionStack=new ArrayList();exceptionStack.add('java/lang/Throwable');
    m.instructions.add(new Frame(O.F_FULL,handlerLocals.size(),handlerLocals.toArray(),1,exceptionStack.toArray()));
    m.instructions.add(new Var(O.ASTORE,slot+1));m.instructions.add(new Var(O.ALOAD,slot));m.instructions.add(new Call(O.INVOKESTATIC,helper,'endHurt','(Ljava/lang/Object;)V',false));m.instructions.add(new Var(O.ALOAD,slot+1));m.instructions.add(new Insn(O.ATHROW));
    m.tryCatchBlocks.add(new Catch(start,end,handler,'java/lang/Throwable'));m.maxLocals+=2;m.maxStack=Math.max(m.maxStack+2,3);
   }return node;
  }};
 });
 return out;
}
