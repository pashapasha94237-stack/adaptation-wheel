// Attack-site hooks generated from the supplied Forge 1.20.1 archives.
// Every original virtual invocation remains a single dynamic dispatch; super calls stay native.
function initializeCoreMod(){
 var A=Java.type('net.minecraftforge.coremod.api.ASMAPI'),O=Java.type('org.objectweb.asm.Opcodes');
 var Ldc=Java.type('org.objectweb.asm.tree.LdcInsnNode');
 var Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Field=Java.type('org.objectweb.asm.tree.FieldInsnNode');
 var defs=A.loadData('compat/mod-attacks.json').classes,output={};
 Object.keys(defs).forEach(function(owner){
  var definition=defs[owner];
  output[owner.replace(/\//g,'_')]={target:{type:'CLASS',name:owner.replace(/\//g,'.')},transformer:function(node){
   var rules=definition.methods;
   for(var n=0;n<node.methods.size();n++){
    var m=node.methods.get(n),rule=rules[m.name+m.desc];
    if(!rule){Object.keys(rules).some(function(signature){var p=signature.indexOf('(');if(signature.substring(p)===m.desc&&A.mapMethod(signature.substring(0,p))===m.name){rule=rules[signature];return true;}return false;});}
    if(!rule)continue;var changed=false;
    for(var ins=m.instructions.getFirst();ins!==null;ins=ins.getNext()){
     if(ins.getOpcode()!==O.INVOKEVIRTUAL&&ins.getOpcode()!==O.INVOKEINTERFACE)continue;
     var hurt=(ins.name==='m_6469_'||ins.name==='hurt')&&ins.desc==='(Lnet/minecraft/world/damagesource/DamageSource;F)Z';
     var health=rule.kind.indexOf('health')>=0&&(ins.name==='m_21153_'||ins.name==='setHealth')&&ins.desc==='(F)V';
     if(!hurt&&!health)continue;
     m.instructions.insertBefore(ins,new Ldc(rule.key));ins.setOpcode(O.INVOKESTATIC);
     ins.owner='ru/pasha/adaptationwheel/server/ModAttackCompatibility';ins.name=hurt?'hurt':'setHealth';
     ins.desc=hurt?'(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FLjava/lang/String;)Z':'(Lnet/minecraft/world/entity/LivingEntity;FLjava/lang/String;)V';
     if(health&&owner==='com/brandon3055/draconicevolution/entity/guardian/control/LaserBeamPhase'){
      m.instructions.insertBefore(ins,new Var(O.ALOAD,0));m.instructions.insertBefore(ins,new Field(O.GETFIELD,owner,'damage','Lnet/minecraft/world/damagesource/DamageSource;'));
      ins.name='setHealthFromSource';ins.desc='(Lnet/minecraft/world/entity/LivingEntity;FLjava/lang/String;Lnet/minecraft/world/damagesource/DamageSource;)V';m.maxStack++;
     }
     if(hurt&&(owner==='twilightforest/entity/boss/HydraHeadContainer'||owner==='twilightforest/entity/boss/SnowQueen'||owner==='twilightforest/entity/boss/UrGhast')){
      m.instructions.insertBefore(ins,new Var(O.ALOAD,0));
      if(owner==='twilightforest/entity/boss/HydraHeadContainer')m.instructions.insertBefore(ins,new Field(O.GETFIELD,owner,'hydra','Ltwilightforest/entity/boss/Hydra;'));
      ins.name='hurtOwned';ins.desc='(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FLjava/lang/String;Lnet/minecraft/world/entity/Entity;)Z';m.maxStack++;
     }
     ins.itf=false;changed=true;
    }if(changed)m.maxStack++;
   }
   return node;
  }};
 });
 return output;
}
