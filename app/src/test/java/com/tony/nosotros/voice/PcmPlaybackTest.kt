package com.tony.nosotros.voice

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
class PcmPlaybackTest {
 @Test fun routeFailureRecreatesOutputAndKeepsAllPcm(){
  var opened=0;var closed=0;val played=mutableListOf<Byte>()
  val p=PcmPlayback {val attempt=++opened;object:PcmDevice{
   override fun write(pcm:ByteBuffer):Int {if(attempt==1)return -6;val count=minOf(2,pcm.remaining());repeat(count){played.add(pcm.get())};return count}
   override fun close(){closed++}
  }}
  p.write(ByteBuffer.wrap(byteArrayOf(1,2,3,4,5)));assertEquals(listOf<Byte>(1,2,3,4,5),played);assertEquals(2,opened)
  p.close();p.close();assertEquals(2,closed)
 }
 @Test fun persistentErrorFailsInsteadOfSilentlyDroppingVoiceOrLooping(){
  var opened=0;val p=PcmPlayback{opened++;object:PcmDevice{override fun write(pcm:ByteBuffer)=-3;override fun close(){}}}
  try{p.write(ByteBuffer.wrap(byteArrayOf(1,2)));fail("Permanent output error ignored")}catch(expected:IllegalStateException){}
  assertEquals(2,opened);p.close()
 }
 @Test fun closeInterruptsAWriteWithoutHoldingItsLock(){
  val writing=java.util.concurrent.CountDownLatch(1);val stopped=java.util.concurrent.CountDownLatch(1)
  val p=PcmPlayback{object:PcmDevice{
   override fun write(pcm:ByteBuffer):Int{writing.countDown();check(stopped.await(2,java.util.concurrent.TimeUnit.SECONDS));return -6}
   override fun close(){stopped.countDown()}
  }}
  val thread=Thread{try{p.write(ByteBuffer.wrap(byteArrayOf(1)))}catch(expected:IllegalStateException){}}
  thread.start();assertTrue(writing.await(1,java.util.concurrent.TimeUnit.SECONDS));p.close();thread.join(1000);assertFalse(thread.isAlive)
 }
 @Test fun stoppedOutputCannotResumeWithoutAUserSession(){
  val p=PcmPlayback{error("Must not open")};p.close()
  try{p.write(ByteBuffer.wrap(byteArrayOf(1)));fail("Closed output reopened")}catch(expected:IllegalStateException){}
 }
}
