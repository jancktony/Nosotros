package com.tony.nosotros.voice

/** Codec output buffers may contain several AMR-NB frames, or part of one. */
internal class AmrFrames {
    private var pending=ByteArray(0)
    private var beginning=true
    private val magic="#!AMR\n".toByteArray(Charsets.US_ASCII)
    private val sizes=intArrayOf(13,14,16,18,20,21,27,32,6,0,0,0,0,0,0,1)
    fun feed(bytes: ByteArray): List<ByteArray> {
        require(bytes.size<=8192)
        val data=pending+bytes;var offset=0
        if(beginning){
            if(data.size<magic.size && data.indices.all {data[it]==magic[it]}){pending=data;return emptyList()}
            if(data.size>=magic.size && magic.indices.all {data[it]==magic[it]})offset=magic.size
            beginning=false
        }
        val result=mutableListOf<ByteArray>()
        while(offset<data.size){
            val header=data[offset].toInt() and 255
            val size=sizes[(header shr 3) and 15]
            require(header and 0x83 == 0 && size>0){"Formato AMR no compatible"}
            if(data.size-offset<size)break
            result.add(data.copyOfRange(offset,offset+size));offset+=size
        }
        pending=data.copyOfRange(offset,data.size)
        check(pending.size<=32)
        return result
    }
}
