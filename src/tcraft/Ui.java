package tcraft;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;

public final class Ui {

    public static final int BTN_NORMAL = 0;
    public static final int BTN_THEME = 1;
    public static final int BTN_HIGHLIGHT = 2;
    public static final int BTN_RED = 3;
    public static final int BTN_DARK = 4;

    private Ui() {
    }

    // SPLINE 控制点须在 [0,1]，越界会崩在类初始化
    public static Interpolator fluentOut(final double p) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return 1 - Math.pow(1 - t, p);
            }
        };
    }

    public static Interpolator fluentIn(final double p) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return Math.pow(t, p);
            }
        };
    }

    public static Interpolator backOut(final double p) {
        return new Interpolator() {
            @Override
            protected double curve(double t) {
                return 1 - Math.pow(1 - t, p) * Math.cos(1.5 * Math.PI * t);
            }
        };
    }

    public static final Interpolator EASE_OUT_FLUENT = fluentOut(3);
    public static final Interpolator EASE_OUT_FLUENT_WEAK = fluentOut(2);
    public static final Interpolator EASE_OUT_FLUENT_STRONG = fluentOut(4);
    public static final Interpolator EASE_OUT_BACK = backOut(2);
    public static final Interpolator EASE_OUT_BACK_MIDDLE = backOut(1.5);

    public static Label label(String text, double size, Color color) {
        Label l = new Label(Lang.t(text));
        l.setFont(Theme.font(size));
        l.setTextFill(color);
        return l;
    }

    public static Label bold(String text, double size, Color color) {
        Label l = new Label(Lang.t(text));
        l.setFont(Theme.font(size, true));
        l.setTextFill(color);
        return l;
    }

    public static Label semi(String text, double size, Color color) {
        Label l = new Label(Lang.t(text));
        l.setFont(Theme.font(size, FontWeight.SEMI_BOLD));
        l.setTextFill(color);
        return l;
    }

    public static Label wrap(Label label, double width) {
        label.setWrapText(true);
        label.setMaxWidth(width);
        return label;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(double spacing, Node... nodes) {
        HBox h = new HBox(spacing, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static Region line(Color color, double height) {
        Region r = new Region();
        r.setMinHeight(height);
        r.setMaxHeight(height);
        r.setBackground(fill(color, 0));
        return r;
    }

    public static class Card extends StackPane {
        public final VBox content;
        public final HBox header;
        private final Label titleLabel;
        private final Node chevron;
        private final DropShadow glow = new DropShadow(5, 0, 0, Color.web("#343d4a", 0.07));
        private Timeline glowOn;
        private Timeline glowOff;
        private boolean collapsed;
        private boolean collapsible;
        private double expandedHeight = -1;
        private Timeline toggleAnim;
        private Runnable lazyContent;
        private boolean lazyBuilt;

        public Card(String title) {
            // title 归一成 ""，card(null) 走无标题卡片
            if (title == null) title = "";
            setBackground(glassFill(14));
            setBorder(glassBorder(14));
            setEffect(glow);
            setAlignment(Pos.TOP_LEFT);
            setMaxWidth(Double.MAX_VALUE);
            // 不设 clip，clip 会把 DropShadow 一起裁掉

            VBox root = new VBox();
            root.setMaxWidth(Double.MAX_VALUE);
            header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setMaxWidth(Double.MAX_VALUE);
            header.setPadding(new Insets(12, 15, 10, 15));
            titleLabel = bold(title, 13, Theme.text);
            chevron = Theme.icon("chevron-down", 14, Theme.text);
            chevron.setOpacity(0);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            header.getChildren().addAll(titleLabel, sp, chevron);
            content = new VBox();
            content.setMaxWidth(Double.MAX_VALUE);
            content.setPadding(new Insets(0, 15, 14, 15));
            root.getChildren().addAll(header, content);
            if (title.isEmpty()) {
                header.setVisible(false);
                header.setManaged(false);
                content.setPadding(new Insets(14, 15, 14, 15));
            }
            getChildren().add(root);

            Region shine = new Region();
            shine.setMouseTransparent(true);
            shine.setMaxHeight(40);
            StackPane.setAlignment(shine, Pos.TOP_CENTER);
            shine.setBackground(new Background(new BackgroundFill(
                    new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.web("#ffffff", 0.26)), new Stop(0.55, Color.web("#ffffff", 0.04)),
                            new Stop(1, Color.TRANSPARENT)), new CornerRadii(13, 13, 0, 0, false), Insets.EMPTY)));
            shine.setVisible(!title.isEmpty());
            getChildren().add(shine);

            if (!title.isEmpty()) {
                header.setCursor(Cursor.HAND);
                header.setOnMouseClicked(e -> {
                    if (collapsible) toggle();
                });
            }

            // 悬停阴影用 color alpha 插值，DropShadow 无 opacity
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                animateGlow(true);
                titleLabel.setTextFill(Theme.accentDark);
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                animateGlow(false);
                titleLabel.setTextFill(Theme.text);
            });
        }

        private void animateGlow(boolean hover) {
            double radius = hover ? 11 : 5;
            Color color = hover ? Color.web(Theme.accent.toString(), 0.16)
                    : Color.web("#343d4a", 0.07);
            if (getHeight() > 260) {
                glow.setRadius(radius);
                glow.setColor(color);
                return;
            }
            Timeline anim = hover ? glowOn : glowOff;
            if (anim == null) {
                anim = new Timeline(new KeyFrame(Duration.millis(hover ? 150 : 200),
                        new KeyValue(glow.radiusProperty(), radius, EASE_OUT_FLUENT),
                        new KeyValue(glow.colorProperty(), color, EASE_OUT_FLUENT)));
                if (hover) glowOn = anim;
                else glowOff = anim;
            }
            anim.playFromStart();
        }

        public void add(Node... nodes) {
            content.getChildren().addAll(nodes);
        }

        public void setLazyContent(Runnable builder) {
            this.lazyContent = builder;
        }

        private void ensureContent() {
            if (lazyContent != null && !lazyBuilt) {
                lazyBuilt = true;
                lazyContent.run();
            }
        }

        public void setSpacing(double s) {
            content.setSpacing(s);
        }

        public void setContentPadding(Insets insets) {
            content.setPadding(insets);
        }

        public void setCollapsible(boolean value) {
            collapsible = value;
            chevron.setOpacity(value ? 1 : 0);
            if (!value) collapsed = false;
            updateChevron();
        }

        public void setCollapsed(boolean value) {
            setCollapsible(true);
            if (collapsed == value) return;
            stopToggleAnimation();
            collapsed = value;
            content.setOpacity(1);
            content.setTranslateY(0);
            if (value) {
                expandedHeight = content.getHeight() > 0 ? content.getHeight() : -1;
                content.setVisible(false);
                content.setManaged(false);
                content.setMaxHeight(Region.USE_COMPUTED_SIZE);
            } else {
                ensureContent();
                content.setVisible(true);
                content.setManaged(true);
                content.setMaxHeight(Region.USE_COMPUTED_SIZE);
            }
            updateChevron();
        }

        public void toggle() {
            if (!collapsible) return;
            ensureContent();
            boolean collapse = !collapsed;
            stopToggleAnimation();
            setEffect(null);
            if (collapse) {
                expandedHeight = Math.max(20, content.getHeight());
                content.setVisible(true);
                content.setManaged(true);
                content.setOpacity(1);
                content.setTranslateY(0);
                int ms = aniMs(180);
                if (ms <= 0) {
                    content.setVisible(false);
                    content.setManaged(false);
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                    collapsed = true;
                    updateChevron();
                    return;
                }
                boolean useCache = expandedHeight <= 300;
                content.setCache(useCache);
                content.setCacheHint(useCache ? CacheHint.SPEED : CacheHint.DEFAULT);
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(content.maxHeightProperty(), expandedHeight)),
                        new KeyFrame(Duration.millis(ms), new KeyValue(content.maxHeightProperty(), 0,
                                Interpolator.EASE_IN)));
                t.setOnFinished(e -> {
                    if (toggleAnim != t) return;
                    toggleAnim = null;
                    content.setVisible(false);
                    content.setManaged(false);
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                });
                toggleAnim = t;
                t.play();
            } else {
                content.setVisible(true);
                content.setManaged(true);
                content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                content.setOpacity(1);
                content.setTranslateY(0);
                applyCss();
                content.applyCss();
                double target = Math.max(20, content.prefHeight(Math.max(100, getWidth() - 30)));
                int ms = aniMs(target > 300 ? 180 : 240);
                if (ms <= 0) {
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                    collapsed = false;
                    updateChevron();
                    return;
                }
                boolean useCache = target <= 300;
                content.setCache(useCache);
                content.setCacheHint(useCache ? CacheHint.SPEED : CacheHint.DEFAULT);
                content.setMaxHeight(0);
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(content.maxHeightProperty(), 0)),
                        new KeyFrame(Duration.millis(ms), new KeyValue(content.maxHeightProperty(),
                                target, Interpolator.EASE_OUT)));
                t.setOnFinished(e -> {
                    if (toggleAnim != t) return;
                    toggleAnim = null;
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                });
                toggleAnim = t;
                t.play();
            }
            collapsed = collapse;
            updateChevron();
        }

        private void stopToggleAnimation() {
            if (toggleAnim != null) {
                toggleAnim.stop();
                toggleAnim = null;
            }
            content.setCache(false);
            setEffect(glow);
        }

        private void animationCleanup() {
            setEffect(glow);
            content.setCache(false);
            content.setOpacity(1);
            content.setTranslateY(0);
        }

        private void updateChevron() {
            javafx.scene.transform.Rotate r = new javafx.scene.transform.Rotate(collapsed ? 0 : 180, 7, 7);
            chevron.getTransforms().setAll(r);
        }

        public Label titleLabel() {
            return titleLabel;
        }
    }

    public static Card card(String title) {
        return new Card(title);
    }

    public static Card card(String title, Node... nodes) {
        Card c = new Card(title);
        c.setSpacing(6);
        c.add(nodes);
        return c;
    }

    public static StackPane button(String text, int type) {
        return button(text, type, null);
    }

    public static StackPane button(String text, int type, Runnable action) {
        StackPane pane = new StackPane();
        Label lab = bold(text, 13, Color.WHITE);
        lab.setAlignment(Pos.CENTER);
        pane.getChildren().add(lab);
        pane.setPadding(new Insets(6, 18, 6, 18));
        pane.setMinHeight(34);
        pane.setCursor(Cursor.HAND);
        pane.setAlignment(Pos.CENTER);
        pane.setEffect(Theme.shadow(9, 2, 0.22, Color.BLACK));
        final Color baseBg;
        final Color baseBorder;
        final Color hoverBg;
        final Color hoverBorder;
        final Color textColor;
        final Color hoverText;
        switch (type) {
            case BTN_THEME -> {
                baseBg = Theme.accentDark;
                baseBorder = Color.TRANSPARENT;
                hoverBg = Theme.accent;
                hoverBorder = Color.TRANSPARENT;
                textColor = Color.WHITE;
                hoverText = Color.WHITE;
            }
            case BTN_RED -> {
                baseBg = Theme.halfWhite;
                baseBorder = Theme.redDark;
                hoverBg = Theme.redBack;
                hoverBorder = Theme.red;
                textColor = Theme.redDark;
                hoverText = Theme.red;
            }
            case BTN_DARK -> {
                baseBg = Theme.darkBtnBg;
                baseBorder = Color.WHITE;
                hoverBg = Theme.darkBtnHover;
                hoverBorder = Color.WHITE;
                textColor = Color.WHITE;
                hoverText = Color.WHITE;
            }
            case BTN_HIGHLIGHT -> {
                baseBg = Theme.halfWhite;
                baseBorder = Theme.accentDark;
                hoverBg = Theme.brush7;
                hoverBorder = Theme.accent;
                textColor = Theme.accentDark;
                hoverText = Theme.accent;
            }
            default -> {
                baseBg = Theme.halfWhite;
                baseBorder = Theme.text;
                hoverBg = Theme.brush7;
                hoverBorder = Theme.accent;
                textColor = Theme.text;
                hoverText = Theme.accent;
            }
        }
        lab.setTextFill(textColor);
        pane.setBackground(fill(baseBg, 8));
        pane.setBorder(type == BTN_THEME ? null : border(baseBorder, 8, 1));
        int inMs = 100;
        int outMs = type == BTN_THEME ? 100 : 200;
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            if (pane.isDisabled()) return;
            animateBg(pane, baseBg, hoverBg, inMs, EASE_OUT_FLUENT);
            if (type != BTN_THEME) animateBorder(pane, baseBorder, hoverBorder, inMs);
            lab.setTextFill(hoverText);
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            if (pane.isDisabled()) return;
            animateBg(pane, hoverBg, baseBg, outMs, EASE_OUT_FLUENT);
            if (type != BTN_THEME) animateBorder(pane, hoverBorder, baseBorder, outMs);
            lab.setTextFill(textColor);
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> scale(pane, 0.96));
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> scale(pane, 1));
        pane.disabledProperty().addListener((o, was, now) -> {
            if (now) {
                if (type == BTN_THEME) {
                    animateBg(pane, Theme.accentDark, Theme.accentDark, 1);
                    lab.setTextFill(Theme.accentPale);
                } else if (type == BTN_DARK) {
                    lab.setTextFill(Theme.accentPale2);
                } else {
                    animateBorder(pane, baseBorder, Theme.gray4, outMs);
                    lab.setTextFill(Theme.gray4);
                }
            } else {
                if (type == BTN_THEME) {
                    animateBg(pane, Theme.accentDark, Theme.accentDark, 1);
                    lab.setTextFill(Color.WHITE);
                } else if (type == BTN_DARK) {
                    lab.setTextFill(Color.WHITE);
                } else {
                    animateBorder(pane, Theme.gray4, baseBorder, inMs);
                    lab.setTextFill(textColor);
                }
            }
        });
        if (action != null) pane.setOnMouseClicked(e -> action.run());
        return pane;
    }

    public static void animateBg(Region region, Color from, Color to, int ms) {
        animateBg(region, from, to, ms, null);
    }

    // 同一节点同一槽位的动画先停旧的再播新的，快速 hover 终态才不会被上一段拽回去
    static void startExclusive(Node owner, Object slot, javafx.animation.Animation t) {
        Object old = owner.getProperties().put(slot, t);
        if (old instanceof javafx.animation.Animation running) running.stop();
        t.setOnFinished(e -> {
            if (owner.getProperties().get(slot) == t) owner.getProperties().remove(slot);
        });
        t.play();
    }

    public static void animateBg(Region region, Color from, Color to, int ms, Interpolator ease) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(Math.max(1, ms)));
                if (ease != null) setInterpolator(ease);
            }

            @Override
            protected void interpolate(double frac) {
                Color c = from.interpolate(to, frac);
                region.setBackground(fill(c, corner(region.getBackground())));
            }
        };
        startExclusive(region, "uiBgAnim", t);
    }

    private static double corner(Background bg) {
        if (bg == null || bg.getFills().isEmpty()) return 8;
        return bg.getFills().get(0).getRadii().getTopLeftHorizontalRadius();
    }

    public static void animateBorder(Region region, Color from, Color to, int ms) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(ms));
            }

            @Override
            protected void interpolate(double frac) {
                Color c = from.interpolate(to, frac);
                double r = 8;
                if (region.getBorder() != null && !region.getBorder().getStrokes().isEmpty()) {
                    BorderStroke s = region.getBorder().getStrokes().get(0);
                    r = s.getRadii().getTopLeftHorizontalRadius();
                }
                region.setBorder(border(c, r, 1));
            }
        };
        startExclusive(region, "uiBorderAnim", t);
    }

    public static void scale(Node node, double target) {
        ScaleTransition st = new ScaleTransition(Duration.millis(target < 1 ? 60 : 200), node);
        st.setToX(target);
        st.setToY(target);
        st.setInterpolator(target < 1 ? EASE_OUT_FLUENT_STRONG : EASE_OUT_FLUENT);
        st.play();
    }

    public static final class MenuRow {
        final String title;
        final String icon;
        final Runnable action;
        final boolean sep;
        final boolean keepOpen;

        private MenuRow(String title, String icon, Runnable action, boolean sep, boolean keepOpen) {
            this.title = title;
            this.icon = icon;
            this.action = action;
            this.sep = sep;
            this.keepOpen = keepOpen;
        }

        public static MenuRow of(String title, String icon, Runnable action) {
            return new MenuRow(title, icon, action, false, false);
        }

        /** 二级菜单入口：点它不关卡片，而是在同一张卡片里换内容（对齐 WPF 的二级卡片） */
        public static MenuRow submenu(String title, String icon, Runnable action) {
            return new MenuRow(title, icon, action, false, true);
        }

        public static MenuRow separator() {
            return new MenuRow(null, null, null, true, false);
        }
    }

    /** 当前打开的菜单卡片；二级菜单靠它拿到同一张卡片 */
    private static javafx.stage.Popup activeMenu;

    public static javafx.stage.Popup activeMenu() {
        return activeMenu;
    }

    /** 只构建菜单行（不含卡片外壳），二级菜单原地换内容时复用 */
    public static VBox menuRows(MenuRow... rows) {
        VBox box = new VBox(0);
        for (MenuRow row : rows) {
            if (row.sep) {
                Region sep = new Region();
                sep.setMinHeight(1);
                sep.setMaxHeight(1);
                sep.setBackground(fill(Theme.gray6, 0));
                VBox.setMargin(sep, new Insets(5, 8, 5, 8));
                box.getChildren().add(sep);
                continue;
            }
            ListItem item = new ListItem(Theme.icon(row.icon, 16, Theme.text), row.title, null, 34);
            item.setOnMouseClicked(e -> {
                if (!row.keepOpen) closeMenuCard(activeMenu);
                if (row.action != null) row.action.run();
            });
            box.getChildren().add(item);
        }
        return box;
    }

    public static javafx.stage.Popup menuCard(javafx.scene.Node anchor, MenuRow... rows) {
        VBox card = menuCardShell(210);
        card.getChildren().addAll(menuRows(rows).getChildren());
        return showMenuCard(anchor, card);
    }

    public static javafx.stage.Popup menuCardNode(javafx.scene.Node anchor,
                                                  javafx.scene.Node content, double width) {
        VBox card = menuCardShell(width);
        card.getChildren().add(content);
        return showMenuCard(anchor, card);
    }

    /** 卡片给投影留的白边，和 WPF 版 MenuCard.ShadowPad 一致 */
    private static final double MENU_SHADOW_PAD = 14;

    private static VBox menuCardShell(double width) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(8));
        // 只设下限，实际宽度在 showMenuCard 里按内容量出来后回填
        card.setMinWidth(width);
        card.setBackground(fill(popupBg(), 10));
        card.setBorder(border(Color.color(Theme.text.getRed(), Theme.text.getGreen(), Theme.text.getBlue(), 0.10), 10, 1));
        card.setEffect(Theme.shadow(18, 3, 0.35, Theme.text));
        return card;
    }

    public static Color popupBg() {
        int pct = Data.get().getInt("UiMenuOpacity", 100);
        pct = Math.max(0, Math.min(100, pct));
        Color base = Theme.cardBg;
        // WPF 的卡片底色是 #f5ffffff（96% 白）；组件半透明调低时不能再跟着一起透，
        // 否则弹层叠在壁纸上等于没有底板，字看不清。这里给个 0.85 的底
        double alpha = Math.max(base.getOpacity(), 0.85) * pct / 100.0;
        if (pct > 0) alpha = Math.max(alpha, 0.30);
        return Color.color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * 菜单可用区域：优先用主窗口范围（菜单不能超出窗口），窗口取不到时退回所在屏幕。
     * 两者取交集，保证既不出窗也不出屏。
     */
    private static javafx.geometry.Rectangle2D menuArea(javafx.stage.Window owner,
                                                       javafx.geometry.Rectangle2D screen) {
        if (owner instanceof javafx.stage.Stage) {
            javafx.stage.Stage st = (javafx.stage.Stage) owner;
            double x = st.getX(), y = st.getY(), w = st.getWidth(), h = st.getHeight();
            if (w > 100 && h > 100 && !Double.isNaN(x) && !Double.isNaN(y)) {
                double minX = Math.max(screen.getMinX(), x);
                double minY = Math.max(screen.getMinY(), y);
                double maxX = Math.min(screen.getMaxX(), x + w);
                double maxY = Math.min(screen.getMaxY(), y + h);
                if (maxX - minX > 160 && maxY - minY > 160) {
                    return new javafx.geometry.Rectangle2D(minX, minY, maxX - minX, maxY - minY);
                }
            }
        }
        return screen;
    }

    /**
     * 按锚点算菜单最大高度，移植 WPF MenuCard.CalcMaxMenuHeight：
     * 取「下方」和「上方」里更宽敞的一侧，留 16px 边距，再扣掉卡片内边距和投影留白。
     */
    private static double calcMaxMenuHeight(javafx.geometry.Bounds ab,
                                            javafx.geometry.Rectangle2D area) {
        double below = area.getMaxY() - ab.getMaxY() - 16;
        double above = ab.getMinY() - area.getMinY() - 16;
        double usable = Math.max(below, above);
        if (usable < 200) usable = area.getHeight() - 32;      // 坐标异常时退化成整块区域可用
        return Math.max(160, Math.min(usable - 48, area.getHeight() - 80));
    }

    private static javafx.stage.Popup showMenuCard(javafx.scene.Node anchor, VBox card) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        card.getProperties().put("popup", popup);

        javafx.geometry.Bounds ab = anchor.localToScreen(anchor.getBoundsInLocal());
        if (ab == null) ab = new javafx.geometry.BoundingBox(0, 0, 0, 0);
        javafx.geometry.Rectangle2D screen = screenOf(ab);
        javafx.stage.Window owner = anchor.getScene() == null ? null : anchor.getScene().getWindow();
        javafx.geometry.Rectangle2D area = menuArea(owner, screen);

        MenuBox box = layoutMenu(card, ab, area);
        box.visual.setOpacity(0);
        box.visual.setScaleX(0.94);
        box.visual.setScaleY(0.94);
        box.visual.setTranslateY(10);
        box.visual.getProperties().put("menuAnchor", anchor);
        popup.getContent().add(box.visual);

        double[] xy = placeMenu(box, ab, area);
        if (owner != null) popup.show(owner, xy[0], xy[1]);
        else popup.show(anchor, xy[0], xy[1]);

        activeMenu = popup;
        popup.setOnHidden(e -> {
            if (activeMenu == popup) activeMenu = null;
        });
        attachThemeCss(box.visual);
        expandIn(box.visual);
        return popup;
    }

    /**
     * 二级菜单：在同一张卡片里换内容（对齐 WPF 的二级卡片——PanInstanceMenu.Children.Clear() 后重建）。
     * 不关窗、不重播入场动画，卡片风格和位置与一级菜单完全一致。
     */
    public static void swapMenuCard(javafx.stage.Popup popup, javafx.scene.Node content, double width) {
        if (popup == null || !popup.isShowing() || popup.getContent().isEmpty()) return;
        javafx.scene.Node old = popup.getContent().get(0);
        javafx.scene.Node anchor = (javafx.scene.Node) old.getProperties().get("menuAnchor");
        if (anchor == null) return;

        VBox card = menuCardShell(width);
        card.getChildren().add(content);

        javafx.geometry.Bounds ab = anchor.localToScreen(anchor.getBoundsInLocal());
        if (ab == null) ab = new javafx.geometry.BoundingBox(0, 0, 0, 0);
        javafx.geometry.Rectangle2D screen = screenOf(ab);
        javafx.stage.Window owner = anchor.getScene() == null ? null : anchor.getScene().getWindow();
        javafx.geometry.Rectangle2D area = menuArea(owner, screen);

        MenuBox box = layoutMenu(card, ab, area);
        box.visual.setOpacity(0);
        box.visual.getProperties().put("menuAnchor", anchor);

        // Popup 显示中改宽度不生效（高度会自动跟内容走），宽度变了只能原位重开一次
        boolean widthChanged = Math.abs(popup.getWidth() - box.vw) > 1;
        popup.getContent().set(0, box.visual);
        popup.setHeight(box.vh);
        double[] xy = placeMenu(box, ab, area);
        if (widthChanged && owner != null) {
            popup.hide();
            popup.show(owner, xy[0], xy[1]);
            activeMenu = popup;
        } else {
            popup.setX(xy[0]);
            popup.setY(xy[1]);
        }
        attachThemeCss(box.visual);

        javafx.animation.Timeline in = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(box.visual.opacityProperty(), 0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(110),
                        new javafx.animation.KeyValue(box.visual.opacityProperty(), 1)));
        in.play();
    }

    /** 排版结果：内容节点 + 弹窗整体尺寸（含投影留白） */
    private static final class MenuBox {
        javafx.scene.layout.Pane visual;
        double w;
        double vw;
        double vh;
    }

    private static MenuBox layoutMenu(VBox card, javafx.geometry.Bounds ab,
                                     javafx.geometry.Rectangle2D area) {
        // VBox.prefWidth() 不含 minWidth，不兜底会被压成一条、文字只剩一个字；
        // 内容自身的最小宽度也要算进来（先放开 minWidth 才能量到真实值）
        card.setMinWidth(Region.USE_COMPUTED_SIZE);
        double naturalMin = card.minWidth(-1);
        double w = Math.max(210, Math.max(naturalMin, card.prefWidth(-1)));
        w = Math.min(w, Math.max(220, area.getWidth() - 48));
        card.setMinWidth(w);
        card.setPrefWidth(w);
        card.setMaxWidth(w);

        double contentH = card.prefHeight(w);
        double panelH = Math.min(contentH, calcMaxMenuHeight(ab, area));

        MenuBox box = new MenuBox();
        box.w = w;
        box.vw = w + MENU_SHADOW_PAD * 2;
        box.vh = panelH + MENU_SHADOW_PAD * 2;
        // 统一走滚动壳：内容放得下时壳里不显示滚动条，外观和裸卡片一致
        box.visual = scrollShell(card, w, panelH, contentH);
        return box;
    }

    private static double[] placeMenu(MenuBox box, javafx.geometry.Bounds ab,
                                      javafx.geometry.Rectangle2D area) {
        double pad = MENU_SHADOW_PAD;
        // 候选位置与 WPF 一致：右侧对齐/左侧对齐 × 下方/上方，取第一个能放进区域的
        double xRight = ab.getMaxX() - box.w - pad;
        double xLeft = ab.getMinX() - pad;
        double yBelow = ab.getMaxY() + 2 - pad;
        double yAbove = ab.getMinY() - box.vh - 2 + pad;
        double[] xs = {xRight, xRight, xLeft, xLeft};
        double[] ys = {yBelow, yAbove, yBelow, yAbove};
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] >= area.getMinX() + 4 && xs[i] + box.vw <= area.getMaxX() - 4
                    && ys[i] >= area.getMinY() + 4 && ys[i] + box.vh <= area.getMaxY() - 4) {
                return new double[]{xs[i], ys[i]};
            }
        }
        return new double[]{
                Math.max(area.getMinX() + 4, Math.min(xRight, area.getMaxX() - box.vw - 4)),
                Math.max(area.getMinY() + 4, Math.min(yBelow, area.getMaxY() - box.vh - 4))};
    }

    private static void attachThemeCss(javafx.scene.Node visual) {
        // Popup 有独立 scene，不继承主窗样式表；手动挂主题 CSS
        try {
            if (visual.getScene() != null && Theme.css() != null) {
                visual.getScene().getStylesheets().setAll(Theme.css().toExternalForm());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 菜单滚动壳：外层 Pane 就是 Popup 的内容，尺寸固定为「卡片 + 投影留白」；
     * 里面再套一层裁剪到卡片圆角的视口，卡片在视口里上下平移，右侧是自绘滚动条。
     * 不用 ScrollPane：它的视口会吃掉圆角背景，fitToWidth 还会把卡片压窄。
     */
    private static javafx.scene.layout.Pane scrollShell(VBox card, double w, double viewH, double contentH) {
        final double pad = MENU_SHADOW_PAD;
        final double vw = w + pad * 2;
        final double vh = viewH + pad * 2;
        final boolean scrollable = contentH > viewH + 0.5;

        javafx.scene.layout.Pane root = new javafx.scene.layout.Pane();
        root.setMinSize(vw, vh);
        root.setPrefSize(vw, vh);
        root.setMaxSize(vw, vh);
        // 外层也裁一刀：否则子节点的溢出会把 bounds 撑大，Popup 会跟着撑到屏幕外
        root.setClip(new Rectangle(vw, vh));

        javafx.scene.layout.Pane view = new javafx.scene.layout.Pane();
        view.setManaged(false);
        view.resizeRelocate(pad, pad, w, viewH);
        Rectangle clip = new Rectangle(w, viewH);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        view.setClip(clip);

        // 投影底板：视口会把卡片的阴影一起裁掉，所以阴影单独画一层垫在下面
        javafx.scene.layout.Region back = new javafx.scene.layout.Region();
        back.setManaged(false);
        back.resizeRelocate(pad, pad, w, viewH);
        back.setBackground(fill(popupBg(), 10));
        back.setEffect(Theme.shadow(18, 3, 0.35, Theme.text));
        root.getChildren().add(back);

        root.getChildren().add(view);

        card.setLayoutX(0);
        card.setLayoutY(0);
        card.setPrefHeight(contentH);
        card.setEffect(null);          // 阴影已由底板负责，卡片再画一次会被裁出硬边
        view.getChildren().add(card);

        final double barW = 5;
        final double barX = pad + w - barW - 4;
        final double trackY = pad + 8;
        final double trackH = Math.max(20, viewH - 16);
        final double thumbH = Math.max(32, trackH * viewH / Math.max(viewH, contentH));
        final double maxScroll = Math.max(1, contentH - viewH);

        javafx.scene.layout.Region track = new javafx.scene.layout.Region();
        track.setManaged(false);
        track.setVisible(scrollable);
        track.setBackground(fill(Color.color(Theme.text.getRed(), Theme.text.getGreen(),
                Theme.text.getBlue(), 0.06), barW / 2.0));
        track.resizeRelocate(barX, trackY, barW, trackH);
        root.getChildren().add(track);

        javafx.scene.layout.Region thumb = new javafx.scene.layout.Region();
        thumb.setManaged(false);
        thumb.setVisible(scrollable);
        thumb.setCursor(Cursor.HAND);
        thumb.setBackground(fill(Color.color(Theme.accent.getRed(), Theme.accent.getGreen(),
                Theme.accent.getBlue(), 0.62), barW / 2.0));
        root.getChildren().add(thumb);

        final double[] off = {0};
        Runnable apply = () -> {
            card.setLayoutY(-off[0]);
            double ty = trackY + (trackH - thumbH) * (off[0] / maxScroll);
            thumb.resizeRelocate(barX, ty, barW, thumbH);
        };
        apply.run();

        if (scrollable) {
            root.setOnScroll(e -> {
                if (e.getDeltaY() == 0) return;
                off[0] = Math.max(0, Math.min(maxScroll, off[0] - e.getDeltaY()));
                apply.run();
                e.consume();
            });

            final double[] drag = new double[2];
            thumb.setOnMousePressed(e -> {
                drag[0] = e.getSceneY();
                drag[1] = off[0];
                e.consume();
            });
            thumb.setOnMouseDragged(e -> {
                double span = Math.max(1, trackH - thumbH);
                off[0] = Math.max(0, Math.min(maxScroll,
                        drag[1] + (e.getSceneY() - drag[0]) / span * maxScroll));
                apply.run();
                e.consume();
            });
        }
        return root;
    }

    private static javafx.geometry.Rectangle2D screenOf(javafx.geometry.Bounds screenBounds) {
        if (screenBounds != null) {
            for (javafx.stage.Screen s : javafx.stage.Screen.getScreensForRectangle(
                    screenBounds.getMinX(), screenBounds.getMinY(),
                    Math.max(1, screenBounds.getWidth()), Math.max(1, screenBounds.getHeight()))) {
                return s.getVisualBounds();
            }
        }
        return javafx.stage.Screen.getPrimary().getVisualBounds();
    }

    private static void expandIn(javafx.scene.Node card) {
        if (!Data.get().getBool("UiMenuAnimation", true)) {
            card.setOpacity(1);
            card.setScaleX(1);
            card.setScaleY(1);
            card.setTranslateY(0);
            return;
        }
        javafx.animation.Timeline in = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(card.opacityProperty(), 0),
                        new javafx.animation.KeyValue(card.scaleXProperty(), 0.94),
                        new javafx.animation.KeyValue(card.scaleYProperty(), 0.94),
                        new javafx.animation.KeyValue(card.translateYProperty(), 10)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(130),
                        new javafx.animation.KeyValue(card.opacityProperty(), 1)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(220),
                        new javafx.animation.KeyValue(card.scaleXProperty(), 1, EASE_OUT_BACK_MIDDLE),
                        new javafx.animation.KeyValue(card.scaleYProperty(), 1, EASE_OUT_BACK_MIDDLE),
                        new javafx.animation.KeyValue(card.translateYProperty(), 0, EASE_OUT_FLUENT)));
        in.play();
    }

    public static void closeMenuCard(javafx.stage.Popup popup) {
        if (popup == null || !popup.isShowing()) return;
        javafx.scene.Node card = popup.getContent().isEmpty() ? null : popup.getContent().get(0);
        if (card == null) {
            popup.hide();
            return;
        }
        javafx.animation.Timeline out = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(card.opacityProperty(), card.getOpacity())),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(80),
                        new javafx.animation.KeyValue(card.opacityProperty(), 0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(90),
                        new javafx.animation.KeyValue(card.scaleXProperty(), 0.96),
                        new javafx.animation.KeyValue(card.scaleYProperty(), 0.96)));
        out.setOnFinished(e -> popup.hide());
        out.play();
    }

    // 列表行内按钮：点击不冒泡到整行
    public static StackPane listAction(String icon, double size, double iconSize, String tooltip, Runnable action) {
        StackPane pane = iconButton(icon, size, iconSize, tooltip, action);
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                javafx.scene.input.MouseEvent::consume);
        return pane;
    }

    public static StackPane iconButton(String icon, double size, double iconSize, String tooltip, Runnable action) {
        Node glyph = Theme.icon(icon, iconSize, Color.web("#808080"));
        StackPane pane = new StackPane(glyph);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.setCursor(Cursor.HAND);
        pane.setBackground(fill(Color.TRANSPARENT, 6));
        if (tooltip != null) tooltip(pane, tooltip);
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            pane.setBackground(fill(Color.web("#ffffff", 50 / 255.0), 6));
            tint(glyph, Theme.accent);
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            pane.setBackground(fill(Color.TRANSPARENT, 6));
            tint(glyph, Color.web("#808080"));
        });
        if (action != null) pane.setOnMouseClicked(e -> action.run());
        return pane;
    }

    public static HBox iconTextButton(String icon, String text, boolean highlight, Runnable action) {
        HBox pane = new HBox(7);
        pane.setAlignment(Pos.CENTER_LEFT);
        pane.setMinHeight(27);
        pane.setPrefHeight(27);
        pane.setMaxHeight(27);
        pane.setPadding(new Insets(0, 12, 0, 12));
        pane.setBackground(fill(Theme.semiTransparent, 13.5));
        pane.setCursor(Cursor.HAND);
        Node glyph = Theme.icon(icon, 16, highlight ? Theme.accent : Theme.text);
        Label lab = label(text, 13, highlight ? Theme.accent : Theme.text);
        final Color baseFg = highlight ? Theme.accent : Theme.text;
        pane.getChildren().addAll(glyph, lab);
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            if (isDisabledNode(pane)) return;
            animateBg(pane, Theme.semiTransparent, Theme.listHoverBg, aniMs(100));
            colorTween(pane, "uiIconTint", c -> tint(glyph, c), baseFg, Theme.accent, aniMs(100));
            colorTween(pane, "uiLabelText", c -> lab.setTextFill(c), baseFg, Theme.accent, aniMs(100));
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            animateBg(pane, Theme.listHoverBg, Theme.semiTransparent, aniMs(150));
            colorTween(pane, "uiIconTint", c -> tint(glyph, c), Theme.accent, baseFg, aniMs(150));
            colorTween(pane, "uiLabelText", c -> lab.setTextFill(c), Theme.accent, baseFg, aniMs(150));
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                e -> animateBg(pane, pane.getBackground() == null ? Theme.halfWhite : Theme.listHoverBg,
                        Theme.accentPale2, aniMs(70)));
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            boolean over = pane.isHover();
            animateBg(pane, Theme.accentPale2, over ? Theme.listHoverBg : Theme.semiTransparent, aniMs(100));
            if (!over) {
                colorTween(pane, "uiIconTint", c -> tint(glyph, c), Theme.accent, baseFg, aniMs(150));
                colorTween(pane, "uiLabelText", c -> lab.setTextFill(c), Theme.accent, baseFg, aniMs(150));
            }
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            if (isDisabledNode(pane)) return;
            if (action != null) action.run();
        });
        pane.disabledProperty().addListener((o, was, now) -> {
            Color c = now ? Theme.gray5 : baseFg;
            tint(glyph, c);
            lab.setTextFill(c);
        });
        return pane;
    }

    private static boolean isDisabledNode(Node n) {
        return n.isDisabled();
    }

    public static StackPane iconButtonWhite(String icon, double size, double iconSize, String tooltip, Runnable action) {
        StackPane pane = new StackPane(Theme.icon(icon, iconSize, Theme.accentPale3));
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.setCursor(Cursor.HAND);
        pane.setBackground(fill(Color.TRANSPARENT, 6));
        if (tooltip != null) tooltip(pane, tooltip);
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            javafx.animation.Transition t = new javafx.animation.Transition() {
                {
                    setCycleDuration(Duration.millis(Math.max(1, aniMs(120))));
                }

                @Override
                protected void interpolate(double f) {
                    pane.setBackground(fill(Color.color(1, 1, 1, f * 50 / 255.0), 6));
                }
            };
            t.play();
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            Color from = pane.getBackground() != null && !pane.getBackground().getFills().isEmpty()
                    ? (Color) pane.getBackground().getFills().get(0).getFill() : Color.TRANSPARENT;
            javafx.animation.Transition t = new javafx.animation.Transition() {
                {
                    setCycleDuration(Duration.millis(Math.max(1, aniMs(150))));
                }

                @Override
                protected void interpolate(double f) {
                    pane.setBackground(fill(Color.color(1, 1, 1, from.getOpacity() * (1 - f)), 6));
                }
            };
            t.play();
        });
        if (action != null) pane.setOnMouseClicked(e -> action.run());
        return pane;
    }

    public static class ListItem extends HBox {
        public final Label title;
        public final Label info;
        private final Region check;
        private final Node icon;
        private final List<Node> actions = new java.util.ArrayList<>();
        private boolean selected;
        private boolean hover;
        private boolean pressed;
        private Color currentBack = Color.TRANSPARENT;

        public ListItem(Node icon, String text, String infoText, double height) {
            super(0);
            setAlignment(Pos.CENTER_LEFT);
            setMinHeight(height);
            setPrefHeight(height);
            setMaxHeight(height);
            setCursor(Cursor.HAND);
            setBackground(fill(Color.TRANSPARENT, 4));
            setMaxWidth(Double.MAX_VALUE);
            check = new Region();
            check.setMinWidth(5);
            check.setPrefWidth(5);
            check.setMaxWidth(5);
            check.setMaxHeight(Double.MAX_VALUE);
            check.setBackground(fill(Theme.accent, 2.5));
            check.setVisible(false);
            HBox.setMargin(check, new Insets(6, 0, 6, -1));
            getChildren().add(check);
            this.icon = icon;
            if (icon != null) {
                HBox iconBox = new HBox(icon);
                iconBox.setAlignment(Pos.CENTER);
                iconBox.setMinWidth(34);
                iconBox.setPrefWidth(34);
                HBox.setMargin(icon, new Insets(0, 0, 0, 6));
                getChildren().add(iconBox);
            }
            VBox texts = new VBox(0);
            texts.setAlignment(Pos.CENTER_LEFT);
            title = label(text, height < 40 ? 13 : 14, Theme.text);
            texts.getChildren().add(title);
            info = label(infoText == null ? "" : infoText, 11.5, Theme.gray2);
            info.setVisible(infoText != null && !infoText.isEmpty());
            info.setManaged(info.isVisible());
            texts.getChildren().add(info);
            HBox.setHgrow(texts, Priority.ALWAYS);
            getChildren().add(texts);
            setPadding(new Insets(0, 4, 0, 1));
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                hover = true;
                animateBack(hoverFill(), 120);
                refreshColor();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                hover = false;
                pressed = false;
                animateBack(Color.TRANSPARENT, 180);
                scaleTo(1, 180);
                refreshColor();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                pressed = true;
                animateBack(pressFill(), 120);
                scaleTo(0.98, 108);
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                pressed = false;
                animateBack(hoverFill(), 120);
                scaleTo(1, 144);
            });
        }

        private Color hoverFill() {
            return Theme.listHoverBg;
        }

        private Color pressFill() {
            return Theme.accentPale2;
        }

        private void animateBack(Color to, int ms) {
            Color from = currentBack;
            currentBack = to;
            if (Data.get().getInt("UiAnimSpeed", 100) <= 0) {
                setBackground(fill(to, 4));
                return;
            }
            javafx.animation.Transition t = new javafx.animation.Transition() {
                { setCycleDuration(javafx.util.Duration.millis(ms)); }
                @Override protected void interpolate(double f) {
                    setBackground(fill(from.interpolate(to, f), 4));
                }
            };
            t.play();
        }

        private void scaleTo(double target, int ms) {
            if (Data.get().getInt("UiAnimSpeed", 100) <= 0) {
                setScaleX(target);
                setScaleY(target);
                return;
            }
            javafx.animation.Timeline tl = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(ms),
                            new javafx.animation.KeyValue(scaleXProperty(), target, EASE_OUT_FLUENT),
                            new javafx.animation.KeyValue(scaleYProperty(), target, EASE_OUT_FLUENT)));
            startExclusive(this, "uiListScale", tl);
        }

        public void setSelected(boolean value) {
            selected = value;
            refreshColor();
        }

        public boolean isSelected() {
            return selected;
        }

        public void addAction(Node action) {
            actions.add(action);
            action.setOpacity(Data.get().getBool("UiListButtonsAlwaysShow", false) ? 1 : 0);
            getChildren().add(action);
            refreshColor();
        }

        private void refreshColor() {
            check.setVisible(selected);
            title.setTextFill(selected ? Theme.accent : Theme.text);
            info.setTextFill(selected ? Theme.accentLight : Theme.gray2);
            if (icon != null) tint(icon, selected ? Theme.accent : Theme.text);
            boolean always = Data.get().getBool("UiListButtonsAlwaysShow", false);
            for (Node n : actions) {
                n.setOpacity(hover || always ? 1 : 0);
                n.setMouseTransparent(!(hover || always));
            }
        }
    }

    public static void tint(Node node, Color color) {
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) tint(child, color);
        } else if (node instanceof javafx.scene.Group group) {
            for (Node child : group.getChildren()) tint(child, color);
        } else if (node instanceof javafx.scene.shape.Shape shape) {
            if (shape.getFill() != null && !(shape.getFill() instanceof Color c && c.getOpacity() == 0)) {
                shape.setFill(color);
            }
            if (shape.getStroke() != null) shape.setStroke(color);
        }
    }

    public static TextField field(String hint) {
        TextField tf = new TextField();
        tf.setPromptText(Lang.t(hint));
        tf.setFont(Theme.font(13));
        tf.setPrefHeight(30);
        tf.setStyle(fieldStyle(Theme.fieldBg, Theme.accentPale));
        tf.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            if (!tf.isFocused() && tf.isEditable()) tf.setStyle(fieldStyle(Theme.brush7, Theme.accentLight));
        });
        tf.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            if (!tf.isFocused() && tf.isEditable()) tf.setStyle(fieldStyle(Theme.fieldBg, Theme.accentPale));
        });
        tf.focusedProperty().addListener((o, was, now) ->
                tf.setStyle(fieldStyle(Theme.brush7, now ? Theme.accent : Theme.accentPale)));
        tf.disabledProperty().addListener((o, was, now) -> {
            if (now) tf.setStyle(fieldStyle(Theme.gray6, Theme.gray5));
        });
        return tf;
    }

    private static String fieldStyle(Color bg, Color border) {
        return "-fx-background-color: " + web(bg) + ";"
                + "-fx-background-radius: 3; -fx-border-radius: 3;"
                + "-fx-border-color: " + web(border) + "; -fx-border-width: 1;"
                + "-fx-text-fill: " + web(Theme.gray1) + ";"
                + "-fx-prompt-text-fill: " + web(Theme.gray4) + ";"
                + "-fx-highlight-fill: " + web(Theme.accentPale) + ";"
                + "-fx-highlight-text-fill: " + web(Theme.text) + ";"
                + "-fx-font-size: 13;";
    }

    private static String web(Color c) {
        return c.toString().replace("0x", "#");
    }

    public static ComboBox<String> combo(String[] items, int selected) {
        ComboBox<String> cb = new ComboBox<>();
        for (String item : items) cb.getItems().add(Lang.t(item));
        if (selected >= 0 && selected < items.length) cb.getSelectionModel().select(selected);
        cb.setPrefWidth(180);
        cb.setPrefHeight(30);
        cb.setStyle(comboStyle(Theme.fieldBg, Theme.accentPale));
        cb.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            if (!cb.isShowing()) cb.setStyle(comboStyle(Theme.brush7, Theme.accentLight));
        });
        cb.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            if (!cb.isShowing()) cb.setStyle(comboStyle(Theme.fieldBg, Theme.accentPale));
        });
        cb.showingProperty().addListener((o, was, now) -> {
            if (now) {
                cb.setStyle(comboStyle(Theme.brush7, Theme.accent));
                javafx.application.Platform.runLater(() -> animateComboPopup(cb));
            } else {
                cb.setStyle(comboStyle(Theme.fieldBg, Theme.accentPale));
            }
        });
        return cb;
    }

    private static String comboStyle(Color bg, Color border) {
        return "-fx-font-size: 13;"
                + "-fx-background-color: " + web(bg) + ";"
                + "-fx-background-radius: 3; -fx-border-radius: 3;"
                + "-fx-border-color: " + web(border) + "; -fx-border-width: 1;"
                + "-fx-control-inner-background: " + web(bg) + ";"
                + "-fx-prompt-text-fill: " + web(Theme.gray1) + ";"
                + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
    }

    private static void animateComboPopup(ComboBox<?> cb) {
        if (Data.get().getInt("UiAnimSpeed", 100) <= 0 || cb.getScene() == null) return;
        javafx.stage.Window owner = cb.getScene().getWindow();
        Node card = null;
        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
            if (!(w instanceof javafx.stage.PopupWindow pw) || !pw.isShowing()) continue;
            if (pw.getOwnerWindow() != owner || pw.getScene() == null) continue;
            Node listView = pw.getScene().getRoot().lookup(".list-view");
            if (listView != null) card = listView;
        }
        if (card == null) return;
        Node anim = card;
        anim.setScaleX(0.94);
        anim.setScaleY(0.94);
        anim.setTranslateY(10);
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(anim.scaleXProperty(), 1, EASE_OUT_BACK_MIDDLE),
                new KeyValue(anim.scaleYProperty(), 1, EASE_OUT_BACK_MIDDLE),
                new KeyValue(anim.translateYProperty(), 0, EASE_OUT_FLUENT_WEAK)));
        tl.play();
    }

    public static HBox settingRow(String labelText, Node control) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(38);
        Label l = label(labelText, 13, Theme.text);
        l.setMinWidth(150);
        row.getChildren().addAll(l, spacer(), control);
        return row;
    }

    public static HBox settingRow(String labelText, Node control, String note) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(38);
        Label l = label(labelText, 13, Theme.text);
        l.setMinWidth(150);
        Label n = label(note, 11.5, Theme.gray3);
        HBox.setMargin(n, new Insets(0, 8, 0, 0));
        row.getChildren().addAll(l, spacer(), n, control);
        return row;
    }

    public static HBox hint(String text, int type) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(9, 12, 9, 12));
        double fa = Math.max(0, Math.min(1, Theme.componentOpacity));
        Color bg;
        Color border;
        Color fg;
        String icon;
        switch (type) {
            case 1 -> {
                bg = Color.web("#ffebd7", fa);
                border = Color.web("#f57a00");
                fg = Color.web("#d86c00");
                icon = "triangle-alert";
            }
            case 2 -> {
                bg = Color.web("#ffdddf", fa);
                border = Color.web("#d82929");
                fg = Color.web("#bf0b0b");
                icon = "circle-minus";
            }
            default -> {
                Color hb = Theme.blueHintBg;
                bg = Color.color(hb.getRed(), hb.getGreen(), hb.getBlue(), fa);
                border = Theme.accentLight;
                fg = Theme.accentDark;
                icon = "info";
            }
        }
        box.setBackground(fill(bg, 7));
        box.setBorder(border(border, 7, 1));
        Node ic = Theme.icon(icon, 16, fg);
        Label lab = label(text, 12.5, fg);
        lab.setWrapText(true);
        HBox.setHgrow(lab, Priority.ALWAYS);
        box.getChildren().addAll(ic, lab);
        return box;
    }

    static int aniMs(int ms) {
        int speed = Data.get().getInt("UiAnimSpeed", 100);
        if (speed <= 0) return 0;
        return Math.max(1, (int) Math.round(ms * speed / 100.0));
    }

    private static javafx.animation.Transition colorTween(javafx.beans.property.ObjectProperty<Color> prop,
                                                          Color from, Color to, int ms) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(Math.max(1, ms)));
            }

            @Override
            protected void interpolate(double f) {
                prop.set(from.interpolate(to, f));
            }
        };
        t.play();
        return t;
    }

    private static javafx.animation.Transition colorTween(Node owner, Object slot,
                                                          javafx.beans.property.ObjectProperty<Color> prop,
                                                          Color from, Color to, int ms) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(Math.max(1, ms)));
            }

            @Override
            protected void interpolate(double f) {
                prop.set(from.interpolate(to, f));
            }
        };
        startExclusive(owner, slot, t);
        return t;
    }

    public static final class Check extends HBox {
        private final Region box = new Region();
        private final javafx.scene.shape.SVGPath check = new javafx.scene.shape.SVGPath();
        private final Label lab;
        private final javafx.scene.transform.Scale checkScale =
                new javafx.scene.transform.Scale(0, 0, 6, 6);
        private final javafx.scene.transform.Scale boxScale =
                new javafx.scene.transform.Scale(1, 1, 0, 9);
        private boolean selected;
        private boolean pressed;
        private Color textColor = Theme.text;
        private Runnable onAction;
        // 停掉上一段动画，不然旧动画会把勾拉回去
        private javafx.animation.Animation checkAnim;
        private javafx.animation.Animation boxAnim;

        public Check(String text, boolean checked) {
            super(7);
            setAlignment(Pos.CENTER_LEFT);
            setCursor(Cursor.HAND);
            setPickOnBounds(true);
            lab = label(text, 13, Theme.text);
            box.setMinSize(18, 18);
            box.setPrefSize(18, 18);
            box.setMaxSize(18, 18);
            box.setBackground(fill(Theme.halfWhite, 3));
            box.setBorder(border(Theme.text, 3, 1.1));
            box.getTransforms().add(boxScale);
            box.setMouseTransparent(true);
            check.setContent("M0,6L1.5,4.5 4.5,7.5 10.5,1.5 12,3 4.5,10.5 0,6z");
            check.setFill(Theme.text);
            check.setMouseTransparent(true);
            check.getTransforms().add(checkScale);
            HBox.setMargin(box, new Insets(0, 0, 0, 1));
            StackPane boxHolder = new StackPane(box, check);
            boxHolder.setMouseTransparent(true);
            getChildren().addAll(boxHolder, lab);
            lab.setMouseTransparent(true);
            this.selected = checked;
            checkScale.setX(checked ? 1 : 0);
            checkScale.setY(checked ? 1 : 0);
            box.setBorder(border(checked ? Theme.accentDark : Theme.text, 3, 1.1));
            check.setFill(checked ? Theme.accentDark : Theme.text);
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (isDisabled()) return;
                colorTween(this, "uiCheckText", c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.accent, aniMs(100));
                colorTween(this, "uiCheckBorder", boxBorderProperty(), currentBorder(), Theme.accent, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (pressed) {
                    pressed = false;
                    releaseVisual();
                }
                if (isDisabled()) return;
                colorTween(this, "uiCheckText", c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.text, aniMs(200));
                colorTween(this, "uiCheckBorder", boxBorderProperty(), currentBorder(),
                        !isDisabled() ? (selected ? Theme.accentDark : Theme.text) : Theme.gray5, aniMs(200));
                box.setBackground(fill(Theme.halfWhite, 3));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (isDisabled()) return;
                pressed = true;
                box.setBackground(fill(Theme.listHoverBg, 3));
                animateBox(16.5 / 18.0, aniMs(1000), EASE_OUT_FLUENT_STRONG);
                if (selected) animateCheck(0.9, aniMs(1000), EASE_OUT_FLUENT_STRONG, 0);
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                if (!pressed) return;
                pressed = false;
                setSelected(!selected);
                releaseVisual();
                if (onAction != null) onAction.run();
            });
            disabledProperty().addListener((o, was, now) -> {
                Color c = now ? Theme.gray5 : (selected ? Theme.accentDark : Theme.text);
                lab.setTextFill(now ? Theme.gray5 : Theme.text);
                box.setBorder(border(c, 3, 1.1));
                check.setFill(c);
            });
        }

        private Color currentBorder() {
            return box.getBorder() != null && !box.getBorder().getStrokes().isEmpty()
                    ? (Color) box.getBorder().getStrokes().get(0).getTopStroke() : Theme.text;
        }

        private javafx.beans.property.ObjectProperty<Color> boxBorderProperty() {
            return new javafx.beans.property.SimpleObjectProperty<>() {
                @Override
                public void set(Color c) {
                    box.setBorder(border(c, 3, 1.1));
                    check.setFill(c);
                }
            };
        }

        private void releaseVisual() {
            box.setBackground(fill(Theme.halfWhite, 3));
            animateBox(1, aniMs(400), EASE_OUT_FLUENT_STRONG);
            animateCheck(selected ? 1 : 0, aniMs(500), EASE_OUT_FLUENT_STRONG, 0);
        }

        private void animateCheck(double target, int ms, Interpolator ease, int delay) {
            if (checkAnim != null) checkAnim.stop();
            Timeline tl = new Timeline(new KeyFrame(Duration.millis(Math.max(1, ms)),
                    new KeyValue(checkScale.xProperty(), target, ease),
                    new KeyValue(checkScale.yProperty(), target, ease)));
            tl.setDelay(Duration.millis(delay));
            checkAnim = tl;
            tl.play();
        }

        private void animateBox(double target, int ms, Interpolator ease) {
            if (boxAnim != null) boxAnim.stop();
            Timeline tl = new Timeline(new KeyFrame(Duration.millis(Math.max(1, ms)),
                    new KeyValue(boxScale.xProperty(), target, ease),
                    new KeyValue(boxScale.yProperty(), target, ease)));
            boxAnim = tl;
            tl.play();
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            if (value == selected) return;
            selected = value;
            if (selected) {
                animateCheck(1, aniMs(300), EASE_OUT_BACK, aniMs(105));
                colorTween(this, "uiCheckBorder", boxBorderProperty(), currentBorder(),
                        isHover() && !isDisabled() ? Theme.accent : Theme.accentDark, aniMs(150));
            } else {
                animateCheck(0, aniMs(135), fluentIn(2), 0);
                colorTween(this, "uiCheckBorder", boxBorderProperty(), currentBorder(), Theme.text, aniMs(150));
            }
        }

        public void setOnAction(Runnable action) {
            this.onAction = action;
        }
    }

    public static Check checkBox(String text, boolean checked) {
        return new Check(text, checked);
    }

    public static final class RadioBox extends HBox {
        private final javafx.scene.shape.Ellipse ring = new javafx.scene.shape.Ellipse(9, 9);
        private final javafx.scene.shape.Ellipse dot = new javafx.scene.shape.Ellipse(4.5, 4.5);
        private final Label lab;
        private final javafx.scene.transform.Scale dotScale =
                new javafx.scene.transform.Scale(0, 0, 9, 9);
        private final javafx.scene.transform.Scale ringScale =
                new javafx.scene.transform.Scale(1, 1, 9, 9);
        private boolean selected;
        private Color textColor = Theme.text;
        private final javafx.beans.property.BooleanProperty selectedProp =
                new javafx.beans.property.SimpleBooleanProperty(false);
        private Runnable onAction;

        public RadioBox(String text, boolean checked) {
            super(7);
            setAlignment(Pos.CENTER_LEFT);
            setCursor(Cursor.HAND);
            setPickOnBounds(true);
            lab = label(text, 13, Theme.text);
            lab.setMouseTransparent(true);
            StackPane holder = new StackPane(ring, dot);
            holder.setPrefSize(19, 18);
            holder.setMinSize(19, 18);
            holder.setMaxSize(19, 18);
            holder.setMouseTransparent(true);
            ring.setStroke(Theme.text);
            ring.setStrokeWidth(1.1);
            ring.setFill(Theme.halfWhite);
            dot.setFill(Theme.text);
            dot.setMouseTransparent(true);
            ring.getTransforms().add(ringScale);
            dot.getTransforms().add(dotScale);
            dotScale.setX(checked ? 1 : 0);
            dotScale.setY(checked ? 1 : 0);
            if (checked) {
                ring.setStroke(Theme.accentDark);
                dot.setFill(Theme.accentDark);
            }
            getChildren().addAll(holder, lab);
            this.selected = checked;
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (isDisabled()) return;
                colorTween(this, "uiRadioText", c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.accent, aniMs(100));
                ringColorTween(currentRing(), Theme.accent, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (isDisabled()) return;
                colorTween(this, "uiRadioText", c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.text, aniMs(200));
                ringColorTween(currentRing(), selected ? Theme.accentDark : Theme.text, aniMs(200));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                if (isDisabled()) return;
                if (selected) return;
                javafx.scene.Parent p = getParent();
                if (p != null) {
                    for (Node n : p.getChildrenUnmodifiable()) {
                        if (n instanceof RadioBox r && r != this && r.isSelected()) r.setSelected(false);
                    }
                }
                setSelected(true);
                if (onAction != null) onAction.run();
            });
        }

        private Color currentRing() {
            return ring.getStroke() instanceof Color c ? c : Theme.text;
        }

        private void ringColorTween(Color from, Color to, int ms) {
            javafx.animation.Transition t = new javafx.animation.Transition() {
                {
                    setCycleDuration(Duration.millis(Math.max(1, ms)));
                }

                @Override
                protected void interpolate(double f) {
                    Color c = from.interpolate(to, f);
                    ring.setStroke(c);
                    dot.setFill(c);
                }
            };
            startExclusive(this, "uiRadioRing", t);
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            if (value == selected) return;
            selected = value;
            selectedProp.set(value);
            if (selected) {
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(390))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 1, EASE_OUT_BACK),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 1, EASE_OUT_BACK)));
                tl.play();
                ringColorTween(currentRing(), isHover() && !isDisabled() ? Theme.accent : Theme.accentDark, aniMs(150));
            } else {
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(150))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 0, Interpolator.EASE_IN),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 0, Interpolator.EASE_IN)));
                tl.play();
                ringColorTween(currentRing(), Theme.text, aniMs(150));
            }
        }

        public void setOnAction(Runnable action) {
            this.onAction = action;
        }
    }

    public static RadioBox radioBox(String text, boolean checked) {
        return new RadioBox(text, checked);
    }

    public static final class SliderBar extends Pane {
        private final Region back = new Region();
        private final Region fore = new Region();
        private final javafx.scene.shape.Ellipse dot = new javafx.scene.shape.Ellipse(0, 8, 5, 5);
        private final javafx.scene.transform.Scale dotScale = new javafx.scene.transform.Scale(1, 1, 0, 8);
        private final double min;
        private final double max;
        private double value;
        private boolean dragging;
        private Color lineColor = Theme.accentPale;
        private java.util.function.DoubleConsumer onChanged;
        private javafx.animation.Transition moveAnim;

        public SliderBar(double min, double max, double value) {
            this.min = min;
            this.max = max;
            this.value = value;
            setPrefHeight(16);
            setMinHeight(16);
            setMaxHeight(16);
            setCursor(Cursor.HAND);
            back.setMouseTransparent(true);
            fore.setMouseTransparent(true);
            dot.setMouseTransparent(true);
            // Pane 按 pref 重排 managed 子节点，滑条需自行定位
            back.setManaged(false);
            fore.setManaged(false);
            dot.setManaged(false);
            dot.getTransforms().add(dotScale);
            getChildren().addAll(back, fore, dot);
            widthProperty().addListener((o, a, b) -> layoutValue());
            applyColor(lineColor);
            layoutValue();
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (!dragging) colorTween(this, "uiSliderLine", this::applyColor, lineColor, Theme.accent, aniMs(40));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (!dragging && !isDisabled())
                    colorTween(this, "uiSliderLine", this::applyColor, lineColor, Theme.accentPale, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (isDisabled()) return;
                dragging = true;
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(40))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 1.3, EASE_OUT_FLUENT),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 1.3, EASE_OUT_FLUENT)));
                tl.play();
                colorTween(this, "uiSliderLine", this::applyColor, lineColor, Theme.accent, aniMs(40));
                jumpTo(e.getX());
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                if (!dragging) return;
                jumpTo(e.getX());
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                dragging = false;
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(200))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 1, EASE_OUT_FLUENT),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 1, EASE_OUT_FLUENT)));
                tl.play();
                colorTween(this, "uiSliderLine", this::applyColor, lineColor, isHover() ? Theme.accent : Theme.accentPale, aniMs(100));
            });
            disabledProperty().addListener((o, was, now) ->
                    applyColor(now ? Theme.gray5 : (dragging || isHover() ? Theme.accent : Theme.accentPale)));
        }

        private void applyColor(Color c) {
            lineColor = c;
            back.setBackground(fill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.3), 0));
            fore.setBackground(fill(c, 0));
            dot.setFill(c);
            dot.setStroke(c);
        }

        private void jumpTo(double x) {
            double trackW = Math.max(0, getWidth() - 10);
            double percent = Math.max(0, Math.min(1, (x - 5) / Math.max(1, trackW)));
            double v = min + percent * (max - min);
            if (max - min <= 200) v = Math.round(v);
            setValue(v, true);
        }

        public double getValue() {
            return value;
        }

        public void setValue(double v, boolean animate) {
            v = Math.max(min, Math.min(max, v));
            if (v == value) return;
            double old = value;
            value = v;
            if (animate && getWidth() > 10) {
                double delta = Math.abs(value / (max - min) - old / (max - min));
                int ms = Math.max(1, (int) ((1 - Math.pow(1 - delta, 3)) * 300));
                animateTo(ms);
            } else {
                layoutValue();
            }
            if (onChanged != null) onChanged.accept(value);
        }

        public void setOnChanged(java.util.function.DoubleConsumer c) {
            this.onChanged = c;
        }

        private void animateTo(int ms) {
            double fromX = dot.getLayoutX();
            double trackW = Math.max(0, getWidth() - 10);
            double targetX = (value - min) / (max - min) * trackW;
            if (moveAnim != null) moveAnim.stop();
            moveAnim = new javafx.animation.Transition() {
                {
                    setCycleDuration(Duration.millis(Math.max(1, ms)));
                    setInterpolator(EASE_OUT_FLUENT);
                }

                @Override
                protected void interpolate(double f) {
                    double x = fromX + (targetX - fromX) * f;
                    fore.resizeRelocate(0, 7, Math.max(0, x + 0.5), 2);
                    back.resizeRelocate(x + 0.5, 7.5, Math.max(0, getWidth() - 10 - x), 1);
                    dot.setLayoutX(x);
                }
            };
            moveAnim.play();
        }

        private void layoutValue() {
            double trackW = Math.max(0, getWidth() - 10);
            double x = (value - min) / (max - min) * trackW;
            fore.resizeRelocate(0, 7, Math.max(0, x + 0.5), 2);
            back.resizeRelocate(x + 0.5, 7.5, Math.max(0, getWidth() - 10 - x), 1);
            dot.setLayoutX(x);
        }
    }

    public static SliderBar sliderBar(double min, double max, double value) {
        return new SliderBar(min, max, value);
    }

    private static javafx.animation.Transition colorTween(java.util.function.Consumer<Color> setter,
                                                          Color from, Color to, int ms) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(Math.max(1, ms)));
            }

            @Override
            protected void interpolate(double f) {
                setter.accept(from.interpolate(to, f));
            }
        };
        t.play();
        return t;
    }

    private static javafx.animation.Transition colorTween(Node owner, Object slot,
                                                          java.util.function.Consumer<Color> setter,
                                                          Color from, Color to, int ms) {
        javafx.animation.Transition t = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(Math.max(1, ms)));
            }

            @Override
            protected void interpolate(double f) {
                setter.accept(from.interpolate(to, f));
            }
        };
        startExclusive(owner, slot, t);
        return t;
    }

    public static final class SearchBox extends StackPane {
        public final TextField field;
        private final StackPane clearBtn;
        private Timeline glowOn;
        private Timeline glowOff;

        public SearchBox(String hint) {
            setAlignment(Pos.CENTER_LEFT);
            setMinHeight(40);
            setPrefHeight(40);
            setMaxHeight(40);
            setBackground(fill(Theme.cardBg, 14));
            DropShadow glow = new DropShadow(5, 0, 0, Color.web("#343d4a", 0.07));
            setEffect(glow);
            setMaxWidth(Double.MAX_VALUE);
            field = new TextField();
            field.setPromptText(Lang.t(hint));
            field.setFont(Theme.font(13.5));
            field.setStyle("-fx-background-color: transparent;"
                    + "-fx-text-fill: " + web(Theme.text) + ";"
                    + "-fx-prompt-text-fill: " + web(Theme.gray4) + ";"
                    + "-fx-padding: 0 40 0 10;"
                    + "-fx-highlight-fill: " + web(Color.web("#7ebeea", 0.45)) + ";"
                    + "-fx-font-size: 13.5;");
            field.setBackground(Background.EMPTY);
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setMaxWidth(Double.MAX_VALUE);
            Node magnifier = Theme.icon("search", 14, Theme.text);
            clearBtn = iconButton("x", 24, 12, null, null);
            clearBtn.setOpacity(0);
            clearBtn.setMouseTransparent(true);
            clearBtn.setOnMouseClicked(e -> field.clear());
            Region leftPad = new Region();
            leftPad.setPrefWidth(6);
            Region rightPad = new Region();
            rightPad.setPrefWidth(4);
            HBox content = new HBox(leftPad, magnifier, field, rightPad, clearBtn);
            content.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(content);
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (glowOn == null) {
                    glowOn = new Timeline(new KeyFrame(Duration.millis(160),
                            new KeyValue(glow.radiusProperty(), 11, EASE_OUT_FLUENT),
                            new KeyValue(glow.colorProperty(), Color.web(Theme.accent.toString(), 0.16), EASE_OUT_FLUENT)));
                }
                glowOn.playFromStart();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (glowOff == null) {
                    glowOff = new Timeline(new KeyFrame(Duration.millis(220),
                            new KeyValue(glow.radiusProperty(), 5, EASE_OUT_FLUENT),
                            new KeyValue(glow.colorProperty(), Color.web("#343d4a", 0.07), EASE_OUT_FLUENT)));
                }
                glowOff.playFromStart();
            });
            field.textProperty().addListener((o, a, b) -> {
                boolean has = b != null && !b.isEmpty();
                Timeline t = new Timeline(new KeyFrame(Duration.millis(100),
                        new KeyValue(clearBtn.opacityProperty(), has ? 1 : 0)));
                t.play();
                clearBtn.setMouseTransparent(!has);
            });
        }

        public javafx.beans.property.Property<String> textProperty() {
            return field.textProperty();
        }

        public String getText() {
            return field.getText();
        }

        public void setText(String s) {
            field.setText(s);
        }
    }

    public static SearchBox searchBox(String hint) {
        return new SearchBox(hint);
    }

    public static ScrollPane scroll(Node content) {
        return scroll(content, SmoothScroll.DEFAULT_WHEEL_SPEED);
    }

    // wheelSpeed 每 pane 可调，弹窗小框要慢些对齐 WPF MyMsgText 的 DeltaMult=0.7
    public static ScrollPane scroll(Node content, double wheelSpeed) {
        if (content instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setBackground(Background.EMPTY);
        sp.setPannable(false);
        sp.setMaxWidth(Double.MAX_VALUE);
        sp.setMaxHeight(Double.MAX_VALUE);
        sp.setMinWidth(0);
        sp.setMinHeight(0);
        sp.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        SmoothScroll.install(sp, wheelSpeed);
        return sp;
    }

    public static ImageView image(String file, double w, double h, boolean rounded) {
        Image img = Theme.loadImage(file);
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(false);
        iv.setSmooth(false);
        if (rounded) {
            Rectangle clip = new Rectangle(w, h);
            clip.setArcWidth(7);
            clip.setArcHeight(7);
            iv.setClip(clip);
        }
        return iv;
    }

    public static StackPane avatar(Data.Account account, double size) {
        StackPane box = new StackPane();
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setBackground(fill(Color.web("#000000", 0.20), 4));
        Image skin = account == null ? null : Theme.loadImage(account.skin);
        if (skin == null) skin = Theme.loadImage("Steve.png");
        if (skin != null) {
            ImageView head = Theme.headImage(skin, size);
            box.getChildren().add(head);
        }
        return box;
    }

    public static class Loading extends StackPane {
        private static final String PICKAXE =
                "M 963.6 858.2 410.816 305.504 C 508.116 213.304 609.204 196.8 711.104 128.6 "
                        + "837.11367 49.573762 879.34045 50.334062 751.5 49.5 611.3 52 471.8 96.2 353.3 182.4 "
                        + "309.8 155.7 252.1 161.2 214.5 198.9 176.9 236.6 171.3 294.2 198 337.7 111.8 456.3 "
                        + "67.6 595.8 65.1 735.9 63.315254 883.82034 65.077966 837.29308 144.2 695.488 212.4 "
                        + "593.588 228.888 492.4 321.088 395.2 L 873.9 948 c 0.60001 0.59999 1.6 0.6 2.2 0 "
                        + "l 87.5 -87.5 c 0.6 -0.7 0.6 -1.6 0 -2.3 z";

        private static final double STAGE_W = 60, STAGE_H = 47;
        private final Timeline swing, debrisFly, debrisFade;
        private final javafx.scene.shape.SVGPath glyph;
        private final javafx.scene.shape.Rectangle bar;
        private final javafx.scene.transform.Rotate rotate;

        public Loading(double width, double height, String text) {
            setPrefSize(width, height);
            setMinSize(width, height);
            VBox box = new VBox(4);
            box.setAlignment(Pos.CENTER);
            boolean hasText = text != null && !text.isEmpty();
            double stageH = Math.max(18, Math.min(STAGE_H, height - (hasText ? 24 : 2)));
            double k = stageH / STAGE_H;

            rotate = new javafx.scene.transform.Rotate(55, 40, 36);

            glyph = new javafx.scene.shape.SVGPath();
            glyph.setContent(PICKAXE);
            glyph.setFill(Color.TRANSPARENT);
            glyph.setStroke(Theme.accent);
            javafx.geometry.Bounds b = glyph.getLayoutBounds();
            double gs = 35.0 / b.getHeight();
            glyph.setStrokeWidth(2 / gs); // 原始路径 963×948，缩放后补回 2px 线宽
            double gw = b.getWidth() * gs;
            glyph.setScaleX(gs);
            glyph.setScaleY(gs);
            glyph.setLayoutX(10 + gw / 2 - (b.getMinX() + b.getWidth() / 2));
            glyph.setLayoutY(6 + 17.5 - (b.getMinY() + b.getHeight() / 2));

            bar = new javafx.scene.shape.Rectangle(25, 2);
            bar.setFill(Theme.accent);
            bar.setLayoutY(STAGE_H - 2);

            javafx.scene.shape.SVGPath left = spark(-45);
            javafx.scene.shape.SVGPath right = spark(45);
            left.setLayoutX(7);
            left.setLayoutY(41);
            right.setLayoutX(14);
            right.setLayoutY(41);
            left.setOpacity(0);
            right.setOpacity(0);

            javafx.scene.Group pickaxe = new javafx.scene.Group(glyph);
            pickaxe.getTransforms().add(rotate);
            Pane stage = new Pane(bar, left, right, pickaxe);
            stage.setPrefSize(STAGE_W, STAGE_H);
            stage.setMinSize(STAGE_W, STAGE_H);
            stage.setMaxSize(STAGE_W, STAGE_H);
            javafx.scene.Group spin = new javafx.scene.Group(stage);
            spin.setScaleX(k);
            spin.setScaleY(k);
            spin.setLayoutX(30 * k - 30);
            spin.setLayoutY(STAGE_H / 2 * k - STAGE_H / 2);
            Pane holder = new Pane(spin);
            holder.setPrefSize(STAGE_W * k, STAGE_H * k);
            holder.setMinSize(STAGE_W * k, STAGE_H * k);
            holder.setMaxSize(STAGE_W * k, STAGE_H * k);

            box.getChildren().add(holder);
            if (hasText) {
                Label l = label(text, 16, Theme.text);
                l.setAlignment(Pos.CENTER);
                box.getChildren().add(l);
            }
            getChildren().add(box);

            swing = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(rotate.angleProperty(), 55)),
                    new KeyFrame(Duration.millis(250), new KeyValue(rotate.angleProperty(), 55)),
                    new KeyFrame(Duration.millis(600), new KeyValue(rotate.angleProperty(), -20, Interpolator.EASE_IN)),
                    new KeyFrame(Duration.millis(1500), new KeyValue(rotate.angleProperty(), 30, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(2400), new KeyValue(rotate.angleProperty(), 55, EASE_OUT_FLUENT)));
            swing.setCycleCount(Timeline.INDEFINITE);

            debrisFade = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(left.opacityProperty(), 0),
                            new KeyValue(right.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(1499), new KeyValue(left.opacityProperty(), 0),
                            new KeyValue(right.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(1500), new KeyValue(left.opacityProperty(), 1),
                            new KeyValue(right.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(1650), new KeyValue(left.opacityProperty(), 0),
                            new KeyValue(right.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(2400), new KeyValue(left.opacityProperty(), 0),
                            new KeyValue(right.opacityProperty(), 0)));
            debrisFade.setCycleCount(Timeline.INDEFINITE);
            debrisFly = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(left.translateXProperty(), 0), new KeyValue(left.translateYProperty(), 0),
                            new KeyValue(right.translateXProperty(), 0), new KeyValue(right.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(1500),
                            new KeyValue(left.translateXProperty(), 0), new KeyValue(left.translateYProperty(), 0),
                            new KeyValue(right.translateXProperty(), 0), new KeyValue(right.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(1680),
                            new KeyValue(left.translateXProperty(), -5, EASE_OUT_FLUENT),
                            new KeyValue(left.translateYProperty(), -6, EASE_OUT_FLUENT),
                            new KeyValue(right.translateXProperty(), 5, EASE_OUT_FLUENT),
                            new KeyValue(right.translateYProperty(), -6, EASE_OUT_FLUENT)),
                    new KeyFrame(Duration.millis(2400),
                            new KeyValue(left.translateXProperty(), 0), new KeyValue(left.translateYProperty(), 0),
                            new KeyValue(right.translateXProperty(), 0), new KeyValue(right.translateYProperty(), 0)));
            debrisFly.setCycleCount(Timeline.INDEFINITE);
        }

        private static javafx.scene.shape.SVGPath spark(double angle) {
            javafx.scene.shape.SVGPath p = new javafx.scene.shape.SVGPath();
            p.setContent("M0,0L2,0L1,4");
            p.setFill(Theme.accent);
            p.getTransforms().add(new javafx.scene.transform.Rotate(angle));
            return p;
        }

        private boolean wantRun;

        {
            // 离开场景就停表，被换下来的 Loading 不停会一直空转烧 CPU
            sceneProperty().addListener((o, oldScene, newScene) -> applyRunState());
        }

        public void start() {
            wantRun = true;
            applyRunState();
        }

        public void stop() {
            wantRun = false;
            applyRunState();
        }

        private void applyRunState() {
            boolean run = wantRun && getScene() != null;
            if (run) {
                glyph.setStroke(Theme.accent);
                bar.setFill(Theme.accent);
                if (swing.getStatus() != javafx.animation.Animation.Status.RUNNING) swing.play();
                if (debrisFly.getStatus() != javafx.animation.Animation.Status.RUNNING) debrisFly.play();
                if (debrisFade.getStatus() != javafx.animation.Animation.Status.RUNNING) debrisFade.play();
            } else {
                swing.stop();
                debrisFly.stop();
                debrisFade.stop();
            }
        }
    }

    public static StackPane extraTextButton(String icon, String text, Runnable action) {
        StackPane pill = new StackPane();
        pill.setMinHeight(52);
        pill.setPrefHeight(52);
        pill.setMaxHeight(52);
        pill.setCursor(Cursor.HAND);
        pill.setBackground(fill(Theme.accent, 21));
        pill.setEffect(Theme.shadow(10, 0, 0.2, Color.BLACK));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(0, 20, 0, 20));
        Node ic = Theme.icon(icon, 16, Color.WHITE);
        Label lab = label(text, 16, Color.WHITE);
        row.getChildren().addAll(ic, lab);
        pill.getChildren().add(row);

        pill.setOnMouseEntered(e -> pill.setBackground(fill(Theme.accentLight, 21)));
        pill.setOnMouseExited(e -> pill.setBackground(fill(Theme.accent, 21)));
        pill.setOnMousePressed(e -> pill.setScaleX(0.96));
        pill.setOnMouseReleased(e -> pill.setScaleX(1));
        if (action != null) pill.setOnMouseClicked(e -> action.run());
        pill.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return pill;
    }

    public static Node loadingCard(String text) {
        StackPane box = new StackPane();
        box.setPadding(new Insets(8, 0, 10, 0));
        box.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Loading loading = new Loading(200, 76, text);
        loading.start();
        box.getChildren().add(loading);
        return box;
    }

    public static Background fill(Color color, double radius) {
        return new Background(new BackgroundFill(color, new CornerRadii(radius), Insets.EMPTY));
    }

    public static Background glassFill(double radius) {
        double a = Theme.cardBg.getOpacity();
        Color top = Color.web("#ffffff", Math.min(1, a + 0.10));
        Color bottom = Color.web("#ffffff", Math.max(0.08, a * 0.86));
        return new Background(new BackgroundFill(
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, top), new Stop(0.62, top.interpolate(bottom, 0.35)), new Stop(1, bottom)),
                new CornerRadii(radius), Insets.EMPTY));
    }

    public static Border glassBorder(double radius) {
        return new Border(new BorderStroke(Color.web("#ffffff", 0.55), BorderStrokeStyle.SOLID,
                new CornerRadii(radius), new BorderWidths(1)));
    }

    public static Background fill(Color color, CornerRadii radii) {
        return new Background(new BackgroundFill(color, radii, Insets.EMPTY));
    }

    public static Border border(Color color, double radius, double width) {
        return new Border(new BorderStroke(color, BorderStrokeStyle.SOLID, new CornerRadii(radius),
                new BorderWidths(width)));
    }

    public static void tooltip(Node node, String text) {
        Tooltip tip = new Tooltip(Lang.t(text));
        tip.setFont(Theme.font(12));
        tip.setWrapText(true);
        tip.setMaxWidth(280);
        tip.setShowDelay(Duration.millis(350));
        tip.setHideDelay(Duration.millis(120));
        tip.setShowDuration(Duration.seconds(20));
        Tooltip.install(node, tip);
        node.getProperties().put("tclTooltip", tip);
    }
}
