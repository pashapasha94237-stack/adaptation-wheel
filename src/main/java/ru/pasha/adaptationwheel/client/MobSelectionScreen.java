package ru.pasha.adaptationwheel.client;

import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import ru.pasha.adaptationwheel.core.MobSelectionModel;
import ru.pasha.adaptationwheel.integration.ConfiguredMobSelectionAccess;

/** Replaces only Configured's specialMobs editor. Done stages its holder; Configured's Save commits it. */
public final class MobSelectionScreen extends Screen {
    private final Screen parent;
    private final ConfiguredMobSelectionAccess.Session session;
    private final MobSelectionModel model;
    private final List<Button> buttons=new ArrayList<>();
    private final List<EditBox> multipliers=new ArrayList<>();
    private final List<String> rowIds=new ArrayList<>();
    private List<MobSelectionModel.Entry> visible=List.of();
    private EditBox search;
    private Button filter,previous,next,done;
    private int page,rows,left,panelWidth,footer;
    private boolean selectedOnly,saveFailed,updating;
    private String query="";
    public MobSelectionScreen(ConfiguredMobSelectionAccess.Session session) {
        super(Component.translatable("gui.adaptationwheel.mob_picker.title"));
        this.session=session;this.parent=(Screen)session.parent();
        List<MobSelectionModel.Entry> entries=new ArrayList<>();
        for(EntityType<?> type:BuiltInRegistries.ENTITY_TYPE) {
            if(type==EntityType.PLAYER || type==EntityType.ARMOR_STAND || !DefaultAttributes.hasSupplier(type))continue;
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            String name=I18n.exists(type.getDescriptionId())?Component.translatable(type.getDescriptionId()).getString():id.substring(id.indexOf(':')+1).replace('_',' ');
            entries.add(new MobSelectionModel.Entry(id,name,true));
        }
        model=new MobSelectionModel(entries,session.initial(),session.initialMultipliers());
    }
    @Override protected void init() {
        buttons.clear();multipliers.clear();rowIds.clear();panelWidth=Math.min(620,width-24);left=(width-panelWidth)/2;footer=height-78;
        rows=Math.max(1,(footer-92)/26);
        search=addRenderableWidget(new EditBox(font,left+8,46,panelWidth-152,20,Component.translatable("gui.adaptationwheel.mob_picker.search")));
        search.setMaxLength(128);search.setHint(Component.translatable("gui.adaptationwheel.mob_picker.search"));search.setValue(query);
        search.setResponder(value->{query=value;page=0;refresh();});
        filter=addRenderableWidget(Button.builder(Component.empty(),b->{selectedOnly=!selectedOnly;page=0;refresh();})
            .bounds(left+panelWidth-136,46,128,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.adaptationwheel.mob_picker.clear"),b->{model.clear();refresh();})
            .bounds(left+panelWidth-114,24,106,18).build());
        for(int i=0;i<rows;i++) {
            int row=i;
            buttons.add(addRenderableWidget(Button.builder(Component.empty(),b->{int index=page*rows+row;if(index<visible.size()){model.toggle(visible.get(index).id());refresh();}})
                .bounds(left+8,86+i*26,panelWidth-104,22).build()));
            rowIds.add(null);
            EditBox input=addRenderableWidget(new EditBox(font,left+panelWidth-80,86+i*26,72,22,Component.translatable("gui.adaptationwheel.mob_picker.multiplier")));
            input.setMaxLength(32);input.setHint(Component.literal(String.format(Locale.ROOT,"%.2f",session.defaultMultiplier())));
            input.setTooltip(Tooltip.create(Component.translatable("gui.adaptationwheel.mob_picker.multiplier_hint",String.format(Locale.ROOT,"%.2f",session.defaultMultiplier()))));
            input.setResponder(text->{
                if(updating || rowIds.get(row)==null)return;
                String id=rowIds.get(row);model.setMultiplierInput(id,text);input.setTextColor(model.validMultiplierInput(id)?0xFFE6E6E6:0xFFFF7777);
                done.active=model.valid();
            });
            multipliers.add(input);
        }
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;refresh();}).bounds(left+8,footer,36,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;refresh();}).bounds(left+panelWidth-44,footer,36,20).build());
        done=addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{
            try{session.stage(model.selection(),model.multipliers());minecraft.setScreen(parent);}
            catch(RuntimeException failure){saveFailed=true;LogUtils.getLogger().error("Could not stage mob selection in Configured",failure);}
        }).bounds(width/2-104,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
        refresh();
    }
    private void refresh() {
        if(filter==null)return;
        visible=model.filtered(query,selectedOnly);page=Math.max(0,Math.min(page,Math.max(0,(visible.size()-1)/rows)));
        filter.setMessage(Component.translatable(selectedOnly?"gui.adaptationwheel.mob_picker.selected_only":"gui.adaptationwheel.mob_picker.all"));
        updating=true;
        for(int i=0;i<buttons.size();i++) {
            Button button=buttons.get(i);EditBox input=multipliers.get(i);int index=page*rows+i;
            button.visible=index<visible.size();input.visible=false;rowIds.set(i,null);if(!button.visible)continue;
            var entry=visible.get(index);
            rowIds.set(i,entry.id());input.visible=model.selected(entry.id());input.setValue(model.multiplierInput(entry.id()));
            input.setTextColor(model.validMultiplierInput(entry.id())?0xFFE6E6E6:0xFFFF7777);
            String name=entry.available()?entry.name():Component.translatable("gui.adaptationwheel.mob_picker.unavailable",entry.id()).getString();
            Component label=Component.translatable(model.selected(entry.id())?"gui.adaptationwheel.mob_picker.checked":"gui.adaptationwheel.mob_picker.unchecked",name);
            button.setMessage(Component.literal(font.plainSubstrByWidth(label.getString(),panelWidth-120)));
            button.setTooltip(Tooltip.create(Component.literal(name+"\n"+entry.id())));
        }
        updating=false;done.active=model.valid();
        previous.active=page>0;next.active=(page+1)*rows<visible.size();
    }
    @Override public void tick(){search.tick();for(EditBox input:multipliers)input.tick();}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(delta!=0){page+=delta<0?1:-1;refresh();return true;}return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics);graphics.fill(left,6,left+panelWidth,height-5,0xEF171923);
        graphics.drawCenteredString(font,title,width/2,12,0xFFEBC568);
        graphics.drawString(font,Component.translatable("gui.adaptationwheel.mob_picker.count",model.selection().size()),left+8,29,0xFFBBB5A2,false);
        graphics.drawString(font,Component.translatable("gui.adaptationwheel.mob_picker.multiplier"),left+panelWidth-81,73,0xFFB5A78B,false);
        if(visible.isEmpty())graphics.drawCenteredString(font,Component.translatable("gui.adaptationwheel.mob_picker.empty"),width/2,88,0xFFB5A78B);
        graphics.drawCenteredString(font,Component.translatable("gui.adaptationwheel.mob_picker.page",page+1,Math.max(1,(visible.size()+rows-1)/rows)),width/2,footer+6,0xFFDDD5C4);
        Component hint=Component.translatable(saveFailed?"gui.adaptationwheel.mob_picker.error":!model.valid()?"gui.adaptationwheel.mob_picker.invalid_multiplier":"gui.adaptationwheel.mob_picker.save_hint");
        graphics.drawCenteredString(font,font.plainSubstrByWidth(hint.getString(),panelWidth-16),width/2,height-49,saveFailed || !model.valid()?0xFFFF7777:0xFFB5A78B);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
}
