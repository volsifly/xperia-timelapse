package com.xperia.timelapse.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.xperia.timelapse.video.GalleryVideoStore

/** The same checked-copy publishing contract as video, targeting the image collection. */
class MediaStorePhotoStore(context:Context):GalleryVideoStore {
 private val resolver=context.contentResolver
 override fun create(name:String):String=checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
  put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,if(name.endsWith(".dng",true))"image/x-adobe-dng" else "image/jpeg")
  put(MediaStore.Images.Media.RELATIVE_PATH,"${Environment.DIRECTORY_PICTURES}/XperiaTimelapse")
  put(MediaStore.Images.Media.DATE_TAKEN,System.currentTimeMillis());put(MediaStore.Images.Media.IS_PENDING,1)
 })) {"无法创建相册照片，原片已保留"}.toString()
 override fun output(id:String)=checkNotNull(resolver.openOutputStream(Uri.parse(id),"w")) {"无法写入相册照片"}
 override fun input(id:String)=checkNotNull(resolver.openInputStream(Uri.parse(id))) {"无法校验相册照片"}
 override fun publish(id:String) {check(resolver.update(Uri.parse(id),ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)==1) {"无法发布相册照片"}}
 override fun discard(id:String) {resolver.delete(Uri.parse(id),null,null)}
}
