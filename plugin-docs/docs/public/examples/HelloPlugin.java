import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tcraft.api.CommandContext;
import tcraft.api.PageView;
import tcraft.api.PluginContext;
import tcraft.api.TcraftPlugin;
import tcraft.api.Widgets;

public class HelloPlugin implements TcraftPlugin {

    private PluginContext ctx;

    @Override
    public void onLoad(PluginContext ctx) {
        this.ctx = ctx;

        ctx.ui().titleButton("puzzle", "Hello 插件", () -> ctx.commands().run("hello.say", "标题栏"));
        ctx.ui().toolButton("Hello 按钮", () -> ctx.commands().run("hello.say", "实用工具"));
        ctx.ui().launchButton("Hello 首页按钮", () -> ctx.commands().run("hello.say", "首页"));
        ctx.ui().floatButton("star", "Hello 悬浮按钮", () -> ctx.commands().run("hello.say", "悬浮按钮"));

        ctx.ui().page("main", "Hello 页面", "puzzle", view -> {
            view.header("Hello 插件", "这个页面完全由插件绘制，验证页面注入、卡片、按钮与命令。");
            Label intro = Widgets.text("插件可以使用 JavaFX 任意控件，也可以用 Widgets 保持启动器风格。");
            intro.setWrapText(true);
            view.add(intro);

            tcraft.Ui.Card card = Widgets.card("交互测试");
            card.setSpacing(10);
            Label status = Widgets.label("还没点过按钮。", 12.5);
            HBox row = Widgets.row(10,
                    Widgets.primaryButton("弹出问候", () -> {
                        ctx.commands().run("hello.say", "页面按钮");
                        status.setText("刚才弹出了一个问候窗口。");
                    }),
                    Widgets.button("写配置", () -> {
                        ctx.storage().set("lastClick", System.currentTimeMillis());
                        ctx.storage().save();
                        status.setText("已写入 storage：" + ctx.storage().get("lastClick"));
                    }));
            card.add(row);
            card.add(status);
            VBox.setMargin(card, new Insets(14, 0, 0, 0));
            view.add(card);
            return null;
        });

        ctx.commands().register("hello.say", "弹出一个问候窗口，参数为名字", this::say);
        ctx.events().on("page.open", e -> ctx.log("页面打开：" + e.string("page")));
    }

    private void say(CommandContext c) {
        String name = c.args.isBlank() ? "世界" : c.args;
        ctx.dialogs().message("来自插件", "你好，" + name + "！");
        c.reply("已问候 " + name);
    }
}
