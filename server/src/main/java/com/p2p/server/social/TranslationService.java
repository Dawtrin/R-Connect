package com.p2p.server.social;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small offline translator used by the desktop demo. The original message is
 * always kept in the messages table; this service only creates a display copy.
 * The implementation is deliberately dependency-free so the LAN demo still
 * works without an internet translation provider.
 */
final class TranslationService {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+|[^\\p{L}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String, String> PHRASE_EN_TO_VI = new LinkedHashMap<>();
    private static final Map<String, String> PHRASE_VI_TO_EN = new LinkedHashMap<>();
    private static final Map<String, String> WORD_EN_TO_VI = new LinkedHashMap<>();
    private static final Map<String, String> WORD_VI_TO_EN = new LinkedHashMap<>();
    private static final Map<String, String> EN_TO_VI = new LinkedHashMap<>();
    private static final Map<String, String> VI_TO_EN = new LinkedHashMap<>();

    static {
        phrase("hello", "xin chào");
        phrase("hello everyone", "xin chào mọi người");
        phrase("how are you", "bạn khỏe không");
        phrase("how are you?", "bạn khỏe không?");
        phrase("i am fine", "tôi khỏe");
        phrase("thank you", "cảm ơn bạn");
        phrase("good morning", "chào buổi sáng");
        phrase("good afternoon", "chào buổi chiều");
        phrase("good evening", "chào buổi tối");
        phrase("see you later", "hẹn gặp lại");
        phrase("nice to meet you", "rất vui được gặp bạn");
        phrase("what is your name", "bạn tên là gì");
        phrase("my name is", "tên tôi là");
        phrase("where are you from", "bạn đến từ đâu");
        phrase("i am from vietnam", "tôi đến từ Việt Nam");
        phrase("please wait", "vui lòng chờ");
        phrase("see you", "hẹn gặp lại");
        phrase("yes", "có");
        phrase("no", "không");
        phrase("ok", "được");
        phrase("thanks", "cảm ơn");
        phrase("welcome", "chào mừng");
        phrase("good luck", "chúc may mắn");

        words("hello", "xin chào");
        words("hi", "chào");
        words("everyone", "mọi người");
        words("how", "thế nào");
        words("are", "là");
        words("you", "bạn");
        words("i", "tôi");
        words("me", "tôi");
        words("my", "của tôi");
        words("we", "chúng tôi");
        words("friend", "bạn");
        words("friends", "bạn bè");
        words("group", "nhóm");
        words("message", "tin nhắn");
        words("chat", "trò chuyện");
        words("good", "tốt");
        words("morning", "buổi sáng");
        words("afternoon", "buổi chiều");
        words("evening", "buổi tối");
        words("today", "hôm nay");
        words("tomorrow", "ngày mai");
        words("please", "vui lòng");
        words("thanks", "cảm ơn");
        words("thank", "cảm ơn");
        words("very", "rất");
        words("good", "tốt");
        words("fine", "khỏe");
        words("learn", "học");
        words("network", "mạng");
        words("server", "máy chủ");
        words("client", "máy khách");

        wordsVi("xin chào", "hello");
        wordsVi("chào", "hello");
        wordsVi("mọi người", "everyone");
        wordsVi("bạn", "you");
        wordsVi("tôi", "i");
        wordsVi("chúng tôi", "we");
        wordsVi("bạn bè", "friends");
        wordsVi("nhóm", "group");
        wordsVi("tin nhắn", "message");
        wordsVi("trò chuyện", "chat");
        wordsVi("hôm nay", "today");
        wordsVi("ngày mai", "tomorrow");
        wordsVi("vui lòng", "please");
        wordsVi("cảm ơn", "thank you");
        wordsVi("khỏe", "fine");
        wordsVi("tốt", "good");
        wordsVi("rất", "very");
        wordsVi("học", "learn");
        wordsVi("mạng", "network");
        wordsVi("máy chủ", "server");
        wordsVi("máy khách", "client");
        wordsVi("có", "yes");
        wordsVi("không", "no");
        wordsVi("được", "ok");
    }

    private static void phrase(String english, String vietnamese) {
        PHRASE_EN_TO_VI.put(english, vietnamese);
        PHRASE_VI_TO_EN.put(vietnamese, english);
        EN_TO_VI.put(english, vietnamese);
        VI_TO_EN.put(vietnamese, english);
    }

    private static void words(String english, String vietnamese) {
        WORD_EN_TO_VI.putIfAbsent(english, vietnamese);
        EN_TO_VI.putIfAbsent(english, vietnamese);
    }

    private static void wordsVi(String vietnamese, String english) {
        WORD_VI_TO_EN.putIfAbsent(vietnamese, english);
        VI_TO_EN.putIfAbsent(vietnamese, english);
    }

    Map<String, Object> translate(String content, String targetLanguage) {
        String target = normalizeLanguage(targetLanguage);
        String source = detect(content);
        if ("und".equals(source) || source.equals(target)) {
            return result(content, content, source, target, false);
        }
        String translated = target.equals("vi") ? replacePhrases(content, PHRASE_EN_TO_VI) : replacePhrases(content, PHRASE_VI_TO_EN);
        translated = translateWords(translated, target.equals("vi") ? WORD_EN_TO_VI : WORD_VI_TO_EN);
        return result(content, translated, source, target, !translated.equals(content));
    }

    private static Map<String, Object> result(String original, String translated, String source, String target, boolean translatedFlag) {
        return new LinkedHashMap<>(Map.of(
                "originalContent", original,
                "translatedContent", translated,
                "sourceLanguage", source,
                "targetLanguage", target,
                "translated", translatedFlag,
                "provider", "offline-demo"
        ));
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "vi";
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("en")) return "en";
        return "vi";
    }

    private static String detect(String content) {
        String value = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (value.codePoints().anyMatch(c -> "ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ".indexOf(c) >= 0)) return "vi";
        for (String word : EN_TO_VI.keySet()) if (value.matches(".*\\b" + Pattern.quote(word) + "\\b.*")) return "en";
        return "und";
    }

    private static String replacePhrases(String content, Map<String, String> dictionary) {
        String result = content;
        String lower = result.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : dictionary.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length())).toList()) {
            String source = entry.getKey();
            int from = lower.indexOf(source);
            if (from >= 0) {
                result = result.substring(0, from) + entry.getValue() + result.substring(from + source.length());
                lower = result.toLowerCase(Locale.ROOT);
            }
        }
        return result;
    }

    private static String translateWords(String content, Map<String, String> dictionary) {
        Matcher matcher = WORD.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = dictionary.get(token.toLowerCase(Locale.ROOT));
            out.append(replacement == null ? token : replacement);
        }
        return out.toString();
    }
}
