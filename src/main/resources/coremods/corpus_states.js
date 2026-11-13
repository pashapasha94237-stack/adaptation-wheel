// Exact additional corpus states; preserve original lifecycle, recovery and native symptom cleanup.
function initializeCoreMod() {
 var O=Java.type('org.objectweb.asm.Opcodes'),IL=Java.type('org.objectweb.asm.tree.InsnList');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
 var helper='ru/pasha/adaptationwheel/server/CorpusStateCompatibility',out={};
 out.nuclear_stage={target:{type:'METHOD','class':'igentuman.nc.radiation.data.PlayerRadiation',methodName:'updateContaminationStage',methodDesc:'(Lnet/minecraft/world/entity/player/Player;)V'},transformer:function(m){
  var nodes=m.instructions.toArray(),reads=0,stages=0;
  for(var i=0;i<nodes.length;i++){
   var n=nodes[i];
   if(n.getOpcode()===O.GETFIELD&&n.owner==='igentuman/nc/radiation/data/PlayerRadiation'&&n.name==='radiation'&&n.desc==='J')reads++;
   if(n.getType()===5&&n.owner==='igentuman/nc/radiation/data/PlayerRadiation'&&n.name==='setContaminationStage'&&n.desc==='(ILnet/minecraft/world/entity/player/Player;)V')stages++;
  }
  if(reads!==4||stages!==4)return m;
  for(var j=0;j<nodes.length;j++){
   var n=nodes[j];
   if(n.getOpcode()===O.GETFIELD&&n.owner==='igentuman/nc/radiation/data/PlayerRadiation'&&n.name==='radiation'&&n.desc==='J'){
    m.instructions.insertBefore(n,new Var(O.ALOAD,1));m.instructions.set(n,new Call(O.INVOKESTATIC,helper,'nuclearDose','(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)J',false));
   }
  }
  var begin=new IL();begin.add(new Var(O.ALOAD,0));begin.add(new Var(O.ALOAD,1));begin.add(new Call(O.INVOKESTATIC,helper,'beforeNuclearStage','(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V',false));
  m.instructions.insert(begin);m.maxStack=Math.max(m.maxStack+1,3);return m;
 }};
 out.sea_dehydration={target:{type:'METHOD','class':'by.dragonsurvivalteam.dragonsurvival.common.dragon_types.types.SeaDragonType',methodName:'onPlayerUpdate',methodDesc:'(Lnet/minecraft/world/entity/player/Player;Lby/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler;)V'},transformer:function(m){
  var nodes=m.instructions.toArray(),writes=0;
  for(var i=0;i<nodes.length;i++){var n=nodes[i];if(n.getOpcode()===O.PUTFIELD&&n.owner==='by/dragonsurvivalteam/dragonsurvival/common/dragon_types/types/SeaDragonType'&&n.name==='timeWithoutWater'&&n.desc==='D')writes++;}
  if(writes!==4)return m;
  for(var j=0;j<nodes.length;j++){var n=nodes[j];if(n.getOpcode()===O.PUTFIELD&&n.owner==='by/dragonsurvivalteam/dragonsurvival/common/dragon_types/types/SeaDragonType'&&n.name==='timeWithoutWater'&&n.desc==='D'){
   m.instructions.insertBefore(n,new Var(O.ALOAD,1));m.instructions.set(n,new Call(O.INVOKESTATIC,helper,'seaCounter','(Ljava/lang/Object;DLnet/minecraft/world/entity/Entity;)V',false));
  }}
  m.maxStack=Math.max(m.maxStack+1,4);return m;
 }};
 return out;
}
