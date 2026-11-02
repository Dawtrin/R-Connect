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
}
