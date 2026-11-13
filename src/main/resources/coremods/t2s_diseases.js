// Optional entry guards for T2S's internal disease procedures. No external JAR is modified.
function initializeCoreMod() {
    var Opcodes=Java.type('org.objectweb.asm.Opcodes');
    var Type=Java.type('org.objectweb.asm.Type');
    var InsnList=Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode=Java.type('org.objectweb.asm.tree.VarInsnNode');
    var LdcInsnNode=Java.type('org.objectweb.asm.tree.LdcInsnNode');
    var MethodInsnNode=Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var JumpInsnNode=Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var LabelNode=Java.type('org.objectweb.asm.tree.LabelNode');
    var InsnNode=Java.type('org.objectweb.asm.tree.InsnNode');
    var FrameNode=Java.type('org.objectweb.asm.tree.FrameNode');
    var result={};
    function guard(name,id) {
        result[name]={target:{type:'CLASS',name:'net.mcreator.trytosurvivetwo.procedures.'+name},transformer:function(node){
            for(var n=0;n<node.methods.size();n++) {
                var method=node.methods.get(n);
                if(method.name!=='execute'||Type.getReturnType(method.desc).getSort()!==Type.VOID)continue;
                var args=Type.getArgumentTypes(method.desc),slot=(method.access&Opcodes.ACC_STATIC)!==0?0:1,entity=-1;
                for(var a=0;a<args.length;a++){if(args[a].getClassName()==='net.minecraft.world.entity.Entity')entity=slot;slot+=args[a].getSize();}
                if(entity<0)continue;
                var proceed=new LabelNode(),instructions=new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD,entity));instructions.add(new LdcInsnNode(id));
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,'ru/pasha/adaptationwheel/server/T2SCompatibility','skipDiseaseProcedure','(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;)Z',false));
                instructions.add(new JumpInsnNode(Opcodes.IFEQ,proceed));instructions.add(new InsnNode(Opcodes.RETURN));
                instructions.add(proceed);instructions.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(instructions);method.maxStack=Math.max(method.maxStack,2);
            }
            return node;
        }};
    }
    guard('T2SEnteritisProceduresProcedure','enteritis');
    guard('T2SEncephalitisProceduresProcedure','encephalitis');
    guard('T2SHypothermiaProceduresProcedure','hypothermia');
    guard('T2SStomachUclerProceduresProcedure','stomach_ulcer');
    guard('T2SStrokeProceduresProcedure','heatstroke');
    guard('T2SBrainDamageProceduresProcedure','brain_damage');
    guard('T2SExhaustionProceduresProcedure','exhaustion');
    guard('T2SFluDiseaseEffectOnEffectActiveTickProcedure','flu');
    guard('T2SFeverDiseaseEffectOnEffectActiveTickProcedure','fever');
    guard('T2SRabiesDiseaseOnEffectActiveTickProcedure','rabies');
    guard('T2SFluInfectionProgressionProcedureProcedure','flu');
    guard('T2SColdInfectionProgressionProcedureProcedure','cold');
    guard('T2SFeverInfectionProgressionProcedureProcedure','fever');
    guard('T2SRabiesInfectionProgressionProcedureProcedure','rabies');
    return result;
}
