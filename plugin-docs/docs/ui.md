# 界面与布局扩展

插件可以把界面元素放到启动器的五个位置，也可以完全自己画一整页。
如果想要更自由的效果，还能直接拿到 `Stage` / `Scene` 修改任意节点。

## 五个放置位置

| 位置 | API | 出现位置 | 适合放什么 |
| --- | --- | --- | --- |
| 完整页面 | `ui.page(...)` | 「更多」左栏 + 右侧内容区 | 设置面板、数据看板、工具页、教程 |
| 标题栏 | `ui.titleButton(...)` | 窗口右上角，搜索和最小化之间 | 快捷入口、状态灯、账号相关 |
| 首页 | `ui.launchButton(...)` | 首页「插件扩展」卡片 | 一键操作、活动入口、公告 |
| 工具按钮 | `ui.toolButton(...)` | 「更多 → 实用工具」 | 小工具、批处理、诊断 |
| 悬浮按钮 | `ui.floatButton(...)` | 窗口右下角 | 常驻助手、快捷菜单 |

### 完整页面

```java
ctx.ui().page("dashboard", "数据看板", "chart-column", view -> {
    view.header("数据看板", "统计本启动器的使用情况。");

    var card = Widgets.card("今天");
    card.add(Widgets.title("3 次启动"));
    card.add(Widgets.text("最近一次是 2 分钟前。"));
    view.add(card);

    return null;
});
```

- 页面会自动出现在「更多」左栏，标题就是 `title`。
- `icon` 可以是内置图标名；目录插件也可以写 `icon.png` 这样的相对图片路径。
- 每次打开页面都会重新调用 builder，不要在 builder 外缓存带父节点的 Node。

### 标题栏按钮

```java
ctx.ui().titleButton("star", "打开收藏", () -> {
    ctx.ui().openPage("other");
});
```

图标名来自启动器内置图标集，常用：`star`、`puzzle`、`package`、`download`、`folder-open`、
`power`、`info`、`refresh-cw`、`trash-2`、`triangle-alert`、`settings`、`music`、`play`。

### 首页按钮

```java
ctx.ui().launchButton("一键签到", () -> {
    ctx.dialogs().message("签到", "今天已经签到过了。");
});
```

首页的按钮会收进一张标题为「插件扩展」的卡片。没有插件按钮时这张卡片不会显示。

### 工具按钮

```java
ctx.ui().toolButton("清理插件缓存", () -> {
    ctx.dialogs().confirm("清理", "确定清理本插件缓存吗？", () -> {
        // 清理逻辑
    });
});
```

它会和「清理垃圾」「今日人品」并排出现在「更多 → 实用工具」。

### 悬浮按钮

```java
ctx.ui().floatButton("headset", "联系客服", () -> {
    ctx.ui().openPage("link");
});
```

悬浮按钮固定在右下角，和任务管理、背景音乐等按钮同一列。

## 自定义完整页面

`PageView.root()` 是一个 `VBox`，可以放任何 JavaFX 节点。下面是一个表单加表格的完整例子：

```java
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tcraft.api.*;

ctx.ui().page("servers", "服务器列表", "globe", view -> {
    view.header("服务器列表", "管理你常用的联机地址。");

    TextField name = Widgets.field("名称");
    TextField address = Widgets.field("地址，例如 example.com:25565");
    TableView<String[]> table = new TableView<>();
    table.setPrefHeight(260);

    TableColumn<String[], String> c1 = new TableColumn<>("名称");
    c1.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue()[0]));
    TableColumn<String[], String> c2 = new TableColumn<>("地址");
    c2.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue()[1]));
    table.getColumns().addAll(c1, c2);

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    form.add(new Label("名称"), 0, 0);
    form.add(name, 1, 0);
    form.add(new Label("地址"), 0, 1);
    form.add(address, 1, 1);
    GridPane.setHgrow(address, Priority.ALWAYS);

    var add = Widgets.primaryButton("添加", () -> {
        if (address.getText().isBlank()) return;
        table.getItems().add(new String[]{name.getText(), address.getText()});
        name.clear();
        address.clear();
    });
    HBox actions = Widgets.row(10, add, Widgets.button("清空", table.getItems()::clear));

    var card = Widgets.card("新增服务器");
    card.add(form);
    card.add(actions);
    view.add(card);
    view.add(table);
    return null;
});
```

::: tip 宽度
页面根节点已经填满可用宽度。卡片、表格默认会拉伸；如果不想拉伸，给节点设置
`setMaxWidth(Region.USE_PREF_SIZE)`。
:::

## 用 Widgets 保持启动器风格

推荐优先使用 `Widgets`，它会跟随启动器主题、圆角和字号：

```java
var card = Widgets.card("示例卡片");
card.add(Widgets.title("标题"));
card.add(Widgets.text("正文说明"));
card.add(Widgets.row(10,
        Widgets.primaryButton("主要操作", () -> {}),
        Widgets.button("次要操作", () -> {})));
view.add(card);
```

| 需求 | 用法 |
| --- | --- |
| 大标题 | `Widgets.title("...")` |
| 正文 | `Widgets.text("...")` |
| 普通 / 主题 / 危险按钮 | `Widgets.button` / `primaryButton` / `dangerButton` |
| 卡片 | `Widgets.card("标题")`，再 `.add(...)` |
| 水平 / 垂直排列 | `Widgets.row(10, a, b)` / `Widgets.column(10, a, b)` |
| 输入框 | `Widgets.field("提示")` |
| 复选框 | `Widgets.check("文字", true).isSelected()` |
| 下拉框 | `Widgets.combo(new String[]{"A","B"}, 0)` |
| 图片 | `Widgets.image("path.png", 64, 64)` |

## 使用任意 JavaFX 控件

启动器已经加载 JavaFX 17 的 `javafx.controls`、`javafx.graphics`、`javafx.media`。
下面这些都能直接用：

- 布局：`VBox`、`HBox`、`GridPane`、`BorderPane`、`FlowPane`、`TilePane`、`StackPane`、`ScrollPane`
- 控件：`Label`、`Button`、`TextField`、`TextArea`、`CheckBox`、`ComboBox`、`Slider`、`ProgressBar`、
  `TableView`、`ListView`、`TreeView`、`TabPane`、`Accordion`、`DatePicker`、`ColorPicker`
- 图形：`Canvas`、`ImageView`、`WebView`（需要 `javafx.web`，默认未加载）
- 动画：`Timeline`、`Transition`、`FadeTransition`、`TranslateTransition`

::: warning javafx.web / javafx.fxml
默认只加载 `javafx.controls`、`javafx.media`。需要 WebView 或 FXML 时，请向启动器作者申请加入模块，
或者使用 `javafx.scene.web` 之外的替代方案。
:::

## 直接修改启动器布局（高级）

拿到 Scene 根节点后，可以把任何节点加到启动器上：

```java
StackPane root = (StackPane) ctx.stage().getScene().getRoot();

Label badge = new Label("插件运行中");
badge.setStyle("-fx-background-color: rgba(47,111,235,.92); -fx-text-fill: white;"
        + "-fx-padding: 4 10; -fx-background-radius: 6;");
badge.setId("demo-badge");

StackPane.setAlignment(badge, javafx.geometry.Pos.TOP_RIGHT);
StackPane.setMargin(badge, new javafx.geometry.Insets(70, 18, 0, 0));
root.getChildren().add(badge);
```

在 `onDisable()` 里移除自己加的节点：

```java
StackPane root = (StackPane) ctx.stage().getScene().getRoot();
root.getChildren().removeIf(n -> "demo-badge".equals(n.getId()));
```

::: danger
直接改根节点可能覆盖或遮挡启动器内置界面。请给节点设置唯一 `id` 或 style class，
只增删自己的节点，不要清空或替换启动器节点。
:::

## 布局建议

- 页面从 `view.header(...)` 开始，保持标题层级一致。
- 主要内容放进 `Widgets.card(...)`，视觉上和内置页面一致。
- 一屏内容过多时，页面外层已经是 `ScrollPane`，直接继续添加节点即可。
- 使用 `Widgets.row` / `column` 控制间距，不要写死像素宽度。
- 需要主题色时优先用 `Widgets.primaryButton`；自定义颜色请考虑深色模式。
- 不要在 JavaFX 线程里做长任务；用后台线程 + `Platform.runLater`。
