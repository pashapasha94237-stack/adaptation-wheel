// Guard actual native callbacks, including effects already loaded with the player.
// Useful/neutral service effects are checked at runtime and keep their native actions.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var definitions=A.loadData('compat/mod-effects.json').classes,out={};
 Object.keys(definitions).forEach(function(owner){
  out[owner]={target:{type:'CLASS',name:owner.replace(/\//g,'.')},transformer:function(node){
   for(var i=0;i<node.methods.size();i++){
    var m=node.methods.get(i),kind=definitions[owner][m.name+m.desc];
    if(!kind)Object.keys(definitions[owner]).some(function(signature){var p=signature.indexOf('(');if(signature.substring(p)===m.desc&&A.mapMethod(signature.substring(0,p))===m.name){kind=definitions[owner][signature];return true;}return false;});
    if(!kind||(m.access&(O.ACC_ABSTRACT|O.ACC_NATIVE))!==0)continue;
    var patch=new List(),next=new Label();patch.add(new Var(O.ALOAD,0));patch.add(new Var(O.ALOAD,kind==='instant'||kind==='extended_application'?3:1));
    patch.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/NegativeEffects',kind==='punitive_expiry'?'suppressPunitiveExpiry':'skipNativeAction','(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;)Z',false));
    patch.add(new Jump(O.IFEQ,next));
    if(kind==='extended_incoming'||kind==='extended_outgoing'){patch.add(new Var(O.FLOAD,kind==='extended_incoming'?4:5));patch.add(new Insn(O.FRETURN));}
    else if(kind==='extended_allow'){patch.add(new Insn(O.ICONST_1));patch.add(new Insn(O.IRETURN));}
    else patch.add(new Insn(O.RETURN));
    patch.add(next);patch.add(new Frame(O.F_SAME,0,null,0,null));
    if(kind==='punitive_expiry'){
     // This exact native override first removes attributes, then summons hostile mobs.
     // Keep super cleanup, and skip only the audited punitive callback after immunity.
     for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext())if(ins.getOpcode()===O.INVOKESPECIAL&&(ins.name==='m_6386_'||ins.name==='removeAttributeModifiers')){m.instructions.insert(ins,patch);break;}
    }else m.instructions.insert(patch);
    m.maxStack+=2;
   }return node;
  }};
 });
 return out;
}
