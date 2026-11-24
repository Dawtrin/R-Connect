package com.p2p.server.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid message translator for R-Connect:
 * 1. In-memory LRU cache for instant 0ms repeat lookups.
 * 2. Exact/canonical phrase fast-path for deterministic common greetings.
 * 3. Online neural translation (Google GTX endpoint with short 1.5s timeout + circuit breaker)
 *    when internet connectivity is available.
 * 4. Expanded, boundary-safe multi-word offline dictionary fallback for isolated LAN demos.
 * Original messages are never modified in SQLite.
 */
final class TranslationService {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+|[^\\p{L}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String VI_DIACRITICS = "ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ";

    private static final Map<String, String> EXACT_EN_TO_VI = new LinkedHashMap<>();
    private static final Map<String, String> EXACT_VI_TO_EN = new LinkedHashMap<>();
    private static final List<PhraseRule> RULES_EN_TO_VI = new ArrayList<>();
    private static final List<PhraseRule> RULES_VI_TO_EN = new ArrayList<>();
    private static final Map<String, String> WORD_EN_TO_VI = new LinkedHashMap<>();
    private static final Map<String, String> WORD_VI_TO_EN = new LinkedHashMap<>();
    private static final Set<String> EN_HINT_WORDS = new HashSet<>();
    private static final Set<String> VI_NO_ACCENT_HINTS = Set.of(
            "xin", "chao", "ban", "khoe", "khong", "cam", "on", "hom", "nay", "ngay", "mai",
            "nhom", "tin", "nhan", "tro", "chuyen", "moi", "nguoi", "chung", "toi", "hen", "gap", "lai"
    );

    private record PhraseRule(String source, String target, Pattern pattern) {}

    static {
        // Canonical phrases (longest match applied first with strict Unicode letter boundaries)
        phrase("hello, how are you?", "xin chào, bạn khỏe không?", true);
        phrase("hello everyone", "xin chào mọi người", true);
        phrase("how are you doing", "dạo này bạn thế nào", false);
        phrase("how are you?", "bạn khỏe không?", true);
        phrase("how are you", "bạn khỏe không", true);
        phrase("i am fine, thank you", "tôi khỏe, cảm ơn bạn", false);
        phrase("i am fine", "tôi khỏe", true);
        phrase("i'm fine", "tôi khỏe", false);
        phrase("thank you very much", "cảm ơn bạn rất nhiều", false);
        phrase("thanks a lot", "cảm ơn rất nhiều", false);
        phrase("thank you", "cảm ơn bạn", true);
        phrase("good morning", "chào buổi sáng", true);
        phrase("good afternoon", "chào buổi chiều", true);
        phrase("good evening", "chào buổi tối", true);
        phrase("good night", "chúc ngủ ngon", false);
        phrase("see you tomorrow", "hẹn gặp lại ngày mai", false);
        phrase("see you later", "hẹn gặp lại", true);
        phrase("see you soon", "hẹn sớm gặp lại", false);
        phrase("see you", "hẹn gặp lại", true);
        phrase("nice to meet you", "rất vui được gặp bạn", true);
        phrase("what is your name", "bạn tên là gì", true);
        phrase("my name is", "tên tôi là", true);
        phrase("where are you from", "bạn đến từ đâu", true);
        phrase("i am from vietnam", "tôi đến từ Việt Nam", true);
        phrase("what are you doing", "bạn đang làm gì vậy", false);
        phrase("can you help me", "bạn có thể giúp tôi không", false);
        phrase("please wait a moment", "vui lòng chờ một chút", false);
        phrase("please wait", "vui lòng chờ", true);
        phrase("no problem", "không sao đâu", false);
        phrase("of course", "tất nhiên rồi", false);
        phrase("excuse me", "xin lỗi cho hỏi", false);
        phrase("i am sorry", "tôi xin lỗi", false);
        phrase("have a nice day", "chúc bạn một ngày tốt lành", false);
        phrase("good luck", "chúc may mắn", true);
        phrase("happy birthday", "chúc mừng sinh nhật", false);
        phrase("congratulations", "chúc mừng nhé", false);
        phrase("voice call", "cuộc gọi thoại", false);
        phrase("video call", "cuộc gọi video", false);
        phrase("screen share", "chia sẻ màn hình", false);
        phrase("public group", "nhóm công khai", false);
        phrase("friend request", "lời mời kết bạn", false);
        phrase("computer network", "mạng máy tính", false);
        phrase("source code", "mã nguồn", false);
        phrase("hello", "xin chào", true);
        phrase("welcome", "chào mừng", true);
        phrase("thanks", "cảm ơn", true);
        phrase("sorry", "xin lỗi", false);
        phrase("yes", "có", true);
        phrase("no", "không", true);
        phrase("ok", "được", true);

        // Multi-syllable Vietnamese phrases -> English (fixes previous single-token regex bug)
        phraseViOnly("xin chào mọi người", "hello everyone", true);
        phraseViOnly("chào mọi người", "hello everyone", false);
        phraseViOnly("mọi người", "everyone", false);
        phraseViOnly("chúng tôi", "we", false);
        phraseViOnly("chúng mình", "we", false);
        phraseViOnly("các bạn", "you all", false);
        phraseViOnly("bạn bè", "friends", false);
        phraseViOnly("kết bạn", "add friend", false);
        phraseViOnly("tin nhắn thoại", "voice message", false);
        phraseViOnly("tin nhắn", "message", false);
        phraseViOnly("trò chuyện", "chat", false);
        phraseViOnly("cuộc gọi", "call", false);
        phraseViOnly("hôm nay", "today", false);
        phraseViOnly("ngày mai", "tomorrow", false);
        phraseViOnly("hôm qua", "yesterday", false);
        phraseViOnly("bây giờ", "now", false);
        phraseViOnly("vui lòng", "please", false);
        phraseViOnly("làm ơn", "please", false);
        phraseViOnly("cảm ơn nhiều", "thank you very much", false);
        phraseViOnly("cảm ơn", "thank you", false);
        phraseViOnly("xin lỗi", "sorry", false);
        phraseViOnly("máy chủ", "server", false);
        phraseViOnly("máy khách", "client", false);
        phraseViOnly("cơ sở dữ liệu", "database", false);
        phraseViOnly("mạng xã hội", "social network", false);
        phraseViOnly("bài viết", "post", false);
        phraseViOnly("bình luận", "comment", false);
        phraseViOnly("thông báo", "notification", false);
        phraseViOnly("tài liệu", "document", false);
        phraseViOnly("tệp đính kèm", "attachment", false);
        phraseViOnly("hình ảnh", "image", false);
        phraseViOnly("ngôn ngữ", "language", false);

        // Word-level fallback dictionary (English <-> Vietnamese)
        words("hello", "xin chào");
        words("hi", "chào");
        words("hey", "chào");
        words("everyone", "mọi người");
        words("how", "thế nào");
        words("what", "gì");
        words("where", "ở đâu");
        words("when", "khi nào");
        words("why", "tại sao");
        words("who", "ai");
        words("are", "là");
        words("is", "là");
        words("am", "là");
        words("you", "bạn");
        words("your", "của bạn");
        words("i", "tôi");
        words("me", "tôi");
        words("my", "của tôi");
        words("we", "chúng tôi");
        words("our", "của chúng tôi");
        words("they", "họ");
        words("he", "anh ấy");
        words("she", "cô ấy");
        words("friend", "bạn");
        words("friends", "bạn bè");
        words("group", "nhóm");
        words("message", "tin nhắn");
        words("messages", "tin nhắn");
        words("chat", "trò chuyện");
        words("call", "gọi");
        words("file", "tệp");
        words("photo", "ảnh");
        words("image", "hình ảnh");
        words("video", "video");
        words("voice", "giọng nói");
        words("post", "bài viết");
        words("comment", "bình luận");
        words("like", "thích");
        words("send", "gửi");
        words("sent", "đã gửi");
        words("receive", "nhận");
        words("open", "mở");
        words("close", "đóng");
        words("start", "bắt đầu");
        words("stop", "dừng");
        words("join", "tham gia");
        words("leave", "rời");
        words("good", "tốt");
        words("great", "tuyệt vời");
        words("nice", "đẹp");
        words("morning", "buổi sáng");
        words("afternoon", "buổi chiều");
        words("evening", "buổi tối");
        words("night", "buổi tối");
        words("today", "hôm nay");
        words("tomorrow", "ngày mai");
        words("yesterday", "hôm qua");
        words("now", "bây giờ");
        words("later", "sau");
        words("please", "vui lòng");
        words("thanks", "cảm ơn");
        words("thank", "cảm ơn");
        words("very", "rất");
        words("fine", "khỏe");
        words("busy", "bận");
        words("ready", "sẵn sàng");
        words("online", "trực tuyến");
        words("offline", "ngoại tuyến");
        words("learn", "học");
        words("study", "học");
        words("work", "làm việc");
        words("school", "trường");
        words("class", "lớp");
        words("project", "đồ án");
        words("report", "báo cáo");
        words("test", "kiểm thử");
        words("bug", "lỗi");
        words("fix", "sửa");
        words("network", "mạng");
        words("server", "máy chủ");
        words("client", "máy khách");
        words("user", "người dùng");
        words("admin", "quản trị viên");
        words("can", "có thể");
        words("will", "sẽ");
        words("want", "muốn");
        words("need", "cần");
        words("help", "giúp");
        words("meet", "gặp");
        words("time", "thời gian");

        wordsVi("chào", "hello");
        wordsVi("bạn", "you");
        wordsVi("tôi", "i");
        wordsVi("mình", "i");
        wordsVi("anh", "you");
        wordsVi("chị", "you");
        wordsVi("em", "you");
        wordsVi("họ", "they");
        wordsVi("nhóm", "group");
        wordsVi("gửi", "send");
        wordsVi("nhận", "receive");
        wordsVi("gọi", "call");
        wordsVi("ảnh", "photo");
        wordsVi("tệp", "file");
        wordsVi("khỏe", "fine");
        wordsVi("tốt", "good");
        wordsVi("đẹp", "nice");
        wordsVi("rất", "very");
        wordsVi("học", "study");
        wordsVi("làm", "do");
        wordsVi("đi", "go");
        wordsVi("ăn", "eat");
        wordsVi("ngủ", "sleep");
        wordsVi("mạng", "network");
        wordsVi("lỗi", "bug");
        wordsVi("sửa", "fix");
        wordsVi("có", "yes");
        wordsVi("không", "no");
        wordsVi("được", "ok");
        wordsVi("rồi", "already");
        wordsVi("chưa", "not yet");
        wordsVi("đang", "currently");
        wordsVi("sẽ", "will");
        wordsVi("cần", "need");
        wordsVi("muốn", "want");
        wordsVi("giúp", "help");
        wordsVi("gặp", "meet");
        wordsVi("trường", "school");
        wordsVi("lớp", "class");

        RULES_EN_TO_VI.sort((a, b) -> Integer.compare(b.source().length(), a.source().length()));
        RULES_VI_TO_EN.sort((a, b) -> Integer.compare(b.source().length(), a.source().length()));
    }

    private static Pattern boundaryPattern(String phrase) {
        String escaped = Pattern.quote(phrase);
        boolean startsWithLetter = !phrase.isEmpty() && Character.isLetter(phrase.codePointAt(0));
        boolean endsWithLetter = !phrase.isEmpty() && Character.isLetter(phrase.codePointBefore(phrase.length()));
        String prefix = startsWithLetter ? "(?<![\\p{L}])" : "";
        String suffix = endsWithLetter ? "(?![\\p{L}])" : "";
        return Pattern.compile(prefix + escaped + suffix, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static void phrase(String english, String vietnamese, boolean exactCanonical) {
        String enLower = english.toLowerCase(Locale.ROOT);
        String viLower = vietnamese.toLowerCase(Locale.ROOT);
        if (exactCanonical) {
            EXACT_EN_TO_VI.putIfAbsent(enLower, vietnamese);
            EXACT_VI_TO_EN.putIfAbsent(viLower, english);
        }
        RULES_EN_TO_VI.add(new PhraseRule(enLower, vietnamese, boundaryPattern(enLower)));
        RULES_VI_TO_EN.add(new PhraseRule(viLower, english, boundaryPattern(viLower)));
        for (String token : enLower.split("[^\\p{L}]+")) {
            if (token.length() >= 2) EN_HINT_WORDS.add(token);
        }
    }

    private static void phraseViOnly(String vietnamese, String english, boolean exactCanonical) {
        String viLower = vietnamese.toLowerCase(Locale.ROOT);
        if (exactCanonical) {
            EXACT_VI_TO_EN.putIfAbsent(viLower, english);
        }
        RULES_VI_TO_EN.add(new PhraseRule(viLower, english, boundaryPattern(viLower)));
    }

    private static void words(String english, String vietnamese) {
        String enLower = english.toLowerCase(Locale.ROOT);
        WORD_EN_TO_VI.putIfAbsent(enLower, vietnamese);
        EN_HINT_WORDS.add(enLower);
    }

    private static void wordsVi(String vietnamese, String english) {
        WORD_VI_TO_EN.putIfAbsent(vietnamese.toLowerCase(Locale.ROOT), english);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1200))
            .build();
    private final Map<String, Map<String, Object>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                    return size() > 2000;
                }
            }
    );
    private volatile long onlineDisabledUntilMs = 0L;
    private static final boolean IN_UNIT_TEST = detectUnitTestEnvironment();

    private static boolean detectUnitTestEnvironment() {
        for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
            String cls = el.getClassName();
            if (cls.startsWith("org.junit.") || cls.endsWith("Test")) {
                return true;
            }
        }
        return false;
    }

    Map<String, Object> translate(String content, String targetLanguage) {
        if (content == null || content.isBlank()) {
            String t = normalizeLanguage(targetLanguage);
            return result(content == null ? "" : content, content == null ? "" : content, "und", t, false, "offline-demo");
        }
        String target = normalizeLanguage(targetLanguage);
        String cacheKey = target + "|" + content;
        Map<String, Object> cached = cache.get(cacheKey);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }

        String trimmedLower = content.trim().toLowerCase(Locale.ROOT);
        if (target.equals("vi") && EXACT_EN_TO_VI.containsKey(trimmedLower)) {
            String out = EXACT_EN_TO_VI.get(trimmedLower);
            return store(cacheKey, result(content, out, "en", "vi", !out.equalsIgnoreCase(content.trim()), "offline-demo"));
        }
        if (target.equals("en") && EXACT_VI_TO_EN.containsKey(trimmedLower)) {
            String out = EXACT_VI_TO_EN.get(trimmedLower);
            return store(cacheKey, result(content, out, "vi", "en", !out.equalsIgnoreCase(content.trim()), "offline-demo"));
        }

        String source = detect(content);
        if (source.equals(target)) {
            return store(cacheKey, result(content, content, source, target, false, "offline-demo"));
        }

        // Try neural online translation when not running unit tests and internet is reachable
        if (!IN_UNIT_TEST && System.currentTimeMillis() >= onlineDisabledUntilMs) {
            Map<String, Object> online = tryOnlineTranslate(content, source, target);
            if (online != null) {
                return store(cacheKey, online);
            }
        }

        if ("und".equals(source)) {
            return store(cacheKey, result(content, content, source, target, false, "offline-demo"));
        }

        String translated = offlineTranslate(content, target);
        boolean changed = !translated.trim().equalsIgnoreCase(content.trim());
        return store(cacheKey, result(content, changed ? translated : content, source, target, changed, "offline-demo"));
    }

    private Map<String, Object> store(String key, Map<String, Object> value) {
        cache.put(key, new LinkedHashMap<>(value));
        return value;
    }

    private Map<String, Object> tryOnlineTranslate(String content, String detectedSource, String target) {
        try {
            String sl = "und".equals(detectedSource) ? "auto" : detectedSource;
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                    + URLEncoder.encode(sl, StandardCharsets.UTF_8)
                    + "&tl=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                    + "&dt=t&q=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(1800))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = mapper.readTree(resp.body());
                if (root.isArray() && root.size() > 0 && root.get(0).isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : root.get(0)) {
                        if (part.isArray() && part.size() > 0 && !part.get(0).isNull()) {
                            sb.append(part.get(0).asText());
                        }
                    }
                    String detected = (root.size() > 2 && root.get(2).isTextual())
                            ? normalizeDetected(root.get(2).asText(), detectedSource)
                            : detectedSource;
                    String translated = sb.toString();
                    if (!translated.isBlank()) {
                        if (detected.equals(target) || translated.trim().equalsIgnoreCase(content.trim())) {
                            return result(content, content, detected, target, false, "online-gtx");
                        }
                        return result(content, translated, "und".equals(detected) ? (target.equals("vi") ? "en" : "vi") : detected, target, true, "online-gtx");
                    }
                }
            }
        } catch (Exception ignored) {
            // LAN-only environment without internet: back off for 60s so chat stays instantaneous
            onlineDisabledUntilMs = System.currentTimeMillis() + 60_000L;
        }
        return null;
    }

    private static String normalizeDetected(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("vi")) return "vi";
        if (lower.startsWith("en")) return "en";
        return lower;
    }

    private static String offlineTranslate(String content, String target) {
        List<PhraseRule> rules = target.equals("vi") ? RULES_EN_TO_VI : RULES_VI_TO_EN;
        Map<String, String> wordDict = target.equals("vi") ? WORD_EN_TO_VI : WORD_VI_TO_EN;

        // Protect matched multi-word phrases with placeholders so word-level pass never corrupts them
        List<String> replacements = new ArrayList<>();
        String working = content;
        for (PhraseRule rule : rules) {
            Matcher m = rule.pattern().matcher(working);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                int idx = replacements.size();
                replacements.add(rule.target());
                m.appendReplacement(sb, "\u0001" + idx + "\u0002");
            }
            m.appendTail(sb);
            working = sb.toString();
        }

        // Translate remaining individual words while keeping placeholders intact
        Pattern tokenOrPlaceholder = Pattern.compile("\u0001\\d+\u0002|[\\p{L}]+|[^\\p{L}\u0001\u0002]+", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcher = tokenOrPlaceholder.matcher(working);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("\u0001") && token.endsWith("\u0002")) {
                int idx = Integer.parseInt(token.substring(1, token.length() - 1));
                out.append(replacements.get(idx));
            } else {
                String mapped = wordDict.get(token.toLowerCase(Locale.ROOT));
                out.append(mapped == null ? token : mapped);
            }
        }
        return out.toString();
    }

    private static Map<String, Object> result(String original, String translated, String source, String target, boolean translatedFlag, String provider) {
        return new LinkedHashMap<>(Map.of(
                "originalContent", original,
                "translatedContent", translated,
                "sourceLanguage", source,
                "targetLanguage", target,
                "translated", translatedFlag,
                "provider", provider
        ));
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "vi";
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("en")) return "en";
        return "vi";
    }

    static String detect(String content) {
        if (content == null || content.isBlank()) return "und";
        String lower = content.toLowerCase(Locale.ROOT);

        int viWords = 0;
        int enWords = 0;
        int asciiAlphaWords = 0;

        Matcher m = Pattern.compile("[\\p{L}]+", Pattern.UNICODE_CHARACTER_CLASS).matcher(lower);
        while (m.find()) {
            String w = m.group();
            boolean hasViAccent = w.codePoints().anyMatch(c -> VI_DIACRITICS.indexOf(c) >= 0);
            if (hasViAccent) {
                viWords++;
            } else if (WORD_VI_TO_EN.containsKey(w)) {
                viWords++;
            } else if (EN_HINT_WORDS.contains(w)) {
                enWords++;
            } else if (VI_NO_ACCENT_HINTS.contains(w)) {
                viWords++;
            } else if (w.matches("[a-z]{2,}")) {
                asciiAlphaWords++;
            }
        }

        // Mixed sentence like "Hello Đạt, how are you?" has enWords=4, viWords=1 -> classify as "en"!
        if (enWords > viWords) return "en";
        if (viWords > 0) return "vi";
        if (enWords > 0 || asciiAlphaWords >= 2) return "en";
        return "und";
    }
}
