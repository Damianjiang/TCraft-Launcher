package tcraft;

import java.util.regex.Pattern;

final class Redact {

    // 顺序敏感：先匹配完整键值对再兜底 token:，不然 key 换掉了、值还留半截
    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)(access[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(refresh[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(client[_-]?token[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("(?i)(password[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]{4,}"),
            Pattern.compile("(?i)(SystemProxyPass\\s*=\\s*)[^\\s&]+"),
            Pattern.compile("(?i)(systemcursekey\\s*=\\s*)\\S+"),
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}"),
            // 旧版启动参数 --session token:<accessToken>:<uuid>
            Pattern.compile("(?i)(--session\\s+)[^\\s\"']+"),
            Pattern.compile("(?i)(auth[_-]?session[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+"),
            Pattern.compile("(?i)(--accessToken\\s+)[^\\s\"']+"),
            // 兜底：日志里任何残留的 token:xxxx:yyyy 形式
            Pattern.compile("(?i)(\\btoken:)[^\\s\"',}]+"),
            Pattern.compile("(?i)((?:secret|api)[_-]?key[\"'\\s:=]{0,5})[A-Za-z0-9._~+/=-]{8,}")
    };

    private Redact() {
    }

    static String apply(String text) {
        if (text == null || text.isEmpty()) return "";
        // 绝大多数日志不带敏感词，先过一遍关键词再跑正则
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("token") && !lower.contains("password")
                && !lower.contains("bearer") && !lower.contains("proxypass")
                && !lower.contains("session") && !lower.contains("secret")
                && !lower.contains("api_key") && !lower.contains("apikey")
                && !lower.contains("cursekey")) {
            return text;
        }
        String out = text;
        for (Pattern p : SECRETS) out = p.matcher(out).replaceAll("$1<TOKEN>");
        return out;
    }
}
