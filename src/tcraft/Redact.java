package tcraft;

import java.util.regex.Pattern;

/** 日志、错误报告里的令牌与密码统一脱敏。 */
final class Redact {

    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)(access[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(refresh[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(client[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]{4,}"),
            Pattern.compile("(?i)(SystemProxyPass\\s*=\\s*)[^\\s&]+"),
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}")
    };

    private Redact() {
    }

    static String apply(String text) {
        if (text == null || text.isEmpty()) return "";
        // 普通日志占绝大多数，先做一次低成本关键词判断，避免跑全部正则
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("token") && !lower.contains("password")
                && !lower.contains("bearer") && !lower.contains("proxypass")) {
            return text;
        }
        String out = text;
        for (Pattern p : SECRETS) out = p.matcher(out).replaceAll("$1<TOKEN>");
        return out;
    }
}
