package ru.pasha.adaptationwheel.integration;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Optional Configured bridge for «Облик колеса»: Configured lists an enum by its English constant names, so the wheel
 * opens its own look menu instead and hands the choice back through Configured's own save of that value.
 */
public final class ConfiguredSkinAccess {
    public static final String ENUM_SCREEN = "com.mrcrayfish.configured.client.screen.ChangeEnumScreen";

    /** Configured's screen to return to and its save of the edited value. */
    public record Session(Object parent, Consumer<Object> save) {
    }

    private ConfiguredSkinAccess() {
    }

    @SuppressWarnings("unchecked")
    public static Optional<Session> read(Object editor) throws ReflectiveOperationException {
        ClassLoader loader = editor.getClass().getClassLoader();
        Class<?> configApi = Class.forName("com.mrcrayfish.configured.api.IModConfig", false, loader);
        Class<?> valueApi = Class.forName("com.mrcrayfish.configured.api.IConfigValue", false, loader);
        Object config = editor.getClass().getMethod("getActiveConfig").invoke(editor);
        if (!"adaptationwheel".equals(configApi.getMethod("getModId").invoke(config))
                || (Boolean) configApi.getMethod("isReadOnly").invoke(config)) return Optional.empty();
        Object holder = field(editor, "holder");
        if (!"wheelSkin".equals(valueApi.getMethod("getName").invoke(holder))
                || !"config.adaptationwheel.wheel_skin".equals(valueApi.getMethod("getTranslationKey").invoke(holder)))
            return Optional.empty();
        Object parent = field(editor, "parent"), save = field(editor, "onSave");
        if (!(save instanceof Consumer<?>)) throw new IllegalStateException("Configured enum screen without a save callback");
        return Optional.of(new Session(parent, (Consumer<Object>) save));
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
