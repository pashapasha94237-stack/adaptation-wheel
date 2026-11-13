package ru.pasha.adaptationwheel.client;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import java.util.*;
import org.lwjgl.opengl.GL11;
public final class GuiGraphics {
 public final MatrixStack pose;private final PortScreen screen;
 public GuiGraphics(MatrixStack p,PortScreen s){pose=p;screen=s;}
 public void fill(int x1,int y1,int x2,int y2,int color){AbstractGui.fill(pose,x1,y1,x2,y2,color);}
 public void drawString(FontRenderer f,String text,int x,int y,int color,boolean shadow){if(shadow)f.drawShadow(pose,text,x,y,color);else f.draw(pose,text,x,y,color);}
 public void drawString(FontRenderer f,ITextComponent text,int x,int y,int color,boolean shadow){if(shadow)f.drawShadow(pose,text,x,y,color);else f.draw(pose,text,x,y,color);}
 public void drawString(FontRenderer f,IReorderingProcessor text,int x,int y,int color,boolean shadow){if(shadow)f.drawShadow(pose,text,x,y,color);else f.draw(pose,text,x,y,color);}
 public void drawCenteredString(FontRenderer f,ITextComponent text,int x,int y,int color){AbstractGui.drawCenteredString(pose,f,text,x,y,color);}
 public void renderTooltip(FontRenderer f,List<ITextComponent> text,Optional<?> image,int x,int y){if(screen!=null)screen.tooltip(this,text,x,y);}
 public void enableScissor(int left,int top,int right,int bottom){Minecraft m=Minecraft.getInstance();double scale=m.getWindow().getGuiScale();GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor((int)(left*scale),(int)(m.getWindow().getHeight()-bottom*scale),(int)((right-left)*scale),(int)((bottom-top)*scale));}
 public void disableScissor(){GL11.glDisable(GL11.GL_SCISSOR_TEST);}
}