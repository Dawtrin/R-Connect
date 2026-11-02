package com.p2p.client.network.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VoiceCallStreamerTest {
    @Test
    void testStreamerAllocationAndMute() throws Exception {
        VoiceCallStreamer s1 = new VoiceCallStreamer();
        VoiceCallStreamer s2 = new VoiceCallStreamer();
        try {
            assertTrue(s1.getLocalPort() > 0);
            assertTrue(s2.getLocalPort() > 0);
            assertNotEquals(s1.getLocalPort(), s2.getLocalPort());

            s1.setMuted(true);
            assertTrue(s1.isMuted());
            s1.setMuted(false);
            assertFalse(s1.isMuted());
        } finally {
            s1.stop();
            s2.stop();
        }
    }
}
