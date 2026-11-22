package com.tony.nosotros.voice
import org.junit.Assert.*
import org.junit.Test
class VoicePacketExpiryTest {
 private val now=1_800_000_000_000L
 @Test fun pendingServerTimestampNeverExpiresNewVoice(){
  assertFalse(VoicePacketExpiry.canClean(false,true,null,now))
  assertFalse(VoicePacketExpiry.canClean(false,false,null,now))
  assertFalse(VoicePacketExpiry.canClean(false,true,now-60000,now))
 }
 @Test fun cacheAndFutureDatesNeverAuthorizeCleanup(){
  assertFalse(VoicePacketExpiry.canClean(true,false,now-60000,now))
  assertFalse(VoicePacketExpiry.canClean(false,false,now+1000,now))
 }
 @Test fun onlyConfirmedExpiredPacketsAreDeleted(){
  assertFalse(VoicePacketExpiry.canClean(false,false,now,now))
  assertFalse(VoicePacketExpiry.canClean(false,false,now-15000,now))
  assertTrue(VoicePacketExpiry.canClean(false,false,now-15001,now))
 }
}
