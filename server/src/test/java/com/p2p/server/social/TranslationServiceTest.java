package com.p2p.server.social;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationServiceTest {
    private final TranslationService service = new TranslationService();

    @Test
    void translatesEnglishToVietnameseAndKeepsOriginal() {
        Map<String, Object> result = service.translate("Hello, how are you?", "vi");
        assertEquals("Hello, how are you?", result.get("originalContent"));
        assertEquals("xin chào, bạn khỏe không?", result.get("translatedContent"));
        assertEquals("en", result.get("sourceLanguage"));
        assertEquals("vi", result.get("targetLanguage"));
        assertEquals(true, result.get("translated"));
    }

    @Test
    void translatesVietnameseToEnglishAndLeavesSameLanguageUntouched() {
        Map<String, Object> result = service.translate("Xin chào mọi người", "en");
        assertEquals("hello everyone", result.get("translatedContent"));
        assertEquals("vi", result.get("sourceLanguage"));

        Map<String, Object> same = service.translate("Xin chào", "vi");
        assertEquals("Xin chào", same.get("translatedContent"));
        assertEquals(false, same.get("translated"));
    }

    @Test
    void doesNotCorruptSubstringsInsideWordsAndHandlesMultiSyllableVietnamese() {
        // Regression: "know", "now", "book" must never be corrupted by "no" -> "không" or "ok" -> "được"
        Map<String, Object> boundary = service.translate("Hello, send photo now!", "vi");
        String viText = String.valueOf(boundary.get("translatedContent")).toLowerCase();
        assertFalse(viText.contains("khôngw"), "Word boundary violated on 'now': " + viText);
        assertTrue(viText.contains("bây giờ"), "Expected 'now' -> 'bây giờ': " + viText);

        // Regression: Multi-syllable Vietnamese phrases like "hôm nay", "tin nhắn", "bạn bè"
        Map<String, Object> multi = service.translate("Hôm nay gửi tin nhắn cho bạn bè", "en");
        String enText = String.valueOf(multi.get("translatedContent")).toLowerCase();
        assertTrue(enText.contains("today"), "Expected 'hôm nay' -> 'today': " + enText);
        assertTrue(enText.contains("message"), "Expected 'tin nhắn' -> 'message': " + enText);
        assertTrue(enText.contains("friends"), "Expected 'bạn bè' -> 'friends': " + enText);
        assertFalse(enText.contains("bè"), "Multi-syllable word 'bạn bè' was split: " + enText);

        // Regression: Mixed English sentence with a Vietnamese name should still detect as English
        Map<String, Object> mixed = service.translate("Hello Đạt, how are you today?", "vi");
        assertEquals("en", mixed.get("sourceLanguage"));
        assertEquals(true, mixed.get("translated"));
    }
}
