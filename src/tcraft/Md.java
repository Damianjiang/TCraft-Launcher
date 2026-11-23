package tcraft;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

// 极简 Markdown 标题 列表 粗体 行内代码
public final class Md {

    private Md() {
    }

    public static Node render(String markdown) {
        VBox box = new VBox(4);
        if (markdown == null || markdown.trim().isEmpty()) {
            box.getChildren().add(Ui.label("该版本未填写更新日志。", 12.5, Theme.gray2));
            return box;
        }
        for (String raw : markdown.replace("\r\n", "\n").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ")) {
                String title = line.replaceFirst("^#{1,3}\\s+", "");
                Label head = Ui.bold(title, 13, Theme.text);
                VBox.setMargin(head, new javafx.geometry.Insets(6, 0, 1, 0));
                box.getChildren().add(head);
                continue;
            }
            boolean bullet = line.startsWith("- ") || line.startsWith("* ");
            String text = bullet ? line.substring(2) : line;
            TextFlow flow = inline(text);
            if (bullet) {
                Text dot = new Text("• ");
                dot.setFill(Theme.gray2);
                flow.getChildren().add(0, dot);
            }
            box.getChildren().add(flow);
        }
        return box;
    }

    private static TextFlow inline(String text) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(1.5);
        int i = 0;
        while (i < text.length()) {
            int bold = text.indexOf("**", i);
            int code = text.indexOf('`', i);
            int next;
            boolean isBold = false;
            if (bold >= 0 && (code < 0 || bold < code)) {
                next = bold;
                isBold = true;
            } else if (code >= 0) {
                next = code;
            } else {
                next = -1;
            }
            if (next < 0) {
                flow.getChildren().add(plain(text.substring(i)));
                break;
            }
            if (next > i) flow.getChildren().add(plain(text.substring(i, next)));
            if (isBold) {
                int end = text.indexOf("**", next + 2);
                if (end < 0) {
                    flow.getChildren().add(plain(text.substring(next)));
                    break;
                }
                Text t = new Text(text.substring(next + 2, end));
                t.setFont(Theme.font(12.5, true));
                t.setFill(Theme.text);
                flow.getChildren().add(t);
                i = end + 2;
            } else {
                int end = text.indexOf('`', next + 1);
                if (end < 0) {
                    flow.getChildren().add(plain(text.substring(next)));
                    break;
                }
                Text t = new Text(text.substring(next + 1, end));
                t.setFont(javafx.scene.text.Font.font("Consolas", 12));
                t.setFill(Theme.gray1);
                flow.getChildren().add(t);
                i = end + 1;
            }
        }
        return flow;
    }

    private static Text plain(String s) {
        Text t = new Text(s);
        t.setFont(Theme.font(12.5));
        t.setFill(Theme.gray1);
        return t;
    }
}
