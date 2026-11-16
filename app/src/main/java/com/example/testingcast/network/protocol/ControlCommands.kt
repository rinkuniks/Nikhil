package com.example.testingcast.network.protocol

object ControlCommands {
    const val START = "START"
    const val STOP = "STOP"
    const val PING = "PING"
    const val PONG = "PONG"
    const val PAUSE = "PAUSE"
    const val RESUME = "RESUME"
    const val ACK = "ACK"

    /** Sender -> receiver: payload = [width:int][height:int]. */
    const val VIDEO_FORMAT = "VIDEO_FORMAT"

    /** Sender -> receiver: payload = [sampleRate:int][channelCount:int]. */
    const val AUDIO_FORMAT = "AUDIO_FORMAT"

    /** Receiver -> sender: ask the encoder for an IDR frame (decoder reset / packet loss). */
    const val REQUEST_KEYFRAME = "REQUEST_KEYFRAME"
}
