package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** Real native WebRTC offer/answer + ICE + channel. No accounts, internet, microphone or personal data. */
class PeerConnectionDeviceTest {
    private open class Observer:PeerConnection.Observer {
        override fun onSignalingChange(p:PeerConnection.SignalingState){}
        override fun onIceConnectionChange(p:PeerConnection.IceConnectionState){}
        override fun onIceConnectionReceivingChange(p:Boolean){}
        override fun onIceGatheringChange(p:PeerConnection.IceGatheringState){}
        override fun onIceCandidate(p:IceCandidate){}
        override fun onIceCandidatesRemoved(p:Array<out IceCandidate>){}
        override fun onAddStream(p:MediaStream){}
        override fun onRemoveStream(p:MediaStream){}
        override fun onDataChannel(p:DataChannel){}
        override fun onRenegotiationNeeded(){}
        override fun onAddTrack(p:RtpReceiver,s:Array<out MediaStream>){}
    }
    private fun description(action:(SdpObserver)->Unit):SessionDescription {
        val done=CountDownLatch(1);var result:SessionDescription?=null;var error:String?=null
        action(object:SdpObserver{
            override fun onCreateSuccess(s:SessionDescription){result=s;done.countDown()}
            override fun onCreateFailure(s:String){error=s;done.countDown()}
            override fun onSetSuccess(){}
            override fun onSetFailure(s:String){error=s;done.countDown()}
        })
        assertTrue("SDP creation timed out",done.await(15,TimeUnit.SECONDS));assertNull(error);return requireNotNull(result)
    }
    private fun setDescription(action:(SdpObserver)->Unit) {
        val done=CountDownLatch(1);var error:String?=null
        action(object:SdpObserver{
            override fun onSetSuccess(){done.countDown()}
            override fun onSetFailure(s:String){error=s;done.countDown()}
            override fun onCreateSuccess(s:SessionDescription){}
            override fun onCreateFailure(s:String){error=s;done.countDown()}
        })
        assertTrue("SDP set timed out",done.await(15,TimeUnit.SECONDS));assertNull(error)
    }
    @Test fun twoNativePeersExchangeCompressedVoicePacket() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val factory=PeerConnectionFactory.builder().createPeerConnectionFactory()
        var a:PeerConnection?=null;var b:PeerConnection?=null;var receiver:DataChannel?=null
        val iceForA=ConcurrentLinkedQueue<IceCandidate>();val iceForB=ConcurrentLinkedQueue<IceCandidate>()
        val open=CountDownLatch(1);val received=CountDownLatch(1)
        val expected=VoiceWire.packet(0,listOf(byteArrayOf(1,2,3,4)),123)
        var actual:ByteArray?=null
        try {
            a=factory.createPeerConnection(PeerConnection.RTCConfiguration(emptyList()),object:Observer(){override fun onIceCandidate(p:IceCandidate){iceForB.add(p)}})
            b=factory.createPeerConnection(PeerConnection.RTCConfiguration(emptyList()),object:Observer(){
                override fun onIceCandidate(p:IceCandidate){iceForA.add(p)}
                override fun onDataChannel(p:DataChannel){receiver=p;p.registerObserver(object:DataChannel.Observer{
                    override fun onStateChange(){}
                    override fun onBufferedAmountChange(v:Long){}
                    override fun onMessage(buffer:DataChannel.Buffer){val bytes=ByteArray(buffer.data.remaining());buffer.data.get(bytes);actual=bytes;received.countDown()}
                })}
            })
            val sender=requireNotNull(a).createDataChannel(VoiceWire.CHANNEL,DataChannel.Init().apply{ordered=false;maxRetransmits=0})
            sender.registerObserver(object:DataChannel.Observer{
                override fun onStateChange(){if(sender.state()==DataChannel.State.OPEN)open.countDown()}
                override fun onBufferedAmountChange(v:Long){}
                override fun onMessage(buffer:DataChannel.Buffer){}
            })
            val offer=description{a!!.createOffer(it,MediaConstraints())}
            setDescription{a!!.setLocalDescription(it,offer)};setDescription{b!!.setRemoteDescription(it,offer)}
            val answer=description{b!!.createAnswer(it,MediaConstraints())}
            setDescription{b!!.setLocalDescription(it,answer)};setDescription{a!!.setRemoteDescription(it,answer)}
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
            while(open.count>0&&System.nanoTime()<until){
                while(true)a!!.addIceCandidate(iceForA.poll()?:break)
                while(true)b!!.addIceCandidate(iceForB.poll()?:break)
                open.await(50,TimeUnit.MILLISECONDS)
            }
            assertEquals("Native data channel did not connect",0L,open.count)
            assertTrue(sender.send(DataChannel.Buffer(ByteBuffer.wrap(expected),true)))
            assertTrue("Voice packet was not received",received.await(5,TimeUnit.SECONDS))
            assertArrayEquals(expected,actual);assertNotNull(VoiceWire.frames(requireNotNull(actual)))
            sender.unregisterObserver();sender.close();sender.dispose()
        } finally {receiver?.unregisterObserver();receiver?.close();receiver?.dispose();a?.close();b?.close();a?.dispose();b?.dispose();factory.dispose()}
    }
}
