package ru.pasha.adaptationwheel.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.net.WheelEntry;
import java.util.*;

import static ru.pasha.adaptationwheel.client.WheelUi.*;

/**
 * Running adaptations in the corner, under the effect icons ({@link HudLayout}): category icon, name and stage squares,
 * the stage and what it leads to, the time left in seconds of adaptation (×1) with a gold speed chip, a progress bar.
 * A stage just reached stays lit in its own row for three seconds, a repeat hit lights its row for a second and a half.
 */
public final class AdaptationOverlay {
    private record Row(WheelEntry entry,int done) {}
    private static final List<WheelEntry> PENDING=new ArrayList<>();
    private static final List<Row> ROWS=new ArrayList<>();
    // Translated text is rebuilt once per client tick (names only on a language change), not every frame:
    // String.format and Component.getString were 9 % of render-thread allocation in JFR.
    private static final Map<String,String> NAMES=new HashMap<>();
    private static final Map<String,String[]> ROW_TEXT=new HashMap<>();
    private static String textLanguage="", title="", hint="", more="";
    private static int textTick=Integer.MIN_VALUE, moreCount=-1;
    private AdaptationOverlay() {}
    public static void render(ForgeGui gui,GuiGraphics g,float partialTick,int width,int height) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.options.hideGui || mc.player.isSpectator()) return;
        var state=ClientWheelState.get(mc.player.getId());
        if(state==null || !state.enabled() || !state.equipped()) return;
        MetaClient.render(g,width,height);
        if(AdaptationScreen.showing(mc.screen)) return;
        // Runs every frame: reused lists instead of copying and streaming all entries.
        List<Row> rows=ROWS;
        rows.clear();
        for(var c:state.completions()) {
            if(c.age(partialTick)>=ClientWheelState.COMPLETION_TICKS) continue;
            for(var e:state.entries()) if(e.key().equals(c.key()) && e.enabled()) { rows.add(new Row(e,c.stage())); break; }
        }
        var fps=FpsAdaptation.entry();
        boolean fpsDone=fps!=null && fps.enabled() && FpsAdaptation.completionAge(partialTick)<ClientWheelState.COMPLETION_TICKS && fps.stage()>0;
        if(fpsDone) rows.add(new Row(fps,fps.stage()));
        List<WheelEntry> entries=PENDING;
        entries.clear();
        for(var e:state.entries()) if(e.enabled() && e.pending() && !lit(rows,e.key())) entries.add(e);
        if(fps!=null && fps.enabled() && fps.pending() && !fpsDone) entries.add(fps);
        if(entries.size()>1) entries.sort(Comparator.comparingDouble(e -> remaining(e,state,partialTick)));
        for(var e:entries) rows.add(new Row(e,0));
        if(rows.isEmpty()) return;
        refreshText(mc);
        int top=top(mc);
        // One managed batch: the strings are flushed and sorted once instead of once per drawString.
        g.drawManaged(() -> draw(g,mc.font,state,rows,partialTick,width,height,top));
    }
    private static boolean lit(List<Row> rows,String key) {
        for(var row:rows) if(row.entry().key().equals(key)) return true;
        return false;
    }
    /** Under the effect icons vanilla draws in the same corner, when there are any. */
    private static int top(Minecraft mc) {
        boolean beneficial=false,harmful=false;
        for(MobEffectInstance effect:mc.player.getActiveEffects()) {
            if(!effect.showIcon() || !IClientMobEffectExtensions.of(effect).isVisibleInGui(effect)) continue;
            if(effect.getEffect().isBeneficial()) beneficial=true; else harmful=true;
        }
        return HudLayout.top(beneficial,harmful);
    }
    private static void refreshText(Minecraft mc) {
        String language=mc.options.languageCode;
        if(!language.equals(textLanguage)) { textLanguage=language; NAMES.clear(); textTick=Integer.MIN_VALUE; }
        int tick=mc.gui.getGuiTicks();
        if(tick==textTick) return;
        textTick=tick;
        ROW_TEXT.clear();
        moreCount=-1;
        title=Component.translatable("gui.adaptationwheel.hud.title").getString();
        hint=Component.translatable("gui.adaptationwheel.hint.short",ClientEvents.MENU.getTranslatedKeyMessage()).getString();
    }
    private static void draw(GuiGraphics g,Font font,ClientWheelState.State state,List<Row> all,float partialTick,int width,int height,int y) {
        int rows=HudLayout.rows(all.size(),height);
        int w=Math.min(HudLayout.WIDTH,width-16),x=width-w-8;
        int panelH=HudLayout.height(rows,all.size());
        g.fill(x,y,x+w,y+panelH,0xD8141117);
        g.fill(x,y,x+2,y+panelH,GOLD);
        g.drawString(font,title,x+8,y+5,GOLD,false);
        String shortHint=fit(font,hint,w-22-font.width(title));
        g.drawString(font,shortHint,x+w-6-font.width(shortHint),y+5,FAINT,false);
        for(int i=0;i<rows;i++) {
            var row=all.get(i); var e=row.entry(); int rowY=y+HudLayout.HEADER+i*HudLayout.ROW;
            boolean isFps=e.key().equals(FpsAdaptation.KEY);
            int color=isFps?FPS:e.profile().color;
            if(row.done()>0) {
                // The stage just reached: lit green for three seconds, with the protection it gives now.
                g.fill(x+2,rowY-2,x+w,rowY+22,0x2EBDE596);
                g.fill(x+2,rowY-2,x+3,rowY+22,GOOD);
                icon(g,CategoryIcons.forKey(e.key(),e.profile()),x+8,rowY,color);
                int total=Math.max(e.totalStages(),row.done());
                int pipsWidth=pips(g,x+w-8-(total*7-2),rowY+1,total,Math.min(row.done(),total),0,GOOD);
                g.drawString(font,fit(font,name(e),w-34-pipsWidth),x+18,rowY,TEXT,false);
                String[] text=doneText(e,row.done(),isFps);
                parts(g,font,List.of(part(text[0],GOOD),part(text[1],TEXT)),x+18,rowY+10,w-26);
                continue;
            }
            boolean qualifying=isFps && FpsAdaptation.qualifying();
            // Work left at ×1, not real time: a repeat hit leaves the number as it is and makes it run faster.
            float left=work(e,state,partialTick);
            float progress=Mth.clamp(1-left/(qualifying?100:Math.max(1,e.duration())),0,1);
            boolean flash=!isFps && e.progressing() && state.hitFlashAge(e.key(),partialTick)<ClientWheelState.HIT_FLASH_TICKS;
            if(flash) {
                // A repeat hit lights the row for a second and a half.
                g.fill(x+2,rowY-2,x+w,rowY+22,tint(GOLD));
                g.fill(x+2,rowY-2,x+3,rowY+22,GOLD);
            }
            icon(g,CategoryIcons.forKey(e.key(),e.profile()),x+8,rowY,e.progressing()?color:dim(color));
            int pipsWidth=pips(g,x+w-8-(e.totalStages()*7-2),rowY+1,e.totalStages(),Math.min(e.stage(),e.totalStages()),progress,color);
            int nameWidth=w-34-pipsWidth;
            if(flash) {
                String label=flashText(e);
                g.drawString(font,label,x+w-8-pipsWidth-6-font.width(label),rowY,LIT,false);
                nameWidth-=font.width(label)+6;
            }
            g.drawString(font,fit(font,name(e),nameWidth),x+18,rowY,TEXT,false);
            String[] text=rowText(e,isFps,qualifying,left);
            // The category shows as the row's colour and icon; the line itself says the stage, which matters more here.
            int timeWidth;
            if(text[2].isEmpty()) {
                timeWidth=font.width(text[1]);
                g.drawString(font,text[1],x+w-8-timeWidth,rowY+10,e.progressing()?GOLD:FAINT,false);
            } else {
                // The speed is a gold chip right of the time.
                int cw=font.width(text[2])+6,cx=x+w-8-cw;
                timeWidth=font.width(text[1])+4+cw;
                g.fill(cx,rowY+9,cx+cw,rowY+18,tint(GOLD));
                g.fill(cx,rowY+9,cx+1,rowY+18,GOLD);
                g.drawString(font,text[2],cx+3,rowY+10,GOLD,false);
                g.drawString(font,text[1],cx-4-font.width(text[1]),rowY+10,GOLD,false);
            }
            g.drawString(font,fit(font,text[0],w-32-timeWidth),x+18,rowY+10,color,false);
            bar(g,x+18,rowY+20,w-26,2,progress,e.progressing()?color:dim(color));
        }
        if(all.size()>rows) {
            int hidden=all.size()-rows;
            if(hidden!=moreCount) { moreCount=hidden; more=Component.translatable("gui.adaptationwheel.more",hidden).getString(); }
            g.drawString(font,more,x+8,y+HudLayout.HEADER+rows*HudLayout.ROW,MUTED,false);
        }
    }
    /** «✓ Этап 4/4 пройден» and « · защита 100%» of a stage just reached. */
    private static String[] doneText(WheelEntry e,int stage,boolean isFps) {
        String[] text=ROW_TEXT.get("done:"+e.key());
        if(text!=null) return text;
        if(stage>=5) text=new String[]{Component.translatable("gui.adaptationwheel.hud.done_existence").getString(),""};
        else {
            boolean protects=!isFps && MetaMechanic.fromKey(e.key())==null && e.protections().fourth()>0;
            text=new String[]{Component.translatable("gui.adaptationwheel.hud.done",stage,e.totalStages()).getString(),
                protects?Component.translatable("gui.adaptationwheel.hud.done_protection",e.protections().percent(stage)).getString():""};
        }
        ROW_TEXT.put("done:"+e.key(),text);
        return text;
    }
    /** Stage line, real time left and speed of a row, built at most once per client tick. */
    private static String[] rowText(WheelEntry e,boolean isFps,boolean qualifying,float left) {
        String[] text=ROW_TEXT.get(e.key());
        if(text!=null) return text;
        Component stage=qualifying?Component.translatable("adaptationwheel.fps.qualifying")
            : isFps?Component.translatable("adaptationwheel.fps.phase",e.stage()+1)
            : e.stage()==4?Component.translatable("gui.adaptationwheel.existence")
            : MetaMechanic.fromKey(e.key())!=null?Component.translatable("meta.adaptationwheel.progress",e.stage()+1)
            // The protection this stage leads to; the current one was 0% for every first stage.
            : Component.translatable("gui.adaptationwheel.hud.target",e.stage()+1,e.protections().percent(Math.min(4,e.stage()+1)));
        String time=!e.progressing()?Component.translatable("gui.adaptationwheel.paused").getString()
            : left>0?Component.translatable("gui.adaptationwheel.seconds",String.format(Locale.ROOT,"%.1f",left/20)).getString()
            : Component.translatable("gui.adaptationwheel.waiting").getString();
        String speed=e.progressing() && Math.abs(e.speed()-1)>0.005?String.format(Locale.ROOT,"×%.2f",e.speed()):"";
        text=new String[]{stage.getString(),time,speed};
        ROW_TEXT.put(e.key(),text);
        return text;
    }
    /** «+30% скорости»: what the configured repeat hit adds. */
    private static String flashText(WheelEntry e) {
        String key="flash:"+e.hitAccelerationPercent();
        String[] text=ROW_TEXT.get(key);
        if(text==null) {
            text=new String[]{Component.translatable("gui.adaptationwheel.hud.hit_speed",e.hitAccelerationPercent()).getString()};
            ROW_TEXT.put(key,text);
        }
        return text[0];
    }
    /** Work left in ticks at ×1: the time shown and the progress bar. */
    private static float work(WheelEntry e,ClientWheelState.State state,float partialTick) {
        if(e.key().equals(FpsAdaptation.KEY)) return FpsAdaptation.qualifying()?(float)FpsAdaptation.qualificationRemainingSeconds()*20:(float)Math.max(0,e.duration()-e.work());
        return state.remaining(e,partialTick);
    }
    /** Real ticks left at the current speed: only the order of the rows. */
    private static float remaining(WheelEntry e,ClientWheelState.State state,float partialTick) {
        if(e.key().equals(FpsAdaptation.KEY)) return work(e,state,partialTick);
        return state.realRemaining(e,partialTick);
    }
    private static String name(WheelEntry entry) {
        String cached=NAMES.get(entry.key());
        if(cached!=null) return cached;
        // Only translated names are kept: the catalogue fallback can still arrive from the server later.
        if (!I18n.exists(entry.translationKey())) return ClientWheelState.catalogue().stream().filter(known -> known.key().equals(entry.key()))
            .map(known -> known.fallbackName().isBlank() ? entry.key() : known.fallbackName())
            .findFirst().orElse(entry.key());
        String name=Component.translatable(entry.translationKey()).getString();
        NAMES.put(entry.key(),name);
        return name;
    }
}
