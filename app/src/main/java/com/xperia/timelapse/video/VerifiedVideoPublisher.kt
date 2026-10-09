package com.xperia.timelapse.video

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

interface GalleryVideoStore {
 fun create(name:String):String
 fun output(id:String):OutputStream
 fun input(id:String):InputStream
 fun publish(id:String)
 fun discard(id:String)
}

/** Publish only a verified, byte-for-byte complete copy; hide partial entries. */
class VerifiedVideoPublisher(private val store:GalleryVideoStore) {
 fun save(source:File,name:String,verified:Boolean,cancelled:()->Boolean):String {
  check(verified && source.isFile && source.length()>0) {"媒体文件尚未验证，无法保存到相册"}
  check(!cancelled()) {"保存已取消，原始照片已保留"}
  val id=store.create(name)
  try {
   val expected=MessageDigest.getInstance("SHA-256")
   source.inputStream().buffered().use {input->store.output(id).buffered().use {out->
    val buffer=ByteArray(64*1024)
    while(true) {
     check(!cancelled()) {"保存已取消，原始照片已保留"}
     val count=input.read(buffer);if(count<0)break
     expected.update(buffer,0,count);out.write(buffer,0,count)
    }
   }}
   val actual=MessageDigest.getInstance("SHA-256")
   var size=0L
   store.input(id).buffered().use {input->
    val buffer=ByteArray(64*1024)
    while(true) {
     check(!cancelled()) {"保存已取消，原始照片已保留"}
     val count=input.read(buffer);if(count<0)break
     actual.update(buffer,0,count);size+=count
    }
   }
   check(size==source.length() && expected.digest().contentEquals(actual.digest())) {"相册文件复制校验失败，原始照片已保留"}
   check(!cancelled()) {"保存已取消，原始照片已保留"}
   store.publish(id)
   return id
  }catch(e:Exception) {runCatching {store.discard(id)};throw e}
 }
}
