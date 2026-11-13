package ru.pasha.adaptationwheel.client;
import net.minecraft.util.text.*;
public final class Text {
 public static ITextComponent translatable(String k,Object... args){return new TranslationTextComponent(k,args);}
 public static ITextComponent literal(String s){return new StringTextComponent(s);}
 public static ITextComponent empty(){return StringTextComponent.EMPTY;}
}