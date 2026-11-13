package ru.pasha.adaptationwheel.client;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.ITextComponent;
import java.util.List;
public abstract class PortScreen extends net.minecraft.client.gui.screen.Screen {
 protected PortScreen(ITextComponent title){super(title);}
 protected <T extends Widget>T addRenderableWidget(T w){return addButton(w);}
 protected void rebuildWidgets(){buttons.clear();children.clear();init();}
 protected void renderBackground(GuiGraphics g){super.renderBackground(g.pose);}
 public void render(GuiGraphics g,int mx,int my,float pt){super.render(g.pose,mx,my,pt);}
 @Override public final void render(MatrixStack pose,int mx,int my,float pt){render(new GuiGraphics(pose,this),mx,my,pt);}
 void tooltip(GuiGraphics g,List<ITextComponent> text,int x,int y){renderComponentTooltip(g.pose,text,x,y);}
}