package com.tony.nosotros.voice
import org.junit.Assert.*
import org.junit.Test
class VoiceClockTest {
 @Test fun unsynchronizedClockNeverAcceptsCachedVoice(){val c=VoiceClock{0};assertFalse(c.fresh(1000,6000))}
 @Test fun packetsSlightlyAheadOfReceiptAreNotDiscarded(){
  var elapsed=1000L;val c=VoiceClock{elapsed};c.synchronize(1_800_000_000_000)
  elapsed+=100;assertTrue(c.fresh(1_800_000_000_350,6000))
 }
 @Test fun expiresUsingElapsedTimeRatherThanPhoneDate(){
  var elapsed=5000L;val c=VoiceClock{elapsed};c.synchronize(1_800_000_000_000)
  elapsed+=7000;assertFalse(c.fresh(1_800_000_000_000,6000))
  assertEquals(1_800_000_007_000,c.now())
 }
 @Test fun rejectsImplausibleFuturePacketsAndMissingTimestamp(){
  val c=VoiceClock{0};c.synchronize(1_800_000_000_000)
  assertFalse(c.fresh(1_800_000_010_000,6000));assertFalse(c.fresh(0,6000))
 }
 @Test fun resynchronizationHasNoDependenceOnWallClockTimezone(){
  var elapsed=100L;val c=VoiceClock{elapsed};c.synchronize(1_800_000_000_000)
  elapsed=1000;c.synchronize(1_800_000_001_000);elapsed=2000
  assertEquals(1_800_000_002_000,c.now());assertTrue(c.fresh(1_800_000_002_000,6000))
 }
}
