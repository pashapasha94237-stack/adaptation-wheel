package ru.pasha.adaptationwheel.client;
import net.minecraft.client.Minecraft;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.math.MathHelper;

import ru.pasha.adaptationwheel.net.WheelEntry;
import java.util.*;

public final class AdaptationOverlay {
    private AdaptationOverlay() {}
    public static void render(GuiGraphics g,float partialTick,int width,int height) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.options.hideGui || mc.player.isSpectator()) return;
        ClientWheelState.State state=ClientWheelState.get(mc.player.getId());
        if(state==null || !state.enabled() || !state.equipped()) return;
        List<WheelEntry> entries=state.entries().stream().filter(e -> e.enabled() && e.pending())
            .sorted(Comparator.comparingDouble(e -> state.remaining(e,partialTick))).collect(java.util.stream.Collectors.toList());
        int rows=Math.min(entries.size(),Math.max(1,Math.min(4,(height-112)/54)));
        int w=Math.min(226,width-16),x=width-w-8,y=38;
        boolean flash=state.completionAge(partialTick)<60;
        int panelH=38+rows*54+(entries.size()>rows?13:0)+(flash?14:0);
        g.fill(x,y,x+w,y+panelH,0xD5111015);
        g.fill(x,y,x+2,y+panelH,0xFFEBC568);
        g.drawString(mc.font,Text.translatable("gui.adaptationwheel.title"),x+9,y+7,0xFFEBC568,false);
        g.drawString(mc.font,Text.translatable("gui.adaptationwheel.hint",ClientEvents.MENU.getTranslatedKeyMessage()),x+9,y+20,0xFFB5A78B,false);
        for(int i=0;i<rows;i++) {
            WheelEntry e=entries.get(i); int rowY=y+37+i*54;
            ITextComponent title=e.stage()==4 ? Text.translatable("gui.adaptationwheel.existence")
                : Text.translatable("gui.adaptationwheel.target",e.stage()+1,e.reduction());
            g.drawString(mc.font,mc.font.plainSubstrByWidth(name(e),w-20),
                x+9,rowY,0xFFF3E8D2,false);
            g.drawString(mc.font,Text.translatable(e.profile().translationKey()),x+9,rowY+11,e.profile().color,false);
            g.drawString(mc.font,title,x+9,rowY+22,0xFFEBC568,false);
            float remaining=state.remaining(e,partialTick);
            ITextComponent seconds=!e.progressing() ? Text.translatable("gui.adaptationwheel.paused") : remaining>0 ? Text.translatable("gui.adaptationwheel.work_seconds",MathHelper.ceil(remaining/20))
                : Text.translatable("gui.adaptationwheel.waiting");
            g.drawString(mc.font,seconds,x+9,rowY+43,0xFFB5A78B,false);
            float progress=MathHelper.clamp(1-remaining/e.duration(),0,1);
            int barY=rowY+35;
            g.fill(x+9,barY,x+w-9,barY+6,0xFF493D2A);
            g.fill(x+10,barY+1,x+10+MathHelper.floor((w-20)*progress),barY+5,0xFFD9A840);
            ITextComponent speed=Text.translatable("gui.adaptationwheel.speed",String.format(Locale.ROOT,"%.2f",e.speed()));
            g.drawString(mc.font,speed,x+w-9-mc.font.width(speed),rowY+43,0xFFB5A78B,false);
        }
        int footer=y+37+rows*54;
        if(entries.size()>rows) { g.drawString(mc.font,Text.translatable("gui.adaptationwheel.more",entries.size()-rows),
            x+9,footer,0xFFB5A78B,false); footer+=13; }
        if(flash) g.drawString(mc.font,Text.translatable("gui.adaptationwheel.finished"),x+9,footer,0xFFBDE596,false);
    }
    private static String name(WheelEntry entry) {
        if (I18n.exists(entry.translationKey())) return Text.translatable(entry.translationKey()).getString();
        return ClientWheelState.catalogue().stream().filter(known -> known.key().equals(entry.key()))
            .map(known -> known.fallbackName().trim().isEmpty() ? entry.key() : known.fallbackName())
            .findFirst().orElse(entry.key());
    }
}
