package ru.pasha.adaptationwheel.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import ru.pasha.adaptationwheel.integration.ConfiguredMobSelectionAccess;
import ru.pasha.adaptationwheel.integration.ConfiguredSkinAccess;

/** Configured remains optional; its other list editors are never intercepted. */
public final class ConfiguredMobPicker {
    private ConfiguredMobPicker() {}
    public static void opening(ScreenEvent.Opening event) {
        var screen=event.getNewScreen();
        if(screen!=null && screen.getClass().getName().equals(ConfiguredSkinAccess.ENUM_SCREEN)) {
            // «Облик колеса»: the look menu with previews instead of Configured's list of English enum names
            try {
                var session=ConfiguredSkinAccess.read(screen);
                if(session.isPresent())event.setNewScreen(new WheelSkinScreen((Screen)session.get().parent(),skin->session.get().save().accept(skin)));
            }catch(ReflectiveOperationException | RuntimeException failure) {
                LogUtils.getLogger().warn("Could not open the wheel look menu; retaining Configured's original editor",failure);
            }
            return;
        }
        if(screen==null || !screen.getClass().getName().equals("com.mrcrayfish.configured.client.screen.EditListScreen"))return;
        try {
            var session=ConfiguredMobSelectionAccess.read(screen);
            if(session.isPresent())event.setNewScreen(new MobSelectionScreen(session.get()));
        }catch(ReflectiveOperationException | RuntimeException failure) {
            LogUtils.getLogger().warn("Could not open mob picker; retaining Configured's original editor",failure);
        }
    }
}
