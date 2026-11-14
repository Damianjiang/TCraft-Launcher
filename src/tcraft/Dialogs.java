package tcraft;

import java.net.URI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 账户管理、确认框这类通用弹窗。 */
public final class Dialogs {

    private static final String[] SKINS = {"Steve.png", "Alex.png", "Ari.png", "Efe.png", "Kai.png",
            "Makena.png", "Noor.png", "Sunny.png", "Zuri.png"};

    private Dialogs() {
    }

    public static void confirm(MainWindow win, String title, String message, Runnable onOk) {
        confirm(win, title, message, onOk, false);
    }

    /** 警告样式：标题和主按钮变红，罩暗红遮罩。 */
    public static void confirm(MainWindow win, String title, String message, Runnable onOk, boolean warn) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 23, 22));
        card.setPrefWidth(400);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 23, warn ? Theme.red : Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        // 长标题换行，不用省略号
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

    /** 单按钮信息框。 */
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
        // 长标题换行，不用省略号
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

    /** 带输入框的弹窗：确定回调非空文本，取消回调 null。 */
    public static void input(MainWindow win, String title, String hint, String defaultValue,
                             java.util.function.Consumer<String> onOk) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 23, 22));
        card.setPrefWidth(430);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 23, Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        Region line = Ui.line(Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        TextField field = new TextField(defaultValue == null ? "" : defaultValue);
        field.setFont(Theme.font(13));
        field.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        field.setPromptText(hint == null ? "" : hint);
        HBox fieldBox = new HBox(field);
        fieldBox.setAlignment(Pos.CENTER_LEFT);
        fieldBox.setPadding(new Insets(8, 10, 8, 10));
        fieldBox.setBackground(Ui.fill(Theme.fieldBg, 3));
        VBox.setMargin(fieldBox, new Insets(13, 0, 17, 0));
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("确定", Ui.BTN_HIGHLIGHT, () -> {
                    String value = field.getText();
                    win.closeModal();
                    onOk.accept(value == null || value.trim().isEmpty() ? null : value.trim());
                }),
                Ui.button("取消", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().addAll(t, line, fieldBox, actions);
        win.showModal(card);
    }

    public static void accountManager(MainWindow win) {
        win.openPage("account", true);
    }

    /** 弹窗统一骨架。 */
    public static VBox msgCard(String title) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 18, 22));
        card.setPrefWidth(410);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(20, 4, 0.8, Theme.text));
        Label t = Ui.semi(title, 23, Theme.accentDark);
        t.setPadding(new Insets(0, 70, 9, 7));
        Region line = Ui.line(Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().addAll(t, line);
        return card;
    }

    /** 选择账户类型后进对应登录流程。 */
    public static void addAccount(MainWindow win, int initialMode) {
        VBox card = msgCard("添加账户");

        VBox rows = new VBox(2);
        rows.setPadding(new Insets(15, 0, 6, 0));
        String[][] items = {
                {"badge-check", "微软账号", "使用微软账号登录，支持正版皮肤与跨设备同步"},
                {"user", "离线账号", "无需正版账号，输入玩家名即可直接游玩"},
                {"globe", "LittleSkin / 第三方 Yggdrasil", "使用 LittleSkin 账号登录并加载其皮肤，第三方 Yggdrasil 外置登录"},
                {"key-round", "统一通行证", "使用服务器码登录第三方服务器（对照 PageLoginNide）"},
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

    /** 离线账户：UUID 按用户名生成，名称不合规只警告。 */
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
        // TextField 没有 maxLength，用 TextFormatter 截断
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
                // 名称不合规只警告，允许继续
                confirm(win, "警告", "离线模式账户用户名建议仅使用英文字符、数字以及下划线，"
                        + "且长度不超过 16 个字符。确定要继续吗？", doAdd);
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

    /** 单选弹窗，确定后回传行号。 */
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

    /** MC 命名规则：字母、数字、下划线，不超过 16 字符。 */
    private static boolean isValidMcName(String n) {
        if (n == null || n.isEmpty() || n.length() > 16) return false;
        for (char c : n.toCharArray()) {
            if (c == '_') continue;
            if (c >= 128 || !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    /** 微软登录：粘贴回调地址换令牌。 */
    public static void msLoginCard(MainWindow win) {
        if (!MsAuth.available()) {
            // 开源版没有 client id，只给说明
            VBox card = msgCard("登录微软账号");
            VBox form = new VBox(10);
            form.setPadding(new Insets(15, 7, 0, 7));
            form.getChildren().add(Ui.hint("这个版本没有内置微软登录凭据，开源版去掉了正版登录。"
                    + "要用正版登录，请自行构建完整版并注入自己的 client id，构建说明见 docs/构建与发布.md。", 0));
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
        form.getChildren().add(Ui.hint("点「打开登录页」在浏览器里登录，登录完成后会自动回到启动器。"
                + "如果浏览器停在别的页面没自动跳，把地址栏整条链接复制粘到下面，点「完成登录」。", 0));

        TextField pasted = Ui.field("粘贴登录后浏览器地址栏的整条链接");
        pasted.setPrefHeight(32);
        Label status = Ui.wrap(Ui.label("尚未开始", 12, Theme.gray2), 380);

        final StackPane[] btnRef = new StackPane[1];
        // 本机回环会话：自动接 code 用；重开或取消时都要关掉
        final MsAuth.Callback[] callbackRef = new MsAuth.Callback[1];
        // 会话对应的 PKCE verifier 和 redirect；手动兜底流程为空 + 固定回调
        final String[] verifierRef = new String[1];
        final String[] redirectRef = new String[1];
        redirectRef[0] = MsAuth.redirectUri();

        Runnable closeCallback = () -> {
            if (callbackRef[0] != null) {
                callbackRef[0].close();
                callbackRef[0] = null;
            }
        };

        StackPane open = Ui.button("打开登录页", Ui.BTN_THEME, () -> {
            closeCallback.run();
            try {
                // HMCL 同款：起本机监听，浏览器登录完跳回 localhost，自动拿 code
                MsAuth.Callback cb = MsAuth.Callback.start();
                callbackRef[0] = cb;
                verifierRef[0] = cb.verifier();
                redirectRef[0] = cb.redirectUri();
                MsAuth.openBrowser(cb.authorizeUrl());
                status.setText("浏览器已打开，登录完成后会自动跳回启动器。");
                Thread waiter = new Thread(() -> {
                    try {
                        String code = cb.awaitCode(5 * 60 * 1000);
                        String verifier = verifierRef[0];
                        String redirect = redirectRef[0];
                        javafx.application.Platform.runLater(() -> {
                            if (callbackRef[0] != cb) return; // 会话被重开顶掉
                            btnRef[0].setDisable(true);
                            status.setText("已收到登录回调，正在换取令牌…");
                            completeMsLogin(win, d, code, redirect, verifier, status, btnRef);
                        });
                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> {
                            if (callbackRef[0] != cb) return;
                            status.setText("自动登录没有完成：" + e.getMessage()
                                    + " 也可以按老办法手动粘贴链接。");
                        });
                    } finally {
                        cb.close();
                        if (callbackRef[0] == cb) callbackRef[0] = null;
                    }
                }, "TCL-MSA-Wait");
                waiter.setDaemon(true);
                waiter.start();
            } catch (Exception e) {
                // 监听起不来（防火墙、端口策略），退回手动粘贴流程
                verifierRef[0] = null;
                redirectRef[0] = MsAuth.redirectUri();
                MsAuth.openBrowser(MsAuth.authorizeUrl());
                status.setText("本机回调监听不可用（可能被防火墙拦截）。请按老办法："
                        + "登录后把地址栏整条链接粘到下面。");
                Errors.error("微软登录本机回调监听失败", e);
            }
        });
        StackPane done = Ui.button("完成登录", Ui.BTN_HIGHLIGHT, () -> {
            String code = MsAuth.parseCode(pasted.getText());
            if (code.isEmpty() || code.contains("error=")) {
                status.setText("链接里没有 code。登录可能没走完，重新点「打开登录页」再试。");
                return;
            }
            btnRef[0].setDisable(true);
            status.setText("正在换取令牌…");
            completeMsLogin(win, d, code, redirectRef[0], verifierRef[0], status, btnRef);
        });
        btnRef[0] = done;

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("取消", Ui.BTN_NORMAL, () -> {
                    closeCallback.run();
                    win.closeModal();
                }), open, done);
        form.getChildren().addAll(pasted, status, actions);
        card.getChildren().add(form);
        win.showModal(card);
    }

    /** 微软登录后半段：换令牌、写账户、刷新界面。回环自动回跳和手动粘贴共用。 */
    private static void completeMsLogin(MainWindow win, Data d, String code, String redirect,
                                        String verifier, Label status, StackPane[] btnRef) {
        Thread worker = new Thread(() -> {
            try {
                MsAuth.Result r = MsAuth.loginWithCode(code, redirect, verifier);
                String skin = MsAuth.saveSkin(r, d.gameDir);
                javafx.application.Platform.runLater(() -> {
                    String name = r.name.isEmpty() ? "Player" : r.name;
                    Data.Account target = null;
                    for (Data.Account a : d.accounts) {
                        if (a.name.equalsIgnoreCase(name)) {
                            target = a;
                            break;
                        }
                    }
                    if (target == null) {
                        target = new Data.Account(name, r.uuid,
                                skin.isEmpty() ? "Steve.png" : skin, true);
                        d.accounts.add(target);
                    }
                    target.uuid = r.uuid.isEmpty() ? target.uuid : r.uuid;
                    target.accessToken = r.accessToken;
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
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    status.setText(e.getMessage() == null ? "登录失败" : e.getMessage());
                    btnRef[0].setDisable(false);
                });
                Errors.error("微软账号登录失败", e);
            }
        }, "TCL-MSA");
        worker.setDaemon(true);
        worker.start();
    }

    /** 第三方 Yggdrasil 登录。 */
    public static void yggdrasilCard(MainWindow win) {
        VBox card = msgCard("登录第三方账户");
        final Data d = Data.get();
        VBox form = new VBox(8);
        form.setPadding(new Insets(15, 7, 0, 7));
        form.getChildren().add(Ui.hint("第三方：通过 Yggdrasil 接口登录（如 LittleSkin / 网易）。", 0));
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
        StackPane login = Ui.button("登录并添加", Ui.BTN_HIGHLIGHT, () -> {
            String srv = (server.getText() == null ? "" : server.getText().trim());
            String usr = (email.getText() == null ? "" : email.getText().trim());
            String pwd = password.getText();
            if (srv.isEmpty() || usr.isEmpty()) {
                win.toast("请完整填写", "服务器地址与用户名不能为空。", 2);
                return;
            }
            lRef[0].setDisable(true);
            Thread worker = new Thread(() -> {
                try {
                    String url = srv.endsWith("/") ? srv + "login" : srv + "/login";
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.getOutputStream().write(("{\"username\":\"" + usr + "\",\"password\":\"" + pwd + "\",\"clientToken\":\"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    int code = c.getResponseCode();
                    if (code != 200) {
                        javafx.application.Platform.runLater(() -> {
                            win.toast("登录失败", "认证服务器返回 HTTP " + code + "。", 2);
                            lRef[0].setDisable(false);
                        });
                        return;
                    }
                    String body = new String(c.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.Map<String, Object> m = Json.obj(Json.parse(body));
                    String token = Json.str(m, "accessToken", "");
                    java.util.Map<String, Object> prof = Json.obj(m.get("selectedProfile"));
                    String uuid = formatUuid(Json.str(prof, "id", Json.str(m, "uuid", "")));
                    String uname = Json.str(prof, "name", Json.str(m, "username", usr));
                    final String fuuid = uuid.isEmpty() ? Data.offlineUuid(uname) : uuid;
                    final String fname = uname;
                    javafx.application.Platform.runLater(() -> {
                        Data.Account a = new Data.Account(fname, fuuid, selectedSkin, true);
                        a.accessToken = token;
                        a.userType = "legacy";
                        d.accounts.add(a);
                        d.selectedAccount = d.accounts.size() - 1;
                        d.save();
                        win.refreshAccountBadge();
                        win.closeModal();
                        win.openPage(win.currentKey, false);
                        win.toast("已添加第三方账户", fname, 1);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> lRef[0].setDisable(false));
                    Errors.error("第三方 Yggdrasil 登录失败", e);
                }
            }, "TCL-Ygg");
            worker.setDaemon(true);
            worker.start();
        });
        lRef[0] = login;
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(login);
        form.getChildren().addAll(server, email, password, actions);
        card.getChildren().add(form);
        win.showModal(card);
    }

    /** 统一通行证登录，走 auth.mc-user.com 的 Yggdrasil。 */
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
            // 填了账号走找回密码，没填走注册
            boolean hasName = email.getText() != null && !email.getText().trim().isEmpty();
            if (!hasName) {
                // 注册页要带服务器码，空码会拼出 404
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
                    String url = "https://auth.mc-user.com:233/" + c + "/authserver/authenticate";
                    String body = "{\"agent\":{\"name\":\"Minecraft\",\"version\":1},\"username\":\""
                            + usr.replace("\"", "") + "\",\"password\":\"" + pwd.replace("\"", "").replace("\\", "\\\\") + "\",\"requestUser\":true}";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(20000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept-Language", "zh-CN");
                    conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    int httpCode = conn.getResponseCode();
                    java.io.InputStream in = httpCode == 200 ? conn.getInputStream() : conn.getErrorStream();
                    String resp = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (httpCode != 200) {
                        // 错误响应里有 errorMessage
                        java.util.Map<String, Object> err = Json.obj(Json.parse(resp));
                        String msg = Json.str(err, "errorMessage", "认证服务器返回 HTTP " + httpCode);
                        final String fmsg = msg;
                        javafx.application.Platform.runLater(() -> {
                            win.toast("登录失败", fmsg, 2);
                            lRef[0].setDisable(false);
                        });
                        return;
                    }
                    java.util.Map<String, Object> m = Json.obj(Json.parse(resp));
                    String token = Json.str(m, "accessToken", "");
                    String client = Json.str(m, "clientToken", "");
                    java.util.Map<String, Object> prof = Json.obj(m.get("selectedProfile"));
                    String uuid = formatUuid(Json.str(prof, "id", ""));
                    String uname = Json.str(prof, "name", usr);
                    final String fuuid = uuid.isEmpty() ? Data.offlineUuid(uname) : uuid;
                    final String fname = uname;
                    final String fclient = client;
                    javafx.application.Platform.runLater(() -> {
                        Data.Account a = new Data.Account(fname, fuuid, selectedSkin, true);
                        a.accessToken = token;
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

    /** 只取路径第一段作为服务器码，兼容整条 URL。 */
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

    /** 校验尺寸后复制到 res/images/Skins，返回相对路径。 */
    private static String importLocalSkin(String playerName, java.nio.file.Path source) throws Exception {
        if (!java.nio.file.Files.isRegularFile(source)) throw new Exception("皮肤文件不存在");
        javafx.scene.image.Image img = new javafx.scene.image.Image(source.toUri().toString());
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        if (img.isError() || w == 0 || w % 64 != 0 || (h != w / 2 && h != w)) {
            throw new Exception("皮肤图片尺寸无效（" + w + "×" + h + "，应为 64×32、64×64 或它们的整数倍）");
        }
        String safe = playerName == null || playerName.isEmpty() ? "skin" : playerName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        java.nio.file.Path dir = Theme.res("images/Skins");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path target = dir.resolve(safe + ".png");
        java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return "Skins/" + safe + ".png";
    }

    /** 皮肤弹窗：选来源先预览，点确定才写入 account.skin。 */
    public static void skinPicker(MainWindow win, Data.Account account) {
        boolean readonly = account.online;
        VBox card = msgCard((readonly ? "查看皮肤" : "编辑皮肤") + " - " + account.name);
        card.setPrefWidth(580);
        card.setMinWidth(580);
        VBox body = new VBox(10);
        body.setPadding(new Insets(15, 7, 0, 7));

        // 选中只改暂存，确定才写入
        final int[] type = {0}; // 0 默认，1 Steve，2 Alex，3 正版，4 本地，5 LittleSkin，6 自定义站
        final String[] pName = {readonly ? account.name : ""};
        final String[] pApi = {""};
        final String[] skinRes = {account.skin == null ? "" : account.skin}; // 当前预览的资源名
        final int[] ticket = {0};

        Skin3D viewer = new Skin3D(196, 240);
        if (!skinRes[0].isEmpty() && Theme.res("images/" + skinRes[0]).toFile().exists()) {
            viewer.setSkinFile(skinRes[0]);
        }
        StackPane previewBack = new StackPane(viewer);
        previewBack.setAlignment(Pos.CENTER);
        previewBack.setPrefSize(216, 240);
        previewBack.setMinSize(216, 240);
        previewBack.setMaxSize(216, 240);
        previewBack.setBackground(Ui.fill(Theme.brush7, 5));
        previewBack.setPadding(new Insets(0, 0, 0, 0));

        // 要在 lambda 之前声明，输入区就地展开
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
                        // 静默重建，避免换页闪动
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

        // 打开时高亮当前生效的来源
        type[0] = sourceOf(account);
        for (int i = 0; i < items.length; i++) items[i].setSelected(i == type[0]);

        win.showModal(card);
    }

    /** 按 account.skin 反推当前生效的来源。 */
    private static int sourceOf(Data.Account account) {
        String s = account.skin == null ? "" : account.skin;
        if (s.startsWith("Skins/")) return 4;
        if (s.startsWith("Steve")) return 1;
        if (s.startsWith("Alex")) return 2;
        return 0;
    }

    /** 隐藏并取消占位，否则弹窗会被撑高。 */
    private static void hide(Node n) {
        n.setVisible(false);
        n.setManaged(false);
    }

    /** 显示并恢复占位。 */
    private static void show(Node n) {
        n.setVisible(true);
        n.setManaged(true);
    }

    /** 选中来源，内置项直接预览。 */
    private static void selectSource(int t, int[] type, Ui.ListItem[] items, VBox inputArea,
                                    javafx.scene.control.TextField nameField,
                                    javafx.scene.control.TextField apiField,
                                    String[] skinRes, Skin3D viewer, Data.Account account,
                                    boolean readonly, javafx.stage.Stage stage) {
        int prev = type[0]; // 取消选文件时回退用
        type[0] = t;
        for (int i = 0; i < items.length; i++) items[i].setSelected(i == t);
        switch (t) {
            case 0 -> { // 按 UUID 判断 Steve 还是 Alex
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
            case 4 -> { // 选完立即预览
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
            case 3, 5, 6 -> { // 需要输入时展开
                if (t == 6) show(apiField); else hide(apiField); // 只有自定义皮肤站要填 CSL 地址
                show(inputArea);
            }
            default -> {
            }
        }
    }

    /** 内置皮肤应用到 3D 预览。 */
    private static void applyBuiltin(String which, String[] skinRes, Skin3D viewer) {
        String res = which + ".png";
        skinRes[0] = res;
        viewer.setSkinFile(res);
    }

    /** 拉取皮肤，成功后同步到 3D 预览。 */
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
        Data.get().log("获取皮肤（type=" + t + " " + who + "）");
        Thread worker = new Thread(() -> {
            String target;
            try {
                if (t == 3) {
                    target = fetchMojangSkin(who, account.name);
                } else {
                    target = fetchCslSkin(t == 5 ? "https://littleskin.cn" : api, who, account.name);
                }
            } catch (Exception e) {
                Data.get().log("获取皮肤失败：" + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    if (my != ticket[0]) return;
                    Errors.error("获取皮肤失败", e, Errors.Behavior.ALERT_FEEDBACK);
                });
                return;
            }
            javafx.application.Platform.runLater(() -> {
                if (my != ticket[0]) return; // 已切走，丢弃过期回写
                skinRes[0] = target;
                viewer.setSkinFile(target);
                win.toast("已获取皮肤", who, 1);
            });
        }, "TCL-SkinFetch");
        worker.setDaemon(true);
        worker.start();
    }

    /** 正版皮肤：查 UUID 后从 sessionserver 下载。 */
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
                // 官方用大写 SKIN，私服可能小写，两个都查
                String url = Json.str(Json.obj(tx, "SKIN"), "url", "");
                if (url.isEmpty()) url = Json.str(Json.obj(tx, "skin"), "url", "");
                if (!url.isEmpty()) return url;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** CSL 接口取皮肤地址或 hash 后下载。 */
    private static String fetchCslSkin(String base, String who, String accountName) throws Exception {
        String site = base == null ? "" : base.trim();
        if (site.isEmpty()) site = "https://littleskin.cn";
        while (site.endsWith("/")) site = site.substring(0, site.length() - 1);
        String cslBase = site.toLowerCase().endsWith("/csl") ? site : site + "/csl";
        // 站点根，纹理地址挂在根上
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

    /** 兼容两种 CSL 皮肤结构。 */
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

    /** 下载皮肤到 res/images/Skins，返回相对路径。 */
    private static String saveFetchedSkin(String skinUrl, String accountName) throws Exception {
        byte[] data = httpGetBytes(skinUrl);
        // 404 页面可能是 HTML，检查 PNG 头
        if (data == null || data.length < 8 || data[0] != (byte) 0x89 || data[1] != 0x50) {
            throw new Exception("皮肤地址返回的不是 PNG 图片");
        }
        String safe = accountName == null || accountName.isEmpty() ? "skin" : accountName;
        for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
        java.nio.file.Path dir = Theme.res("images/Skins");
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

    /** 用 UUID 四个特定位异或，奇数算 Alex。 */
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
}
