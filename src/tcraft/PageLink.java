package tcraft;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.security.SecureRandom;

/** 联机页：选择、加载、完成三种状态。 */
public class PageLink implements Page {

    private static final SecureRandom RANDOM = new SecureRandom();

    private enum State { SELECT, LOADING, FINISH }

    private static State state = State.SELECT;
    private static boolean serverSide;
    private static String inviteCode = "";
    private static String networkName = "";
    private static String networkSecret = "";
    private static int serverPort = 25565;
    private static String lastCode = "";
    private static String loadingStage = "准备联机模块…";
    private static String loadingError = null;
    private static int rpcPort = -1;
    private static String clientAddress = "";
    /** 邀请码版本号，不一致就互不认码。 */
    private static final int INVITE_CODE_VERSION = 2;
    private static double linkPing;
    private static int peopleCount = 1;
    /** 自己的 NAT 类型，10 表示未知。 */
    private static int myNat = 10;
    /** 目标节点，加入方是房主。 */
    private static EasyTier.Peer targetPeer;
    /** 发现节点 ID，-1 表示待选。 */
    private static int discoverNodeId = -1;
    private static String discoverAddress = "";
    /** 完成页连接质量提示。 */
    private static String hintText = "";
    private static int hintType = -1;
    private static VBox hintSlot;
    private static volatile Thread worker;
    /** 加载进度 0~1。 */
    private static final javafx.beans.property.DoubleProperty loadingProgress =
            new javafx.beans.property.SimpleDoubleProperty(0.06);
    /** 加载卡片里的说明文字。 */
    private static Label loadingDescLabel;

    private final MainWindow win;
    private PauseTransition pending;

    public PageLink(MainWindow win) {
        this.win = win;
    }

    @Override
    public Node right() {
        StackPane root = new StackPane();
        root.setPickOnBounds(false);
        root.setAlignment(Pos.CENTER);

        switch (state) {
            case LOADING -> root.getChildren().add(buildLoading());
            case FINISH -> root.getChildren().add(buildFinish());
            default -> root.getChildren().add(buildSelect());
        }

        // 底部水印，悬停变强调色
        Label always = Ui.label("TCraft", 12.5, Theme.text);
        always.setOpacity(0.4);
        always.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED,
                e -> always.setTextFill(Theme.accent));
        always.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED,
                e -> always.setTextFill(Theme.text));
        StackPane.setAlignment(always, Pos.BOTTOM_CENTER);
        StackPane.setMargin(always, new Insets(0, 0, 15, 0));
        root.getChildren().add(always);
        return root;
    }

    @Override
    public void onHide() {
        if (pending != null) pending.stop();
    }

    private Node buildSelect() {
        VBox column = new VBox(14);
        column.setAlignment(Pos.CENTER);
        column.setPickOnBounds(false);

        HBox cards = new HBox(14);
        cards.setAlignment(Pos.CENTER);
        cards.setPrefWidth(660);
        cards.setMinWidth(660);
        cards.setMaxWidth(660);
        cards.setPickOnBounds(false);
        Node create = linkCard("create-room", "创建房间", "邀请朋友加入你的单人游戏。", this::createRoom);
        Node join = linkCard("join-room", "加入房间", "输入你收到的邀请码，加入朋友的房间。", this::joinRoom);
        HBox.setHgrow(create, Priority.ALWAYS);
        HBox.setHgrow(join, Priority.ALWAYS);
        cards.getChildren().addAll(create, join);

        column.getChildren().addAll(cards, hintCard());

        StackPane holder = new StackPane(column);
        holder.setAlignment(Pos.CENTER);
        holder.setPickOnBounds(false);
        return holder;
    }

    private Node linkCard(String icon, String title, String desc, Runnable action) {
        StackPane card = new StackPane();
        card.setMinWidth(300);
        card.setPrefWidth(320);
        card.setMinHeight(190);
        card.setPrefHeight(190);
        card.setBackground(Ui.fill(Theme.cardBg, 16));
        card.setBorder(Ui.border(Color.web("#000000", 0.086), 15, 1));
        card.setCursor(Cursor.HAND);
        card.setPickOnBounds(true);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        VBox content = new VBox(10);
        content.setPadding(new Insets(26, 22, 26, 24));
        content.setAlignment(Pos.TOP_LEFT);
        content.setMouseTransparent(true);

        StackPane badge = new StackPane(Theme.icon(icon, 24, Color.WHITE));
        badge.setMinSize(46, 46);
        badge.setMaxSize(46, 46);
        badge.setBackground(Ui.fill(Theme.accent, 15));
        Label t = Ui.semi(title, 19, Theme.text);
        Label d = Ui.wrap(Ui.label(desc, 13, Theme.gray1), 250);

        Region gap = new Region();
        gap.setMinHeight(10);
        content.getChildren().addAll(badge, gap, t, d);
        card.getChildren().add(content);

        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    private Node hintCard() {
        StackPane card = new StackPane();
        card.setPrefWidth(660);
        card.setMinWidth(660);
        card.setMaxWidth(660);
        card.setBackground(Ui.fill(Theme.cardBg, 14));
        card.setBorder(Ui.border(Color.web("#000000", 0.07), 13, 1));
        card.setPickOnBounds(true);

        HBox row = new HBox(12);
        row.setPadding(new Insets(15, 20, 15, 18));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMouseTransparent(true);

        Rectangle bar = new Rectangle(3, 40);
        bar.setFill(Theme.accent);
        bar.setOpacity(0.75);
        bar.setArcWidth(2);
        bar.setArcHeight(2);

        Label text = Ui.label("联机基于 EasyTier 虚拟局域网（与陶瓦联机同一方案），房间内只有 Minecraft 的流量会被转发。\n"
                + "需要以管理员身份运行 TCL 才能创建虚拟网卡；房主在单人游戏中「对局域网开放」后即可创建房间。", 13, Theme.gray2);
        text.setWrapText(true);
        text.setLineSpacing(4);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(bar, text);
        card.getChildren().add(row);
        return card;
    }

    private Node buildLoading() {
        if (loadingError != null) return buildError();

        // 加载卡片，底部 2px 进度条
        StackPane card = new StackPane();
        card.setMinWidth(300);
        card.setPrefWidth(430);
        card.setMaxWidth(430);
        card.setMaxHeight(Region.USE_PREF_SIZE); // 不设会被外层拉成整页高
        card.setBackground(Ui.fill(Theme.cardBg, 12));
        card.setBorder(Ui.border(Color.web("#000000", 0.07), 12, 1));

        HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(20, 20, 32, 25));
        Ui.Loading loading = new Ui.Loading(46, 44, null);
        loading.setMouseTransparent(true);
        loading.start();
        VBox texts = new VBox(5);
        texts.setAlignment(Pos.CENTER_LEFT);
        Label title = Ui.label(serverSide ? "正在创建联机房间" : "正在加入联机房间", 19, Theme.accent);
        Label desc = Ui.wrap(Ui.label(loadingStage, 14, Theme.gray3), 330);
        loadingDescLabel = desc;
        texts.getChildren().addAll(title, desc);
        HBox.setHgrow(texts, Priority.ALWAYS);
        row.getChildren().addAll(loading, texts);
        card.getChildren().add(row);

        // 底部进度条，宽度按进度
        Rectangle fill = new Rectangle();
        fill.setHeight(2);
        fill.setFill(Theme.accent);
        fill.widthProperty().bind(card.widthProperty().multiply(loadingProgress));
        StackPane track = new StackPane(fill);
        track.setAlignment(Pos.BOTTOM_LEFT);
        track.setPickOnBounds(false);
        track.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        track.setClip(clip);
        card.getChildren().add(track);

        StackPane close = Ui.iconButton("x", 25, 15, "取消", this::cancelLoading);
        close.setOpacity(0.35);
        StackPane box = new StackPane(card, close);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(-7, -7, 0, 0));

        StackPane holder = new StackPane(box);
        holder.setAlignment(Pos.CENTER);
        holder.setPickOnBounds(false);
        return holder;
    }

    private Node buildError() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20, 22, 18, 22));
        card.setMaxWidth(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setBackground(Ui.fill(Theme.cardBg, 12));
        card.setBorder(Ui.border(Color.web("#000000", 0.07), 12, 1));

        Label title = Ui.label(serverSide ? "创建房间失败" : "加入房间失败", 19, Theme.accent);
        Label body = Ui.wrap(Ui.label(loadingError, 13, Theme.gray2), 416);
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("返回", Ui.BTN_NORMAL, this::cancelLoading),
                Ui.button("重试", Ui.BTN_THEME, () -> {
                    loadingError = null;
                    win.reloadPage();
                }));
        card.getChildren().addAll(title, body, actions);
        StackPane holder = new StackPane(card);
        holder.setAlignment(Pos.CENTER);
        holder.setPickOnBounds(false);
        return holder;
    }

    private void cancelLoading() {
        if (worker != null) worker.interrupt();
        EasyTier.stop();
        loadingError = null;
        state = State.SELECT;
        win.reloadPage();
    }

    private Node buildFinish() {
        VBox column = new VBox(20);
        column.setAlignment(Pos.CENTER);
        column.setPickOnBounds(false);

        GridPane card = new GridPane();
        card.setPadding(new Insets(21, 25, 21, 25));
        card.setHgap(13);
        card.setVgap(9);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setBackground(Ui.fill(Theme.cardBg, 12));
        card.setBorder(Ui.border(Color.web("#000000", 0.07), 12, 1));
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(260);
        ColumnConstraints c2 = new ColumnConstraints();
        card.getColumnConstraints().addAll(c0, c1, c2);

        Node logo = Theme.icon("network", 28, Theme.accent);
        Label title = Ui.label(serverSide ? "已创建房间" : "已加入房间", 20, Theme.accent);
        Label desc = Ui.wrap(Ui.label(finishDesc(), 14, Theme.gray2), 400);
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (serverSide) actions.getChildren().add(Ui.button("复制邀请码", Ui.BTN_HIGHLIGHT, this::copyCode));
        actions.getChildren().add(Ui.button(serverSide ? "关闭" : "离开", Ui.BTN_NORMAL, this::exitRoom));

        card.add(logo, 0, 0);
        GridPane.setValignment(logo, javafx.geometry.VPos.TOP);
        card.add(title, 1, 0);
        card.add(desc, 1, 1);
        card.add(actions, 2, 2);

        hintSlot = new VBox(0);
        hintSlot.setAlignment(Pos.CENTER);
        hintText = "";
        hintType = -1;
        column.getChildren().addAll(hintSlot, card, infoCard());
        updateFinishInfo(); // 先填一遍提示和数值

        StackPane holder = new StackPane(column);
        holder.setAlignment(Pos.CENTER);
        holder.setPickOnBounds(false);
        refreshPeersAsync();
        return holder;
    }

    /** 房主给邀请码，加入方给地址。 */
    private String finishDesc() {
        if (serverSide) {
            return "把邀请码发给朋友，让大家加入房间吧！\n邀请码：" + inviteCode;
        }
        return "如果多人游戏列表里没有自动出现房间，请选择「直接连接」，\n地址填 " + clientAddress
                + "（可点击下方的地址复制）。\n注意：使用离线登录时不要手动输入 IP！";
    }

    private static Label pingLabel;
    private static Label peopleLabel;

    private Node infoCard() {
        // 房主显示端口，加入方显示 IP
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 3, 0, 3));
        row.setMaxWidth(Region.USE_PREF_SIZE);
        row.setMaxHeight(Region.USE_PREF_SIZE);
        row.setBackground(Ui.fill(Theme.cardBg, 8));
        row.setBorder(Ui.border(Color.web("#000000", 0.07), 8, 1));

        pingLabel = Ui.label(pingText(linkPing), 12, Theme.gray1);
        peopleLabel = Ui.label(peopleCount + " 人", 12, Theme.gray1);
        row.getChildren().add(infoItem("activity", serverSide ? "网络延迟" : "与房主的延迟", pingLabel, false));
        row.getChildren().add(divider());
        row.getChildren().add(infoItem("user", "房间内人数", peopleLabel, false));
        row.getChildren().add(divider());
        if (serverSide) {
            row.getChildren().add(infoItem("ethernet-port", "端口",
                    Ui.label(String.valueOf(connectPort()), 12, Theme.gray1), false));
        } else {
            row.getChildren().add(infoItem("globe", "服务器 IP（点击复制）",
                    Ui.label(connectAddress(), 12, Theme.gray1), true));
        }
        return row;
    }

    /** 延迟 >=10ms 取整，没测到显示连接优化中。 */
    private static String pingText(double ping) {
        if (ping <= 0) return "连接优化中";
        return ping >= 10 ? String.format(java.util.Locale.ROOT, "%.0f ms", ping)
                : String.format(java.util.Locale.ROOT, "%.1f ms", ping);
    }

    private String connectAddress() {
        return serverSide ? localAddress() + ":" + serverPort : clientAddress;
    }

    private int connectPort() {
        return serverPort;
    }

    /** 每秒刷延迟和人数，断线或房主跑掉直接提示。 */
    private void refreshPeersAsync() {
        final int port = rpcPort;
        if (port < 0) return; // 没启动时没有 CLI 可查
        Thread t = new Thread(() -> {
            int fails = 0;
            boolean[] warned = {false};
            while (state == State.FINISH && !Thread.currentThread().isInterrupted()) {
                try {
                    java.util.List<EasyTier.Peer> peers = EasyTier.peers(port);
                    fails = 0;
                    applyPeers(peers);
                    boolean anyOther = false, hasServer = false;
                    for (EasyTier.Peer p : peers) {
                        if (p.type == EasyTier.PEER_SELF) continue;
                        anyOther = true;
                        if (p.type == EasyTier.PEER_SERVER) hasServer = true;
                    }
                    final boolean lost = !anyOther;
                    final boolean hostGone = !serverSide && !hasServer;
                    javafx.application.Platform.runLater(() -> {
                        updateFinishInfo();
                        if (!warned[0] && hostGone) {
                            Dialogs.confirm(win, "联机结束", "房主已离开房间！", this::doExitRoom);
                        } else if (!warned[0] && lost) {
                            Dialogs.confirm(win, "联机已断开", "网络连接已断开，请检查网络后重试。", this::doExitRoom);
                        }
                        if (hostGone || lost) warned[0] = true;
                    });
                } catch (Exception e) {
                    fails++;
                    if (fails >= 4) {
                        javafx.application.Platform.runLater(() -> updateFinishInfo());
                        fails = 0;
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "TCL-LinkPeers");
        t.setDaemon(true);
        t.start();
    }

    /** 把延迟、人数和提示刷到完成页。 */
    private static void updateFinishInfo() {
        if (pingLabel != null) pingLabel.setText(pingText(linkPing));
        if (peopleLabel != null) peopleLabel.setText(peopleCount + " 人");
        String text = "";
        int type = -1;
        if (!serverSide && targetPeer != null) {
            if (targetPeer.relay) {
                if (myNat >= 6 && targetPeer.natType >= 6) text = "你和房主的网络环境都不太好，";
                else if (myNat >= 6) text = "你的网络环境不太好，";
                else if (targetPeer.natType >= 6) text = "房主的网络环境不太好，";
                else text = "你或者房主的网络环境不太好，";
                boolean custom = !Data.get().get("LinkRelay", "").isBlank();
                text += custom ? "正通过自定义节点进行中继。" : "正使用社区节点进行中继。";
                type = 1; // 黄
            } else if (myNat >= 1 && myNat < 5 && targetPeer.natType >= 5) {
                text = "你的网络环境比房主更好！如果你来当房主，其他玩家或许能更加流畅！";
                type = 0; // 蓝
            }
        }
        if (!text.equals(hintText) || type != hintType) {
            hintText = text;
            hintType = type;
            if (hintSlot != null) {
                hintSlot.getChildren().clear();
                if (!text.isEmpty()) hintSlot.getChildren().add(Ui.hint(text, type));
                hintSlot.setVisible(!text.isEmpty());
                hintSlot.setManaged(!text.isEmpty());
            }
        }
    }

    private Node infoItem(String icon, String tip, Label value, boolean clickable) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 13, 0, 13));
        box.setMinHeight(34);
        Node ic = Theme.icon(icon, 16, Theme.gray4);
        ic.setMouseTransparent(true);
        value.setMouseTransparent(true);
        box.getChildren().addAll(ic, value);
        if (tip != null && !tip.isEmpty()) Ui.tooltip(box, tip);
        if (clickable) {
            box.setCursor(Cursor.HAND);
            box.setOnMouseClicked(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(connectAddress());
                Clipboard.getSystemClipboard().setContent(cc);
                win.toast("已复制服务器地址！", connectAddress(), 1);
            });
        }
        return box;
    }

    private Node divider() {
        Region line = new Region();
        line.setPrefWidth(1);
        line.setMinHeight(20);
        line.setMaxHeight(20);
        line.setBackground(Ui.fill(Theme.text, 0.12));
        return line;
    }

    /** 复制邀请码和节点说明。 */
    private void copyCode() {
        String nodeText = discoverNodeId > 0
                ? "发现节点 ID：" + discoverNodeId + "（已随邀请码自动匹配）"
                : "发现节点：自动分配";
        String text = "在 TCL 启动器中输入邀请码【" + inviteCode + "】，即可加入联机房间！\n" + nodeText;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        win.toast("已复制邀请码！", inviteCode, 1);
    }

    private void exitRoom() {
        // 还有别人在线时先确认
        if (serverSide && peopleCount > 1) {
            Dialogs.confirm(win, "退出联机",
                    "你确定要关闭联机房间吗？\n所有玩家都需要重新输入邀请码才可加入游戏！",
                    this::doExitRoom, true);
            return;
        }
        doExitRoom();
    }

    private void doExitRoom() {
        EasyTier.stop();
        state = State.SELECT;
        inviteCode = "";
        clientAddress = "";
        linkPing = 0;
        peopleCount = 1;
        rpcPort = -1;
        targetPeer = null;
        myNat = 10;
        discoverNodeId = -1;
        discoverAddress = "";
        hintText = "";
        hintType = -1;
        hintSlot = null;
        pingLabel = null;
        peopleLabel = null;
        win.reloadPage();
    }

    private void createRoom() {
        if (!Data.get().getBool("LinkEnabled", true)) {
            win.toast("联机已禁用", "请先在 设置 → 联机 中启用联机功能。", 2);
            return;
        }
        // 各平台虚拟网卡权限不同
        String blocker = EasyTier.platformBlocker();
        if (blocker != null) {
            win.toast(EasyTier.isElevated() ? "无法创建虚拟网卡" : "需要管理员权限", blocker, 3);
            return;
        }
        TextField field = Ui.field("25565");
        field.setText("25565");
        field.setPrefHeight(32);
        showInput("输入端口",
                "在单人游戏的暂停菜单选择「对局域网开放」，然后输入端口数字。\n甚至可以输入其他游戏的端口……嗯……",
                "端口号", field, value -> {
                    int port;
                    try {
                        port = Integer.parseInt(value.trim());
                    } catch (Exception e) {
                        win.toast("端口号无效", "端口号应为 1024 ~ 65535 的整数。", 2);
                        return false;
                    }
                    if (port < 1024 || port > 65535) {
                        win.toast("端口号无效", "端口号应为 1024 ~ 65535 的整数。", 2);
                        return false;
                    }
                    serverSide = true;
                    serverPort = port;
                    discoverNodeId = -1; // 房主运行时再挑发现节点
                    String hexPort = Integer.toHexString(port).toUpperCase(java.util.Locale.ROOT);
                    while (hexPort.length() < 4) hexPort = "0" + hexPort;
                    networkName = "P" + hexPort + "-" + randomCode(5);
                    networkSecret = randomCode(5);
                    inviteCode = buildInviteCode();
                    startLoading();
                    return true;
                });
    }

    private void joinRoom() {
        if (!Data.get().getBool("LinkEnabled", true)) {
            win.toast("联机已禁用", "请先在 设置 → 联机 中启用联机功能。", 2);
            return;
        }
        TextField field = Ui.field(lastCode.isEmpty() ? "输入邀请码" : lastCode);
        field.setText(lastCode);
        field.setPrefHeight(32);
        showInput("输入邀请码", "输入房主发给你的邀请码。",
                lastCode.isEmpty() ? "" : "使用上一次的邀请码", field, value -> {
                    String code = value.trim().toUpperCase(java.util.Locale.ROOT)
                            .replace("O", "0").replace("I", "1");
                    // Pxxxx-xxxxx-VVNNN：网名 11 密码 5 版本 2 节点 3
                    if (code.length() != 23 || code.charAt(0) != 'P'
                            || code.charAt(11) != '-' || code.charAt(17) != '-') {
                        win.toast("邀请码有误", "请让房主使用 TCL 创建房间！", 2);
                        return false;
                    }
                    int version;
                    try {
                        version = Integer.parseInt(code.substring(18, 20));
                    } catch (Exception e) {
                        version = 0;
                    }
                    if (version <= 0) {
                        win.toast("邀请码有误", "邀请码格式不正确，请让房主重新生成。", 2);
                        return false;
                    }
                    if (version > INVITE_CODE_VERSION) {
                        win.toast("邀请码版本过高", "此邀请码对应的 TCL 版本更高，请升级启动器。", 2);
                        return false;
                    }
                    serverSide = false;
                    try {
                        serverPort = Integer.parseInt(code.substring(1, 5), 16);
                    } catch (Exception e) {
                        serverPort = 25565;
                    }
                    networkName = code.substring(0, 11);
                    networkSecret = code.substring(12, 17);
                    inviteCode = code;
                    lastCode = code;
                    try {
                        discoverNodeId = Integer.parseInt(code.substring(20, 23), 16);
                    } catch (Exception e) {
                        discoverNodeId = 0;
                    }
                    startLoading();
                    return true;
                });
    }

    /** 只给 --page=link 截图用。 */
    static void debugState(String name) {
        switch (name) {
            case "loading" -> {
                state = State.LOADING;
                serverSide = true;
                loadingStage = "正在启动联机模块…";
                loadingError = null;
            }
            case "error" -> {
                state = State.LOADING;
                serverSide = true;
                loadingError = "无法连接到房主：可能的原因是网络环境不佳、房主已关闭房间或邀请码输错。";
            }
            case "finish" -> {
                state = State.FINISH;
                serverSide = true;
                loadingError = null;
                inviteCode = "P63DD-2QK8X-02000";
                discoverNodeId = 0;
                // 房主一个人时的样子
                linkPing = 0;
                peopleCount = 1;
            }
            case "finishClient" -> {
                state = State.FINISH;
                serverSide = false;
                loadingError = null;
                clientAddress = "localhost:25565";
                linkPing = 0;
                peopleCount = 3;
            }
            default -> {
                state = State.SELECT;
                loadingError = null;
            }
        }
    }

    private void startLoading() {
        win.closeModal();
        state = State.LOADING;
        loadingStage = "准备联机模块…";
        loadingError = null;
        loadingProgress.set(0.06);
        loadingDescLabel = null;
        targetPeer = null;
        myNat = 10;
        hintText = "";
        hintType = -1;
        hintSlot = null;
        pingLabel = null;
        peopleLabel = null;
        win.reloadPage();
        worker = new Thread(this::runLink, "TCL-Link");
        worker.setDaemon(true);
        worker.start();
    }

    private void setStage(String text) {
        if (text == null || text.equals(loadingStage)) return;
        loadingStage = text;
        javafx.application.Platform.runLater(() -> {
            Label desc = loadingDescLabel;
            if (state == State.LOADING && desc != null && desc.getScene() != null) {
                // 只改这一行字
                desc.setText(text);
                double from = loadingProgress.get();
                double to = Math.min(0.92, from + 0.12);
                new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                                new javafx.animation.KeyValue(loadingProgress, from)),
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(200),
                                new javafx.animation.KeyValue(loadingProgress, to, Ui.EASE_OUT_FLUENT))).play();
            } else {
                win.reloadPage();
            }
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 读监听端口，非法就回退随机端口。 */
    private static int configuredPort() {
        try {
            int p = Integer.parseInt(Data.get().get("LinkPort", "11010").trim());
            return p > 0 && p < 65536 ? p : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 联机流程：装模块、取节点、启动 core、等连上。 */
    private void runLink() {
        try {
            EasyTier.ensureInstalled(false, (stage, v) -> setStage(stage));
            if (Thread.currentThread().isInterrupted()) return;
            String blocker = EasyTier.platformBlocker();
            if (blocker != null) throw new Exception(blocker);

            setStage("正在获取节点列表…");
            java.util.List<String> peerAddresses = resolvePeers();
            if (Thread.currentThread().isInterrupted()) return;
            // 房主的邀请码要带上选中的发现节点 ID
            if (serverSide) inviteCode = buildInviteCode();

            int port = EasyTier.freePort();
            rpcPort = port;
            EasyTier.rpcPort(port);
            int listeners = configuredPort();
            if (listeners <= 0) listeners = EasyTier.freePort();
            int clientPort = EasyTier.freePort();
            String host = (serverSide ? "Server-" : "Client-") + stableName();

            StringBuilder a = new StringBuilder("--mtu 1380 --console-log-level info");
            a.append(" --network-name=").append(networkName)
                    .append(" --network-secret=").append(networkSecret)
                    .append(" --listeners ").append(listeners)
                    .append(" --rpc-portal ").append(port)
                    .append(" --private-mode true");
            String adapter = Data.get().get("LinkAdapter", "TCL-EasyTier");
            if (!isWindows() && adapter != null && !adapter.isBlank()) {
                a.append(" --dev-name ").append(adapter.trim());
            }
            if (serverSide) {
                a.append(" -i 10.114.114.114 --hostname=").append(host)
                        .append(" --tcp-whitelist=").append(serverPort)
                        .append(" --udp-whitelist=").append(serverPort);
            } else {
                a.append(" -d --hostname=").append(host).append(" --tcp-whitelist=0 --udp-whitelist=0");
                clientAddress = "localhost:" + clientPort;
                String map = "/10.114.114.114:" + serverPort;
                // IPv4 和 IPv6 环回都转发
                a.append(" --port-forward tcp://127.0.0.1:").append(clientPort).append(map);
                a.append(" --port-forward udp://127.0.0.1:").append(clientPort).append(map);
                a.append(" --port-forward tcp://[::1]:").append(clientPort).append(map);
                a.append(" --port-forward udp://[::1]:").append(clientPort).append(map);
            }
            for (String peer : peerAddresses) a.append(" -p=\"").append(peer).append("\"");
            if ("1".equals(Data.get().get("LinkLatencyMode", "0"))) a.append(" --latency-first");

            setStage("正在启动联机模块…");
            EasyTier.start(a.toString());

            waitUntilConnected(port);
            if (Thread.currentThread().isInterrupted()) return;
            if (!serverSide) waitStable(port); // 加入方再多等最多 5 秒

            state = State.FINISH;
            javafx.application.Platform.runLater(() -> {
                win.reloadPage();
                // 房主的邀请码直接进剪贴板
                if (serverSide) copyCode();
                else win.toast("已加入房间", clientAddress, 1);
            });
        } catch (InterruptedException e) {
            // 用户取消
        } catch (Exception e) {
            EasyTier.stop();
            String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
            javafx.application.Platform.runLater(() -> {
                loadingError = msg;
                state = State.LOADING;
                win.reloadPage();
            });
            // 页内错误态只说状态，真实原因交给弹窗
            Errors.panic("无法建立联机", e);
        }
    }

    /** 轮询 CLI 直到能 ping 到目标节点。 */
    private void waitUntilConnected(int port) throws Exception {
        long begin = System.currentTimeMillis();
        while (System.currentTimeMillis() - begin < 90_000) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Thread.sleep(500); // 先等 core 起好
            if (EasyTier.crashed()) throw new Exception("联机模块已崩溃，请检查是否有权限创建虚拟网卡。");
            java.util.List<EasyTier.Peer> peers;
            try {
                peers = EasyTier.peers(port);
            } catch (Exception e) {
                continue;
            }
            applyPeers(peers);
            if (targetPeer != null) return;
            int alive = 0;
            for (EasyTier.Peer p : peers) if (p.ping > 0) alive++;
            setStage(alive == 0 ? "正在连接到节点…" : "正在连接到房主…");
        }
        throw new Exception("无法连接到" + (serverSide ? "节点" : "房主")
                + "：可能的原因是网络环境不佳、房主已关闭房间或邀请码输错。");
    }

    /** 加入方再等 5 秒，让连接优化成直连。 */
    private void waitStable(int port) {
        setStage("连接优化中……");
        for (int i = 0; i < 50; i++) {
            if (Thread.currentThread().isInterrupted()) return;
            if (targetPeer != null && !targetPeer.relay && targetPeer.ping < 100) return;
            try {
                applyPeers(EasyTier.peers(port));
                Thread.sleep(100);
            } catch (Exception e) {
                return;
            }
        }
    }

    /** 折算节点列表：人数、最低延迟节点。 */
    private static void applyPeers(java.util.List<EasyTier.Peer> peers) {
        if (peers == null) return;
        EasyTier.Peer best = null;
        int count = 1; // 自己也算一个人
        for (EasyTier.Peer p : peers) {
            if (p.type == EasyTier.PEER_SELF) {
                myNat = p.natType;
                continue;
            }
            if (p.type != EasyTier.PEER_MISC) count++;
            boolean candidate = serverSide ? p.ping > 0
                    : (p.type == EasyTier.PEER_SERVER && p.ping > 0);
            if (candidate && (best == null || p.ping < best.ping)) best = p;
        }
        peopleCount = count;
        targetPeer = best;
        if (best != null) linkPing = best.ping;
    }

    /** 取节点列表，房主挑发现节点，加入方按 ID 找。 */
    private java.util.List<String> resolvePeers() {
        java.util.List<String> out = new java.util.ArrayList<>();
        Data d = Data.get();
        // 0 自动、1~3 指定内置节点、4 自定义
        int peer = d.getInt("LinkPeer", 0);
        String custom = d.get("LinkCustomPeer", "");
        if (custom == null || custom.isBlank()) custom = d.get("LinkRelay", ""); // 兼容旧配置
        if (peer == 4 && custom != null && !custom.isBlank()) {
            for (String one : custom.split("[,;\\s]+")) {
                if (!one.isBlank()) out.add(one.trim());
            }
            if (!serverSide) win.toast("提示", "房主可能没有使用相同的自定义节点，请确认设置一致。", 1);
            return out;
        }
        if (peer >= 1 && peer <= 3) {
            String chosen = EasyTier.PUBLIC_PEERS[peer - 1];
            out.add(chosen);
            for (String fallback : EasyTier.PUBLIC_PEERS) {
                if (!fallback.equals(chosen) && !out.contains(fallback)) out.add(fallback);
            }
            return out;
        }
        if (custom != null && !custom.isBlank()) {
            for (String one : custom.split("[,;\\s]+")) {
                if (!one.isBlank()) out.add(one.trim());
            }
            if (!serverSide) win.toast("提示", "房主可能没有使用相同的自定义节点，请确认设置一致。", 1);
            return out;
        }
        try {
            java.util.List<EasyTier.Node> nodes = EasyTier.fetchNodes(discoverNodeId, null);
            EasyTier.Node discover = null;
            if (serverSide) {
                for (EasyTier.Node n : nodes) {
                    if (!n.allowRelay) {
                        discover = n;
                        break;
                    }
                }
            } else if (discoverNodeId > 0) {
                for (EasyTier.Node n : nodes) {
                    if (n.id == discoverNodeId) {
                        discover = n;
                        break;
                    }
                }
                if (discover == null) throw new Exception("房间已过期：邀请码里的发现节点已下线，请让房主重新创建房间！");
            }
            if (discover != null) {
                out.add(discover.address);
                discoverAddress = discover.address;
                discoverNodeId = discover.id;
            }
            if (!serverSide) {
                int want = relayCount();
                int added = 0;
                for (EasyTier.Node n : nodes) {
                    if (!n.allowRelay) continue;
                    if (added >= want) break;
                    out.add(n.address);
                    added++;
                }
            }
            if (out.isEmpty()) throw new Exception("没有可用的发现节点");
            return out;
        } catch (Exception e) {
            // 官方源不可用就退回内置节点
            Data.get().log("联机：节点列表不可用，改用内置节点（" + e.getMessage() + "）");
            java.util.Collections.addAll(out, EasyTier.PUBLIC_PEERS);
            return out;
        }
    }

    private int relayCount() {
        try {
            return Math.max(0, Math.min(5, Integer.parseInt(Data.get().get("LinkRelayCount", "2"))));
        } catch (Exception e) {
            return 2;
        }
    }

    /** 邀请码：网络名-密码-版本-发现节点。 */
    private static String buildInviteCode() {
        String node = Integer.toString(Math.max(0, discoverNodeId), 16).toUpperCase(java.util.Locale.ROOT);
        while (node.length() < 3) node = "0" + node;
        return networkName + "-" + networkSecret + "-"
                + String.format(java.util.Locale.ROOT, "%02d", INVITE_CODE_VERSION) + node;
    }

    /** 稳定的节点名。 */
    private String stableName() {
        int hash = (System.getProperty("user.name", "player") + "@" + System.getProperty("os.name", ""))
                .hashCode() & 0x7fffffff;
        return Integer.toString(hash, 36).toUpperCase(java.util.Locale.ROOT);
    }

    private interface InputHandler {
        boolean apply(String value);
    }

    private void showInput(String title, String message, String hint, TextField field, InputHandler onOk) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20, 22, 18, 22));
        card.setPrefWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 12));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label body = Ui.wrap(Ui.label(message, 12.5, Theme.gray1), 356);
        if (!hint.isEmpty()) {
            Label h = Ui.label(hint, 12, Theme.gray2);
            HBox actions = new HBox(10);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.getChildren().addAll(
                    Ui.button("取消", Ui.BTN_NORMAL, win::closeModal),
                    Ui.button("确定", Ui.BTN_THEME, () -> {
                        if (onOk.apply(field.getText() == null ? "" : field.getText())) win.closeModal();
                    }));
            card.getChildren().addAll(Ui.bold(title, 17, Theme.text), body, h, field, actions);
        } else {
            HBox actions = new HBox(10);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.getChildren().addAll(
                    Ui.button("取消", Ui.BTN_NORMAL, win::closeModal),
                    Ui.button("确定", Ui.BTN_THEME, () -> {
                        if (onOk.apply(field.getText() == null ? "" : field.getText())) win.closeModal();
                    }));
            card.getChildren().addAll(Ui.bold(title, 17, Theme.text), body, field, actions);
        }
        win.showModal(card);
        javafx.application.Platform.runLater(field::requestFocus);
    }

    private String randomCode(int length) {
        String chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private String localAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
