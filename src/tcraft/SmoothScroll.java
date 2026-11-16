package tcraft;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** 滚轮平滑滚动：先累积像素目标，再逐帧写入 vvalue。 */
final class SmoothScroll {

    /** 防重复安装。 */
    private static final Object KEY = new Object();

    /** 滚轮 delta 到像素的倍率。 */
    private static final double WHEEL_SPEED = 3.0;

    /** 临界阻尼弹簧的角频率。 */
    private static final double OMEGA = 20.0;

    /** 足够接近时直接吸附。 */
    private static final double SNAP = 0.25;

    /** 单帧最大积分时长。 */
    private static final double MAX_FRAME = 0.05;

    /** 积分子步长。 */
    private static final double SUB_STEP = 0.008;

    private final ScrollPane pane;
    private final Axis x = new Axis();
    private final Axis y = new Axis();
    private final AnimationTimer timer;
    private long lastFrame;
    private boolean ticking;
    /** 自己写入 vvalue 时置位。 */
    private boolean writing;
    /** 滚动期间临时摘掉的特效。 */
    private final List<EffectNode> suspended = new ArrayList<>();
    private PauseTransition restoreDelay;
    private boolean effectsSuspended;

    static void install(ScrollPane pane) {
        if (pane == null || pane.getProperties().get(KEY) instanceof SmoothScroll) return;
        SmoothScroll scroll = new SmoothScroll(pane);
        pane.getProperties().put(KEY, scroll);
        // 过滤阶段先拦下，避免原生 skin 再滚一次
        pane.addEventFilter(ScrollEvent.SCROLL, scroll::onScroll);
        // 按下滚动条或列表项时停止缓动
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> scroll.stop());
        pane.sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                scroll.stop();
                scroll.resumeEffects();
            }
        });
        pane.visibleProperty().addListener((o, a, b) -> {
            if (!b) scroll.stop();
        });
        // 外部改位置时让位，同时进入滚动降载
        pane.vvalueProperty().addListener((o, a, b) -> {
            if (!scroll.writing) {
                scroll.stop();
                scroll.suspendEffects();
            }
        });
        pane.hvalueProperty().addListener((o, a, b) -> {
            if (!scroll.writing) {
                scroll.stop();
                scroll.suspendEffects();
            }
        });
    }

    private SmoothScroll(ScrollPane pane) {
        this.pane = pane;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
    }

    private void onScroll(ScrollEvent e) {
        if (e.getEventType() != ScrollEvent.SCROLL || e.isConsumed()) return;
        // 子控件自带滚动区时优先给它
        if (insideNestedScroller(e.getTarget())) return;
        double dx = e.getDeltaX();
        double dy = e.getDeltaY();
        if (dx == 0 && dy == 0) return;
        // Shift + 滚轮是横向滚动
        if (e.isShiftDown() && dx == 0) {
            dx = dy;
            dy = 0;
        }
        double px = -dx * WHEEL_SPEED;
        double py = -dy * WHEEL_SPEED;
        // 只有横向溢出时让滚轮横移
        if (py != 0 && overflow(false) <= 0.5 && overflow(true) > 0.5) {
            px = py;
            py = 0;
        } else if (px != 0 && overflow(true) <= 0.5 && overflow(false) > 0.5) {
            py += px;
            px = 0;
        }

        // 触摸板/触屏直接跟手
        boolean gesture = e.isDirect() || e.getTouchCount() > 0 || e.isInertia();
        boolean moved;
        if (gesture || instant()) {
            moved = scrollBy(true, px);
            moved |= scrollBy(false, py);
        } else {
            moved = queue(true, px);
            moved |= queue(false, py);
        }
        // 到边界就放行给外层
        if (moved) {
            suspendEffects();
            e.consume();
        }
    }

    /** 事件目标是否落在自带滚动能力的子控件里。 */
    private boolean insideNestedScroller(javafx.event.EventTarget target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null && node != pane) {
            if (node instanceof TextArea || node instanceof ListView
                    || node instanceof TableView || node instanceof TreeView) {
                return true;
            }
            // 子 ScrollPane 自己处理
            if (node instanceof ScrollPane) return true;
            node = node.getParent();
        }
        return false;
    }

    /** 关闭动画时直接跳。 */
    private static boolean instant() {
        return Data.get().getInt("UiAnimSpeed", 100) <= 0;
    }

    private boolean queue(boolean horizontal, double delta) {
        if (delta == 0) return false;
        double over = overflow(horizontal);
        if (over <= 0.5) return false;
        Axis axis = horizontal ? x : y;
        double position = position(horizontal);
        if (!axis.active) {
            axis.current = position;
            axis.target = position;
            axis.velocity = 0;
            axis.active = true;
        }
        double next = clamp(axis.target + delta, 0, over);
        boolean moved = Math.abs(next - position) > 0.01;
        // 反向滚轮先卸掉旧速度，掉头更跟手
        if (moved && axis.velocity * delta < 0 && Math.abs(axis.velocity) > 150) {
            axis.velocity = 0;
        }
        axis.target = next;
        if (moved) start();
        return moved;
    }

    private boolean scrollBy(boolean horizontal, double delta) {
        if (delta == 0) return false;
        double over = overflow(horizontal);
        if (over <= 0.5) return false;
        double before = position(horizontal);
        double after = clamp(before + delta, 0, over);
        if (Math.abs(after - before) < 0.01) return false;
        moveTo(horizontal, after);
        return true;
    }

    private void start() {
        if (ticking) return;
        ticking = true;
        lastFrame = 0;
        timer.start();
    }

    private void tick(long now) {
        if (!x.active && !y.active) {
            stopTimer();
            return;
        }
        // 动画未结束前不恢复特效
        if (restoreDelay != null) restoreDelay.stop();
        double dt = lastFrame == 0 ? 1.0 / 60 : Math.min(MAX_FRAME, (now - lastFrame) / 1e9);
        lastFrame = now;
        double omega = omega();
        boolean more = step(true, dt, omega);
        more |= step(false, dt, omega);
        if (!more) stopTimer();
    }

    private boolean step(boolean horizontal, double dt, double omega) {
        Axis axis = horizontal ? x : y;
        if (!axis.active) return false;
        double over = overflow(horizontal);
        if (over <= 0.5) {
            axis.active = false;
            axis.velocity = 0;
            return false;
        }
        double goal = clamp(axis.target, 0, over);
        // 临界阻尼弹簧：起步从静止缓慢加速，结束自然减速，多格滚轮之间速度连续
        double stiffness = omega * omega;
        double damping = 2 * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / SUB_STEP));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double acceleration = stiffness * (goal - axis.current) - damping * axis.velocity;
            axis.velocity += acceleration * h;
            double next = axis.current + axis.velocity * h;
            double limited = clamp(next, 0, over);
            if (limited != next) axis.velocity = 0; // 撞到边界别回弹
            axis.current = limited;
        }
        moveTo(horizontal, axis.current);
        axis.current = position(horizontal);
        if (Math.abs(goal - axis.current) <= SNAP && Math.abs(axis.velocity) < 3) {
            axis.velocity = 0;
            axis.active = false;
            moveTo(horizontal, goal);
            return false;
        }
        return true;
    }

    /** 滚动速度跟随动画设置。 */
    private static double omega() {
        int speed = Data.get().getInt("UiAnimSpeed", 100);
        double scale = Math.min(1.6, Math.max(0.6, speed / 100.0));
        return OMEGA * scale;
    }

    private void moveTo(boolean horizontal, double pixels) {
        double over = overflow(horizontal);
        double min = horizontal ? pane.getHmin() : pane.getVmin();
        double max = horizontal ? pane.getHmax() : pane.getVmax();
        if (over <= 0.5 || !(max > min)) return;
        double value = min + clamp(pixels, 0, over) / over * (max - min);
        writing = true;
        try {
            if (horizontal) pane.setHvalue(value);
            else pane.setVvalue(value);
        } finally {
            writing = false;
        }
    }

    /** 内容相对视口多出来的像素。 */
    private double overflow(boolean horizontal) {
        Node content = pane.getContent();
        if (content == null) return 0;
        javafx.geometry.Bounds bounds = content.getLayoutBounds();
        javafx.geometry.Bounds viewport = pane.getViewportBounds();
        double contentSize = horizontal ? bounds.getWidth() : bounds.getHeight();
        double viewSize = horizontal ? viewport.getWidth() : viewport.getHeight();
        return Math.max(0, contentSize - viewSize);
    }

    /** 当前滚动位置，单位是像素。 */
    private double position(boolean horizontal) {
        double over = overflow(horizontal);
        double min = horizontal ? pane.getHmin() : pane.getVmin();
        double max = horizontal ? pane.getHmax() : pane.getVmax();
        if (over <= 0.5 || !(max > min)) return 0;
        double value = horizontal ? pane.getHvalue() : pane.getVvalue();
        return clamp((value - min) / (max - min) * over, 0, over);
    }

    private void stopTimer() {
        ticking = false;
        lastFrame = 0;
        timer.stop();
        if (effectsSuspended) restartRestore();
    }

    private void stop() {
        stopTimer();
        x.active = false;
        y.active = false;
        x.velocity = 0;
        y.velocity = 0;
        if (effectsSuspended) restartRestore();
    }

    /** 滚动时摘掉大块阴影，停下后恢复。 */
    private void suspendEffects() {
        if (effectsSuspended) {
            restartRestore();
            return;
        }
        Node content = pane.getContent();
        if (content == null) return;
        effectsSuspended = true;
        collectEffects(content);
        for (EffectNode node : suspended) node.node.setEffect(null);
        restartRestore();
    }

    private void collectEffects(Node node) {
        Effect effect = node.getEffect();
        if (effect != null) {
            // 只摘大块节点，小卡片开销低
            javafx.geometry.Bounds bounds = node.getLayoutBounds();
            if (bounds.getWidth() * bounds.getHeight() >= 200_000) {
                suspended.add(new EffectNode(node, effect));
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) collectEffects(child);
        }
    }

    private void restartRestore() {
        if (!effectsSuspended) return;
        if (restoreDelay == null) {
            restoreDelay = new PauseTransition(Duration.millis(220));
            restoreDelay.setOnFinished(e -> resumeEffects());
        }
        restoreDelay.stop();
        restoreDelay.play();
    }

    private void resumeEffects() {
        if (!effectsSuspended) return;
        effectsSuspended = false;
        if (restoreDelay != null) restoreDelay.stop();
        for (EffectNode node : suspended) {
            // 期间被别处改过的节点尊重新状态，不强行回滚
            if (node.node.getEffect() == null) node.node.setEffect(node.effect);
        }
        suspended.clear();
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    /** 一个方向的弹簧状态。 */
    private static final class Axis {
        double current;
        double target;
        double velocity;
        boolean active;
    }

    /** 记下被临时摘掉的特效，停下后挂回。 */
    private static final class EffectNode {
        final Node node;
        final Effect effect;

        EffectNode(Node node, Effect effect) {
            this.node = node;
            this.effect = effect;
        }
    }
}
