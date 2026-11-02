package com.p2p.client.network.social;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * High-performance UDP Video Streaming Engine for R-Connect P2P Video Calls.
 * Captures real physical webcam via Sarxos Webcam Capture API, supports live Screen Sharing via Robot,
 * provides immediate local preview (PiP), and includes virtual avatar fallback when webcam is busy.
 */
public final class VideoCallStreamer implements AutoCloseable {
    public enum SourceMode {
        CAMERA,
        SCREEN_SHARE
    }

    private static final int MAGIC = 0x56494445; // "VIDE"
    private static final int HEADER_SIZE = 16;
    private static final int CHUNK_PAYLOAD_SIZE = 1100;
    private static final int PACKET_BUFFER_SIZE = HEADER_SIZE + CHUNK_PAYLOAD_SIZE;

    private static final int TARGET_WIDTH = 320;
    private static final int TARGET_HEIGHT = 240;

    private final DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean videoEnabled = new AtomicBoolean(true);
    private volatile SourceMode sourceMode = SourceMode.CAMERA;
    private final String localDisplayName;

    private volatile InetAddress remoteAddress;
    private volatile int remotePort = 0;

    private final AtomicInteger frameSequence = new AtomicInteger(0);
    private final Map<Integer, FrameAssembly> assemblingFrames = new ConcurrentHashMap<>();
    private volatile int latestRenderedFrameId = -1;

    private Consumer<BufferedImage> localFrameListener;
    private Consumer<BufferedImage> remoteFrameListener;

    private Thread captureThread;
    private Thread receiveThread;
    private Robot robot;
    private Webcam webcam;

    public VideoCallStreamer(String localDisplayName) throws SocketException {
        this.socket = new DatagramSocket(); // dynamic OS allocated UDP port
        this.localDisplayName = (localDisplayName == null || localDisplayName.isBlank()) ? "R-Connect User" : localDisplayName;
        try {
            this.robot = new Robot();
        } catch (AWTException ignored) {
            this.robot = null;
        }
        initWebcam();
    }

    private void initWebcam() {
        try {
            this.webcam = Webcam.getDefault();
            if (this.webcam != null) {
                Dimension[] sizes = webcam.getViewSizes();
                Dimension chosen = new Dimension(320, 240);
                if (sizes != null && sizes.length > 0) {
                    chosen = sizes[0];
                    for (Dimension d : sizes) {
                        if (d.width == 320 && d.height == 240) {
                            chosen = d;
                            break;
                        }
                    }
                }
                webcam.setViewSize(chosen);
                if (!webcam.isOpen()) {
                    webcam.open(true);
                }
            }
        } catch (Throwable ignored) {
            this.webcam = null;
        }
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public void onLocalFrame(Consumer<BufferedImage> listener) {
        this.localFrameListener = listener;
    }

    public void onRemoteFrame(Consumer<BufferedImage> listener) {
        this.remoteFrameListener = listener;
    }

    public void setVideoEnabled(boolean enabled) {
        this.videoEnabled.set(enabled);
        if (enabled && sourceMode == SourceMode.CAMERA && webcam != null && !webcam.isOpen()) {
            try { webcam.open(true); } catch (Throwable ignored) {}
        }
    }

    public boolean isVideoEnabled() {
        return videoEnabled.get();
    }

    public void setSourceMode(SourceMode mode) {
        this.sourceMode = mode;
        if (mode == SourceMode.CAMERA) {
            if (webcam != null && !webcam.isOpen()) {
                try { webcam.open(true); } catch (Throwable ignored) {}
            }
        }
    }

    public SourceMode getSourceMode() {
        return sourceMode;
    }

    /**
     * Starts capturing frames immediately for local preview in PiP, even before call is accepted.
     */
    public synchronized void startPreview() {
        if (captureThread != null && captureThread.isAlive()) return;
        running.set(true);

        captureThread = new Thread(() -> {
            ImageWriter writer = null;
            ImageWriteParam writeParam = null;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                writer = writers.next();
                writeParam = writer.getDefaultWriteParam();
                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionQuality(0.55f);
                }
            }

            long frameCounter = 0;
            while (running.get() && !socket.isClosed()) {
                long startMs = System.currentTimeMillis();

                if (!videoEnabled.get()) {
                    if (remoteAddress != null && remotePort > 0) {
                        sendFlagPacket(remoteAddress, remotePort, 2);
                    }
                    if (localFrameListener != null) localFrameListener.accept(null);
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                    continue;
                }

                BufferedImage frame = captureFrame(frameCounter++);
                if (frame != null) {
                    if (localFrameListener != null) {
                        localFrameListener.accept(frame);
                    }
                    if (remoteAddress != null && remotePort > 0) {
                        sendFrame(frame, remoteAddress, remotePort, writer, writeParam);
                    }
                }

                long elapsed = System.currentTimeMillis() - startMs;
                long sleepMs = Math.max(15, 60 - elapsed); // ~16-20 FPS
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (writer != null) writer.dispose();
        }, "RConnect-VideoCapture-" + socket.getLocalPort());
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /**
     * Connects remote UDP target for full bidirectional transmission.
     */
    public synchronized void start(InetAddress remoteAddress, int remotePort) {
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        startPreview();

        if (receiveThread != null && receiveThread.isAlive()) return;

        receiveThread = new Thread(() -> {
            byte[] packetBuffer = new byte[PACKET_BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);

            while (running.get() && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    if (packet.getLength() < HEADER_SIZE) continue;

                    ByteBuffer bb = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
                    int magic = bb.getInt();
                    if (magic != MAGIC) continue;

                    int frameId = bb.getInt();
                    short chunkIndex = bb.getShort();
                    short totalChunks = bb.getShort();
                    short payloadLen = bb.getShort();
                    short flags = bb.getShort();

                    if (frameId < latestRenderedFrameId - 10) continue;

                    if (assemblingFrames.size() > 15) {
                        assemblingFrames.entrySet().removeIf(e -> e.getKey() < frameId - 5);
                    }

                    boolean isCameraOff = (flags & 2) != 0;
                    if (isCameraOff) {
                        if (remoteFrameListener != null) {
                            remoteFrameListener.accept(null);
                        }
                        continue;
                    }

                    FrameAssembly assembly = assemblingFrames.computeIfAbsent(frameId, id -> new FrameAssembly(id, totalChunks));
                    byte[] chunkBytes = new byte[payloadLen];
                    bb.get(chunkBytes, 0, Math.min(payloadLen, bb.remaining()));
                    assembly.addChunk(chunkIndex, chunkBytes);

                    if (assembly.isComplete()) {
                        assemblingFrames.remove(frameId);
                        if (frameId >= latestRenderedFrameId) {
                            latestRenderedFrameId = frameId;
                            byte[] fullJpeg = assembly.assemble();
                            try {
                                BufferedImage img = ImageIO.read(new ByteArrayInputStream(fullJpeg));
                                if (img != null && remoteFrameListener != null) {
                                    remoteFrameListener.accept(img);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (IOException e) {
                    if (!running.get()) break;
                }
            }
        }, "RConnect-VideoReceiver-" + socket.getLocalPort());
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private BufferedImage captureFrame(long tick) {
        // 1. Screen Share mode
        if (sourceMode == SourceMode.SCREEN_SHARE && robot != null) {
            try {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int captureW = Math.min(1366, screenSize.width);
                int captureH = Math.min(768, screenSize.height);
                BufferedImage raw = robot.createScreenCapture(new Rectangle(0, 0, captureW, captureH));

                BufferedImage scaled = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(raw, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
                g.dispose();
                return scaled;
            } catch (Exception ignored) {}
        }

        // 2. Physical hardware webcam mode
        if (webcam != null && webcam.isOpen()) {
            try {
                BufferedImage camImg = webcam.getImage();
                if (camImg != null) {
                    if (camImg.getWidth() == TARGET_WIDTH && camImg.getHeight() == TARGET_HEIGHT) {
                        return camImg;
                    }
                    BufferedImage scaled = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = scaled.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(camImg, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
                    g.dispose();
                    return scaled;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback: virtual animated video frame if camera is locked or not found
        return generateVirtualAvatarFrame(tick);
    }

    private BufferedImage generateVirtualAvatarFrame(long tick) {
        BufferedImage img = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(0x11, 0x14, 0x1D));
        g.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);

        double phase = (tick * 0.1);
        int haloX = (int) (TARGET_WIDTH / 2 + Math.cos(phase) * 30);
        int haloY = (int) (TARGET_HEIGHT / 2 + Math.sin(phase) * 15);
        g.setColor(new Color(0x38, 0x8B, 0xF5, 0x20));
        g.fillOval(haloX - 70, haloY - 70, 140, 140);

        int avatarSize = 80;
        int ax = (TARGET_WIDTH - avatarSize) / 2;
        int ay = (TARGET_HEIGHT - avatarSize) / 2 - 15;
        g.setColor(new Color(0x1B, 0x22, 0x30));
        g.fillOval(ax, ay, avatarSize, avatarSize);
        g.setColor(new Color(0x40, 0x4B, 0x62));
        g.setStroke(new BasicStroke(2.0f));
        g.drawOval(ax, ay, avatarSize, avatarSize);

        String initial = localDisplayName.isEmpty() ? "R" : localDisplayName.substring(0, 1).toUpperCase();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        g.setColor(new Color(0x86, 0xA0, 0xEE));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(initial, ax + (avatarSize - fm.stringWidth(initial)) / 2, ay + (avatarSize + fm.getAscent()) / 2 - 4);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(new Color(0xF0, 0xF2, 0xF5));
        fm = g.getFontMetrics();
        g.drawString(localDisplayName, (TARGET_WIDTH - fm.stringWidth(localDisplayName)) / 2, ay + avatarSize + 26);

        g.setColor(new Color(0x00, 0x00, 0x00, 0x80));
        g.fillRoundRect(10, 10, 80, 20, 8, 8);
        g.setColor(new Color(0x4F, 0xBF, 0x8F));
        g.fillOval(16, 16, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString("CAMERA", 30, 24);

        g.dispose();
        return img;
    }

    private void sendFrame(BufferedImage frame, InetAddress remoteAddr, int remotePort, ImageWriter writer, ImageWriteParam writeParam) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
            if (writer != null && writeParam != null) {
                try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(mcios);
                    writer.write(null, new IIOImage(frame, null, null), writeParam);
                }
            } else {
                ImageIO.write(frame, "jpg", baos);
            }
            byte[] jpegBytes = baos.toByteArray();
            int totalBytes = jpegBytes.length;
            short totalChunks = (short) ((totalBytes + CHUNK_PAYLOAD_SIZE - 1) / CHUNK_PAYLOAD_SIZE);
            if (totalChunks <= 0) return;

            int frameId = frameSequence.incrementAndGet();
            short flags = (short) (sourceMode == SourceMode.SCREEN_SHARE ? 1 : 0);

            for (short chunkIdx = 0; chunkIdx < totalChunks; chunkIdx++) {
                int offset = chunkIdx * CHUNK_PAYLOAD_SIZE;
                int len = Math.min(CHUNK_PAYLOAD_SIZE, totalBytes - offset);

                ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE + len);
                bb.putInt(MAGIC);
                bb.putInt(frameId);
                bb.putShort(chunkIdx);
                bb.putShort(totalChunks);
                bb.putShort((short) len);
                bb.putShort(flags);
                bb.put(jpegBytes, offset, len);

                DatagramPacket packet = new DatagramPacket(bb.array(), bb.position(), remoteAddr, remotePort);
                socket.send(packet);
            }
        } catch (Exception ignored) {}
    }

    private void sendFlagPacket(InetAddress remoteAddr, int remotePort, int flagValue) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE);
            bb.putInt(MAGIC);
            bb.putInt(frameSequence.incrementAndGet());
            bb.putShort((short) 0);
            bb.putShort((short) 1);
            bb.putShort((short) 0);
            bb.putShort((short) flagValue);

            DatagramPacket packet = new DatagramPacket(bb.array(), bb.position(), remoteAddr, remotePort);
            socket.send(packet);
        } catch (Exception ignored) {}
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            closeWebcam();
            if (socket != null && !socket.isClosed()) socket.close();
            return;
        }
        closeWebcam();
        if (socket != null && !socket.isClosed()) socket.close();
        if (captureThread != null) captureThread.interrupt();
        if (receiveThread != null) receiveThread.interrupt();
        assemblingFrames.clear();
    }

    private void closeWebcam() {
        if (webcam != null) {
            try {
                if (webcam.isOpen()) {
                    webcam.close();
                }
            } catch (Throwable ignored) {}
            webcam = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    private static final class FrameAssembly {
        final int frameId;
        final short totalChunks;
        final byte[][] chunks;
        int receivedChunks = 0;

        FrameAssembly(int frameId, short totalChunks) {
            this.frameId = frameId;
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        synchronized void addChunk(short idx, byte[] data) {
            if (idx >= 0 && idx < totalChunks && chunks[idx] == null) {
                chunks[idx] = data;
                receivedChunks++;
            }
        }

        synchronized boolean isComplete() {
            return receivedChunks == totalChunks;
        }

        synchronized byte[] assemble() {
            int totalSize = 0;
            for (byte[] c : chunks) if (c != null) totalSize += c.length;
            byte[] full = new byte[totalSize];
            int pos = 0;
            for (byte[] c : chunks) {
                if (c != null) {
                    System.arraycopy(c, 0, full, pos, c.length);
                    pos += c.length;
                }
            }
            return full;
        }
    }
}
