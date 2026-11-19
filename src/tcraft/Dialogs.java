package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Dialogs {

    private Dialogs() {
    }

    public static void confirm(MainWindow win, String title, String message, Runnable onOk) {
        confirm(win, title, message, onOk, false);
    }

    public static void confirm(MainWindow win, String title, String message, Runnable onOk, boolean warn) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 23, 22));
        card.setPrefWidth(400);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 23, warn ? Theme.red : Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        t.setWrapText(true);
        Region line = Ui.line(warn ? Theme.red : Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        Label body = Ui.label(message, 15, Theme.text);
        body.setWrapText(true);
        body.setMaxWidth(360);
        body.setPadding(new Insets(0, 15, 0, 7));
        VBox.setMargin(body, new Insets(13, 0, 17, 0));
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("确定", warn ? Ui.BTN_RED : Ui.BTN_HIGHLIGHT, () -> {
                    win.closeModal();
                    onOk.run();
                }),
                Ui.button("取消", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().addAll(t, line, body, actions);
        win.showModal(card, true, warn ? Theme.warnMask : null);
    }

    public static void message(MainWindow win, String title, String message) {
        message(win, title, message, false, "确定");
    }

    public static void message(MainWindow win, String title, String message, boolean warn, String okText) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 23, 22));
        card.setPrefWidth(400);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 23, warn ? Theme.red : Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        t.setWrapText(true);
        Region line = Ui.line(warn ? Theme.red : Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        Label body = Ui.label(message, 15, Theme.text);
        body.setWrapText(true);
        body.setMaxWidth(360);
        body.setPadding(new Insets(0, 15, 0, 7));
        VBox.setMargin(body, new Insets(13, 0, 17, 0));
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(Ui.button(okText == null || okText.isEmpty() ? "确定" : okText,
                warn ? Ui.BTN_RED : Ui.BTN_HIGHLIGHT, win::closeModal));
        card.getChildren().addAll(t, line, body, actions);
        win.showModal(card, true, warn ? Theme.warnMask : null);
    }

    public static void input(MainWindow win, String title, String hint, String defaultValue,
                             java.util.function.Consumer<String> onOk) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 23, 22));
        boolean en = Lang.mode == 1;
        card.setPrefWidth(en ? 460 : 430);
        card.setMinWidth(en ? 440 : 400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 23, Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        Region line = Ui.line(Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        TextField field = new TextField(defaultValue == null ? "" : defaultValue);
        field.setFont(Theme.font(13));
        field.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        field.setPromptText(hint == null ? "" : Lang.t(hint));
        HBox fieldBox = new HBox(field);
        fieldBox.setAlignment(Pos.CENTER_LEFT);
        fieldBox.setPadding(new Insets(8, 10, 8, 10));
        fieldBox.setBackground(Ui.fill(Theme.fieldBg, 3));
        VBox.setMargin(fieldBox, new Insets(13, 0, 17, 0));
        Runnable submit = () -> {
            String value = field.getText();
            win.closeModal();
            onOk.accept(value == null || value.trim().isEmpty() ? null : value.trim());
        };
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("确定", Ui.BTN_HIGHLIGHT, submit),
                Ui.button("取消", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().addAll(t, line, fieldBox, actions);
        field.setOnAction(e -> submit.run());
        win.showModal(card);
    }

    public static void accountManager(MainWindow win) {
        win.openPage("account", true);
    }

    public static VBox msgCard(String title) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 18, 22));
        boolean en = Lang.mode == 1;
        card.setPrefWidth(en ? 480 : 410);
        card.setMinWidth(en ? 460 : 400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(20, 4, 0.8, Theme.text));
        Label t = Ui.semi(title, 23, Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        Region line = Ui.line(Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().addAll(t, line);
        return card;
    }

    public static void addAccount(MainWindow win, int initialMode) {
        VBox card = msgCard("添加账户");

        VBox rows = new VBox(2);
        rows.setPadding(new Insets(15, 0, 6, 0));
        String[][] items = {
                {"badge-check", "微软账号", "使用微软账号登录，支持正版皮肤与跨设备同步"},
                {"user", "离线账号", "无需正版账号，输入玩家名即可直接游玩"},
                {"globe", "LittleSkin / 第三方 Yggdrasil", "使用 LittleSkin 账号登录并加载其皮肤，第三方 Yggdrasil 外置登录"},
                {"key-round", "统一通行证", "使用服务器码登录第三方服务器"},
        };
        for (int i = 0; i < items.length; i++) {
            final int target = i;
            Node icon = Theme.icon(items[i][0], 17, Theme.text);
            Ui.ListItem row = new Ui.ListItem(icon, items[i][1], items[i][2], 46);
            row.setOnMouseClicked(e -> {
                if (target == 0) msLoginCard(win);
                else if (target == 1) offlineCard(win);
                else if (target == 2) yggdrasilCard(win);
                else nideCard(win);
            });
            rows.getChildren().add(row);
        }
        card.getChildren().add(rows);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(6, 7, 0, 7));
        actions.getChildren().add(Ui.button("取消", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    public static void offlineCard(MainWindow win) {
        VBox card = msgCard("添加离线模式账户");
        VBox form = new VBox(0);
        form.setPadding(new Insets(15, 7, 0, 7));
        HBox nameRow = new HBox(15);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        nameRow.getChildren().addAll(Ui.label("用户名", 14, Theme.text),
                Ui.field("建议使用英文字符、数字以及下划线命名，且长度不超过 16 个字符"));
        TextField name = (TextField) nameRow.getChildren().get(1);
        name.setPrefHeight(32);
        name.setTextFormatter(new javafx.scene.control.TextFormatter<String>(
                change -> change.getControlNewText().length() <= 60 ? change : null));
        HBox.setHgrow(name, Priority.ALWAYS);
        VBox.setMargin(nameRow, new Insets(0, 0, 12, 0));
        Label purchase = Ui.label("购买 Minecraft", 14, Theme.accentDark);
        purchase.setCursor(javafx.scene.Cursor.HAND);
        purchase.setOnMouseClicked(e -> {
            try {
                Shell.openUrl("https://www.minecraft.net/zh-hans/store/minecraft-java-bedrock-edition-pc");
            } catch (Exception ex) {
                win.toast("打开链接失败", ex.getMessage() == null ? "未知错误" : ex.getMessage(), 2);
            }
        });
        VBox.setMargin(purchase, new Insets(0, 0, 12, 2));
        Label error = Ui.label("", 13, Theme.red);
        final StackPane[] btnRef = new StackPane[1];
        StackPane loginBtn = Ui.button("登录", Ui.BTN_HIGHLIGHT, () -> {
            String n = (name.getText() == null ? "" : name.getText().replace("¨", "").trim());
            Runnable doAdd = () -> {
                Data d = Data.get();
                d.accounts.add(new Data.Account(n, Data.offlineUuid(n), selectedSkin, false));
                d.selectedAccount = d.accounts.size() - 1;
                d.save();
                win.refreshAccountBadge();
                win.closeModal();
                win.openPage(win.currentKey, false);
                win.toast("已添加账户", n, 1);
            };
            if (!isValidMcName(n)) {
                // 两段是独立词条，各自 Lang.t 后拼接，整串查不到表
                confirm(win, "警告", Lang.t("离线模式账户用户名建议仅使用英文字符、数字以及下划线，")
                        + Lang.t("且长度不超过 16 个字符。确定要继续吗？"), doAdd);
                return;
            }
            doAdd.run();
        });
        btnRef[0] = loginBtn;
        loginBtn.setDisable(true);
        name.textProperty().addListener((o, a, b) -> {
            boolean ok = b != null && !b.trim().isEmpty();
            loginBtn.setDisable(!ok);
            error.setText(ok ? "" : "必填项");
        });
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(error, Ui.spacer(), loginBtn, Ui.button("取消", Ui.BTN_NORMAL, win::closeModal));
        form.getChildren().addAll(nameRow, purchase, actions);
        card.getChildren().add(form);
        win.showModal(card);
    }

    public static void select(MainWindow win, String title, String[] options,
                              String btn1, String btn2, boolean isWarn,
                              java.util.function.IntConsumer onOk) {
        VBox card = msgCard(title);
        Label t = (Label) card.getChildren().get(0);
        t.setTextFill(isWarn ? Theme.red : Theme.accentDark);
        Region line = (Region) card.getChildren().get(1);
        line.setBackground(Ui.fill(isWarn ? Theme.red : Theme.accentDark, 0));
        VBox rows = new VBox(2);
        rows.setPadding(new Insets(15, 0, 6, 0));
        final int[] selected = {-1};
        final StackPane[] okBtn = new StackPane[1];
        for (int i = 0; i < options.length; i++) {
            Ui.RadioBox rb = Ui.radioBox(options[i], false);
            final int idx = i;
            rb.setOnAction(() -> {
                selected[0] = idx;
                okBtn[0].setDisable(false);
            });
            rows.getChildren().add(rb);
        }
        card.getChildren().add(rows);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(6, 7, 0, 7));
        StackPane ok = Ui.button(btn1, isWarn ? Ui.BTN_RED : Ui.BTN_HIGHLIGHT, () -> {
            if (selected[0] < 0) return;
            win.closeModal();
            onOk.accept(selected[0]);
        });
        ok.setDisable(true);
        okBtn[0] = ok;
        actions.getChildren().add(ok);
        if (btn2 != null && !btn2.isEmpty()) actions.getChildren().add(Ui.button(btn2, Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card, true, isWarn ? Theme.warnMask : null);
    }

    private static boolean isValidMcName(String n) {
        if (n == null || n.isEmpty() || n.length() > 16) return false;
        for (char c : n.toCharArray()) {
            if (c == '_') continue;
            if (c >= 128 || !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    public static void msLoginCard(MainWindow win) {
        if (!MsAuth.available()) {
            VBox card = msgCard("登录微软账号");
            VBox form = new VBox(10);
            form.setPadding(new Insets(15, 7, 0, 7));
            // Ui.hint 内部对整串做 Lang.t，拼接串必须先按片段译好
            form.getChildren().add(Ui.hint(Lang.t("这个版本没有内置微软登录凭据，开源版去掉了正版登录。")
                    + Lang.t("要用正版登录，请自行构建完整版并注入自己的 client id，构建说明见 docs/构建与发布.md。"), 0));
            HBox actions = new HBox(12);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.getChildren().add(Ui.button("知道了", Ui.BTN_NORMAL, win::closeModal));
            form.getChildren().add(actions);
            card.getChildren().add(form);
            win.showModal(card);
            return;
        }
        VBox card = msgCard("登录微软账号");
        final Data d = Data.get();
        VBox form = new VBox(10);
        form.setPadding(new Insets(15, 7, 0, 7));
        double wrapWidth = Lang.mode == 1 ? 430 : 360;

        Label hint = Ui.wrap(Ui.label(Lang.t("用浏览器打开微软设备登录页，输入下面的代码完成微软账号登录，")
                + Lang.t("授权后启动器会自动继续。"), 13, Theme.gray2), wrapWidth);
        hint.setLineSpacing(3);

        Label code = Ui.bold("获取中…", 24, Theme.accentDark);
        Label copyHint = Ui.label("点击复制代码", 12, Theme.gray2);
        copyHint.setOpacity(0.7);
        VBox codeBox = new VBox(2, code, copyHint);
        codeBox.setAlignment(Pos.CENTER_LEFT);
        codeBox.setPadding(new Insets(10, 16, 10, 16));
        codeBox.setBackground(Ui.fill(Theme.fieldBg, 6));
        codeBox.setBorder(Ui.border(Theme.accentPale2, 6, 1));
        codeBox.setCursor(javafx.scene.Cursor.HAND);

        Label status = Ui.wrap(Ui.label("正在获取登录代码…", 12, Theme.gray2), wrapWidth);
        Label error = Ui.wrap(Ui.label("", 12, Theme.red), wrapWidth);
        error.setVisible(false);
        error.setManaged(false);

        final String[] verification = {"https://www.microsoft.com/link"};
        final String[] userCode = {""};
        codeBox.setOnMouseClicked(e -> {
            if (userCode[0].isEmpty()) return;
            ClipboardContent c = new ClipboardContent();
            c.putString(userCode[0]);
            Clipboard.getSystemClipboard().setContent(c);
            win.toast("已复制登录代码", userCode[0], 1);
        });

        StackPane open = Ui.button("打开登录页", Ui.BTN_THEME, () -> {
            String url = verification[0];
            if ("https://www.microsoft.com/link".equals(url) && !userCode[0].isEmpty()) {
                url += "?otc=" + userCode[0];
            }
            MsAuth.openBrowser(url);
        });
        open.setDisable(true);
        StackPane retry = Ui.button("重新获取", Ui.BTN_NORMAL, null);
        retry.setDisable(true);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("取消", Ui.BTN_NORMAL, win::closeModal), retry, open);

        form.getChildren().addAll(hint, codeBox, status, error, actions);
        card.getChildren().add(form);
        win.showModal(card);

        final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Thread[] worker = {null};
        Runnable start = () -> {
            cancelled.set(false);
            if (worker[0] != null) worker[0].interrupt();
            code.setText(Lang.t("获取中…"));
            status.setText(Lang.t("正在获取登录代码…"));
            error.setVisible(false);
            error.setManaged(false);
            userCode[0] = "";
            retry.setDisable(true);
            open.setDisable(true);
            Thread t = new Thread(() -> {
                try {
                    MsAuth.DeviceCode dc = MsAuth.requestDeviceCode();
                    javafx.application.Platform.runLater(() -> {
                        if (cancelled.get()) return;
                        verification[0] = dc.verificationUri;
                        userCode[0] = dc.userCode;
                        code.setText(dc.userCode);
                        open.setDisable(false);
                        status.setText(Lang.tf("等待浏览器授权，每 %d 秒自动检查一次…", dc.interval));
                    });
                    MsAuth.Result r = MsAuth.login(dc, cancelled::get, text ->
                            javafx.application.Platform.runLater(() -> {
                                if (!cancelled.get()) status.setText(Lang.t(text));
                            }));
                    String skin = MsAuth.saveSkin(r);
                    javafx.application.Platform.runLater(() -> {
                        if (!cancelled.get()) applyMsAccount(win, d, r, skin);
                    });
                } catch (MsAuth.CancelledException ignored) {
                } catch (Exception ex) {
                    if (cancelled.get()) return;
                    javafx.application.Platform.runLater(() -> {
                        status.setText(Lang.t("登录失败"));
                        code.setText(Lang.t("获取失败"));
                        String msg = ex.getMessage() == null ? "未知错误" : ex.getMessage();
                        error.setText(Lang.t(msg));
                        error.setVisible(true);
                        error.setManaged(true);
                        retry.setDisable(false);
                        open.setDisable(true);
                    });
                    d.log("微软登录失败：" + Log.describe(ex));
                }
            }, "TCL-MSA");
            t.setDaemon(true);
            worker[0] = t;
            t.start();
        };
        retry.setOnMouseClicked(e -> start.run());
        win.setModalCloseHook(() -> {
            cancelled.set(true);
            if (worker[0] != null) worker[0].interrupt();
        });
        start.run();
    }

    private static void applyMsAccount(MainWindow win, Data d, MsAuth.Result r, String skin) {
        String name = r.name.isEmpty() ? "Player" : r.name;
        String key = normUuid(r.uuid);
        Data.Account target = null;
        for (Data.Account a : d.accounts) {
            if ((!key.isEmpty() && key.equals(normUuid(a.uuid))) || a.name.equalsIgnoreCase(name)) {
                target = a;
                break;
            }
        }
        if (target == null) {
            target = new Data.Account(name, r.uuid, skin.isEmpty() ? "Steve.png" : skin, true);
            d.accounts.add(target);
        }
        target.name = name;
        if (!r.uuid.isEmpty()) target.uuid = r.uuid;
        target.accessToken = r.accessToken;
        if (!r.refreshToken.isEmpty()) target.refreshToken = r.refreshToken;
        target.expiresAt = r.expiresAt;
        target.userType = "msa";
        target.online = true;
        if (!skin.isEmpty()) target.skin = skin;
        d.selectedAccount = d.accounts.indexOf(target);
        d.save();
        win.refreshAccountBadge();
        win.closeModal();
        // 正版登录不解锁主题
        win.openPage(win.currentKey, false);
        win.toast("登录成功", name, 1);
    }

    private static String normUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static void yggdrasilCard(MainWindow win) {
        VBox card = msgCard("登录第三方账户");
        final Data d = Data.get();
        VBox form = new VBox(8);
        form.setPadding(new Insets(15, 7, 0, 7));
        form.getChildren().add(Ui.hint("皮肤站 API 地址，一般以 /api/yggdrasil 结尾（如 LittleSkin）。", 0));
        TextField server = Ui.field("认证服务器，例如 https://littleskin.cn/api/yggdrasil");
        server.setText("https://littleskin.cn/api/yggdrasil");
        server.setPrefHeight(32);
        TextField email = Ui.field("用户名");
        email.setPrefHeight(32);
        PasswordField password = new PasswordField();
        password.setPromptText("密码");
        password.setFont(Theme.font(13));
        password.setPrefHeight(32);
        final StackPane[] lRef = new StackPane[1];
        Runnable submitAction = () -> {
            String srv = (server.getText() == null ? "" : server.getText().trim());
            String usr = (email.getText() == null ? "" : email.getText().trim());
            String pwd = password.getText();
            if (srv.isEmpty() || usr.isEmpty()) {
                win.toast("请完整填写", "服务器地址与用户名不能为空。", 2);
                return;
            }
            if (lRef[0] == null || lRef[0].isDisabled()) return;
            lRef[0].setDisable(true);
            Thread worker = new Thread(() -> {
                try {
                    Yggdrasil.Server auth = Yggdrasil.locate(srv);
                    Yggdrasil.Session session = Yggdrasil.authenticate(auth, usr, pwd,
                            java.util.UUID.randomUUID().toString());
                    Yggdrasil.Profile profile = session.selected;
                    if (profile == null) {
                        if (session.available.isEmpty()) {
                            throw new Yggdrasil.AuthException(0, "这个账号还没有游戏角色，请先在皮肤站创建角色");
                        }
                        profile = session.available.size() == 1
                                ? session.available.get(0) : chooseProfile(win, session.available);
                        if (profile == null) {
                            javafx.application.Platform.runLater(() -> lRef[0].setDisable(false));
                            return;
                        }
                        Yggdrasil.Session picked = Yggdrasil.refresh(auth, session.accessToken,
                                session.clientToken, profile);
                        session = picked;
                        if (picked.selected != null) profile = picked.selected;
                    }
                    String uuid = formatUuid(profile.id);
                    final String fname = profile.name;
                    final String fuuid = uuid.isEmpty() ? Data.offlineUuid(profile.name) : uuid;
                    final String ftoken = session.accessToken;
                    final String fclient = session.clientToken;
                    final String fserver = auth.apiRoot;
                    final String fsite = auth.display();
                    javafx.application.Platform.runLater(() -> {
                        Data.Account a = new Data.Account(fname, fuuid, selectedSkin, true);
                        a.accessToken = ftoken;
                        a.clientToken = fclient;
                        a.userType = "legacy";
                        a.server = fserver;
                        d.accounts.add(a);
                        d.selectedAccount = d.accounts.size() - 1;
                        d.save();
                        win.refreshAccountBadge();
                        win.closeModal();
                        win.openPage(win.currentKey, false);
                        win.toast(Lang.tf("已添加 %s", fsite), fname, 1);
                    });
                } catch (Yggdrasil.AuthException e) {
                    javafx.application.Platform.runLater(() -> {
                        win.toast("登录失败", e.getMessage(), 3);
                        lRef[0].setDisable(false);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> lRef[0].setDisable(false));
                    Errors.error("第三方 Yggdrasil 登录失败", e);
                }
            }, "TCL-Ygg");
            worker.setDaemon(true);
            worker.start();
        };
        StackPane login = Ui.button("登录并添加", Ui.BTN_HIGHLIGHT, submitAction);
        lRef[0] = login;
        server.setOnAction(e -> submitAction.run());
        email.setOnAction(e -> submitAction.run());
        password.setOnAction(e -> submitAction.run());
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(login);
        form.getChildren().addAll(server, email, password, actions);
        card.getChildren().add(form);
        win.showModal(card);
    }

    private static Yggdrasil.Profile chooseProfile(MainWindow win, java.util.List<Yggdrasil.Profile> list)
            throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<Yggdrasil.Profile> picked =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            VBox card = msgCard("选择角色");
            VBox box = new VBox(8);
            box.setPadding(new Insets(15, 7, 0, 7));
            box.getChildren().add(Ui.hint("这个账号有多个角色，选一个用来启动游戏。", 0));
            for (Yggdrasil.Profile p : list) {
                box.getChildren().add(Ui.button(p.name, Ui.BTN_NORMAL, () -> {
                    picked.set(p);
                    win.closeModal();
                    latch.countDown();
                }));
            }
            StackPane cancel = Ui.button("取消", Ui.BTN_NORMAL, () -> {
                win.closeModal();
                latch.countDown();
            });
            HBox actions = new HBox(cancel);
            actions.setAlignment(Pos.CENTER_RIGHT);
            box.getChildren().add(actions);
            card.getChildren().add(box);
            win.showModal(card);
            // 点遮罩、按 ESC 关掉时也要放行后台线程
            win.setModalCloseHook(latch::countDown);
        });
        latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
        return picked.get();
    }

    public static void nideCard(MainWindow win) {
        VBox card = msgCard("登录统一通行证");
        final Data d = Data.get();
        VBox form = new VBox(8);
        form.setPadding(new Insets(15, 7, 0, 7));
        TextField code = Ui.field("服务器码（联系服务器管理员获取）");
        code.setText(d.get("CacheNideServer", ""));
        code.setPrefHeight(32);
        TextField email = Ui.field("用户名");
        email.setPrefHeight(32);
        PasswordField password = new PasswordField();
        password.setPromptText("密码");
        password.setFont(Theme.font(13));
        password.setPrefHeight(32);
        Label link = Ui.label("注册账号", 12, Theme.accentDark);
        link.setCursor(javafx.scene.Cursor.HAND);
        link.setOnMouseClicked(e -> {
            boolean hasName = email.getText() != null && !email.getText().trim().isEmpty();
            if (!hasName) {
                String c = normalizeNideCode(code.getText());
                if (c.isEmpty()) {
                    win.toast("需要服务器码", "注册页地址由服务器码决定，请先填写服务器码（向服务器管理员索取）。", 3);
                    return;
                }
                Shell.openUrl("https://login.mc-user.com:233/" + c + "/register");
            } else {
                Shell.openUrl("https://login.mc-user.com:233/account/login");
            }
        });
        HBox linkRow = new HBox();
        linkRow.setAlignment(Pos.CENTER_LEFT);
        linkRow.getChildren().addAll(Ui.label("没有账号？", 12, Theme.gray2), link);
        final StackPane[] lRef = new StackPane[1];
        StackPane login = Ui.button("登录并添加", Ui.BTN_HIGHLIGHT, () -> {
            String c = normalizeNideCode(code.getText());
            String usr = (email.getText() == null ? "" : email.getText().trim());
            String pwd = password.getText() == null ? "" : password.getText().trim();
            if (c.isEmpty() || usr.isEmpty() || pwd.isEmpty()) {
                win.toast("请完整填写", "服务器码、用户名与密码均不能为空。", 2);
                return;
            }
            lRef[0].setDisable(true);
            Thread worker = new Thread(() -> {
                try {
                    Yggdrasil.Server auth = new Yggdrasil.Server(
                            "https://auth.mc-user.com:233/" + c + "/", "统一通行证");
                    Yggdrasil.Session session = Yggdrasil.authenticate(auth, usr, pwd,
                            java.util.UUID.randomUUID().toString());
                    Yggdrasil.Profile profile = session.selected;
                    if (profile == null && !session.available.isEmpty()) {
                        profile = session.available.get(0);
                        session = Yggdrasil.refresh(auth, session.accessToken, session.clientToken, profile);
                        if (session.selected != null) profile = session.selected;
                    }
                    final String fname = profile == null ? usr : profile.name;
                    String uuid = profile == null ? "" : formatUuid(profile.id);
                    final String fuuid = uuid.isEmpty() ? Data.offlineUuid(fname) : uuid;
                    final String ftoken = session.accessToken;
                    final String fclient = session.clientToken;
                    javafx.application.Platform.runLater(() -> {
                        Data.Account a = new Data.Account(fname, fuuid, selectedSkin, true);
                        a.accessToken = ftoken;
                        a.clientToken = fclient;
                        a.userType = "nide";
                        a.server = c;
                        d.accounts.add(a);
                        d.selectedAccount = d.accounts.size() - 1;
                        // 记住服务器码，否则注册链接拼不出来
                        if (!c.isEmpty()) d.set("CacheNideServer", c);
                        d.save();
                        win.refreshAccountBadge();
                        win.closeModal();
                        win.openPage(win.currentKey, false);
                        win.toast("已添加统一通行证账户", fname, 1);
                    });
                } catch (Yggdrasil.AuthException e) {
                    javafx.application.Platform.runLater(() -> {
                        win.toast("登录失败", e.getMessage(), 3);
                        lRef[0].setDisable(false);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> lRef[0].setDisable(false));
                    Errors.error("统一通行证登录失败", e);
                }
            }, "TCL-Nide-Login");
            worker.setDaemon(true);
            worker.start();
        });
        lRef[0] = login;
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(login);
        form.getChildren().addAll(code, email, password, linkRow, actions);
        card.getChildren().add(form);
        win.showModal(card);
    }

    private static String normalizeNideCode(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "";
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            int slash = s.indexOf('/', scheme + 3);
            s = slash < 0 ? "" : s.substring(slash + 1);
        }
        int hostSlash = s.indexOf('/');
        if (hostSlash > 0 && s.substring(0, hostSlash).contains(":")) s = s.substring(hostSlash + 1);
        while (s.startsWith("/")) s = s.substring(1);
        int cut = s.indexOf('/');
        if (cut >= 0) s = s.substring(0, cut);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return s.trim();
    }

    private static String importLocalSkin(String playerName, java.nio.file.Path source) throws Exception {
        if (!java.nio.file.Files.isRegularFile(source)) throw new Exception("皮肤文件不存在");
        javafx.scene.image.Image img = new javafx.scene.image.Image(source.toUri().toString());
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        if (img.isError() || w == 0 || w % 64 != 0 || (h != w / 2 && h != w)) {
            throw new Exception("皮肤图片尺寸无效（" + w + "×" + h + "，应为 64×32、64×64 或它们的整数倍）");
        }
        String safe = playerName == null || playerName.isEmpty() ? "skin" : playerName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        java.nio.file.Path dir = Theme.skinDir();
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path target = dir.resolve(safe + ".png");
        java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return "Skins/" + safe + ".png";
    }

    public static void skinPicker(MainWindow win, Data.Account account) {
        boolean readonly = account.online;
        VBox card = msgCard(Lang.t(readonly ? "查看皮肤" : "编辑皮肤") + " - " + account.name);
        card.setPrefWidth(580);
        card.setMinWidth(580);
        VBox body = new VBox(10);
        body.setPadding(new Insets(15, 7, 0, 7));

        final int[] type = {0};
        final String[] pName = {readonly ? account.name : ""};
        final String[] pApi = {""};
        final String[] skinRes = {account.skin == null ? "" : account.skin};
        final int[] ticket = {0};

        Skin3D viewer = new Skin3D(196, 240);
        if (!skinRes[0].isEmpty()) viewer.setSkinFile(skinRes[0]);
        StackPane previewBack = new StackPane(viewer);
        previewBack.setAlignment(Pos.CENTER);
        previewBack.setPrefSize(216, 240);
        previewBack.setMinSize(216, 240);
        previewBack.setMaxSize(216, 240);
        previewBack.setBackground(Ui.fill(Theme.brush7, 5));
        previewBack.setPadding(new Insets(0, 0, 0, 0));

        TextField nameField = Ui.field(readonly ? "玩家名" : "玩家名 / 角色名");
        TextField apiField = Ui.field("皮肤站地址，例如 https://littleskin.cn/csl");
        hide(apiField);
        StackPane fetchBtn = Ui.button("获取皮肤", Ui.BTN_THEME, () -> fetchSource(win, type, pName, pApi, nameField, apiField,
                skinRes, ticket, viewer, readonly, account));
        VBox inputArea = new VBox(6);
        inputArea.setPadding(new Insets(8, 10, 10, 10));
        inputArea.setBackground(Ui.fill(Theme.fieldBg, 4));
        inputArea.getChildren().addAll(nameField, apiField, fetchBtn);
        hide(inputArea);

        String[] srcIcon = {"user", "user", "user", "globe", "image", "cloud-download", "server"};
        String[] srcTitle = {"默认", "Steve", "Alex", "正版玩家皮肤", "本地皮肤图片文件", "LittleSkin 皮肤站", "自定义皮肤站"};
        String[] srcInfo = {
                "根据玩家名自动选择默认皮肤", "经典 Steve 皮肤", "细手臂 Alex 皮肤",
                "输入一个正版 ID，套用它当前使用的皮肤", "从电脑中选择一张 64×32 或 64×64 的皮肤图片",
                "输入 LittleSkin 上的角色名，直接拉取该角色皮肤", "填写 Blessing Skin 等站点的 CSL 地址与角色名"};
        Ui.ListItem[] items = new Ui.ListItem[srcTitle.length];
        VBox list = new VBox(0);
        list.setMinWidth(300);
        for (int i = 0; i < items.length; i++) {
            final int t = i;
            items[i] = new Ui.ListItem(Theme.icon(srcIcon[t], 17, Theme.text), srcTitle[t], srcInfo[t], 36);
            items[i].setOnMouseClicked(e ->
                    selectSource(t, type, items, inputArea, nameField, apiField, skinRes, viewer, account, readonly, win.stage));
            list.getChildren().add(items[i]);
        }
        VBox right = new VBox(8);
        right.setMinWidth(300);
        right.getChildren().addAll(list, inputArea);

        HBox main = new HBox(12);
        main.setAlignment(Pos.TOP_LEFT);
        main.getChildren().addAll(previewBack, right);

        Label hint = Ui.label(readonly
                ? "拖动模型可旋转查看；此窗口只做预览，不会修改皮肤。"
                : "拖动模型可旋转查看，选好来源后点「确定」生效。", 13, Theme.gray2);
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (readonly) {
            actions.getChildren().add(Ui.button("关闭", Ui.BTN_NORMAL, () -> {
                viewer.dispose();
                win.closeModal();
            }));
        } else {
            actions.getChildren().addAll(
                    Ui.button("取消", Ui.BTN_NORMAL, () -> {
                        viewer.dispose();
                        win.closeModal();
                    }),
                    Ui.button("确定", Ui.BTN_HIGHLIGHT, () -> {
                        if (skinRes[0].isEmpty()) {
                            Errors.error("还没有可用的皮肤预览",
                                    new IllegalStateException("先选择一个来源并获取成功后，再点「确定」。"),
                                    Errors.Behavior.ALERT);
                            return;
                        }
                        account.skin = skinRes[0];
                        Data.get().save();
                        win.refreshAccountBadge();
                        viewer.dispose();
                        win.closeModal();
                        // 静默重建，不闪换页动画
                        win.openPage(win.currentKey, false);
                        win.toast("皮肤已更换", account.name + " · " + account.skin, 1);
                    }));
        }
        HBox bottom = new HBox(12);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hint, Priority.ALWAYS);
        hint.setMaxWidth(Double.MAX_VALUE);
        bottom.getChildren().addAll(hint, actions);

        body.getChildren().addAll(main, bottom);
        card.getChildren().add(body);

        type[0] = sourceOf(account);
        for (int i = 0; i < items.length; i++) items[i].setSelected(i == type[0]);

        // ESC / 点蒙层 / 被链式弹窗顶掉时也要停掉 3D 预览的自转
        win.setModalCloseHook(viewer::dispose);
        win.showModal(card);
    }

    private static int sourceOf(Data.Account account) {
        String s = account.skin == null ? "" : account.skin;
        if (s.startsWith("Skins/")) return 4;
        if (s.startsWith("Steve")) return 1;
        if (s.startsWith("Alex")) return 2;
        return 0;
    }

    private static void hide(Node n) {
        n.setVisible(false);
        n.setManaged(false);
    }

    private static void show(Node n) {
        n.setVisible(true);
        n.setManaged(true);
    }

    private static void selectSource(int t, int[] type, Ui.ListItem[] items, VBox inputArea,
                                    javafx.scene.control.TextField nameField,
                                    javafx.scene.control.TextField apiField,
                                    String[] skinRes, Skin3D viewer, Data.Account account,
                                    boolean readonly, javafx.stage.Stage stage) {
        int prev = type[0]; // 取消选文件时回退用
        type[0] = t;
        for (int i = 0; i < items.length; i++) items[i].setSelected(i == t);
        switch (t) {
            case 0 -> {
                hide(inputArea);
                String g = defaultGender(account.uuid);
                applyBuiltin(g, skinRes, viewer);
            }
            case 1 -> {
                hide(inputArea);
                applyBuiltin("Steve", skinRes, viewer);
            }
            case 2 -> {
                hide(inputArea);
                applyBuiltin("Alex", skinRes, viewer);
            }
            case 4 -> {
                hide(inputArea);
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("选择皮肤图片");
                chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("皮肤图片文件", "*.png"));
                java.io.File file = chooser.showOpenDialog(stage);
                if (file == null) {
                    type[0] = prev;
                    for (int i = 0; i < items.length; i++) items[i].setSelected(i == type[0]);
                    return;
                }
                try {
                    String target = importLocalSkin(account.name, file.toPath());
                    skinRes[0] = target;
                    viewer.setSkinFile(target);
                } catch (Exception ex) {
                    Errors.error("导入本地皮肤失败", ex);
                }
            }
            case 3, 5, 6 -> {
                if (t == 6) show(apiField); else hide(apiField);
                show(inputArea);
            }
            default -> {
            }
        }
    }

    private static void applyBuiltin(String which, String[] skinRes, Skin3D viewer) {
        String res = which + ".png";
        skinRes[0] = res;
        viewer.setSkinFile(res);
    }

    private static void fetchSource(MainWindow win, int[] type, String[] pName, String[] pApi,
                                    javafx.scene.control.TextField nameField,
                                    javafx.scene.control.TextField apiField,
                                    String[] skinRes, int[] ticket, Skin3D viewer,
                                    boolean readonly, Data.Account account) {
        String who = nameField.getText() == null ? "" : nameField.getText().trim();
        if (who.isEmpty()) {
            Errors.error("缺少玩家名",
                    new IllegalArgumentException("请先填写玩家名 / 角色名再获取皮肤。"),
                    Errors.Behavior.ALERT);
            return;
        }
        pName[0] = who;
        String api = apiField.getText() == null ? "" : apiField.getText().trim();
        pApi[0] = type[0] == 5 ? "https://littleskin.cn" : api;
        int t = type[0];
        int my = ++ticket[0];
        Thread worker = new Thread(() -> {
            String target;
            try {
                if (t == 3) {
                    target = fetchMojangSkin(who, account.name);
                } else {
                    target = fetchCslSkin(t == 5 ? "https://littleskin.cn" : api, who, account.name);
                }
            } catch (Exception e) {
                Data.get().log("获取皮肤失败：" + Log.describe(e));
                javafx.application.Platform.runLater(() -> {
                    if (my != ticket[0]) return;
                    Errors.error("获取皮肤失败", e, Errors.Behavior.ALERT_FEEDBACK);
                });
                return;
            }
            javafx.application.Platform.runLater(() -> {
                if (my != ticket[0]) return;
                skinRes[0] = target;
                viewer.setSkinFile(target);
                win.toast("已获取皮肤", who, 1);
            });
        }, "TCL-SkinFetch");
        worker.setDaemon(true);
        worker.start();
    }

    private static String fetchMojangSkin(String nameOrUuid, String accountName) throws Exception {
        String id = nameOrUuid.trim();
        String uuid;
        if (id.length() == 32) {
            try {
                Integer.parseInt(id, 16);
                uuid = id.toLowerCase();
            } catch (NumberFormatException e) {
                uuid = mojangUuidFromName(id);
            }
        } else {
            uuid = mojangUuidFromName(id);
        }
        String profile = httpGetString("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        String skinUrl = parseMojangSkinUrl(profile);
        if (skinUrl.isEmpty()) throw new Exception("没取到皮肤地址，该玩家可能用的是默认皮肤");
        if (skinUrl.contains("http://")) skinUrl = skinUrl.replace("http://", "https://");
        return saveFetchedSkin(skinUrl, accountName);
    }

    private static String mojangUuidFromName(String name) throws Exception {
        String j;
        try {
            j = httpGetString("https://api.mojang.com/users/profiles/minecraft/"
                    + java.net.URLEncoder.encode(name, "UTF-8"));
        } catch (Exception e) {
            // 不存在的玩家返回 204，Http 会抛异常
            throw new Exception("查不到这个正版玩家：" + name + "（ID 不存在或网络不通）");
        }
        String id = Json.str(Json.obj(Json.parse(j)), "id", "");
        if (id.isEmpty()) throw new Exception("查不到这个正版玩家：" + name);
        return id;
    }

    private static String parseMojangSkinUrl(String profileJson) {
        try {
            List<Object> props = Json.arr(Json.parse(profileJson), "properties");
            for (Object o : props) {
                Map<String, Object> p = Json.obj(o);
                if (!"textures".equals(Json.str(p, "name", ""))) continue;
                String b64 = Json.str(p, "value", "");
                if (b64.isEmpty()) continue;
                String decoded = new String(java.util.Base64.getDecoder().decode(b64),
                        java.nio.charset.StandardCharsets.UTF_8);
                Map<String, Object> tx = Json.obj(Json.obj(Json.parse(decoded), "textures"));
                String url = Json.str(Json.obj(tx, "SKIN"), "url", "");
                if (url.isEmpty()) url = Json.str(Json.obj(tx, "skin"), "url", "");
                if (!url.isEmpty()) return url;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String fetchCslSkin(String base, String who, String accountName) throws Exception {
        String site = base == null ? "" : base.trim();
        if (site.isEmpty()) site = "https://littleskin.cn";
        while (site.endsWith("/")) site = site.substring(0, site.length() - 1);
        String cslBase = site.toLowerCase().endsWith("/csl") ? site : site + "/csl";
        String root = cslBase.substring(0, cslBase.length() - 4);
        String csl = httpGetString(cslBase + "/" + java.net.URLEncoder.encode(who, "UTF-8") + ".json");
        Map<String, Object> json = Json.obj(Json.parse(csl));
        if (json.isEmpty()) throw new Exception("皮肤站上没有这个角色：" + who);
        String url = Json.str(cslSkinNode(json), "url", "");
        if (url.isEmpty()) {
            // 新版只给 hash，地址是 站点根/textures/hash
            String hash = Json.str(Json.obj(json, "skins"), "default", "");
            if (hash.isEmpty()) hash = Json.str(cslSkinNode(json), "hash", "");
            if (!hash.isEmpty()) url = root + "/textures/" + hash;
        }
        if (url.isEmpty()) throw new Exception("皮肤站返回里没有皮肤地址，这个角色可能还没上传皮肤");
        return saveFetchedSkin(url, accountName);
    }

    private static Map<String, Object> cslSkinNode(Map<String, Object> root) {
        Map<String, Object> textures = Json.obj(root, "textures");
        for (String k : new String[]{"SKIN", "skin"}) {
            Map<String, Object> node = Json.obj(textures, k);
            if (!node.isEmpty()) return node;
        }
        for (String k : new String[]{"skin", "SKIN"}) {
            Map<String, Object> node = Json.obj(root, k);
            if (!node.isEmpty()) return node;
        }
        return new java.util.HashMap<>();
    }

    private static String saveFetchedSkin(String skinUrl, String accountName) throws Exception {
        byte[] data = httpGetBytes(skinUrl);
        if (data == null || data.length < 8 || data[0] != (byte) 0x89 || data[1] != 0x50) {
            throw new Exception("皮肤地址返回的不是 PNG 图片");
        }
        String safe = accountName == null || accountName.isEmpty() ? "skin" : accountName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        java.nio.file.Path dir = Theme.skinDir();
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path target = dir.resolve(safe + ".png");
        java.nio.file.Files.write(target, data);
        return "Skins/" + safe + ".png";
    }

    private static byte[] httpGetBytes(String url) throws Exception {
        // 走共享连接池，偶发 5xx 自动重试
        return Http.getBytes(url);
    }

    private static String httpGetString(String url) throws Exception {
        return new String(httpGetBytes(url), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String defaultGender(String uuidWithDash) {
        String u = uuidWithDash == null ? "" : uuidWithDash.replace("-", "");
        if (u.length() != 32) return "Steve";
        try {
            int a = Integer.parseInt(u.substring(7, 8), 16);
            int b = Integer.parseInt(u.substring(15, 16), 16);
            int c = Integer.parseInt(u.substring(23, 24), 16);
            int d = Integer.parseInt(u.substring(31, 32), 16);
            return ((a ^ b ^ c ^ d) % 2) == 1 ? "Alex" : "Steve";
        } catch (Exception e) {
            return "Steve";
        }
    }

    private static String selectedSkin = "Steve.png";

    private static String formatUuid(String u) {
        String s = (u == null ? "" : u.replace("-", ""));
        if (s.length() != 32) return u == null ? "" : u;
        return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                + "-" + s.substring(16, 20) + "-" + s.substring(20);
    }

    // 与「检查更新」共用同一套弹窗卡片样式
    public static void colorTag(MainWindow win, Data.Instance ins, Runnable onChanged) {
        win.showModal(buildColorCard(win, ins, onChanged), true);
    }

    static VBox buildColorCard(MainWindow win, Data.Instance ins, Runnable onChanged) {
        VBox card = msgCard("颜色标签");
        card.setPrefWidth(430);
        card.setMinWidth(400);

        VBox body = new VBox(12);
        body.setPadding(new Insets(14, 7, 0, 7));

        Label hint = Ui.label("给这个实例选一个颜色标记，列表里会显示一根竖条。", 13, Theme.gray2);
        hint.setWrapText(true);
        hint.setMaxWidth(380);

        FlowPane wrap = new FlowPane(10, 10);
        wrap.setPrefWrapLength(370);
        String current = ins == null ? "" : InstanceMeta.colorTag(ins);
        for (String[] preset : InstanceMeta.COLOR_TAGS) {
            StackPane dot = new StackPane();
            dot.setPrefSize(34, 34);
            dot.setMinSize(34, 34);
            dot.setMaxSize(34, 34);
            dot.setBackground(Ui.fill(colorOf(preset[1]), 17));
            dot.setCursor(javafx.scene.Cursor.HAND);
            if (!current.isEmpty() && current.equalsIgnoreCase(preset[1])) {
                // 当前标签：白色内圈 + 深色外描边，一眼看出已选中
                Region ring = new Region();
                ring.setPrefSize(16, 16);
                ring.setMinSize(16, 16);
                ring.setMaxSize(16, 16);
                ring.setBackground(Ui.fill(Color.WHITE, 8));
                ring.setMouseTransparent(true);
                dot.getChildren().add(ring);
            }
            Ui.tooltip(dot, preset[0]);
            dot.setOnMouseClicked(e -> {
                if (win != null) win.closeModal();
                InstanceMeta.setColorTag(ins, preset[1]);
                if (onChanged != null) onChanged.run();
                if (win != null) win.toast("已设置颜色标签", preset[0], 1);
            });
            wrap.getChildren().add(dot);
        }
        body.getChildren().addAll(hint, wrap);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(16, 7, 0, 7));
        actions.getChildren().addAll(
                Ui.button("清除标签", Ui.BTN_NORMAL, () -> {
                    if (win != null) win.closeModal();
                    InstanceMeta.setColorTag(ins, "");
                    if (onChanged != null) onChanged.run();
                    if (win != null) win.toast("已清除颜色标签", ins.name, 1);
                }),
                Ui.button("取消", Ui.BTN_HIGHLIGHT, () -> {
                    if (win != null) win.closeModal();
                }));

        card.getChildren().addAll(body, actions);
        return card;
    }

    private static javafx.scene.paint.Color colorOf(String hex) {
        try {
            return javafx.scene.paint.Color.web(hex);
        } catch (Exception e) {
            return Theme.gray3;
        }
    }
}
