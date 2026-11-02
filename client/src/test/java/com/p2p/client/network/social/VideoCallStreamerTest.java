package com.p2p.client.network.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VideoCallStreamerTest {
    @Test
    void testVideoCallStreamerLifecycle() throws Exception {
        VideoCallStreamer v1 = new VideoCallStreamer("Tester 1");
        VideoCallStreamer v2 = new VideoCallStreamer("Tester 2");
        try {
            assertTrue(v1.getLocalPort() > 0);
            assertTrue(v2.getLocalPort() > 0);
            assertNotEquals(v1.getLocalPort(), v2.getLocalPort());

            v1.setVideoEnabled(false);
            assertFalse(v1.isVideoEnabled());
            v1.setVideoEnabled(true);
            assertTrue(v1.isVideoEnabled());

            assertEquals(VideoCallStreamer.SourceMode.CAMERA, v1.getSourceMode());
            v1.setSourceMode(VideoCallStreamer.SourceMode.SCREEN_SHARE);
            assertEquals(VideoCallStreamer.SourceMode.SCREEN_SHARE, v1.getSourceMode());
        } finally {
            v1.stop();
            v2.stop();
        }
    }
}
