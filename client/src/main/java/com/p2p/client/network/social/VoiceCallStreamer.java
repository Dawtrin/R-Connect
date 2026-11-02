package com.p2p.client.network.social;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure Java UDP Audio Streaming Engine for R-Connect Voice Calls.
 * Uses 16 kHz 16-bit Mono PCM for crystal clear voice over DatagramSocket without native dependencies.
 */
public final class VoiceCallStreamer implements AutoCloseable {
    public static final float SAMPLE_RATE = 16000.0f;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;
    public static final int FRAME_SIZE_BYTES = 640; // 20ms frame = 320 samples * 2 bytes

    private final DatagramSocket socket;
    private final AudioFormat audioFormat;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);

    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private Thread captureThread;
    private Thread playbackThread;

    public VoiceCallStreamer() throws SocketException {
        this.socket = new DatagramSocket(); // dynamic OS allocated UDP port
        this.audioFormat = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public synchronized void start(InetAddress remoteAddress, int remotePort) throws LineUnavailableException {
        if (running.get()) return;
        running.set(true);

        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

        try {
            micLine = (TargetDataLine) AudioSystem.getLine(micInfo);
            micLine.open(audioFormat, FRAME_SIZE_BYTES * 4);
            micLine.start();
        } catch (Exception e) {
            // In headless/test environments or if mic is busy, line might fail
            micLine = null;
        }

        try {
            speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakerLine.open(audioFormat, FRAME_SIZE_BYTES * 4);
            speakerLine.start();
        } catch (Exception e) {
            speakerLine = null;
        }

        // Capture Thread: Mic -> UDP packets
        if (micLine != null) {
            captureThread = new Thread(() -> {
                byte[] buffer = new byte[FRAME_SIZE_BYTES];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, remotePort);
                while (running.get() && !socket.isClosed()) {
                    int read = micLine.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        if (muted.get()) {
                            Arrays.fill(buffer, (byte) 0);
                        }
                        packet.setData(buffer, 0, read);
                        try {
                            socket.send(packet);
                        } catch (IOException e) {
                            if (!running.get()) break;
                        }
                    }
                }
            }, "RConnect-VoiceMic-" + socket.getLocalPort());
            captureThread.setDaemon(true);
            captureThread.start();
        }

        // Playback Thread: UDP packets -> Speaker
        playbackThread = new Thread(() -> {
            byte[] buffer = new byte[FRAME_SIZE_BYTES * 2];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running.get() && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    if (packet.getLength() > 0 && speakerLine != null && speakerLine.isOpen()) {
                        speakerLine.write(packet.getData(), packet.getOffset(), packet.getLength());
                    }
                } catch (IOException e) {
                    if (!running.get()) break;
                }
            }
        }, "RConnect-VoiceSpeaker-" + socket.getLocalPort());
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void setMuted(boolean mute) {
        muted.set(mute);
    }

    public boolean isMuted() {
        return muted.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            if (socket != null && !socket.isClosed()) socket.close();
            return;
        }
        if (micLine != null) {
            try { micLine.stop(); micLine.close(); } catch (Exception ignored) {}
            micLine = null;
        }
        if (speakerLine != null) {
            try { speakerLine.stop(); speakerLine.close(); } catch (Exception ignored) {}
            speakerLine = null;
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (captureThread != null) captureThread.interrupt();
        if (playbackThread != null) playbackThread.interrupt();
    }

    @Override
    public void close() {
        stop();
    }
}
