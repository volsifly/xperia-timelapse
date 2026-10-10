package com.xperia.timelapse.color

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedLut(val id:String,val name:String)

class ImportedLutRepository(context:Context) {
 private val app=context.applicationContext
 private val directory=File(app.filesDir,"imported-luts")
 private val preferences=app.getSharedPreferences("imported-luts",Context.MODE_PRIVATE)

 @Synchronized fun list():List<ImportedLut> = preferences.getStringSet("ids",emptySet()).orEmpty()
  .mapNotNull {id->preferences.getString("name-$id",null)?.let {ImportedLut(id,it)}}
  .filter {File(directory,"${it.id}.lut").isFile}
  .sortedBy {it.name.lowercase()}

 @Synchronized fun importCube(fileName:String,bytes:ByteArray):ImportedLut {
  require(fileName.substringAfterLast('.',"").equals("cube",true)) {"请选择 .cube 文件"}
  require(bytes.size in 1..(8*1024*1024)) {".cube 文件为空或超过 8 MB"}
  val parsed=CubeLutParser.parse(bytes.toString(Charsets.UTF_8),fileName)
  val id="custom-${UUID.randomUUID()}"
  val name=parsed.name.replace(Regex("[\\r\\n|]")," ").trim().take(64).ifEmpty {"Imported LUT"}
  val target=File(directory,"$id.lut")
  val temporary=File(directory,"$id.tmp")
  check(directory.exists() || directory.mkdirs()) {"无法创建 LUT 存储目录"}
  try {
   FileOutputStream(temporary).use {stream->stream.write(parsed.lut.encode());stream.fd.sync()}
   check(temporary.renameTo(target)) {"无法保存导入的 LUT"}
   val ids=(preferences.getStringSet("ids",emptySet()).orEmpty()+id)
   preferences.edit().putStringSet("ids",ids).putString("name-$id",name).apply()
  } finally {temporary.delete()}
  LutRepository.forget(id)
  return ImportedLut(id,name)
 }

 @Synchronized fun delete(id:String) {
  require(id.startsWith("custom-"))
  File(directory,"$id.lut").delete()
  val ids=preferences.getStringSet("ids",emptySet()).orEmpty()-id
  preferences.edit().putStringSet("ids",ids).remove("name-$id").apply()
  LutRepository.forget(id)
 }

 fun read(id:String):ByteArray {
  require(id.startsWith("custom-"))
  return File(directory,"$id.lut").readBytes()
 }
}
