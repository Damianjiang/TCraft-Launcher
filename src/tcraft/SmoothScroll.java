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
import javafx.collections.ListChangeListener;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SmoothScroll {

    private static final Object KEY = new Object();
    private static final Object WHEEL_KEY = new Object();

    // 1.0≈40px 3.0 过快
    static final double DEFAULT_WHEEL_SPEED = 1.0;

    private static final double OMEGA = 20.0;

    private static final double SNAP = 0.25;

    private static final double MAX_FRAME = 0.05;

    private static final double SUB_STEP = 0.008;

    private final ScrollPane pane;
    private final Axis x = new Axis();
    private final Axis y = new Axis();
    private final AnimationTimer timer;
    private long lastFrame;
    private boolean ticking;
    private boolean writing;
    private final List<EffectNode> suspended = new ArrayList<>();
    private PauseTransition restoreDelay;
    private boolean effectsSuspended;
    private List<EffectNode> effectCache;
    private final Map<Parent, ListChangeListener<Node>> effectWatchers = new HashMap<>();

    static void install(ScrollPane pane) {
        install(pane, DEFAULT_WHEEL_SPEED);
    }

    static void install(ScrollPane pane, double wheelSpeed) {
        if (pane == null || pane.getProperties().get(KEY) instanceof SmoothScroll) return;
        pane.getProperties().put(WHEEL_KEY, wheelSpeed);
        SmoothScroll scroll = new SmoothScroll(pane);
        pane.getProperties().put(KEY, scroll);
        // 先拦截 避免原生再滚
        pane.addEventFilter(ScrollEvent.SCROLL, scroll::onScroll);
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> scroll.stop());
        pane.contentProperty().addListener((o, a, b) -> scroll.invalidateEffectCache());
        pane.sceneProperty().addListener((o, a, b) -> {
            if (b == null) {
                scroll.stop();
                scroll.resumeEffects();
                scroll.dropEffectCache();
            }
        });
        pane.visibleProperty().addListener((o, a, b) -> {
            if (!b) scroll.stop();
        });
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

    private double wheelSpeed() {
        Object v = pane.getProperties().get(WHEEL_KEY);
        return v instanceof Double d && d > 0 ? d : DEFAULT_WHEEL_SPEED;
    }

    private void onScroll(ScrollEvent e) {
        if (e.getEventType() != ScrollEvent.SCROLL || e.isConsumed()) return;
        if (insideNestedScroller(e.getTarget())) return;
        double dx = e.getDeltaX();
        double dy = e.getDeltaY();
        if (dx == 0 && dy == 0) return;
        if (e.isShiftDown() && dx == 0) {
            dx = dy;
            dy = 0;
        }
        double speed = wheelSpeed();
        double px = -dx * speed;
        double py = -dy * speed;
        if (py != 0 && overflow(false) <= 0.5 && overflow(true) > 0.5) {
            px = py;
            py = 0;
        } else if (px != 0 && overflow(true) <= 0.5 && overflow(false) > 0.5) {
            py += px;
            px = 0;
        }

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

    private boolean insideNestedScroller(javafx.event.EventTarget target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null && node != pane) {
            if (node instanceof TextArea || node instanceof ListView
                    || node instanceof TableView || node instanceof TreeView) {
                return true;
            }
            if (node instanceof ScrollPane) return true;
            node = node.getParent();
        }
        return false;
    }

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

    private double overflow(boolean horizontal) {
        Node content = pane.getContent();
        if (content == null) return 0;
        javafx.geometry.Bounds bounds = content.getLayoutBounds();
        javafx.geometry.Bounds viewport = pane.getViewportBounds();
        double contentSize = horizontal ? bounds.getWidth() : bounds.getHeight();
        double viewSize = horizontal ? viewport.getWidth() : viewport.getHeight();
        return Math.max(0, contentSize - viewSize);
    }

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

    private void suspendEffects() {
        if (effectsSuspended) {
            restartRestore();
            return;
        }
        Node content = pane.getContent();
        if (content == null) return;
        effectsSuspended = true;
        if (effectCache != null && staleCache()) dropEffectCache();
        if (effectCache == null) scanEffects(content);
        suspendCached();
        restartRestore();
    }

    // 特效变更时重扫
    private boolean staleCache() {
        for (EffectNode node : effectCache) {
            if (holdsNode(node.node)) continue; // 挂起中被置空的 不算失效
            if (node.node.getEffect() != node.effect) return true;
        }
        return false;
    }

    private boolean holdsNode(Node node) {
        for (EffectNode n : suspended) {
            if (n.node == node) return true;
        }
        return false;
    }

    private void suspendCached() {
        for (EffectNode node : effectCache) {
            if (node.node.getEffect() != null) {
                node.node.setEffect(null);
                suspended.add(node);
            }
        }
    }

    private void scanEffects(Node root) {
        detachEffectWatchers();
        List<EffectNode> found = new ArrayList<>();
        Set<Node> held = null;
        if (effectsSuspended && !suspended.isEmpty()) {
            held = new HashSet<>();
            for (EffectNode n : suspended) held.add(n.node);
        }
        collectEffects(root, found, held);
        effectCache = found;
    }

    private void collectEffects(Node node, List<EffectNode> out, Set<Node> held) {
        Effect effect = node.getEffect();
        if (effect == null && held != null && held.contains(node)) {
            // 挂起节点保留原特效
            for (EffectNode n : suspended) {
                if (n.node == node) {
                    effect = n.effect;
                    break;
                }
            }
        }
        if (effect != null) {
            javafx.geometry.Bounds bounds = node.getLayoutBounds();
            if (bounds.getWidth() * bounds.getHeight() >= 200_000) {
                out.add(new EffectNode(node, effect));
            }
        }
        if (node instanceof Parent parent) {
            watchChildren(parent);
            for (Node child : parent.getChildrenUnmodifiable()) collectEffects(child, out, held);
        }
    }

    private void watchChildren(Parent parent) {
        if (effectWatchers.containsKey(parent)) return;
        ListChangeListener<Node> listener = c -> invalidateEffectCache();
        effectWatchers.put(parent, listener);
        parent.getChildrenUnmodifiable().addListener(listener);
    }

    private void detachEffectWatchers() {
        if (effectWatchers.isEmpty()) return;
        for (Map.Entry<Parent, ListChangeListener<Node>> entry : effectWatchers.entrySet()) {
            entry.getKey().getChildrenUnmodifiable().removeListener(entry.getValue());
        }
        effectWatchers.clear();
    }

    private void invalidateEffectCache() {
        if (effectCache == null && effectWatchers.isEmpty()) return;
        effectCache = null;
        detachEffectWatchers();
        if (!effectsSuspended) return;
        Node content = pane.getContent();
        if (content == null) return;
        scanEffects(content);
        suspendCached();
    }

    private void dropEffectCache() {
        effectCache = null;
        detachEffectWatchers();
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
            if (node.node.getEffect() == null) node.node.setEffect(node.effect);
        }
        suspended.clear();
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    private static final class Axis {
        double current;
        double target;
        double velocity;
        boolean active;
    }

    private static final class EffectNode {
        final Node node;
        final Effect effect;

        EffectNode(Node node, Effect effect) {
            this.node = node;
            this.effect = effect;
        }
    }
}
