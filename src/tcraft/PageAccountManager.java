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
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class PageAccountManager implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private final List<RowRef> rows = new ArrayList<>();

    private VBox content;
    private VBox listBox;
    private Ui.Card skinBox;
    private Data.Account skinCardFor;

    private final java.util.Set<Data.Account> refreshing =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // 每次启动只自动补拉一次
    private static final java.util.Set<String> autoChecked =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ThreadPoolExecutor skinPool =
            new java.util.concurrent.ThreadPoolExecutor(2, 2, 30,
                    java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "TCL-SkinRefresh");
                t.setDaemon(true);
                return t;
            });

    static {
        skinPool.allowCoreThreadTimeOut(true);
    }

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

        rebuildRows();
        syncSkinCard();
        autoLoadSkins();

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

    private Node topBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(15, 0, 0, 0));
        bar.getChildren().addAll(
                Ui.iconTextButton("user-plus", "添加账户", false, this::addAccount),
                Ui.iconTextButton("refresh-cw", "刷新", false, this::refreshManually));
        return bar;
    }

    private void refreshManually() {
        Data.Account current = data.account();
        if (current != null && current.online) {
            refreshAccount(current, false);
        } else {
            refreshSilently();
            win.toast("已刷新", "账户列表与头像已重新读取。", 1);
        }
    }

    private void autoLoadSkins() {
        for (Data.Account a : data.accounts) {
            if (!a.online || "msa".equals(a.userType)) continue;
            if (hasLocalSkin(a.skin)) continue;
            String key = a.userType + "|" + a.uuid + "|" + a.server;
            if (!autoChecked.add(key)) continue;
            refreshAccount(a, true);
        }
    }

    private static boolean hasLocalSkin(String skin) {
        if (skin == null || !skin.startsWith("Skins/")) return false;
        return java.nio.file.Files.isRegularFile(Theme.skinDir().resolve(skin.substring(6)));
    }

    private void refreshAccount(Data.Account a, boolean silent) {
        if (a == null || !a.online || !refreshing.add(a)) return;
        final String who = a.name;
        skinPool.execute(() -> {
            String skin = null;
            String error = null;
            try {
                if ("msa".equals(a.userType)) {
                    skin = MsAuth.refreshSkin(a);
                } else {
                    Yggdrasil.Server server = Yggdrasil.accountServer(a);
                    if (server == null) throw new Exception("缺少认证服务器地址，请重新登录该账户");
                    Yggdrasil.Profile profile = new Yggdrasil.Profile(a.uuid, who);
                    String url = Yggdrasil.skinUrlOf(server, profile);
                    if (url == null) throw new Exception("服务器没有返回该账户的皮肤");
                    skin = Skins.download(who, url);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "未知错误" : e.getMessage();
                Data.get().log("刷新皮肤失败：" + Log.describe(e));
            }
            final String loaded = skin;
            final String reason = error;
            javafx.application.Platform.runLater(() -> {
                refreshing.remove(a);
                if (loaded != null) {
                    a.skin = loaded;
                    data.save();
                    refreshSilently();
                    if (!silent) win.toast("已刷新皮肤！", who, 1);
                } else if (!silent && reason != null) {
                    win.toast("刷新皮肤失败", reason, 2);
                }
            });
        });
    }

    private void refreshSilently() {
        rebuildRows();
        skinCardFor = null;
        syncSkinCard();
        win.refreshAccountBadge();
    }

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

    private void syncSkinCard() {
        if (content == null) return;
        Data.Account current = signedIn() && "msa".equals(data.account().userType)
                ? data.account() : null;
        if (current == skinCardFor) return;
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
            if ("msa".equals(a.userType)) {
                actions.add(Ui.iconButton("shirt", 28, 15, "在官网管理该账户的皮肤",
                        () -> openWebsite("https://www.minecraft.net/profile")));
            }
            actions.add(Ui.iconButton("refresh-cw", 28, 15, "刷新该账户的皮肤", () -> refreshAccount(a, false)));
        } else {
            actions.add(Ui.iconButton("refresh-cw", 28, 15, "刷新该账户的皮肤与头像缓存", () -> {
                refreshSilently();
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
        Ui.RadioBox rb = Ui.radioBox("", selected);
        rb.setPrefWidth(18);
        rb.setMinWidth(18);
        rb.setMaxWidth(18);
        return rb;
    }

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
        syncSkinCard();
        win.refreshAccountBadge();
        win.toast("已切换启动账户。", data.accounts.get(index).name, 1);
    }

    private String rowInfo(int index, boolean current) {
        Data.Account a = data.accounts.get(index);
        // 拼接串查不到整键，两处调用点均创建时翻译
        return Lang.t(loginType(a)) + (current ? Lang.t(" · 当前账户") : "");
    }

    private static String loginType(Data.Account a) {
        if (a == null) return "离线登录";
        if ("msa".equals(a.userType)) return "正版登录";
        if ("nide".equals(a.userType)) return "统一通行证";
        if ("legacy".equals(a.userType) && a.online) return "第三方登录";
        return "离线登录";
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
        Dialogs.confirm(win, "删除账户", Lang.tf("确定要删除账户 %s 吗？此操作不可撤销。", a.name), () -> {
            int i = data.accounts.indexOf(a);
            if (i < 0) return;
            data.accounts.remove(i);
            if (data.selectedAccount >= data.accounts.size()) data.selectedAccount = 0;
            data.save();
            refreshSilently();
            win.toast("账户已删除", a.name, 1);
        }, true);
    }

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
        Label hint = Ui.wrap(Ui.label(Lang.tf("当前正版账户：%s。修改皮肤 / 披风后会同步刷新账户头像与标题栏徽章；皮肤文件也可另存为 PNG。", a.name), 12, Theme.text), 430);
        hint.setOpacity(0.6);
        hint.setLineSpacing(4);

        FlowPane buttons = new FlowPane(12, 10);
        StackPane change = skinButton("修改皮肤", true, () -> Dialogs.skinPicker(win, a));
        StackPane cape = skinButton("更换披风", false, () -> {
            win.toast("披风设置", "请在官网的个人资料页更换披风。", 0);
            openWebsite("https://www.minecraft.net/profile");
        });
        StackPane save = skinButton("保存皮肤文件", false, () -> saveSkinFile(a));
        StackPane refresh = skinButton("刷新皮肤", false, () -> refreshAccount(a, false));
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
        String skinName = a.skin == null ? "Steve.png" : a.skin;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("保存皮肤文件"));
        chooser.setInitialFileName(a.name + ".png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("PNG 图片"), "*.png"));
        java.io.File target = chooser.showSaveDialog(win.stage);
        if (target == null) return;
        try (java.io.InputStream in = Theme.imageStream(skinName)) {
            if (in == null) {
                win.toast("保存失败", "找不到皮肤文件。", 2);
                return;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
