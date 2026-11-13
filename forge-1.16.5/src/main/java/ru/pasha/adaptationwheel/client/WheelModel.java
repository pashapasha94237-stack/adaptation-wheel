package ru.pasha.adaptationwheel.client;
import net.minecraft.client.renderer.model.ModelRenderer;
public final class WheelModel {
 private static ModelRenderer part(float x,float z,float angle){ModelRenderer p=new ModelRenderer(32,32,0,0);p.setPos(x,0,z);p.yRot=angle;return p;}
 public static ModelRenderer create(){
  ModelRenderer root=part(0,0,0);
  for(int i=0;i<24;i++){float a=(float)(i*Math.PI*2/24);ModelRenderer rim=part((float)Math.sin(a)*10,(float)Math.cos(a)*10,a);rim.addBox(-1.39F,-.7F,-.7F,2.78F,1.4F,1.4F);root.addChild(rim);}
  for(int i=0;i<8;i++){float a=(float)(i*Math.PI/4);ModelRenderer spoke=part(0,0,a);spoke.texOffs(0,8).addBox(-.5F,-.55F,0,1,1.1F,14.5F);root.addChild(spoke);root.addChild(ornament((float)Math.sin(a)*14.5F,(float)Math.cos(a)*14.5F,a));}
  root.addChild(ornament(0,0,0));return root;
 }
 private static ModelRenderer ornament(float x,float z,float angle){ModelRenderer p=part(x,z,angle);p.addBox(-2.4F,-1.8F,-2.4F,4.8F,3.6F,4.8F);p.addBox(-1.8F,-2.4F,-1.8F,3.6F,4.8F,3.6F);p.addBox(-2.6F,-1.3F,-1.3F,5.2F,2.6F,2.6F);p.addBox(-1.3F,-1.3F,-2.6F,2.6F,2.6F,5.2F);return p;}
}