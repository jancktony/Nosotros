package com.tony.nosotros.voice
import org.junit.Assert.*
import org.junit.Test
class AmrFramesTest {
 private fun frame(value:Int)=ByteArray(21){value.toByte()}.also {it[0]=0x2c}
 @Test fun largeCodecBuffersDoNotSilentlyDropSpeech(){
  val a=frame(1);val b=frame(2);val c=frame(3);val d=frame(4)
  val parsed=AmrFrames().feed(a+b+c+d);assertEquals(4,parsed.size)
  listOf(a,b,c,d).zip(parsed).forEach{(expected,actual)->assertArrayEquals(expected,actual)}
 }
 @Test fun splitFramesAndOptionalFileHeaderAreReassembled(){
  val a=frame(7);val parser=AmrFrames();assertTrue(parser.feed("#!A".toByteArray()).isEmpty())
  assertTrue(parser.feed("MR\n".toByteArray()+a.copyOfRange(0,10)).isEmpty())
  val result=parser.feed(a.copyOfRange(10,a.size));assertArrayEquals(a,result.single())
 }
 @Test fun noDataFrameRemainsValid(){assertArrayEquals(byteArrayOf(0x7c),AmrFrames().feed(byteArrayOf(0x7c)).single())}
 @Test fun malformedOrUnboundedCodecOutputIsReported(){
  for(bytes in listOf(byteArrayOf(0x48),ByteArray(8193))){try{AmrFrames().feed(bytes);fail("Invalid codec output accepted")}catch(expected:IllegalArgumentException){}}
 }
}
