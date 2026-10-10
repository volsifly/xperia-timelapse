package com.xperia.timelapse.color

import android.graphics.*
import android.os.Build
import android.view.View

object ColorLookRenderer {
 fun apply(bitmap:Bitmap,look:ColorLook):Bitmap {
  if(!look.active)return bitmap
  val output=bitmap.copy(Bitmap.Config.ARGB_8888,true) ?: error("无法创建色彩图像")
  val transform=ColorTransform(look);val row=IntArray(output.width)
  for(y in 0 until output.height) {
   output.getPixels(row,0,output.width,0,y,output.width,1)
   for(x in row.indices)row[x]=transform.pixel(row[x])
   output.setPixels(row,0,output.width,0,y,output.width,1)
  }
  return output
 }
 fun preview(view:View,look:ColorLook,imageWidth:Int=0,imageHeight:Int=0) {
  if(Build.VERSION.SDK_INT<33)return
  PreviewApi33.apply(view,look,imageWidth,imageHeight)
 }
 @androidx.annotation.RequiresApi(33)
 private object PreviewApi33 {
  private val atlases=mutableMapOf<String,Bitmap>()
  private val emptyAtlas=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
  private val applied=java.util.WeakHashMap<View,List<Any>>()
  fun apply(view:View,look:ColorLook,imageWidth:Int,imageHeight:Int) {
   val key=listOf(look,imageWidth,imageHeight,view.width,view.height)
   if(applied[view]==key)return
   if(!look.active) {view.setRenderEffect(null);applied[view]=key;return}
   val shader=RuntimeShader(SHADER)
   val s=look.style
   val lutId=look.importedLutId ?: s.lutId
   val lut=lutId?.let {LutRepository.get(it)}
   val atlas=if(lut==null)emptyAtlas else atlases.getOrPut(lutId!!) {
    val pixels=IntArray(lut.size*lut.size*lut.size) {i->
     -0x1000000 or ((lut.data[i*3].toInt() and 255) shl 16) or ((lut.data[i*3+1].toInt() and 255) shl 8) or (lut.data[i*3+2].toInt() and 255)
    }
    Bitmap.createBitmap(pixels,lut.size*lut.size,lut.size,Bitmap.Config.ARGB_8888)
   }
   val atlasShader=BitmapShader(atlas,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
   atlasShader.setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
   shader.setInputShader("lutImage",atlasShader)
   shader.setFloatUniform("lutSize",lut?.size?.toFloat() ?: 1f)
   shader.setFloatUniform("useLut",if(lut==null)0f else 1f)
   shader.setFloatUniform("recipe",s.contrast,s.saturation,s.lift,s.ceiling)
   shader.setFloatUniform("tint",s.warm,s.cool)
   shader.setFloatUniform("amount",look.strength)
   shader.setFloatUniform("isLog",if(s==ColorStyle.LOG)1f else 0f)
   val scale=if(imageWidth>0 && imageHeight>0)minOf(view.width.toFloat()/imageWidth,view.height.toFloat()/imageHeight) else 0f
   val w=if(scale>0f)imageWidth*scale else view.width.toFloat();val h=if(scale>0f)imageHeight*scale else view.height.toFloat()
   shader.setFloatUniform("bounds",(view.width-w)/2,(view.height-h)/2,(view.width+w)/2,(view.height+h)/2)
   view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader,"inputImage"));applied[view]=key
  }
  private const val SHADER="""
   uniform shader inputImage;
   uniform shader lutImage;
   uniform float lutSize;
   uniform float useLut;
   float3 vertex(float3 i) {
    return float3(lutImage.eval(float2(i.x+i.y*lutSize+0.5,i.z+0.5)).rgb);
   }
   float3 grade(float3 c) {
    float3 p=clamp(c,0.0,1.0)*(lutSize-1.0);
    float3 a=floor(p);float3 b=min(a+float3(1.0),float3(lutSize-1.0));float3 f=p-a;
    float3 z0=mix(mix(vertex(float3(a.x,a.y,a.z)),vertex(float3(b.x,a.y,a.z)),f.x),
                  mix(vertex(float3(a.x,b.y,a.z)),vertex(float3(b.x,b.y,a.z)),f.x),f.y);
    float3 z1=mix(mix(vertex(float3(a.x,a.y,b.z)),vertex(float3(b.x,a.y,b.z)),f.x),
                  mix(vertex(float3(a.x,b.y,b.z)),vertex(float3(b.x,b.y,b.z)),f.x),f.y);
    return mix(z0,z1,f.z);
   }
   uniform float4 recipe;
   uniform float4 bounds;
   uniform float2 tint;
   uniform float amount;
   uniform float isLog;
   float tone(float x) {
    if(isLog>0.5) {
     float linear=x<=0.04045 ? x/12.92 : pow((x+0.055)/1.055,2.4);
     return 0.095+0.81*log(1.0+100.0*linear)/log(101.0);
    }
    return clamp(recipe.z+(recipe.w-recipe.z)*(x+recipe.x*(x-0.5)*4.0*x*(1.0-x)),0.0,1.0);
   }
   half4 main(float2 p) {
    half4 sampleColor=inputImage.eval(p);
    if(p.x<bounds.x || p.y<bounds.y || p.x>bounds.z || p.y>bounds.w)return sampleColor;
    float alpha=float(sampleColor.a);
    float3 c=alpha>0.0 ? float3(sampleColor.rgb)/alpha : float3(0.0);
    if(useLut>0.5)return half4(mix(c,grade(c),amount)*alpha,alpha);
    float3 t=float3(tone(c.r),tone(c.g),tone(c.b));
    float y=dot(t,float3(0.2126,0.7152,0.0722));
    float3 result=float3(y)+(t-float3(y))*recipe.y;
    result+=float3(tint.x*y-tint.y*(1.0-y),tint.y*(1.0-y)*0.35,-tint.x*y+tint.y*(1.0-y));
    return half4(mix(c,clamp(result,0.0,1.0),amount)*alpha,alpha);
   }
  """
 }
}
