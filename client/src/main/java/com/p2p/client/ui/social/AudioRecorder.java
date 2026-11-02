package com.p2p.client.ui.social;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight, thread-safe voice recorder using standard Java Sound API (JDK built-in).
 * Records 16 kHz, 16-bit Mono PCM audio into high-compatibility WAV format.
 */
public final class AudioRecorder {
    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private TargetDataLine line;
    private Thread worker;
    private ByteArrayOutputStream out;
    private long startNano;

    public boolean isRecording() {
        return recording.get();
    }

    public synchronized void start() throws LineUnavailableException {
        if (recording.get()) return;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Hệ thống không hỗ trợ thu âm với định dạng này.");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();

        out = new ByteArrayOutputStream();
        recording.set(true);
        startNano = System.nanoTime();

        worker = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (recording.get()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }, "audio-recorder-worker");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized RecordResult stop() throws IOException {
        if (!recording.getAndSet(false)) return null;

        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }

        if (worker != null) {
            try {
                worker.join(500);
            } catch (InterruptedException ignored) {}
            worker = null;
        }

        long elapsedNanos = System.nanoTime() - startNano;
        int durationSec = Math.max(1, (int) Math.round(elapsedNanos / 1_000_000_000.0));

        byte[] audioBytes = out.toByteArray();
        if (audioBytes.length == 0) return null;

        File tempFile = Files.createTempFile("rconnect_voice_", ".wav").toFile();
        tempFile.deleteOnExit();

        try (ByteArrayInputStream in = new ByteArrayInputStream(audioBytes);
             AudioInputStream ais = new AudioInputStream(in, FORMAT, audioBytes.length / FORMAT.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile);
        }

        return new RecordResult(tempFile, durationSec);
    }

    public synchronized void cancel() {
        recording.set(false);
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        worker = null;
        out = null;
    }

    public record RecordResult(File file, int durationSeconds) {}
}
