package ru.pasha.adaptationwheel.core;

import java.util.Set;
import java.util.regex.Pattern;

/** Mods build flying slashes and beams as living entities, often with a lot of health so nothing shoots them down. They are attacks, not creatures. */
public final class AbilityEntities {
    // Techniques that nothing in their id gives away.
    private static final Set<String> KNOWN=Set.of("jujutsucraft:red","jujutsucraft:blue","jujutsucraft:purple","jujutsucraft:moon_dregs");
    private static final Pattern NAMED=Pattern.compile("(^|_)(projectile|bullet|fragment|tornado|beam|orb)(_|$)|^entity_(slash|scissors)$|_arrow$|attack$");
    private AbilityEntities() {}
    public static boolean is(String id) {
        if(id==null||id.startsWith("minecraft:"))return false;
        return KNOWN.contains(id)||NAMED.matcher(id.substring(id.indexOf(':')+1)).find();
    }
}
