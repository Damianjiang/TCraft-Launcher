package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** 账户管理页，切换账户就地刷新。 */
public class PageAccountManager implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    /** 每行的节点引用，顺序和 data.accounts 一致。 */
    private final List<RowRef> rows = new ArrayList<>();

    private VBox content;
    private VBox listBox;
    private Ui.Card skinBox;
    /** 皮肤卡当前展示的账户，null 表示没显示。 */
    private Data.Account skinCardFor;

    /** 一行的可变节点引用。 */
    private static final class RowRef {
        StackPane row;
        Ui.RadioBox radio;
        Label info;
    }

    public PageAccountManager(MainWindow win) {
        this.win = win;
    }

    @Override
    public Node right() {
        content = new VBox();
        content.setPadding(new Insets(10, 25, 10, 25));
        content.setMaxWidth(Double.MAX_VALUE);
        content.setAlignment(Pos.TOP_LEFT);

        content.getChildren().add(topBar());

        Ui.Card card = Ui.card("账户列表");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setContentPadding(new Insets(6, 20, 20, 20));
        VBox.setMargin(card, new Insets(10, 0, 15, 0));
        listBox = new VBox();
        listBox.setMaxWidth(Double.MAX_VALUE);
        card.add(listBox);
        content.getChildren().add(card);

        rebuildRows();   // 首屏填充，含空态
        syncSkinCard();  // 正版账户才显示皮肤卡

        StackPane holder = new StackPane(content);
        holder.setAlignment(Pos.TOP_LEFT);
        holder.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = Ui.scroll(holder);
        sp.setPadding(new Insets(0));
        return sp;
    }

    private boolean signedIn() {
        return data.account() != null && data.account().online;
    }

    /** 顶部：添加账户 + 刷新。 */
    private Node topBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(15, 0, 0, 0));
        bar.getChildren().addAll(
                Ui.iconTextButton("user-plus", "添加账户", false, this::addAccount),
                Ui.iconTextButton("refresh-cw", "刷新", false, this::refreshManually));
        return bar;
    }

    /** 静默重读账户，头像按文件时间自动失效。 */
    private void refreshManually() {
        refreshSilently();
        win.toast("已刷新", "账户列表与头像已重新读取。", 1);
    }

    /** 重建列表、同步皮肤卡、刷新徽章，无动画。 */
    private void refreshSilently() {
        rebuildRows();
        skinCardFor = null;   // 强制重建皮肤卡
        syncSkinCard();
        win.refreshAccountBadge();
    }

    /** 重建列表行，空列表显示空态。 */
    private void rebuildRows() {
        if (listBox == null) return;
        rows.clear();
        listBox.getChildren().clear();
        for (int i = 0; i < data.accounts.size(); i++) {
            RowRef ref = new RowRef();
            listBox.getChildren().add(accountRow(i, ref));
            rows.add(ref);
        }
        if (data.accounts.isEmpty()) {
            Label empty = Ui.label("尚未添加任何账户，请使用左侧的「添加账户」。", 13, Theme.text);
            empty.setOpacity(0.55);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setAlignment(Pos.CENTER);
            VBox.setMargin(empty, new Insets(12, 0, 0, 0));
            listBox.getChildren().add(empty);
        }
    }

    /** 正版账户才显示皮肤卡。 */
    private void syncSkinCard() {
        if (content == null) return;
        Data.Account current = signedIn() ? data.account() : null;
        if (current == skinCardFor) return;   // 账户没变就不用重建
        if (skinBox != null) {
            content.getChildren().remove(skinBox);
            skinBox = null;
        }
        skinCardFor = current;
        if (current == null) return;
        skinBox = skinCard();
        skinBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(skinBox, new Insets(0, 0, 15, 0));
        content.getChildren().add(skinBox);
    }

    private Node accountRow(int index, RowRef ref) {
        Data.Account a = data.accounts.get(index);
        boolean current = index == data.selectedAccount;

        StackPane row = new StackPane();
        row.setPrefHeight(56);
        row.setMinHeight(56);
        row.setMaxHeight(56);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setBackground(Ui.fill(Theme.cardBg, 6));
        // 选中态由左侧单选钮表达
        row.setBorder(Ui.border(Theme.accentPale2, 6, 1));
        row.setCursor(Cursor.HAND);
        row.setPickOnBounds(true);
        VBox.setMargin(row, new Insets(0, 0, 10, 0));

        HBox layout = new HBox();
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPadding(new Insets(8, 12, 8, 12));
        layout.setMaxWidth(Double.MAX_VALUE);

        Ui.RadioBox radio = radio(current);
        radio.setPrefWidth(44);
        radio.setMinWidth(44);
        radio.setMaxWidth(44);
        radio.setAlignment(Pos.CENTER);

        StackPane face = Ui.avatar(a, 32);
        face.setBorder(Ui.border(Theme.gray6, 4, 1));
        StackPane faceBox = new StackPane(face);
        faceBox.setPrefWidth(46);
        faceBox.setMinWidth(46);
        faceBox.setMaxWidth(46);
        faceBox.setAlignment(Pos.CENTER_LEFT);

        VBox texts = new VBox(1);
        texts.setAlignment(Pos.CENTER_LEFT);
        Label name = Ui.semi(a.name, 13, Theme.text);
        Label info = Ui.label(rowInfo(index, current), 12, Theme.text);
        info.setOpacity(0.55);
        texts.getChildren().addAll(name, info);
        HBox.setHgrow(texts, Priority.ALWAYS);

        HBox buttons = new HBox();
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(0, 10, 0, 0));
        List<Node> actions = new ArrayList<>();
        if (a.online) {
            actions.add(Ui.iconButton("scan-face", 28, 15, "3D 查看该账户的皮肤", () -> Dialogs.skinPicker(win, a)));
            actions.add(Ui.iconButton("shirt", 28, 15, "在官网管理该账户的皮肤", () -> openWebsite("https://www.minecraft.net/profile")));
            actions.add(Ui.iconButton("refresh-cw", 28, 15, "刷新该账户的登录状态", () -> {
                refreshSilently(); // 静默刷新，不闪整页
                win.toast("已刷新。", a.name, 1);
            }));
        } else {
            actions.add(Ui.iconButton("refresh-cw", 28, 15, "刷新该账户的皮肤与头像缓存", () -> {
                refreshSilently(); // 静默刷新，不闪整页
                win.toast("已刷新。", a.name, 1);
            }));
        }
        actions.add(Ui.iconButton("copy", 28, 15, "复制该账户的 UUID", () -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(formatUuid(a.uuid));
            Clipboard.getSystemClipboard().setContent(c);
            win.toast("已复制到剪贴板。", formatUuid(a.uuid), 1);
        }));
        actions.add(Ui.iconButton("palette", 28, 15, "编辑该账户的皮肤", () -> Dialogs.skinPicker(win, a)));
        actions.add(Ui.iconButton("trash-2", 28, 15, "删除此账户", () -> remove(index)));
        for (Node n : actions) {
            HBox.setMargin(n, new Insets(0, 0, 0, 3));
            n.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
        }
        buttons.getChildren().addAll(actions);

        layout.getChildren().addAll(radio, faceBox, texts, buttons);
        row.getChildren().add(layout);

        // 悬停底色过渡到 listHoverBg
        row.setOnMouseEntered(e -> row.setBackground(Ui.fill(Theme.listHoverBg, 6)));
        row.setOnMouseExited(e -> row.setBackground(Ui.fill(Theme.cardBg, 6)));
        row.setOnMouseClicked(e -> {
            if (index == data.selectedAccount) return;
            select(index);
        });
        if (ref != null) {
            ref.row = row;
            ref.radio = radio;
            ref.info = info;
        }
        return row;
    }

    private Ui.RadioBox radio(boolean selected) {
        // 行首单选钮，18×18
        Ui.RadioBox rb = Ui.radioBox("", selected);
        rb.setPrefWidth(18);
        rb.setMinWidth(18);
        rb.setMaxWidth(18);
        return rb;
    }

    /** 切换启动账户，只就地更新选中态。 */
    void select(int index) {
        if (index < 0 || index >= data.accounts.size()) return;
        data.selectedAccount = index;
        data.save();
        for (int i = 0; i < rows.size(); i++) {
            RowRef r = rows.get(i);
            if (r == null) continue;
            if (r.radio != null) r.radio.setSelected(i == index);
            if (r.info != null) r.info.setText(rowInfo(i, i == index));
        }
        syncSkinCard();  // 切到正版账户才出现皮肤卡
        win.refreshAccountBadge(); // 标题栏徽章跟着改
        win.toast("已切换启动账户。", data.accounts.get(index).name, 1);
    }

    /** 行里那句登录状态。 */
    private String rowInfo(int index, boolean current) {
        Data.Account a = data.accounts.get(index);
        return (a.online ? "正版登录" : "离线登录") + (current ? " · 当前账户" : "");
    }

    private void addAccount() {
        Dialogs.addAccount(win, 0);
    }

    private void remove(int index) {
        if (data.accounts.size() <= 1) {
            win.toast("无法删除", "至少需要保留一个账户。", 2);
            return;
        }
        Data.Account a = data.accounts.get(index);
        Dialogs.confirm(win, "删除账户", "确定要删除账户 " + a.name + " 吗？此操作不可撤销。", () -> {
            int i = data.accounts.indexOf(a);
            if (i < 0) return;
            data.accounts.remove(i);
            if (data.selectedAccount >= data.accounts.size()) data.selectedAccount = 0;
            data.save();
            refreshSilently(); // 原地重建，不闪整页
            win.toast("账户已删除", a.name, 1);
        }, true);
    }

    /** 正版皮肤与披风卡片。 */
    private Ui.Card skinCard() {
        Ui.Card card = Ui.card("正版皮肤与披风");
        card.setContentPadding(new Insets(10, 22, 20, 22));
        Data.Account a = data.account();

        HBox row = new HBox(20);
        row.setAlignment(Pos.TOP_LEFT);
        StackPane preview = new StackPane(Ui.avatar(a, 64));
        preview.setMinSize(112, 112);
        preview.setPrefSize(112, 112);
        preview.setMaxSize(112, 112);
        preview.setBackground(Ui.fill(Theme.halfWhite, 6));

        VBox right = new VBox(12);
        Label hint = Ui.wrap(Ui.label("当前正版账户：" + a.name
                + "。修改皮肤 / 披风后会同步刷新账户头像与标题栏徽章；皮肤文件也可另存为 PNG。", 12, Theme.text), 430);
        hint.setOpacity(0.6);
        hint.setLineSpacing(4);

        FlowPane buttons = new FlowPane(12, 10);
        StackPane change = skinButton("修改皮肤", true, () -> Dialogs.skinPicker(win, a));
        StackPane cape = skinButton("更换披风", false, () -> {
            win.toast("披风设置", "请在官网的个人资料页更换披风。", 0);
            openWebsite("https://www.minecraft.net/profile");
        });
        StackPane save = skinButton("保存皮肤文件", false, () -> saveSkinFile(a));
        StackPane refresh = skinButton("刷新皮肤", false, () -> {
            refreshSilently(); // 头像按文件时间自动失效缓存
            win.toast("已刷新。", a.name, 1);
        });
        StackPane redeem = skinButton("兑换 CDKEY", false, () -> openWebsite("https://www.minecraft.net/zh-hans/redeem"));
        StackPane profile = skinButton("官网管理", false, () -> openWebsite("https://www.minecraft.net/profile"));
        buttons.getChildren().addAll(change, cape, save, refresh, redeem, profile);

        right.getChildren().addAll(hint, buttons);
        HBox.setHgrow(right, Priority.ALWAYS);
        row.getChildren().addAll(preview, right);
        card.add(row);
        return card;
    }

    private StackPane skinButton(String text, boolean highlight, Runnable action) {
        StackPane b = Ui.button(text, highlight ? Ui.BTN_THEME : Ui.BTN_NORMAL, action);
        b.setMinWidth(110);
        b.setPrefHeight(32);
        b.setMinHeight(32);
        b.setMaxHeight(32);
        return b;
    }

    private void saveSkinFile(Data.Account a) {
        Path source = Theme.res("images/" + (a.skin == null ? "Steve.png" : a.skin));
        if (!Files.exists(source)) {
            win.toast("保存失败", "找不到皮肤文件。", 2);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存皮肤文件");
        chooser.setInitialFileName(a.name + ".png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        java.io.File target = chooser.showSaveDialog(win.stage);
        if (target == null) return;
        try {
            Files.copy(source, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            win.toast("已保存皮肤文件", target.getAbsolutePath(), 1);
        } catch (Exception e) {
            win.toast("保存失败", e.getMessage() == null ? "未知错误" : e.getMessage(), 2);
        }
    }

    private void openWebsite(String url) {
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            win.toast("无法打开网页", url, 2);
        }
    }

    private String formatUuid(String uuid) {
        if (uuid == null || uuid.length() != 32) return uuid == null ? "" : uuid;
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16)
                + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }
}
