package com.xperia.timelapse.data

import kotlinx.coroutines.*

/** Independent files write concurrently; one file's snapshots retain their submission order. */
class OrderedIoQueue(private val scope:CoroutineScope) {
 private val tails=mutableMapOf<String,Deferred<Unit>>()
 @Synchronized fun submit(key:String,operation:suspend ()->Unit):Deferred<Unit> {
  val previous=tails[key]
  return scope.async {previous?.await();operation()}.also {tails[key]=it}
 }
 suspend fun await(key:String) {val tail=synchronized(this){tails[key]};tail?.await()}
 suspend fun drain() {val pending=synchronized(this){tails.values.toList()};pending.awaitAll()}
}
