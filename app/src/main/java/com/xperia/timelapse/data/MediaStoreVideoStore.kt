package com.xperia.timelapse.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.xperia.timelapse.video.GalleryVideoStore

class MediaStoreVideoStore(context:Context):GalleryVideoStore {
 private val resolver=context.contentResolver
 override fun create(name:String):String = checkNotNull(resolver.insert(
  MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
   put(MediaStore.Video.Media.DISPLAY_NAME,name)
   put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
   put(MediaStore.Video.Media.RELATIVE_PATH,"${Environment.DIRECTORY_MOVIES}/XperiaTimelapse")
   put(MediaStore.Video.Media.DATE_TAKEN,System.currentTimeMillis())
   put(MediaStore.Video.Media.IS_PENDING,1)
  })) {"无法创建相册视频，原始照片已保留"}.toString()
 override fun output(id:String)=checkNotNull(resolver.openOutputStream(Uri.parse(id),"w")) {"无法写入相册"}
 override fun input(id:String)=checkNotNull(resolver.openInputStream(Uri.parse(id))) {"无法校验相册视频"}
 override fun publish(id:String) {check(resolver.update(Uri.parse(id),ContentValues().apply {put(MediaStore.Video.Media.IS_PENDING,0)},null,null)==1) {"无法发布相册视频，原始照片已保留"}}
 override fun discard(id:String) {resolver.delete(Uri.parse(id),null,null)}
}
