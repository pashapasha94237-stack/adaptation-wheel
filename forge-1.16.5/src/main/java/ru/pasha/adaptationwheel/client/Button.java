package ru.pasha.adaptationwheel.client;
import net.minecraft.util.text.ITextComponent;
import java.util.function.Consumer;
public final class Button extends net.minecraft.client.gui.widget.button.Button {
 private Button(int x,int y,int w,int h,ITextComponent text,Consumer<Button> press){super(x,y,w,h,text,b->press.accept((Button)b));}
 public void setX(int x){this.x=x;}
 public boolean isHoveredOrFocused(){return isHovered()||isFocused();}
 public static Builder builder(ITextComponent text,Consumer<Button> press){return new Builder(text,press);}
 public static final class Builder {
  private final ITextComponent text;private final Consumer<Button> press;private int x,y,w,h;
  Builder(ITextComponent t,Consumer<Button> p){text=t;press=p;}
  public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
  public Button build(){return new Button(x,y,w,h,text,press);}
 }
}