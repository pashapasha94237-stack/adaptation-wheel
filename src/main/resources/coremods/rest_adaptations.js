// Sleeping next to monsters (RestAdaptations.preventsRest): the hook runs at the head of Monster.isPreventingPlayerRest and
// answers "this monster does not count" by returning false; otherwise the original body runs untouched. Subclasses that
// override the method (zombified piglins) never reach this body and keep their own rule. No match, no change.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
 var Label=Java.type('org.objectweb.asm.tree.LabelNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 var MONSTER='net/minecraft/world/entity/monster/Monster',PLAYER='net/minecraft/world/entity/player/Player';
 return {'rest_monster_prevents_rest':{target:{type:'CLASS',name:'net.minecraft.world.entity.monster.Monster'},transformer:function(node){
  for(var i=0;i<node.methods.size();i++){
   var m=node.methods.get(i);
   if((m.name!=='isPreventingPlayerRest'&&m.name!=='m_6935_'&&m.name!==A.mapMethod('m_6935_'))||m.desc!=='(L'+PLAYER+';)Z'||(m.access&O.ACC_STATIC)!==0)continue;
   var p=new List(),next=new Label();
   p.add(new Var(O.ALOAD,0));p.add(new Var(O.ALOAD,1));
   p.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/server/RestAdaptations','preventsRest','(L'+MONSTER+';L'+PLAYER+';)Z',false));
   p.add(new Jump(O.IFNE,next));p.add(new Insn(O.ICONST_0));p.add(new Insn(O.IRETURN));
   p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));
   m.instructions.insert(p);m.maxStack=Math.max(2,m.maxStack);
  }return node;
 }}};
}
