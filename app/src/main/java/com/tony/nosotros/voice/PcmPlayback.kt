package com.tony.nosotros.voice

import java.nio.ByteBuffer

internal interface PcmDevice {
    fun write(pcm: ByteBuffer): Int
    fun close()
}
/** Retry a dead Android output once. Closing must be able to interrupt a blocking write. */
internal class PcmPlayback(private val create: () -> PcmDevice) {
    private var device: PcmDevice? = null
    private var closed = false
    private fun output(): PcmDevice = synchronized(this) {
        check(!closed)
        device ?: create().also {device=it}
    }
    private fun reset(expected: PcmDevice) {
        val removed=synchronized(this){if(device===expected){device=null;true}else false}
        if(removed)runCatching {expected.close()}
    }
    fun write(pcm: ByteBuffer) {
        var recovered=false
        while(pcm.hasRemaining()){
            val output=output();val position=pcm.position();val remaining=pcm.remaining()
            val written=output.write(pcm)
            if(written<=0){
                reset(output);pcm.position(position)
                check(!recovered){"Android no pudo reproducir la voz"}
                recovered=true
            }else{
                check(written<=remaining)
                pcm.position(position+written)
            }
        }
    }
    fun close(){
        val previous=synchronized(this){closed=true;device.also {device=null}}
        runCatching {previous?.close()}
    }
}
