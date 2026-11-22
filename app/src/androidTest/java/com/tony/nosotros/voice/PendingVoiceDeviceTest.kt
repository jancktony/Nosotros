package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PendingVoiceDeviceTest {
 @Test fun pendingNotesSurviveRecreationAndStayScopedToAccountAndPair(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val pair="test-drafts-${System.nanoTime()}";val uid="alice"
  val key=java.security.MessageDigest.getInstance("SHA-256").digest("$pair:$uid".toByteArray()).joinToString(""){"%02x".format(it)}
  val dir=File(context.filesDir,"voice-drafts/$key").apply{mkdirs()}
  try{
   File(dir,"abcd_1500.m4a").writeBytes(byteArrayOf(1,2,3))
   val first=VoiceNotes(context,pair){uid};assertEquals(1,first.pending().size);first.close()
   val reopened=VoiceNotes(context,pair){uid};val pending=reopened.pending().single();assertEquals(1500L,pending.duration)
   assertTrue(VoiceNotes(context,pair){"bob"}.pending().isEmpty())
   assertTrue(VoiceNotes(context,"other-pair"){uid}.pending().isEmpty())
   reopened.discard(pending);assertTrue(reopened.pending().isEmpty())
  }finally{dir.deleteRecursively()}
 }
 @Test fun pendingNotesCannotEscapeTheirPrivateDirectory(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val notes=VoiceNotes(context,"path-test"){"alice"}
  try{notes.discard(VoiceNotes.Draft("../other",1000));fail("Traversal accepted")}catch(expected:IllegalArgumentException){}
 }
}
