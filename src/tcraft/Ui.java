package tcraft;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
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
import java.util.function.Consumer;

/** 自绘控件集合，统一外观和动画。 */
public final class Ui {

    public static final int BTN_NORMAL = 0;
    public static final int BTN_THEME = 1;
    public static final int BTN_HIGHLIGHT = 2;
    public static final int BTN_RED = 3;
    public static final int BTN_DARK = 4;

    private Ui() {
    }

    // SPLINE 控制点必须在 [0,1]，越界类初始化就崩，编译期发现不了。
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

    public static VBox col(double spacing, Node... nodes) {
        return new VBox(spacing, nodes);
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
        private boolean collapsed;
        private boolean collapsible;
        private double expandedHeight = -1;
        private Runnable lazyContent;
        private boolean lazyBuilt;

        public Card(String title) {
            // Ui.card(null) 会走无标题卡片，title 先归一成 "" 防 NPE。
            if (title == null) title = "";
            // 毛玻璃底：竖向渐变 + 1px 高光边，圆角 14。
            setBackground(glassFill(14));
            setBorder(glassBorder(14));
            setEffect(glow);
            setAlignment(Pos.TOP_LEFT);
            setMaxWidth(Double.MAX_VALUE);
            // 不设 clip，clip 会把 DropShadow 一起裁掉。

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
            // 无标题卡片隐藏标题行，省下 22px 空白。
            if (title.isEmpty()) {
                header.setVisible(false);
                header.setManaged(false);
                // 标题行不占位，内容自己留顶部内边距
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

            // 悬停点亮阴影，DropShadow 没有 opacity，用 color alpha 插值。
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                Timeline anim = new Timeline(new KeyFrame(Duration.millis(160),
                        new KeyValue(glow.radiusProperty(), 11, EASE_OUT_FLUENT),
                        new KeyValue(glow.colorProperty(), Color.web(Theme.accent.toString(), 0.16), EASE_OUT_FLUENT)));
                anim.play();
                titleLabel.setTextFill(Theme.accentDark);
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                Timeline anim = new Timeline(new KeyFrame(Duration.millis(220),
                        new KeyValue(glow.radiusProperty(), 5, EASE_OUT_FLUENT),
                        new KeyValue(glow.colorProperty(), Color.web("#343d4a", 0.07), EASE_OUT_FLUENT)));
                anim.play();
                titleLabel.setTextFill(Theme.text);
            });
        }

        public void add(Node... nodes) {
            content.getChildren().addAll(nodes);
        }

        /** 懒加载内容，首次展开才构建。 */
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
            collapsed = value;
            if (value) {
                expandedHeight = content.getHeight() > 0 ? content.getHeight() : content.prefHeight(getWidth() - 30);
                content.setVisible(false);
                content.setManaged(false);
            } else {
                ensureContent();
                content.setVisible(true);
                content.setManaged(true);
            }
            updateChevron();
        }

        public void toggle() {
            if (!collapsible) return;
            ensureContent();
            boolean collapse = !collapsed;
            // 展开时每帧都在改高度，辉光阴影逐帧重糊一整张卡片是卡顿大头；
            // 动画期间先摘掉阴影，再把内容子树缓存成贴图，结束后还原。
            setEffect(null);
            content.setCache(true);
            content.setCacheHint(CacheHint.SPEED);
            if (collapse) {
                expandedHeight = Math.max(20, content.getHeight());
                content.setVisible(true);
                content.setManaged(true);
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(content.maxHeightProperty(), expandedHeight)),
                        new KeyFrame(Duration.millis(180), new KeyValue(content.maxHeightProperty(), 0,
                                Interpolator.EASE_IN)));
                t.setOnFinished(e -> {
                    content.setVisible(false);
                    content.setManaged(false);
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                });
                t.play();
            } else {
                content.setVisible(true);
                content.setManaged(true);
                content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                applyCss();
                content.applyCss();
                double target = Math.max(20, content.prefHeight(Math.max(100, getWidth() - 30)));
                content.setMaxHeight(0);
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(content.maxHeightProperty(), 0)),
                        new KeyFrame(Duration.millis(240), new KeyValue(content.maxHeightProperty(),
                                target, Interpolator.EASE_OUT)));
                t.setOnFinished(e -> {
                    content.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    animationCleanup();
                });
                t.play();
            }
            collapsed = collapse;
            updateChevron();
        }

        /** 高度动画结束：恢复辉光，撤掉子树缓存。 */
        private void animationCleanup() {
            setEffect(glow);
            content.setCache(false);
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
        // 按钮阴影：模糊 9、深度 2、不透明度 0.22
        pane.setEffect(Theme.shadow(9, 2, 0.22, Color.BLACK));
        // 文字色跟描边同色，悬停时一起变。
        final Color baseBg;
        final Color baseBorder;
        final Color hoverBg;
        final Color hoverBorder;
        final Color textColor;
        final Color hoverText;
        switch (type) {
            // Theme：实心主题底 + 白字，无边框
            case BTN_THEME -> {
                baseBg = Theme.accentDark;
                baseBorder = Color.TRANSPARENT;
                hoverBg = Theme.accent;
                hoverBorder = Color.TRANSPARENT;
                textColor = Color.WHITE;
                hoverText = Color.WHITE;
            }
            // Red：半透明白底 + 深红描边
            case BTN_RED -> {
                baseBg = Theme.halfWhite;
                baseBorder = Theme.redDark;
                hoverBg = Theme.redBack;
                hoverBorder = Theme.red;
                textColor = Theme.redDark;
                hoverText = Theme.red;
            }
            // Dark：深色半透明底 + 白字白描边
            case BTN_DARK -> {
                baseBg = Theme.darkBtnBg;
                baseBorder = Color.WHITE;
                hoverBg = Theme.darkBtnHover;
                hoverBorder = Color.WHITE;
                textColor = Color.WHITE;
                hoverText = Color.WHITE;
            }
            // Highlight：半透明白底 + 主题色描边
            case BTN_HIGHLIGHT -> {
                baseBg = Theme.halfWhite;
                baseBorder = Theme.accentDark;
                hoverBg = Theme.brush7;
                hoverBorder = Theme.accent;
                textColor = Theme.accentDark;
                hoverText = Theme.accent;
            }
            // Normal：半透明白底 + 文字色描边
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
        // 进 100ms，出 200ms；Theme 两向都 100ms
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
        // 禁用态：Theme 文字变淡，Dark 描边调灰，其余走 gray4
        pane.disabledProperty().addListener((o, was, now) -> {
            if (now) {
                if (type == BTN_THEME) {
                    // 保持主题色底，别淡到看不清版本号
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

    /** ease 为 null 时线性。 */
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
        t.play();
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
        t.play();
    }

    public static void scale(Node node, double target) {
        ScaleTransition st = new ScaleTransition(Duration.millis(target < 1 ? 60 : 200), node);
        st.setToX(target);
        st.setToY(target);
        st.setInterpolator(target < 1 ? EASE_OUT_FLUENT_STRONG : EASE_OUT_FLUENT);
        st.play();
    }

    /** 菜单卡片的一行，separator() 是分隔线。 */
    public static final class MenuRow {
        final String title;
        final String icon;
        final Runnable action;
        final boolean sep;

        private MenuRow(String title, String icon, Runnable action, boolean sep) {
            this.title = title;
            this.icon = icon;
            this.action = action;
            this.sep = sep;
        }

        public static MenuRow of(String title, String icon, Runnable action) {
            return new MenuRow(title, icon, action, false);
        }

        public static MenuRow separator() {
            return new MenuRow(null, null, null, true);
        }
    }

    /** 自绘菜单卡片：圆角 10，行高 34。 */
    public static javafx.stage.Popup menuCard(javafx.scene.Node anchor, MenuRow... rows) {
        VBox card = menuCardShell(210);

        for (MenuRow row : rows) {
            if (row.sep) {
                Region sep = new Region();
                sep.setMinHeight(1);
                sep.setMaxHeight(1);
                sep.setBackground(fill(Theme.gray6, 0));
                VBox.setMargin(sep, new Insets(5, 8, 5, 8));
                card.getChildren().add(sep);
                continue;
            }
            ListItem item = new ListItem(Theme.icon(row.icon, 16, Theme.text), row.title, null, 34);
            item.setOnMouseClicked(e -> {
                closeMenuCard((javafx.stage.Popup) card.getProperties().get("popup"));
                if (row.action != null) row.action.run();
            });
            card.getChildren().add(item);
        }

        return showMenuCard(anchor, card);
    }

    /** 自定义内容的菜单卡片，外壳和动画同 menuCard。 */
    public static javafx.stage.Popup menuCardNode(javafx.scene.Node anchor,
                                                  javafx.scene.Node content, double width) {
        VBox card = menuCardShell(width);
        card.getChildren().add(content);
        return showMenuCard(anchor, card);
    }

    /** 菜单卡片外壳：圆角 10、内边距 8。 */
    private static VBox menuCardShell(double width) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(8));
        // 只设下限，锁死宽度会让文字溢出卡片。
        card.setMinWidth(width);
        card.setBackground(fill(Theme.cardBg, 10));
        card.setEffect(Theme.shadow(18, 3, 0.35, Theme.text));
        return card;
    }

    /** 放进弹层：量出真实宽度后右对齐锚点，再展开。 */
    private static javafx.stage.Popup showMenuCard(javafx.scene.Node anchor, VBox card) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        card.getProperties().put("popup", popup);

        // 宽度要等布局，先隐身，避免闪一下再跳位。
        card.setOpacity(0);
        card.setScaleX(0.94);
        card.setScaleY(0.94);
        card.setTranslateY(10);
        popup.getContent().add(card);

        // 别用 show(anchor, x, y)，锚定定位会顶掉后面的 setX/setY。
        javafx.geometry.Bounds ab = anchor.localToScreen(anchor.getBoundsInLocal());
        javafx.stage.Window owner = anchor.getScene() == null ? null : anchor.getScene().getWindow();
        double w0 = card.prefWidth(-1);
        if (w0 <= 1) w0 = 210;   // 算不出来就按最小宽度摆
        double showX = Math.max(0, ab.getMaxX() - w0);
        double showY = Math.max(0, ab.getMaxY() + 2);
        if (owner != null) popup.show(owner, showX, showY);
        else popup.show(anchor, showX, showY);
        placeMenuCard(popup, card, anchor);
        return popup;
    }

    /** 展开：130ms 淡入，220ms 缩放归位。 */
    private static void expandIn(VBox card) {
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

    /** 布局后校正弹层位置，按实际宽度右对齐。 */
    private static void placeMenuCard(javafx.stage.Popup popup, javafx.scene.layout.VBox card,
                                      javafx.scene.Node anchor) {
        // 弹层是独立窗口，要等布局完再量；延后 60ms，必要时重试。
        attemptPlace(popup, card, anchor, 0);
    }

    private static void attemptPlace(javafx.stage.Popup popup, javafx.scene.layout.VBox card,
                                     javafx.scene.Node anchor, int tries) {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(tries == 0 ? 60 : 40));
        wait.setOnFinished(ev -> {
            javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
            if (b == null) {
                expandIn(card);
                return;
            }
            if (card.getWidth() <= 1 && tries < 4) {   // 还没量出宽度，稍后重试
                attemptPlace(popup, card, anchor, tries + 1);
                return;
            }
            // 只取布局宽度，boundsInLocal 含 18px 阴影。
            double cw = card.getWidth();
            if (cw <= 1) cw = card.prefWidth(-1);
            double ch = card.getHeight() > 1 ? card.getHeight() : card.prefHeight(cw);
            javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
            double x = Math.max(vb.getMinX(), b.getMaxX() - cw);  // 右边缘对齐锚点
            double below = b.getMaxY() + 2;
            double y = below + ch <= vb.getMaxY() ? below : b.getMinY() - ch - 2; // 下方放不下就翻上去
            if (Math.abs(popup.getX() - x) > 1) popup.setX(x);
            if (Math.abs(popup.getY() - y) > 1) popup.setY(Math.max(vb.getMinY(), y));
            expandIn(card);   // 位置对好再淡入，看不到挪位
        });
        wait.play();
    }

    /** 收起菜单卡片：淡出并缩到 0.96 后关闭。 */
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

    /** 带图标的文字按钮：药丸高 27，图标 16。 */
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
            colorTween(c -> tint(glyph, c), baseFg, Theme.accent, aniMs(100));
            colorTween(c -> lab.setTextFill(c), baseFg, Theme.accent, aniMs(100));
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            animateBg(pane, Theme.listHoverBg, Theme.semiTransparent, aniMs(150));
            colorTween(c -> tint(glyph, c), Theme.accent, baseFg, aniMs(150));
            colorTween(c -> lab.setTextFill(c), Theme.accent, baseFg, aniMs(150));
        });
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                e -> animateBg(pane, pane.getBackground() == null ? Theme.halfWhite : Theme.listHoverBg,
                        Theme.accentPale2, aniMs(70)));
        pane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            boolean over = pane.isHover();
            animateBg(pane, Theme.accentPale2, over ? Theme.listHoverBg : Theme.semiTransparent, aniMs(100));
            if (!over) {
                colorTween(c -> tint(glyph, c), Theme.accent, baseFg, aniMs(150));
                colorTween(c -> lab.setTextFill(c), Theme.accent, baseFg, aniMs(150));
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

    /** 白主题图标按钮，悬停只加白底。 */
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
            // 悬停是遮罩渐显，120ms 进、180ms 出。
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                hover = true;
                animateBack(hoverFill(), 120);
                refreshColor();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                hover = false;
                pressed = false;
                animateBack(Color.TRANSPARENT, 180);
                scaleTo(1, 180); // 移出时复位缩放
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

        /** 悬停底色。 */
        private Color hoverFill() {
            return Theme.listHoverBg;
        }

        /** 按下底色，比悬停深一档。 */
        private Color pressFill() {
            return Theme.accentPale2;
        }

        /** 背景色渐变，省掉一层透明度节点。 */
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
            tl.play();
        }

        public void setSelected(boolean value) {
            selected = value;
            refreshColor();
        }

        public boolean isSelected() {
            return selected;
        }

        /** 列表行右侧的操作按钮，默认悬停才显示。 */
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
            // 底色归 animateBack，选中靠左侧竖条和文字色
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

    /** 输入框：圆角 3，聚焦用描边强调。 */
    public static TextField field(String hint) {
        TextField tf = new TextField();
        tf.setPromptText(Lang.t(hint));
        tf.setFont(Theme.font(13));
        tf.setPrefHeight(30);
        tf.setStyle(fieldStyle(Theme.fieldBg, Theme.accentPale));
        // 三态直接瞬变，不做颜色动画
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

    /** 下拉框，展开时弹层有缩放动画。 */
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
        // 展开时底色变亮，弹层出现后播动画
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

    /** 弹层展开：缩放归位并上浮，220ms。 */
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

    /** 提示条：基色乘组件透明度，配描边和深色文字。 */
    public static HBox hint(String text, int type) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(9, 12, 9, 12));
        // 底色 alpha 跟着 UiComponentOpacity 缩放
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
                // 蓝色提示条也按主题色走，紫色主题里不再出现蓝色提示框
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

    /** 时长按 UiAnimSpeed 缩放，0 表示不动画。 */
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

    /** 复选框：18×18 圆角框，勾选时对勾弹出。 */
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
            // 静态初值，不走动画
            checkScale.setX(checked ? 1 : 0);
            checkScale.setY(checked ? 1 : 0);
            box.setBorder(border(checked ? Theme.accentDark : Theme.text, 3, 1.1));
            check.setFill(checked ? Theme.accentDark : Theme.text);
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (isDisabled()) return;
                colorTween(c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.accent, aniMs(100));
                colorTween(boxBorderProperty(), currentBorder(), Theme.accent, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (pressed) {
                    pressed = false;
                    releaseVisual();
                }
                if (isDisabled()) return;
                colorTween(c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.text, aniMs(200));
                colorTween(boxBorderProperty(), currentBorder(),
                        !isDisabled() ? (selected ? Theme.accentDark : Theme.text) : Theme.gray5, aniMs(200));
                box.setBackground(fill(Theme.halfWhite, 3));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (isDisabled()) return;
                pressed = true;
                // 按下：框缩到 16.5
                box.setBackground(fill(Theme.listHoverBg, 3));
                int ms = aniMs(1000);
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, ms)),
                        new javafx.animation.KeyValue(boxScale.xProperty(), 16.5 / 18.0, EASE_OUT_FLUENT_STRONG),
                        new javafx.animation.KeyValue(boxScale.yProperty(), 16.5 / 18.0, EASE_OUT_FLUENT_STRONG)));
                tl.play();
                if (selected) {
                    javafx.animation.Timeline cl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                            Duration.millis(Math.max(1, ms)),
                            new javafx.animation.KeyValue(checkScale.xProperty(), 0.9, EASE_OUT_FLUENT_STRONG),
                            new javafx.animation.KeyValue(checkScale.yProperty(), 0.9, EASE_OUT_FLUENT_STRONG)));
                    cl.play();
                }
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                if (!pressed) return;
                pressed = false;
                releaseVisual();
                setSelected(!selected);
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
            // 直接写回新 Border，1.1px、半径 3
            return new javafx.beans.property.SimpleObjectProperty<>() {
                @Override
                public void set(Color c) {
                    box.setBorder(border(c, 3, 1.1));
                    check.setFill(c);
                }
            };
        }

        private void releaseVisual() {
            // 底色回白，框和勾复位
            box.setBackground(fill(Theme.halfWhite, 3));
            int ms = aniMs(400);
            javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(Math.max(1, ms)),
                    new javafx.animation.KeyValue(boxScale.xProperty(), 1, EASE_OUT_FLUENT_STRONG),
                    new javafx.animation.KeyValue(boxScale.yProperty(), 1, EASE_OUT_FLUENT_STRONG)));
            tl.play();
            javafx.animation.Timeline cl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(Math.max(1, aniMs(500))),
                    new javafx.animation.KeyValue(checkScale.xProperty(), 1, EASE_OUT_FLUENT_STRONG),
                    new javafx.animation.KeyValue(checkScale.yProperty(), 1, EASE_OUT_FLUENT_STRONG)));
            cl.play();
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            if (value == selected) return;
            selected = value;
            // 勾选 300ms 弹出，取消 135ms 收回
            if (selected) {
                int delay = aniMs(105);
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(300))),
                        new javafx.animation.KeyValue(checkScale.xProperty(), 1, EASE_OUT_BACK),
                        new javafx.animation.KeyValue(checkScale.yProperty(), 1, EASE_OUT_BACK)));
                tl.setDelay(Duration.millis(delay));
                tl.play();
                colorTween(boxBorderProperty(), currentBorder(),
                        isHover() && !isDisabled() ? Theme.accent : Theme.accentDark, aniMs(150));
            } else {
                javafx.animation.Transition t = new javafx.animation.Transition() {
                    {
                        setCycleDuration(Duration.millis(Math.max(1, aniMs(135))));
                    }

                    @Override
                    protected void interpolate(double f) {
                        checkScale.setX(1 - f);
                        checkScale.setY(1 - f);
                    }
                };
                t.setInterpolator(fluentIn(2));
                t.play();
                colorTween(boxBorderProperty(), currentBorder(), Theme.text, aniMs(150));
            }
        }

        public void setOnAction(Runnable action) {
            this.onAction = action;
        }
    }

    public static Check checkBox(String text, boolean checked) {
        return new Check(text, checked);
    }

    /** 单选框：18×18 圆框，选中会取消同容器的其他项。 */
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

        /** 选中态属性，可做绑定。 */
        public javafx.beans.property.BooleanProperty selectedProperty() {
            return selectedProp;
        }

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
                colorTween(c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.accent, aniMs(100));
                ringColorTween(currentRing(), Theme.accent, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (isDisabled()) return;
                colorTween(c -> { textColor = c; lab.setTextFill(c); }, textColor, Theme.text, aniMs(200));
                ringColorTween(currentRing(), selected ? Theme.accentDark : Theme.text, aniMs(200));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                if (isDisabled()) return;
                if (selected) return;
                // 取消同容器其他单选框
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
            t.play();
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            if (value == selected) return;
            selected = value;
            selectedProp.set(value);
            // 圆点弹出，描边变色
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

    /** 滑动条：16 高轨道，拖动时弹数值气泡。 */
    public static final class SliderBar extends Pane {
        private final Region back = new Region();
        private final Region fore = new Region();
        private final javafx.scene.shape.Ellipse dot = new javafx.scene.shape.Ellipse(5, 5);
        private final javafx.scene.transform.Scale dotScale = new javafx.scene.transform.Scale(1, 1, 5, 5);
        private final double min;
        private final double max;
        private double value;
        private boolean dragging;
        private Color lineColor = Theme.accentPale;
        private java.util.function.DoubleConsumer onChanged;
        private java.util.function.Function<Double, String> hintFn;
        private javafx.stage.Popup hintPopup;
        private Label hintLabel;
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
            dot.getTransforms().add(dotScale);
            getChildren().addAll(back, fore, dot);
            widthProperty().addListener((o, a, b) -> layoutValue(false));
            applyColor(lineColor);
            layoutValue(false);
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                if (!dragging) colorTween(this::applyColor, lineColor, Theme.accent, aniMs(40));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (!dragging && !isDisabled())
                    colorTween(this::applyColor, lineColor, Theme.accentPale, aniMs(100));
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (isDisabled()) return;
                dragging = true;
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(40))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 1.3, EASE_OUT_FLUENT),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 1.3, EASE_OUT_FLUENT)));
                tl.play();
                colorTween(this::applyColor, lineColor, Theme.accent, aniMs(40));
                jumpTo(e.getX());
                showHint();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                if (!dragging) return;
                jumpTo(e.getX());
                showHint();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                dragging = false;
                javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        Duration.millis(Math.max(1, aniMs(200))),
                        new javafx.animation.KeyValue(dotScale.xProperty(), 1, EASE_OUT_FLUENT),
                        new javafx.animation.KeyValue(dotScale.yProperty(), 1, EASE_OUT_FLUENT)));
                tl.play();
                colorTween(this::applyColor, lineColor, isHover() ? Theme.accent : Theme.accentPale, aniMs(100));
                hideHint();
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
            // 时长按进度差算，拖动即时生效
            if (animate && getWidth() > 10) {
                double delta = Math.abs(value / (max - min) - old / (max - min));
                int ms = Math.max(1, (int) ((1 - Math.pow(1 - delta, 3)) * 300));
                animateTo(ms);
            } else {
                layoutValue(false);
            }
            if (onChanged != null) onChanged.accept(value);
        }

        public void setOnChanged(java.util.function.DoubleConsumer c) {
            this.onChanged = c;
        }

        public void setHintFn(java.util.function.Function<Double, String> fn) {
            this.hintFn = fn;
        }

        private void animateTo(int ms) {
            double fromW = fore.getWidth();
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

        private void layoutValue(boolean animate) {
            double trackW = Math.max(0, getWidth() - 10);
            double x = (value - min) / (max - min) * trackW;
            fore.resizeRelocate(0, 7, Math.max(0, x + 0.5), 2);
            back.resizeRelocate(x + 0.5, 7.5, Math.max(0, getWidth() - 10 - x), 1);
            dot.setLayoutX(x);
        }

        private void showHint() {
            if (hintFn == null) return;
            if (hintPopup == null) {
                hintLabel = new Label();
                hintLabel.setFont(Theme.font(12));
                hintLabel.setTextFill(Theme.text);
                hintLabel.setPadding(new Insets(5, 7, 5, 7));
                javafx.scene.layout.BorderStroke bs = new javafx.scene.layout.BorderStroke(
                        Theme.text, javafx.scene.layout.BorderStrokeStyle.SOLID,
                        new javafx.scene.layout.CornerRadii(3), new javafx.scene.layout.BorderWidths(1.5));
                javafx.scene.layout.BorderStroke[] strokes = {bs};
                javafx.scene.layout.BorderPane bp = new javafx.scene.layout.BorderPane(hintLabel);
                bp.setBorder(new javafx.scene.layout.Border(strokes));
                bp.setBackground(fill(Color.WHITE, 3));
                bp.setEffect(Theme.shadow(4, 2, 0.4, Color.BLACK));
                hintPopup = new javafx.stage.Popup();
                hintPopup.setAutoFix(true);
                hintPopup.getContent().add(bp);
            }
            String text = hintFn.apply(value);
            hintLabel.setText(text);
            if (!hintPopup.isShowing()) {
                javafx.geometry.Bounds b = dot.localToScreen(dot.getBoundsInLocal());
                if (b == null) return;
                double w = hintLabel.prefWidth(-1) + 16;
                hintPopup.show(dot, b.getMinX() - w / 2 + 5, b.getMaxY() + 5);
            }
        }

        private void hideHint() {
            if (hintPopup != null) hintPopup.hide();
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

    /** 搜索框：40 高卡片，有文字才显示清除钮。 */
    public static final class SearchBox extends StackPane {
        public final TextField field;
        private final StackPane clearBtn;

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
            // 悬停点亮阴影，同卡片
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                Timeline anim = new Timeline(new KeyFrame(Duration.millis(160),
                        new KeyValue(glow.radiusProperty(), 11, EASE_OUT_FLUENT),
                        new KeyValue(glow.colorProperty(), Color.web(Theme.accent.toString(), 0.16), EASE_OUT_FLUENT)));
                anim.play();
            });
            addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                Timeline anim = new Timeline(new KeyFrame(Duration.millis(220),
                        new KeyValue(glow.radiusProperty(), 5, EASE_OUT_FLUENT),
                        new KeyValue(glow.colorProperty(), Color.web("#343d4a", 0.07), EASE_OUT_FLUENT)));
                anim.play();
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

        public void textChanged(java.util.function.Consumer<String> c) {
            field.textProperty().addListener((o, a, b) -> c.accept(b == null ? "" : b));
        }
    }

    public static SearchBox searchBox(String hint) {
        return new SearchBox(hint);
    }

    public static ScrollPane scroll(Node content) {
        if (content instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setBackground(Background.EMPTY);
        sp.setPannable(false);
        sp.setMaxWidth(Double.MAX_VALUE);
        sp.setMaxHeight(Double.MAX_VALUE);
        // min 归零，别让内容把右栏顶宽、撑出窗口。
        sp.setMinWidth(0);
        sp.setMinHeight(0);
        // 清 viewport 白底只能用 -fx-background-color，别用 -fx-background。
        sp.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        // viewport 皮肤生成后才存在，就绪后再清一次底
        sp.skinProperty().addListener((o, a, b) -> {
            Node vp = sp.lookup(".viewport");
            if (vp != null) vp.setStyle("-fx-background-color: transparent;");
            // 拖动时完全不透明，其余交给 style.css
            Node thumb = sp.lookup(".scroll-bar .thumb");
            if (thumb != null) {
                thumb.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        e -> thumb.setStyle("-fx-background-color: " + web(Theme.accentLight) + "; -fx-opacity: 1;"));
                thumb.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                        e -> thumb.setStyle(null));
            }
        });
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

    /** 60×47 舞台上挥镐子，砸地甩出碎屑，循环。 */
    public static class Loading extends StackPane {
        private static final String PICKAXE =
                "M 963.6 858.2 410.816 305.504 C 508.116 213.304 609.204 196.8 711.104 128.6 "
                        + "837.11367 49.573762 879.34045 50.334062 751.5 49.5 611.3 52 471.8 96.2 353.3 182.4 "
                        + "309.8 155.7 252.1 161.2 214.5 198.9 176.9 236.6 171.3 294.2 198 337.7 111.8 456.3 "
                        + "67.6 595.8 65.1 735.9 63.315254 883.82034 65.077966 837.29308 144.2 695.488 212.4 "
                        + "593.588 228.888 492.4 321.088 395.2 L 873.9 948 c 0.60001 0.59999 1.6 0.6 2.2 0 "
                        + "l 87.5 -87.5 c 0.6 -0.7 0.6 -1.6 0 -2.3 z";

        /** 舞台逻辑尺寸 60×47，实际渲染时整体等比缩放。 */
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

            // 旋转枢轴在舞台 (40,36)
            rotate = new javafx.scene.transform.Rotate(55, 40, 36);

            // 镐子按高 35 缩放，左上角落在 (10,6)
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

            // 镐子落地的那条横线
            bar = new javafx.scene.shape.Rectangle(25, 2);
            bar.setFill(Theme.accent);
            bar.setLayoutY(STAGE_H - 2); // 贴着舞台底边

            // 砸地甩出的碎屑
            javafx.scene.shape.SVGPath left = spark(-45);
            javafx.scene.shape.SVGPath right = spark(45);
            left.setLayoutX(7);
            left.setLayoutY(41);
            right.setLayoutX(14);
            right.setLayoutY(41);
            left.setOpacity(0);
            right.setOpacity(0);

            // rotate 只挂镐子，横条和碎屑是兄弟节点，不跟着转。
            javafx.scene.Group pickaxe = new javafx.scene.Group(glyph);
            pickaxe.getTransforms().add(rotate);
            Pane stage = new Pane(bar, left, right, pickaxe);
            stage.setPrefSize(STAGE_W, STAGE_H);
            stage.setMinSize(STAGE_W, STAGE_H);
            stage.setMaxSize(STAGE_W, STAGE_H);
            javafx.scene.Group spin = new javafx.scene.Group(stage);
            // 整体缩放绕舞台中心，再把左上角对到控件框原点
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
                Label l = label(text, 16, Theme.text); // 文字字号 16
                l.setAlignment(Pos.CENTER);
                box.getChildren().add(l);
            }
            getChildren().add(box);

            // 55° 定格，蓄力到 -20°，砸到 30°，抬回 55°，无缝循环。
            swing = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(rotate.angleProperty(), 55)),
                    new KeyFrame(Duration.millis(250), new KeyValue(rotate.angleProperty(), 55)),
                    new KeyFrame(Duration.millis(600), new KeyValue(rotate.angleProperty(), -20, Interpolator.EASE_IN)),
                    new KeyFrame(Duration.millis(1500), new KeyValue(rotate.angleProperty(), 30, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(2400), new KeyValue(rotate.angleProperty(), 55, EASE_OUT_FLUENT)));
            swing.setCycleCount(Timeline.INDEFINITE);

            // 碎屑砸地才出现，150ms 内淡出。
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
            // 碎屑砸地后 180ms 内飞出，末尾复位
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

        /** 希望运行的状态，进场景后才真播。 */
        private boolean wantRun;

        {
            // 离开场景就停表，避免被替换掉的 Loading 一直空转烧 CPU。
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
                // 起播时重新取色，主题可能换过
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

    /** 52 高胶囊按钮，主题色底 + 白字。 */
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

    /** 页面内的镐子加载卡片。 */
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

    /** 展开或收起子块，用 maxHeight 动画；动画期间把子树缓存成贴图省掉逐帧重绘。 */
    public static void animateExpand(javafx.scene.layout.Region node, boolean show) {
        if (show == node.isManaged()) return;
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
        if (show) {
            node.setVisible(true);
            node.setManaged(true);
            node.setMaxHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
            node.applyCss();
            double target = Math.max(1, node.prefHeight(node.getWidth() > 1 ? node.getWidth() : 320));
            node.setMaxHeight(0);
            node.setOpacity(0);
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(node.maxHeightProperty(), 0),
                            new KeyValue(node.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(240),
                            new KeyValue(node.maxHeightProperty(), target, EASE_OUT_FLUENT),
                            new KeyValue(node.opacityProperty(), 1)));
            t.setOnFinished(e -> {
                node.setMaxHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                node.setCache(false);
            });
            t.play();
        } else {
            double from = node.getHeight() > 1 ? node.getHeight() : node.prefHeight(-1);
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(node.maxHeightProperty(), from),
                            new KeyValue(node.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(node.maxHeightProperty(), 0, Interpolator.EASE_IN),
                            new KeyValue(node.opacityProperty(), 0)));
            t.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
                node.setMaxHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
                node.setOpacity(1);
                node.setCache(false);
            });
            t.play();
        }
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

    /** 毛玻璃卡片的 1px 高光边，缺了就像贴纸。 */
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

        public static void translateY(Node node, double from, double to, int ms) {
        node.setTranslateY(from);
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(from);
        tt.setToY(to);
        tt.setInterpolator(Interpolator.EASE_OUT);
        tt.play();
    }

    /** 统一悬浮提示，350ms 后出现。 */
    public static void tooltip(Node node, String text) {
        Tooltip tip = new Tooltip(Lang.t(text));
        tip.setFont(Theme.font(12));
        tip.setWrapText(true);
        tip.setMaxWidth(280);
        tip.setShowDelay(Duration.millis(350));
        tip.setHideDelay(Duration.millis(120));
        tip.setShowDuration(Duration.seconds(20));
        Tooltip.install(node, tip);
    }

                        private static void unused(Consumer<?> c) {
    }
}
