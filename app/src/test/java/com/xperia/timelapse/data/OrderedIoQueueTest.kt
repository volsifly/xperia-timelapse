package com.xperia.timelapse.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderedIoQueueTest {
 @Test fun laterSnapshotCannotOvertakeEarlierSnapshot()=runTest {
  val queue=OrderedIoQueue(backgroundScope);val events=mutableListOf<Int>();val release=CompletableDeferred<Unit>()
  queue.submit("metadata") {release.await();events+=1}
  queue.submit("metadata") {events+=2}
  runCurrent();assertTrue(events.isEmpty())
  release.complete(Unit);queue.drain();assertEquals(listOf(1,2),events)
 }
 @Test fun slowMetadataDoesNotBlockIndependentPhotoOrLogWork()=runTest {
  val queue=OrderedIoQueue(backgroundScope);val release=CompletableDeferred<Unit>();var logSaved=false
  queue.submit("metadata") {release.await()}
  queue.submit("log") {logSaved=true}.await()
  assertTrue(logSaved);assertFalse(release.isCompleted)
  release.complete(Unit);queue.drain()
 }
 @Test fun diskFailureIsReportedAndFailedSnapshotIsNotOverwritten()=runTest {
  val writerScope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
  try {
   val queue=OrderedIoQueue(writerScope);var later=false
   queue.submit("metadata") {throw java.io.IOException("disk full")}
   val tail=queue.submit("metadata") {later=true}
   assertTrue(runCatching {tail.await()}.exceptionOrNull() is java.io.IOException)
   assertFalse(later);assertTrue(runCatching {queue.await("metadata")}.isFailure)
  } finally {writerScope.cancel()}
 }
}
