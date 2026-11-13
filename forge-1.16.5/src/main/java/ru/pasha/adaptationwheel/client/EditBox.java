package ru.pasha.adaptationwheel.client;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
public final class EditBox extends net.minecraft.client.gui.widget.TextFieldWidget {
 private final FontRenderer font;private ITextComponent hint;
 public EditBox(FontRenderer f,int x,int y,int w,int h,ITextComponent title){super(f,x,y,w,h,title);font=f;}
 public void setHint(ITextComponent h){hint=h;}
 @Override public void renderButton(MatrixStack pose,int mx,int my,float pt){super.renderButton(pose,mx,my,pt);if(isVisible()&&!isFocused()&&getValue().isEmpty()&&hint!=null)font.draw(pose,font.plainSubstrByWidth(hint.getString(),getInnerWidth()),x+4,y+5,0xFF888888);}
}