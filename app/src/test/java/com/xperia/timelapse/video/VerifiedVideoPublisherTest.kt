package com.xperia.timelapse.video

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class VerifiedVideoPublisherTest {
 private class Store:GalleryVideoStore {
  var bytes=byteArrayOf();var created=0;var published=false;var discarded=false;var corrupt=false;var failPublish=false
  override fun create(name:String):String {created++;return "pending-video"}
  override fun output(id:String):OutputStream=object:ByteArrayOutputStream() {
   override fun close() {bytes=toByteArray();if(corrupt && bytes.isNotEmpty())bytes[0]=(bytes[0].toInt() xor 1).toByte();super.close()}
  }
  override fun input(id:String)=ByteArrayInputStream(bytes)
  override fun publish(id:String) {if(failPublish)throw IOException("full");published=true}
  override fun discard(id:String) {discarded=true}
 }
 private fun source(block:(File)->Unit) {
  val file=File.createTempFile("verified-video-",".mp4")
  try {file.writeBytes(ByteArray(150000){(it%251).toByte()});block(file)}finally{file.delete()}
 }
 @Test fun completeCopyIsPublishedAndSourceIsRetained()=source {file->
  val store=Store();assertEquals("pending-video",VerifiedVideoPublisher(store).save(file,"test.mp4",true){false})
  assertTrue(store.published);assertFalse(store.discarded);assertArrayEquals(file.readBytes(),store.bytes);assertTrue(file.isFile)
 }
 @Test fun unverifiedVideoCannotCreateGalleryEntry()=source {file->
  val store=Store();assertThrows(IllegalStateException::class.java){VerifiedVideoPublisher(store).save(file,"test.mp4",false){false}};assertEquals(0,store.created)
 }
 @Test fun corruptedCopyIsDiscardedBeforePublication()=source {file->
  val store=Store().apply {corrupt=true};assertThrows(IllegalStateException::class.java){VerifiedVideoPublisher(store).save(file,"test.mp4",true){false}};assertTrue(store.discarded);assertFalse(store.published);assertTrue(file.isFile)
 }
 @Test fun cancellationDuringCopyDiscardsPendingEntry()=source {file->
  val store=Store();var checks=0
  assertThrows(IllegalStateException::class.java){VerifiedVideoPublisher(store).save(file,"test.mp4",true){++checks>=3}}
  assertTrue(store.discarded);assertFalse(store.published);assertTrue(file.isFile)
 }
 @Test fun publicationFailureDiscardsPendingEntryAndKeepsSource()=source {file->
  val store=Store().apply {failPublish=true};assertThrows(IOException::class.java){VerifiedVideoPublisher(store).save(file,"test.mp4",true){false}};assertTrue(store.discarded);assertFalse(store.published);assertTrue(file.isFile)
 }
}
