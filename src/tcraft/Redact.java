package tcraft;

import java.util.regex.Pattern;

final class Redact {

    // 先匹配键值对 再兜底裸 token
    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)(access[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(refresh[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(client[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]{4,}"),
            Pattern.compile("(?i)(SystemProxyPass\\s*=\\s*)[^\\s&]+"),
            Pattern.compile("(?i)(systemcursekey\\s*=\\s*)\\S+"),
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}"),
            // 旧参数 --session token 两段 UUID
            Pattern.compile("(?i)(--session\\s+)[^\\s\"']+"),
            Pattern.compile("(?i)(auth[_-]?session[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+"),
            Pattern.compile("(?i)(--accessToken\\s+)[^\\s\"']+"),
            // 兜底裸 token 两段 UUID
            Pattern.compile("(?i)(\\btoken:)[^\\s\"',}]+"),
            Pattern.compile("(?i)((?:secret|api)[_-]?key[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}")
    };

    private Redact() {
    }

    static String apply(String text) {
        if (text == null || text.isEmpty()) return "";
        // 关键词预过滤
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("token") && !lower.contains("password")
                && !lower.contains("bearer") && !lower.contains("proxypass")
                && !lower.contains("session") && !lower.contains("secret")
                // 三种分隔形态 预过滤须一致
                && !lower.contains("api_key") && !lower.contains("apikey")
                && !lower.contains("api-key") && !lower.contains("cursekey")) {
            return text;
        }
        String out = text;
        for (Pattern p : SECRETS) out = p.matcher(out).replaceAll("$1<TOKEN>");
        return out;
    }
}
