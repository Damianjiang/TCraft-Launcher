package demo.aidesktop;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import tcraft.api.PluginContext;
import tcraft.api.TcraftPlugin;
import tcraft.api.Widgets;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A complete example that sends a request to an OpenAI-compatible endpoint,
/// parses a JSON action plan, and executes it after explicit user confirmation.
public class AiDesktopPlugin implements TcraftPlugin {

    private PluginContext ctx;
    private TextField endpoint;
    private TextField model;
    private PasswordField apiKey;
    private TextArea prompt;
    private TextArea output;
    private String lastReply = "";

    @Override
    public void onLoad(PluginContext ctx) {
        this.ctx = ctx;
        ctx.ui().page("main", "AI 桌面助手", "bot", view -> {
            view.header("AI 桌面助手", "把需求发给模型，确认后执行它返回的动作。");
            endpoint = Widgets.field("https://api.openai.com/v1/chat/completions");
            endpoint.setText(ctx.storage().get("endpoint", "https://api.openai.com/v1/chat/completions"));
            model = Widgets.field("gpt-4o-mini");
            model.setText(ctx.storage().get("model", "gpt-4o-mini"));
            apiKey = new PasswordField();
            apiKey.setPromptText("API Key，仅保存在当前页面");
            prompt = new TextArea();
            prompt.setPromptText("例如：打开记事本，输入今天的待办，然后截图保存");
            prompt.setPrefRowCount(4);
            output = new TextArea();
            output.setEditable(false);
            output.setPrefRowCount(12);

            var request = Widgets.card("模型");
            request.add(endpoint, model, apiKey, prompt, Widgets.row(8,
                    Widgets.primaryButton("发送", () -> send(prompt.getText())),
                    Widgets.button("执行上一条动作", this::executeLast),
                    Widgets.button("清空", () -> output.clear())));
            view.add(request);

            var result = Widgets.card("回复");
            result.add(output);
            view.add(result);
            return null;
        });

        ctx.commands().register("aidesk.open", "打开 AI 桌面助手",
                c -> ctx.ui().openPage("plugin:" + ctx.id() + ":main"));
        ctx.commands().register("aidesk.ask", "向模型发送一条消息", c -> ask(c.args));
    }

    private void ask(String text) {
        if (prompt == null) {
            ctx.ui().openPage("plugin:" + ctx.id() + ":main");
            return;
        }
        prompt.setText(text == null ? "" : text);
        send(prompt.getText());
    }

    private void send(String userText) {
        String text = userText == null ? "" : userText.trim();
        if (text.isEmpty()) {
            ctx.dialogs().error("缺少提示词", "请先输入要让电脑完成的事情");
            return;
        }
        String url = endpoint.getText();
        if (url == null || url.isBlank()) {
            ctx.dialogs().error("缺少接口", "请填写 OpenAI 兼容接口地址");
            return;
        }
        ctx.storage().set("endpoint", url);
        ctx.storage().set("model", model.getText());
        ctx.storage().save();
        if (output != null) output.setText("请求中…");

        String system = "You are a desktop automation planner. Return JSON only, no markdown. "
                + "Schema: {\"reply\":\"short text\",\"actions\":[{\"type\":\"run\",\"command\":\"...\"}]}. "
                + "Allowed types: run, type, key, click, screenshot, open, clipboard. "
                + "Use at most 5 actions. Use Windows commands only when the user is on Windows. "
                + "Do not use destructive commands unless the user explicitly asked for them.";
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", text));
        String body = ctx.json().stringify(Map.of(
                "model", model.getText(),
                "temperature", 0.2,
                "messages", messages));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (apiKey.getText() != null && !apiKey.getText().isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey.getText());
        }

        ctx.scheduler().async(() -> {
            try {
                String response = ctx.network().post(url, body, headers);
                Object json = ctx.json().parse(response);
                List<Object> choices = ctx.json().array(json, "choices");
                if (choices.isEmpty()) throw new IOException("响应没有 choices");
                Map<String, Object> message = ctx.json().object(choices.get(0), "message");
                String content = ctx.json().string(message, "content", "");
                if (content.isBlank()) throw new IOException("响应内容为空");
                lastReply = content;
                ctx.scheduler().ui(() -> {
                    if (output != null) output.setText(content);
                });
            } catch (Exception e) {
                ctx.log("AI 请求失败：" + e);
                ctx.scheduler().ui(() -> {
                    if (output != null) output.setText("请求失败：" + e.getMessage());
                    ctx.dialogs().error("AI 请求失败", String.valueOf(e.getMessage()));
                });
            }
        });
    }

    private void executeLast() {
        if (lastReply == null || lastReply.isBlank()) {
            ctx.dialogs().error("没有动作", "先发送一条请求，让模型返回动作 JSON");
            return;
        }
        List<Object> actions;
        try {
            actions = ctx.json().array(ctx.json().parse(lastReply), "actions");
        } catch (Exception e) {
            ctx.dialogs().error("动作格式错误", "模型回复不是约定的 JSON：" + e.getMessage());
            return;
        }
        if (actions.isEmpty()) {
            ctx.dialogs().message("没有动作", "模型只返回了文本，没有可执行动作");
            return;
        }
        String summary = describe(actions);
        ctx.dialogs().confirmDanger("执行桌面动作", "即将执行以下操作：\n\n" + summary,
                () -> ctx.scheduler().async(() -> runActions(actions)));
    }

    private String describe(List<Object> actions) {
        StringBuilder text = new StringBuilder();
        int limit = Math.min(actions.size(), 10);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> action = ctx.json().object(actions.get(i));
            String type = ctx.json().string(action, "type", "?");
            text.append(i + 1).append(". ").append(type);
            switch (type) {
                case "run" -> text.append("：").append(ctx.json().string(action, "command", ""));
                case "type" -> text.append("：输入 ").append(ctx.json().string(action, "text", ""));
                case "key" -> text.append("：按键 ").append(ctx.json().string(action, "key", ""));
                case "click" -> text.append("：点击 (").append(ctx.json().integer(action, "x", 0))
                        .append(", ").append(ctx.json().integer(action, "y", 0)).append(')');
                case "screenshot" -> text.append("：截取屏幕");
                case "open" -> text.append("：打开 ").append(ctx.json().string(action, "url", ""));
                case "clipboard" -> text.append("：写入剪贴板");
                default -> text.append("：未知动作");
            }
            text.append('\n');
        }
        if (actions.size() > limit) text.append("… 共 ").append(actions.size()).append(" 个动作");
        return text.toString();
    }

    private void runActions(List<Object> actions) {
        int count = 0;
        for (Object raw : actions) {
            if (++count > 10) break;
            Map<String, Object> action = ctx.json().object(raw);
            String type = ctx.json().string(action, "type", "");
            try {
                switch (type) {
                    case "run" -> {
                        String command = ctx.json().string(action, "command", "");
                        if (command.isBlank()) continue;
                        var result = ctx.processes().run(command, Duration.ofSeconds(30));
                        append("run: " + command + "\n" + result.stdout + result.stderr);
                    }
                    case "type" -> {
                        String value = ctx.json().string(action, "text", "");
                        if (!value.isEmpty()) ctx.desktop().typeText(value);
                        append("type: " + value);
                    }
                    case "key" -> {
                        String key = ctx.json().string(action, "key", "");
                        if (!key.isBlank()) ctx.desktop().tapKey(key);
                        append("key: " + key);
                    }
                    case "click" -> {
                        int x = ctx.json().integer(action, "x", 0);
                        int y = ctx.json().integer(action, "y", 0);
                        ctx.desktop().click(x, y);
                        append("click: " + x + ", " + y);
                    }
                    case "screenshot" -> {
                        Path shot = ctx.dataDir().resolve("ai-screen.png");
                        ctx.desktop().screenshot(shot);
                        append("screenshot: " + shot);
                    }
                    case "open" -> {
                        String url = ctx.json().string(action, "url", "");
                        if (!url.isBlank()) ctx.system().openUrl(url);
                        append("open: " + url);
                    }
                    case "clipboard" -> {
                        String value = ctx.json().string(action, "text", "");
                        ctx.system().clipboard(value);
                        append("clipboard: " + value);
                    }
                    default -> append("skip: " + type);
                }
            } catch (Exception e) {
                append("failed: " + type + " / " + e.getMessage());
            }
        }
    }

    private void append(String text) {
        ctx.scheduler().ui(() -> {
            if (output != null) output.appendText(text + "\n");
        });
    }
}
