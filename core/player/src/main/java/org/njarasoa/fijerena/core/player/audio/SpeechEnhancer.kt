package org.njarasoa.fijerena.core.player.audio

interface SpeechEnhancer {
    /**
     * Initializes the speech enhancer.
     */
    fun initialize()

    /**
     * Processes a frame of audio.
     * @param buffer The input audio buffer (interleaved, 16-bit PCM).
     * @param sampleRate The sample rate of the input buffer.
     * @param channelCount The number of channels in the input buffer.
     * @return The processed audio buffer.
     */
    fun process(buffer: FloatArray): FloatArray

    /**
     * Releases resources used by the speech enhancer.
     */
    fun release()
}
