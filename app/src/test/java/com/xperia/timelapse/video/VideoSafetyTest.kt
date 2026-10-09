package com.xperia.timelapse.video
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
class VideoSafetyTest {
 @get:Rule val temporary=TemporaryFolder()
 private fun planes()=listOf(Yuv420.Plane(ByteBuffer.allocate(4),2,1),Yuv420.Plane(ByteBuffer.allocate(1),1,1),Yuv420.Plane(ByteBuffer.allocate(1),1,1))
 @Test fun blackIsLimitedRange() {
  val p=planes();Yuv420.write(IntArray(4){0xFF000000.toInt()},2,2,p)
  assertEquals(16,p[0].buffer.get(0).toInt() and 255);assertEquals(128,p[1].buffer.get(0).toInt() and 255);assertEquals(128,p[2].buffer.get(0).toInt() and 255)
 }
 @Test fun whiteIsLimitedRange() {
  val p=planes();Yuv420.write(IntArray(4){-1},2,2,p)
  assertEquals(235,p[0].buffer.get(0).toInt() and 255)
 }
 @Test fun redHasCorrectChromaAndInterleavedPadding() {
  val uv=ByteBuffer.allocate(16);val u=uv.duplicate().apply {position(2)};val v=uv.duplicate().apply {position(3)}
  val y=ByteBuffer.allocate(12)
  Yuv420.write(IntArray(4){0xFFFF0000.toInt()},2,2,listOf(Yuv420.Plane(y,6,1),Yuv420.Plane(u,8,2),Yuv420.Plane(v,8,2)))
  assertEquals(82,y.get(6).toInt() and 255);assertEquals(90,uv.get(2).toInt() and 255);assertEquals(240,uv.get(3).toInt() and 255);assertEquals(0,y.get(2).toInt())
 }
 @Test fun unverifiedVideoCannotDeletePhotos() {
  val photo=temporary.newFile("frame_000001.jpg");val video=temporary.newFile("preview.mp4").apply {writeText("video")}
  assertThrows(IllegalStateException::class.java) {VerifiedPhotoCleanup.delete(listOf(photo),video,false)};assertTrue(photo.exists())
 }
 @Test fun emptyVideoCannotDeletePhotos() {
  val photo=temporary.newFile("frame_000001.jpg");val video=temporary.newFile("preview.mp4")
  assertThrows(IllegalStateException::class.java) {VerifiedPhotoCleanup.delete(listOf(photo),video,true)};assertTrue(photo.exists())
 }
 @Test fun onlyProjectFrameFilesCanBeDeleted() {
  val photo=temporary.newFile("other.jpg");val video=temporary.newFile("preview.mp4").apply {writeText("video")}
  assertThrows(IllegalArgumentException::class.java) {VerifiedPhotoCleanup.delete(listOf(photo),video,true)};assertTrue(photo.exists())
 }
 @Test fun anotherProjectCannotBeDeleted() {
  val outside=java.io.File(temporary.newFolder(),"frame_000001.jpg").apply {writeText("photo")}
  val video=temporary.newFile("preview.mp4").apply {writeText("video")}
  assertThrows(IllegalArgumentException::class.java) {VerifiedPhotoCleanup.delete(listOf(outside),video,true)};assertTrue(outside.exists())
 }
 @Test fun verifiedCleanupKeepsVideoAndMetadata() {
  val photo=temporary.newFile("frame_000001.jpg");val video=temporary.newFile("preview.mp4").apply {writeText("video")};val metadata=temporary.newFile("metadata.json")
  assertEquals(1,VerifiedPhotoCleanup.delete(listOf(photo),video,true));assertFalse(photo.exists());assertTrue(video.exists());assertTrue(metadata.exists())
 }
}
