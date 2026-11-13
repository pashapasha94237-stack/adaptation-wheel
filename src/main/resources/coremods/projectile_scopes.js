// Scope native callbacks, preserving original invocations and all optional-mod mixins.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes'),T=Java.type('org.objectweb.asm.Type');
 var List=Java.type('org.objectweb.asm.tree.InsnList'),ArrayList=Java.type('java.util.ArrayList');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode');
 var Label=Java.type('org.objectweb.asm.tree.LabelNode'),Frame=Java.type('org.objectweb.asm.tree.FrameNode'),Catch=Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
 var H='ru/pasha/adaptationwheel/server/ModAttackCompatibility',out={},classes={};
 var projectiles=A.loadData('compat/mod-attacks.json').projectiles;
 Object.keys(projectiles).forEach(function(id){classes[projectiles[id].entity]=true;});
 A.loadData('compat/projectile-scopes.json').classes.forEach(function(owner){classes[owner]=true;});
 ['Projectile','AbstractArrow','AbstractHurtingProjectile','ThrowableProjectile','ThrowableItemProjectile','ThrownPotion','Fireball','SmallFireball','LargeFireball','DragonFireball','WitherSkull','ShulkerBullet','LlamaSpit','Snowball','ThrownEnderpearl','ThrownEgg','ThrownExperienceBottle'].forEach(function(name){classes['net/minecraft/world/entity/projectile/'+name]=true;});
 function initial(owner,desc){var l=new ArrayList();l.add(owner);var args=T.getArgumentTypes(desc);for(var i=0;i<args.length;i++)l.add(args[i].getInternalName());return l;}
 function padded(locals,slot){var result=new ArrayList(locals),width=0;for(var n=0;n<locals.size();n++){var type=locals.get(n);width+=type==O.LONG||type==O.DOUBLE?2:1;}while(width<slot){result.add(O.TOP);width++;}result.add('java/lang/Object');return result;}
 Object.keys(classes).forEach(function(owner){out[owner]={target:{type:'CLASS',name:owner.replace(/\//g,'.')},transformer:function(node){
  for(var n=0;n<node.methods.size();n++){
   var m=node.methods.get(n);if((m.access&(O.ACC_STATIC|O.ACC_ABSTRACT|O.ACC_NATIVE))!==0)continue;
   var names={'m_8119_':'()V','m_5790_':'(Lnet/minecraft/world/phys/EntityHitResult;)V','m_6532_':'(Lnet/minecraft/world/phys/HitResult;)V','m_8060_':'(Lnet/minecraft/world/phys/BlockHitResult;)V'};
   var matched=false;Object.keys(names).some(function(name){if((m.name===name||m.name===A.mapMethod(name))&&m.desc===names[name]){matched=true;return true;}return false;});if(!matched)continue;
   var slot=m.maxLocals,locals=initial(owner,m.desc);
   for(var i=m.instructions.getFirst();i!==null;i=i.getNext()){
    if(i instanceof Frame){if(i.type===O.F_FULL||i.type===O.F_NEW)locals=new ArrayList(i.local);else if(i.type===O.F_APPEND)locals.addAll(i.local);else if(i.type===O.F_CHOP)locals=new ArrayList(locals.subList(0,locals.size()-i.local.size()));i.type=O.F_FULL;i.local=padded(locals,slot);i.stack=i.stack===null?new ArrayList():new ArrayList(i.stack);}
    if(i.getOpcode()===O.RETURN){var p=new List();p.add(new Var(O.ALOAD,slot));p.add(new Call(O.INVOKESTATIC,H,'endProjectile','(Ljava/lang/Object;)V',false));m.instructions.insertBefore(i,p);}
   }
   var start=new Label(),end=new Label(),handler=new Label(),p=new List();p.add(new Var(O.ALOAD,0));p.add(new Call(O.INVOKESTATIC,H,'beginProjectile','(Lnet/minecraft/world/entity/Entity;)Ljava/lang/Object;',false));p.add(new Var(O.ASTORE,slot));p.add(start);m.instructions.insert(p);
   m.instructions.add(end);m.instructions.add(handler);var hs=padded(initial(owner,m.desc),slot),stack=new ArrayList();stack.add('java/lang/Throwable');m.instructions.add(new Frame(O.F_FULL,hs.size(),hs.toArray(),1,stack.toArray()));
   m.instructions.add(new Var(O.ASTORE,slot+1));m.instructions.add(new Var(O.ALOAD,slot));m.instructions.add(new Call(O.INVOKESTATIC,H,'endProjectile','(Ljava/lang/Object;)V',false));m.instructions.add(new Var(O.ALOAD,slot+1));m.instructions.add(new Insn(O.ATHROW));m.tryCatchBlocks.add(new Catch(start,end,handler,'java/lang/Throwable'));m.maxLocals+=2;m.maxStack=Math.max(m.maxStack+2,2);
  }return node;
 }};});return out;
}
