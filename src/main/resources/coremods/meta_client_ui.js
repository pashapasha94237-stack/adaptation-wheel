// Exact client action-bar entry point. Original GUI body and other mods' calls remain intact.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
 var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
 var Label=Java.type('org.objectweb.asm.tree.LabelNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode');
 return {'wheel_actionbar':{target:{type:'CLASS',name:'net.minecraft.client.gui.Gui'},transformer:function(node){
  for(var i=0;i<node.methods.size();i++){
   var m=node.methods.get(i);if((m.name!=='setOverlayMessage'&&m.name!=='m_93063_'&&m.name!==A.mapMethod('m_93063_'))||m.desc!=='(Lnet/minecraft/network/chat/Component;Z)V')continue;
   var p=new List(),next=new Label();p.add(new Var(O.ALOAD,1));p.add(new Call(O.INVOKESTATIC,'ru/pasha/adaptationwheel/client/MetaClient','overlay','(Lnet/minecraft/network/chat/Component;)Z',false));p.add(new Jump(O.IFEQ,next));p.add(new Insn(O.RETURN));p.add(next);p.add(new Frame(O.F_SAME,0,null,0,null));m.instructions.insert(p);m.maxStack=Math.max(1,m.maxStack);
  }return node;
 }}};
}
