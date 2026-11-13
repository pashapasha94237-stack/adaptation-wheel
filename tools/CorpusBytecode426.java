import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Read-only ASM9 structural sink audit. Never loads vendor classes or emits their bytecode.
 * Usage: CorpusBytecode426 <mods-dir> <output-dir> [mapped-minecraft-jar]; inventory-corpus426.py must run first.
 * The JSONL instruction number includes all ASM nodes (debug labels/lines included).
 * Class SHA256 + method descriptor + instruction identify the exact audited input.
 */
public final class CorpusBytecode426 {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String SYNC = "net/minecraft/network/syncher/SynchedEntityData";
    private static final String ACCESSOR = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
    private static final String NBT = "net/minecraft/nbt/CompoundTag";
    private static final Set<String> HEALTH_SET = Set.of("setHealth", "m_21153_", "method_6033");
    private static final Set<String> HEALTH_GET = Set.of("getHealth", "m_21223_", "method_6032");
    private static final Set<String> SYNC_SET = Set.of("set", "m_135381_", "m_276349_", "method_12778");
    private static final Set<String> NBT_PUT = Set.of("put", "putByte", "putShort", "putInt", "putLong", "putFloat", "putDouble", "putBoolean", "putString",
        "m_128365_", "m_128344_", "m_128376_", "m_128405_", "m_128356_", "m_128350_", "m_128347_", "m_128379_", "m_128359_");
    private static final Set<String> NUMERIC = Set.of("F", "D", "I", "J", "S", "B", "Z");
    private static final Map<String, Info> classes = new HashMap<>();
    private static final Set<String> healthFields = new HashSet<>();
    private static final Map<String, String> owners = new HashMap<>();
    private static final Set<String> baseline = new HashSet<>();
    private static final Map<String, Long> stats = new TreeMap<>();
    private static PrintWriter calls, errors, definitions;
    private record Info(String parent, List<String> interfaces, boolean capability, boolean mixin) {}
    private record Unit(String jar, String entry, byte[] bytes) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("mods-dir output-dir [mapped-minecraft-jar]");
        Path mods = Path.of(args[0]), out = Path.of(args[1]);
        Files.createDirectories(out);
        if (args.length == 3) indexSupport(Path.of(args[2]));
        for (String line : Files.readAllLines(out.resolve("jar-owners.tsv"), StandardCharsets.UTF_8)) {
            String[] split = line.split("\t", -1);
            owners.put(split[0], split.length > 1 ? split[1] : "");
        }
        baseline.addAll(Files.readAllLines(out.resolve("baseline-classes.txt"), StandardCharsets.UTF_8));
        List<Path> jars;
        try (var stream = Files.list(mods)) {
            jars = stream.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        try (PrintWriter callOut = writer(out.resolve("callsites.jsonl"));
             PrintWriter errorOut = writer(out.resolve("bytecode-errors.jsonl"));
             PrintWriter definitionOut = writer(out.resolve("definitions.jsonl"))) {
            calls = callOut; errors = errorOut; definitions = definitionOut;
            for (int phase = 1; phase <= 2; phase++) {
                int count = 0;
                final int pass = phase;
                for (Path jar : jars) {
                    walk(jar, unit -> {
                        try {
                            if (pass == 1) index(unit); else inspect(unit);
                        } catch (Throwable failure) {
                            increment("classErrorsPass" + pass);
                            errors.println(json(Map.of("jar", unit.jar, "entry", unit.entry, "pass", pass, "error", failure.toString())));
                        }
                    });
                    if (++count % 50 == 0) System.out.println("ASM pass " + phase + ": " + count + "/" + jars.size());
                }
            }
        }
        stats.put("topLevelJars", (long) jars.size());
        stats.put("uniqueIndexedClasses", (long) classes.size());
        stats.put("structurallyIdentifiedHealthFields", (long) healthFields.size());
        Files.writeString(out.resolve("bytecode-stats.json"), json(stats) + "\n", StandardCharsets.UTF_8);
        System.out.println(json(stats));
    }

    private static PrintWriter writer(Path file) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    /** Optional vanilla hierarchy headers are supporting evidence, not corpus classes.
     * Only class/super/interface names are read; no vanilla bytecode is exported. */
    private static void indexSupport(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    if (!reader.getClassName().startsWith("net/minecraft/")) continue;
                    classes.put(reader.getClassName(), new Info(reader.getSuperName(), List.of(reader.getInterfaces()), false, false));
                    increment("supportMinecraftClassHeaders");
                }
            }
        }
    }

    private static void walk(Path jar, Consumer<Unit> visitor) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getName().endsWith(".class")) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        visitor.accept(new Unit(jar.getFileName().toString(), entry.getName(), in.readAllBytes()));
                    }
                } else if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        nested(jar.getFileName().toString(), entry.getName(), in.readAllBytes(), visitor, 1);
                    } catch (IOException ex) {
                        errors.println(json(Map.of("jar", jar.getFileName().toString(), "entry", entry.getName(), "error", ex.toString())));
                        increment("archiveErrors");
                    }
                }
            }
        }
    }

    private static void nested(String jar, String prefix, byte[] bytes, Consumer<Unit> visitor, int depth) throws IOException {
        if (depth > 5) throw new IOException("Nested archive depth limit reached");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String path = prefix + "!/" + entry.getName();
                if (entry.getName().endsWith(".class")) visitor.accept(new Unit(jar, path, zip.readAllBytes()));
                else if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) nested(jar, path, zip.readAllBytes(), visitor, depth + 1);
            }
        }
    }

    private static ClassNode node(byte[] bytes, int flags) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, flags);
        return node;
    }

    private static void index(Unit unit) {
        ClassNode node = node(unit.bytes, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        boolean cap = node.name.toLowerCase(Locale.ROOT).matches(".*(capabilit|player(data|variable|state)|properties|attachment|persistent).*");
        cap |= node.interfaces.stream().anyMatch(i -> i.contains("capabilities/") || i.endsWith("INBTSerializable"));
        boolean mixin = annotations(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;") || annotations(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        classes.putIfAbsent(node.name, new Info(node.superName, node.interfaces, cap, mixin));
        increment(unit.entry.contains("!/") ? "nestedClassEntries" : "topLevelClassEntries");
        // A numeric field returned unchanged by a health getter is stronger evidence
        // than a field-name substring. No class initialization or reflection occurs.
        for (MethodNode method : node.methods) {
            if (!HEALTH_GET.contains(method.name) || !method.desc.equals("()F")) continue;
            AbstractInsnNode[] insns = method.instructions.toArray();
            for (int i = 0; i < insns.length; i++) {
                if (insns[i].getOpcode() != Opcodes.FRETURN) continue;
                AbstractInsnNode before = previousOpcode(insns[i]);
                if (before instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && field.desc.equals("F"))
                    healthFields.add(field.owner + "." + field.name + ":" + field.desc);
            }
        }
    }

    private static boolean annotations(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(descriptor));
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getPrevious();
        while (p != null && p.getOpcode() < 0) p = p.getPrevious();
        return p;
    }

    private static boolean living(String owner) {
        Set<String> visited = new HashSet<>();
        while (owner != null && visited.add(owner)) {
            if (owner.equals(LIVING) || owner.equals("net/minecraft/class_1309") || owner.equals("net/minecraft/world/entity/player/Player")
                || owner.equals("net/minecraft/server/level/ServerPlayer") || owner.equals("net/minecraft/world/entity/Mob")) return true;
            Info info = classes.get(owner);
            owner = info == null ? null : info.parent;
        }
        return false;
    }

    private static boolean capability(String owner) {
        Info info = classes.get(owner);
        return info != null && info.capability;
    }

    private static void inspect(Unit unit) throws Exception {
        ClassNode node = node(unit.bytes, ClassReader.SKIP_FRAMES);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(unit.bytes));
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            increment("methodsScanned");
            boolean needsFlow = false;
            boolean persistentAccess = false;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    needsFlow |= (call.owner.equals(SYNC) && SYNC_SET.contains(call.name)) || (call.owner.equals(NBT) && NBT_PUT.contains(call.name))
                        || (HEALTH_SET.contains(call.name) && call.desc.equals("(F)V"));
                    persistentAccess |= call.name.equals("getPersistentData") || call.name.equals("getCapability");
                }
            }
            Frame<SourceValue>[] frames = null;
            if (needsFlow) {
                try {
                    frames = new Analyzer<>(new OriginInterpreter()).analyze(node.name, method);
                } catch (AnalyzerException | RuntimeException failure) {
                    increment("sourceAnalysisFailures");
                    errors.println(json(Map.of("jar", unit.jar, "class", node.name, "method", method.name + method.desc, "error", failure.toString())));
                }
            }
            int index = 0, line = -1;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof LineNumberNode n) line = n.line;
                Frame<SourceValue> frame = frames == null ? null : frames[index];
                if (insn instanceof MethodInsnNode call) {
                    String category = null, evidence = "";
                    if (HEALTH_SET.contains(call.name) && call.desc.equals("(F)V") && (living(call.owner) || !call.name.equals("setHealth"))) {
                        category = "DIRECT_HEALTH_SET"; evidence = "exact health setter signature; may heal/initialize; receiver purpose unproven";
                        if (healthSubtract(stackOrigins(frame, 0), frames, method.instructions, 0))
                            evidence += "; healthReadSubtraction=true (subtrahend sign/receiver identity require runtime)";
                    } else if (call.name.equals("setHealth") && call.desc.equals("(F)V")) {
                        category = "CUSTOM_HEALTH_SET"; evidence = "health-named setter outside confirmed LivingEntity hierarchy; may be data/nonliving health";
                    } else if (call.owner.equals(SYNC) && SYNC_SET.contains(call.name) && call.desc.startsWith("(" + ACCESSOR + "Ljava/lang/Object;")) {
                        Set<AbstractInsnNode> origins = stackOrigins(frame, Type.getArgumentTypes(call.desc).length - 1);
                        String fields = fieldOrigins(origins);
                        boolean health = !origins.isEmpty() && origins.stream().allMatch(n -> n instanceof FieldInsnNode f && f.desc.equals(ACCESSOR) &&
                            (f.name.equals("f_20961_") || f.name.equals("DATA_HEALTH_ID")) && living(f.owner));
                        category = health ? "SYNCED_HEALTH_SET" : "SYNCED_DATA_SET";
                        evidence = health ? "SourceInterpreter proves accessor argument comes from LivingEntity DATA_HEALTH_ID" : "synced property, health not proven";
                        evidence += fields.isEmpty() ? "; accessor origin unresolved" : "; accessor=" + fields;
                    } else if (call.owner.equals(NBT) && NBT_PUT.contains(call.name) && call.desc.startsWith("(Ljava/lang/String;")) {
                        String constants = constants(stackOrigins(frame, Type.getArgumentTypes(call.desc).length - 1));
                        category = persistentAccess ? "PERSISTENT_NBT_WRITE" : "NBT_WRITE";
                        evidence = (persistentAccess ? "method also obtains capability/player persistent data" : "NBT may be item/save/network data") + "; key=" + constants;
                    } else if (Set.of("addEffect", "m_7292_", "m_147207_", "forceAddEffect", "m_147215_").contains(call.name)
                        && call.desc.startsWith("(Lnet/minecraft/world/effect/MobEffectInstance;")) {
                        category = "EFFECT_APPLICATION"; evidence = "effect addition; beneficial/harmful and receiver require runtime registry";
                    } else if (call.owner.equals("net/minecraft/world/entity/ai/attributes/AttributeInstance") &&
                        (Set.of("setBaseValue", "addTransientModifier", "addPermanentModifier", "m_22100_", "m_22118_", "m_22125_").contains(call.name))) {
                        category = "ATTRIBUTE_MUTATION"; evidence = "attribute base/modifier mutation; attribute identity and harmful direction unproven";
                    } else if (capability(call.owner) && mutator(call.name) && !call.name.equals("deserializeNBT") && !call.name.equals("serializeNBT")) {
                        category = "CAPABILITY_MUTATOR_CALL"; evidence = "capability/state class plus mutator signature heuristic; not proof of harm";
                    } else if ((call.owner.equals("java/lang/reflect/Field") && call.name.startsWith("set")) ||
                        ((call.owner.equals("sun/misc/Unsafe") || call.owner.equals("jdk/internal/misc/Unsafe")) && call.name.startsWith("put"))) {
                        category = "REFLECTIVE_WRITE"; evidence = "indirect write; target not statically resolved";
                    }
                    if (category != null) emit(unit, node, method, index, line, category, call.owner, call.name, call.desc, evidence, hash);
                } else if (insn instanceof FieldInsnNode field && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC) && NUMERIC.contains(field.desc)) {
                    String fieldKey = field.owner + "." + field.name + ":" + field.desc;
                    if (healthFields.contains(fieldKey)) {
                        emit(unit, node, method, index, line, "HEALTH_FIELD_WRITE", field.owner, field.name, field.desc,
                            "numeric backing field returned directly by a health getter; semantic target still needs review", hash);
                    } else if (capability(field.owner)) {
                        emit(unit, node, method, index, line, "CAPABILITY_FIELD_WRITE", field.owner, field.name, field.desc,
                            "numeric field write in capability/player-state container; includes normal initialization/serialization", hash);
                    } else if (healthName(field.name)) {
                        emit(unit, node, method, index, line, "HEALTH_NAMED_FIELD_WRITE", field.owner, field.name, field.desc,
                            "field name only; may be UI, cached health or nonliving data; not verified bypass", hash);
                    }
                }
                index++;
            }
            // Definitions aid downstream exact method transformation plans (all access levels).
            if (HEALTH_SET.contains(method.name) && method.desc.equals("(F)V") ||
                HEALTH_GET.contains(method.name) && method.desc.equals("()F")) {
                definitions.println(json(Map.of("jar", unit.jar, "entry", unit.entry, "modOwner", owners.getOrDefault(unit.jar, ""), "class", node.name,
                    "method", method.name, "descriptor", method.desc, "access", method.access, "livingOwner", living(node.name), "classSha256", hash)));
                increment("healthDefinitions");
            }
        }
    }

    private static boolean mutator(String name) {
        return name.startsWith("set") || name.startsWith("add") || name.startsWith("remove") || name.startsWith("decrease") || name.startsWith("increase") || name.startsWith("damage") || name.startsWith("hurt");
    }

    private static void selfTest() throws Exception {
        StringWriter buffer = new StringWriter();
        calls = new PrintWriter(buffer); errors = new PrintWriter(new StringWriter()); definitions = new PrintWriter(new StringWriter());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "audit/PlayerState", null, LIVING, null);
        cw.visitField(Opcodes.ACC_PRIVATE, "opaque", "F", null, null).visitEnd();
        MethodVisitor get = cw.visitMethod(Opcodes.ACC_PUBLIC, "getHealth", "()F", null, null);
        get.visitCode(); get.visitVarInsn(Opcodes.ALOAD, 0); get.visitFieldInsn(Opcodes.GETFIELD, "audit/PlayerState", "opaque", "F");
        get.visitInsn(Opcodes.FRETURN); get.visitMaxs(0,0); get.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "privateMutation", "(Lnet/minecraft/world/entity/LivingEntity;L" + SYNC + ";L" + NBT + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1); mv.visitInsn(Opcodes.FCONST_0); mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIVING, "m_21153_", "(F)V", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, LIVING, "f_20961_", ACCESSOR); mv.visitVarInsn(Opcodes.ASTORE, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, 4); mv.visitInsn(Opcodes.FCONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SYNC, "m_135381_", "(" + ACCESSOR + "Ljava/lang/Object;)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitVarInsn(Opcodes.ALOAD, 4); mv.visitInsn(Opcodes.FCONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false); mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SYNC, "m_276349_", "(" + ACCESSOR + "Ljava/lang/Object;Z)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 2); mv.visitFieldInsn(Opcodes.GETSTATIC, "audit/PlayerState", "SOME_OTHER_DATA", ACCESSOR); mv.visitInsn(Opcodes.FCONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SYNC, "m_135381_", "(" + ACCESSOR + "Ljava/lang/Object;)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitInsn(Opcodes.FCONST_0); mv.visitFieldInsn(Opcodes.PUTFIELD, "audit/PlayerState", "opaque", "F");
        mv.visitVarInsn(Opcodes.ALOAD, 3); mv.visitLdcInsn("custom_negative"); mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NBT, "putInt", "(Ljava/lang/String;I)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 1); mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIVING, "m_21223_", "()F", false); mv.visitInsn(Opcodes.FCONST_1); mv.visitInsn(Opcodes.FSUB);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIVING, "m_21153_", "(F)V", false);
        mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0,0); mv.visitEnd(); cw.visitEnd();
        Unit unit = new Unit("fixture.jar", "audit/PlayerState.class", cw.toByteArray());
        owners.put("fixture.jar", "synthetic"); index(unit); inspect(unit); calls.flush();
        String result = buffer.toString();
        for (String category : List.of("DIRECT_HEALTH_SET", "SYNCED_HEALTH_SET", "HEALTH_FIELD_WRITE", "NBT_WRITE"))
            if (!result.contains("\"sinkCategory\":\"" + category + "\"")) throw new AssertionError("missing " + category);
        if (!result.contains("custom_negative") || !result.contains("privateMutation") || !result.contains("healthReadSubtraction=true")
            || stats.getOrDefault("sink.SYNCED_HEALTH_SET", 0L) != 2 || stats.getOrDefault("sink.SYNCED_DATA_SET", 0L) != 1)
            throw new AssertionError("local-origin/private/key tracking failed");
        if (stats.containsKey("sourceAnalysisFailures")) throw new AssertionError("data-flow analysis failed");
        System.out.println("CorpusBytecode426 self-test PASS: private setter, both health-accessor overloads across local, non-health accessor distinction, opaque backing-field write, constant NBT key, health subtraction provenance.");
    }

    private static boolean healthName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("health") || lower.equals("hp") || lower.equals("maxhp") || lower.equals("hitpoints");
    }

    /** SourceInterpreter normally replaces ALOAD origins with the load instruction.
     * Preserving copy origins permits accessor/key tracing across local variables. */
    private static final class OriginInterpreter extends SourceInterpreter {
        OriginInterpreter() { super(Opcodes.ASM9); }
        @Override public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) { return value; }
        @Override public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
            return insn.getOpcode() == Opcodes.CHECKCAST ? value : super.unaryOperation(insn, value);
        }
    }

    private static Set<AbstractInsnNode> stackOrigins(Frame<SourceValue> frame, int fromTop) {
        if (frame == null || frame.getStackSize() <= fromTop) return Set.of();
        return frame.getStack(frame.getStackSize() - 1 - fromTop).insns;
    }

    private static String fieldOrigins(Set<AbstractInsnNode> origins) {
        return origins.stream().filter(n -> n instanceof FieldInsnNode).map(n -> {
            FieldInsnNode f = (FieldInsnNode)n; return f.owner + "." + f.name + ":" + f.desc;
        }).sorted().reduce((a,b) -> a + "," + b).orElse("");
    }

    private static String constants(Set<AbstractInsnNode> origins) {
        return origins.stream().filter(n -> n instanceof LdcInsnNode l && l.cst instanceof String).map(n -> (String)((LdcInsnNode)n).cst)
            .sorted().reduce((a,b) -> a + "," + b).orElse("<dynamic/unresolved>");
    }

    private static void emit(Unit unit, ClassNode node, MethodNode method, int index, int line, String category,
                             String targetOwner, String targetName, String targetDescriptor, String evidence, String hash) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("jar", unit.jar); row.put("entry", unit.entry); row.put("modOwner", owners.getOrDefault(unit.jar, ""));
        row.put("class", node.name); row.put("method", method.name); row.put("descriptor", method.desc); row.put("access", method.access);
        row.put("instruction", index); row.put("line", line); row.put("sinkCategory", category);
        row.put("targetOwner", targetOwner); row.put("targetName", targetName); row.put("targetDescriptor", targetDescriptor);
        row.put("evidence", evidence); row.put("baselineClass", baseline.contains(node.name)); row.put("classSha256", hash);
        row.put("targetLiving", living(targetOwner)); row.put("modOwnerScope", "top-level-container-declarations");
        calls.println(json(row)); increment("sink." + category);
    }

    private static boolean healthSubtract(Set<AbstractInsnNode> origins, Frame<SourceValue>[] frames, InsnList instructions, int depth) {
        if (frames == null || depth > 8) return false;
        for (AbstractInsnNode source : origins) {
            int index = instructions.indexOf(source);
            if (index < 0) continue;
            Frame<SourceValue> frame = frames[index];
            if (source.getOpcode() == Opcodes.FSUB || source.getOpcode() == Opcodes.DSUB) {
                if (stackOrigins(frame, 1).stream().anyMatch(n -> n instanceof MethodInsnNode call && HEALTH_GET.contains(call.name))) return true;
            }
            if (source instanceof MethodInsnNode call && (call.owner.equals("java/lang/Math") || call.owner.equals("net/minecraft/util/Mth")
                || call.owner.equals("java/lang/Float") || call.owner.equals("java/lang/Double"))) {
                for (int argument = 0; argument < Type.getArgumentTypes(call.desc).length; argument++)
                    if (healthSubtract(stackOrigins(frame, argument), frames, instructions, depth + 1)) return true;
            }
        }
        return false;
    }

    private static void increment(String key) { stats.merge(key, 1L, Long::sum); }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> map) {
            StringJoiner result = new StringJoiner(",", "{", "}");
            map.forEach((k,v) -> result.add(json(k.toString()) + ":" + json(v)));
            return result.toString();
        }
        String text = value.toString();
        StringBuilder result = new StringBuilder("\"");
        for (int i=0;i<text.length();i++) {
            char c = text.charAt(i);
            switch(c) {
                case '\\' -> result.append("\\\\"); case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                default -> { if (c < 32) result.append(String.format("\\u%04x", (int)c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
}
