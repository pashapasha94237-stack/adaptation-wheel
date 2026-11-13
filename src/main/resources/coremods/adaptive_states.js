// Exact source-scoped custom-state plans. No names/fields in arbitrary capabilities are reset.
function initializeCoreMod() {
    var O = Java.type('org.objectweb.asm.Opcodes');
    var IL = Java.type('org.objectweb.asm.tree.InsnList');
    var Insn = Java.type('org.objectweb.asm.tree.InsnNode');
    var Var = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var Call = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var BRIDGE = 'ru/pasha/adaptationwheel/server/AdaptiveStatePlans';
    function isCall(n, owner, name, desc) { return n.getType() === 5 && n.owner === owner && n.name === name && n.desc === desc; }
    function target(id, owner, name, desc, transform) {
        return {target: {type: 'METHOD', 'class': owner, methodName: name, methodDesc: desc}, transformer: transform};
    }
    var result = {};
    result.tan_tick = target('tan_tick', 'toughasnails.thirst.ThirstHandler', 'onPlayerTick', '(Lnet/minecraft/world/entity/player/Player;)V', function(method) {
        var nodes = method.instructions.toArray(), thirst = 0, hydration = 0, lookup = 0;
        for (var i = 0; i < nodes.length; i++) {
            if (isCall(nodes[i], 'toughasnails/api/thirst/IThirst', 'setThirst', '(I)V')) thirst++;
            if (isCall(nodes[i], 'toughasnails/api/thirst/IThirst', 'setHydration', '(F)V')) hydration++;
            if (isCall(nodes[i], 'toughasnails/api/thirst/ThirstHelper', 'getThirst', '(Lnet/minecraft/world/entity/player/Player;)Ltoughasnails/api/thirst/IThirst;')) lookup++;
        }
        // Fail open if a new native version changed this survival tick's structure.
        if (thirst !== 2 || hydration !== 1 || lookup !== 1) return method;
        for (var j = 0; j < nodes.length; j++) {
            var n = nodes[j];
            if (isCall(n, 'toughasnails/api/thirst/ThirstHelper', 'getThirst', '(Lnet/minecraft/world/entity/player/Player;)Ltoughasnails/api/thirst/IThirst;')) {
                var after = new IL(); after.add(new Insn(O.DUP)); after.add(new Var(O.ALOAD, 0));
                after.add(new Call(O.INVOKESTATIC, BRIDGE, 'toughTick', '(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V', false));
                method.instructions.insert(n, after);
            } else if (isCall(n, 'toughasnails/api/thirst/IThirst', 'setThirst', '(I)V')) {
                method.instructions.insertBefore(n, new Var(O.ALOAD, 0));
                method.instructions.set(n, new Call(O.INVOKESTATIC, BRIDGE, 'toughThirst', '(Ljava/lang/Object;ILnet/minecraft/world/entity/Entity;)V', false));
            } else if (isCall(n, 'toughasnails/api/thirst/IThirst', 'setHydration', '(F)V')) {
                method.instructions.insertBefore(n, new Var(O.ALOAD, 0));
                method.instructions.set(n, new Call(O.INVOKESTATIC, BRIDGE, 'toughHydration', '(Ljava/lang/Object;FLnet/minecraft/world/entity/Entity;)V', false));
            }
        }
        method.maxStack = Math.max(method.maxStack + 1, 4); return method;
    });
    function sanity(name, desc, player) {
        return target(name, 'croissantnova.sanitydim.SanityProcessor', name, desc, function(method) {
            var nodes = method.instructions.toArray(), count = 0;
            for (var i = 0; i < nodes.length; i++) if (isCall(nodes[i], 'croissantnova/sanitydim/capability/ISanity', 'setSanity', '(F)V')) count++;
            if (count !== 1) return method;
            for (var j = 0; j < nodes.length; j++) if (isCall(nodes[j], 'croissantnova/sanitydim/capability/ISanity', 'setSanity', '(F)V')) {
                method.instructions.insertBefore(nodes[j], new Var(O.ALOAD, player));
                method.instructions.set(nodes[j], new Call(O.INVOKESTATIC, BRIDGE, 'sanity', '(Ljava/lang/Object;FLnet/minecraft/world/entity/Entity;)V', false));
            }
            method.maxStack = Math.max(method.maxStack + 1, 4); return method;
        });
    }
    result.sanity_active = sanity('addSanity', '(Lcroissantnova/sanitydim/capability/ISanity;FLnet/minecraft/server/level/ServerPlayer;)V', 2);
    result.sanity_passive = sanity('lambda$tickPlayer$3', '(Lnet/minecraft/server/level/ServerPlayer;Lcroissantnova/sanitydim/capability/ISanity;)V', 0);
    result.mek_radiation_tick = target('mek_radiation_tick', 'mekanism.common.lib.radiation.capability.DefaultRadiationEntity', 'update', '(Lnet/minecraft/world/entity/LivingEntity;)V', function(method) {
        var nodes = method.instructions.toArray(), severity = 0, nativeDose = 0;
        // A generic damage guard may already have replaced hurt() calls. Validate
        // the stable dose/severity schema rather than depending on transformer order.
        for (var i = 0; i < nodes.length; i++) {
            if (isCall(nodes[i], 'mekanism/common/lib/radiation/RadiationManager$RadiationScale', 'getScaledDoseSeverity', '(D)D')) severity++;
            if (nodes[i].getType() === 4 && nodes[i].owner === 'mekanism/common/lib/radiation/capability/DefaultRadiationEntity' && nodes[i].name === 'radiation' && nodes[i].desc === 'D') nativeDose++;
        }
        if (severity !== 1 || nativeDose !== 2) return method;
        for (var j = 0; j < nodes.length; j++) {
            var n = nodes[j];
            if (n.getType() === 4 && n.getOpcode() === O.GETFIELD && n.owner === 'mekanism/common/lib/radiation/capability/DefaultRadiationEntity' && n.name === 'radiation' && n.desc === 'D') {
                method.instructions.insertBefore(n, new Var(O.ALOAD, 1));
                method.instructions.set(n, new Call(O.INVOKESTATIC, BRIDGE, 'radiationDose', '(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)D', false));
            }
        }
        var start = new IL(); start.add(new Var(O.ALOAD, 0)); start.add(new Var(O.ALOAD, 1));
        start.add(new Call(O.INVOKESTATIC, BRIDGE, 'radiationTick', '(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V', false));
        method.instructions.insert(start); method.maxStack = Math.max(method.maxStack + 1, 3); return method;
    });
    return result;
}
