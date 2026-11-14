package tcraft;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 皮肤 3D 预览，用 Canvas 透视投影，不依赖 SCENE3D。 */
public final class Skin3D extends Region {

    /** 水平视场角。 */
    private static final double FOV_DEG = 45;

    /** 相机到旋转轴心的距离。 */
    private static final double CAM_DISTANCE = 40;

    /** 旋转轴心 Y。 */
    private static final double PIVOT_Y = 2;

    /** 空闲自转：40ms 一帧、每帧 0.5°。 */
    private static final int AUTO_TICK_MS = 40;
    private static final double AUTO_STEP_DEG = 0.5;

    private final Canvas canvas;
    private final Timeline autoTimer;

    private double yaw = 22, pitch = 6, zoom = 1;
    private boolean dragging;
    private double dragOriginX, dragOriginY, yawOrigin, pitchOrigin;

    /** 皮肤纹理和逐像素色表。 */
    private Image skin;
    private int[] pixels;
    private Color[] palette;
    private int skinW, skinH;

    /** HD 皮肤缩放，按 64 基准算。 */
    private double texScale = 1;

    /** 模型面列表。 */
    private List<Face> faces = new ArrayList<>();

    /** 给定尺寸的 3D 皮肤预览区。 */
    public Skin3D(double width, double height) {
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        setPickOnBounds(true);
        canvas = new Canvas(width, height);
        getChildren().add(canvas);
        installInteraction();

        autoTimer = new Timeline(new KeyFrame(Duration.millis(AUTO_TICK_MS), e -> {
            if (skin != null && !dragging) {
                yaw += AUTO_STEP_DEG;
                if (yaw >= 360) yaw -= 360;
                render();
            }
        }));
        autoTimer.setCycleCount(Timeline.INDEFINITE);
        autoTimer.play();
    }

    /** 拖拽旋转 / 滚轮缩放 / 双击复位。 */
    private void installInteraction() {
        addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() == 2) {
                resetView();
                return;
            }
            dragging = true;
            dragOriginX = e.getX();
            dragOriginY = e.getY();
            yawOrigin = yaw;
            pitchOrigin = pitch;
            setCursor(Cursor.MOVE);
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging) return;
            yaw = norm360(yawOrigin + (e.getX() - dragOriginX) * 0.55);
            pitch = clamp(pitchOrigin + (e.getY() - dragOriginY) * 0.35, -42, 42);
            render();
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (!dragging) return;
            dragging = false;
            setCursor(Cursor.DEFAULT);
        });
        addEventHandler(ScrollEvent.SCROLL, e -> {
            zoom = clamp(zoom + (e.getDeltaY() > 0 ? 0.08 : -0.08), 0.6, 2.2);
            render();
        });
    }

    /** 复位视角。 */
    public void resetView() {
        yaw = 22;
        pitch = 6;
        zoom = 1;
        render();
    }

    /** 设置皮肤纹理并重建模型。 */
    public void setSkin(Image image) {
        this.skin = image;
        readPixels();
        rebuild();
    }

    /** 清空模型。 */
    public void clearSkin() {
        skin = null;
        pixels = null;
        palette = null;
        faces = new ArrayList<>();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /** 按资源名加载皮肤并预览。 */
    public void setSkinFile(String resName) {
        try {
            Path p = Theme.res("images/" + resName);
            if (!Files.exists(p)) {
                clearSkin();
                return;
            }
            Image img = new Image(p.toUri().toString());
            if (img.isError()) {
                clearSkin();
                return;
            }
            setSkin(img);
        } catch (Exception e) {
            Data.get().log("3D 皮肤预览加载失败：" + resName + " " + e);
        }
    }

    /** 停止自转，弹窗关闭时调用。 */
    public void dispose() {
        autoTimer.stop();
    }

    /** 当前模型面数，自检用。 */
    public int faceCount() {
        return faces.size();
    }

    /** 读皮肤像素建颜色表。 */
    private void readPixels() {
        if (skin == null) {
            pixels = null;
            palette = null;
            return;
        }
        skinW = (int) skin.getWidth();
        skinH = (int) skin.getHeight();
        pixels = new int[skinW * skinH];
        palette = new Color[pixels.length];
        javafx.scene.image.PixelReader pr = skin.getPixelReader();
        for (int y = 0; y < skinH; y++) {
            for (int x = 0; x < skinW; x++) {
                int i = y * skinW + x;
                int argb = pr.getArgb(x, y);
                pixels[i] = argb;
                palette[i] = Color.rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                        ((argb >>> 24) & 0xFF) / 255.0);
            }
        }
    }

    /** 用当前皮肤重建全部部件。 */
    private void rebuild() {
        faces = new ArrayList<>();
        if (skin == null) {
            clearSkin();
            return;
        }
        if (skinW < 64) {
            Data.get().log("3D 皮肤预览：贴图宽度 " + skinW + " 不足 64，无法建模");
            return;
        }
        boolean legacy = skinH < skinW; // 64×32 老皮肤，左臂左腿复用右侧贴图
        texScale = skinW / 64.0;  // HD 皮肤按 64 基准缩放
        boolean slim = !legacy && isSlimPixel();
        int armW = slim ? 3 : 4;
        double armX = (8 + armW) / 2.0; // 手臂中心偏移

        addBox(8, 8, 8, 0, 0, 0, -10, 0, 0);  // 头
        addBox(8, 12, 4, 16, 16, 0, 0, 0, 0);  // 身体
        addBox(armW, 12, 4, 40, 16, -armX, 0, 0, 0);  // 右臂
        addBox(armW, 12, 4, legacy ? 40 : 32, legacy ? 16 : 48, armX, 0, 0, 0); // 左臂
        addBox(4, 12, 4, 0, 16, -2, 12, 0, 0);  // 右腿
        addBox(4, 12, 4, legacy ? 0 : 16, legacy ? 16 : 48, 2, 12, 0, 0);  // 左腿
        if (!legacy) {
            addBox(8, 8, 8, 32, 0, 0, -10, 0, 0.5);
            addBox(8, 12, 4, 16, 32, 0, 0, 0, 0.5);
            addBox(armW, 12, 4, 40, 32, -armX, 0, 0, 0.5);
            addBox(armW, 12, 4, 48, 48, armX, 0, 0, 0.5);
            addBox(4, 12, 4, 0, 32, -2, 12, 0, 0.5);
            addBox(4, 12, 4, 0, 48, 2, 12, 0, 0.5);
        }
        render();
    }

    /** 加一个长方体部件。 */
    private void addBox(int W, int H, int D, int sx, int sy,
                        double cx, double cy, double cz, double e) {
        double hw = (W + e) / 2.0, hh = (H + e) / 2.0, hd = (D + e) / 2.0;
        double x0 = cx - hw, x1 = cx + hw;
        double y0 = cy - hh, y1 = cy + hh;
        double z0 = cz - hd, z1 = cz + hd;

        // 展开图六面矩形，乘 texScale 支持 HD
        double topU = (sx + D) * texScale, topV = sy * texScale;
        double botU = (sx + D + W) * texScale, botV = sy * texScale;
        double rightU = sx * texScale, rightV = (sy + D) * texScale;
        double frontU = (sx + D) * texScale, frontV = (sy + D) * texScale;
        double leftU = (sx + D + W) * texScale, leftV = (sy + D) * texScale;
        double backU = (sx + 2 * D + W) * texScale, backV = (sy + D) * texScale;
        double Ws = W * texScale, Hs = H * texScale, Ds = D * texScale;

        // 法线按背离部件中心自动定向
        addFace(new double[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                frontU, frontV, frontU + Ws, frontV + Hs, cx, cy, cz);  // 正面 +Z
        addFace(new double[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                backU, backV, backU + Ws, backV + Hs, cx, cy, cz);  // 背面 -Z
        addFace(new double[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                rightU, rightV, rightU + Ds, rightV + Hs, cx, cy, cz);  // 右侧 +X
        addFace(new double[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                leftU, leftV, leftU + Ds, leftV + Hs, cx, cy, cz);  // 左侧 -X
        addFace(new double[]{x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0},
                topU, topV, topU + Ws, topV + Ds, cx, cy, cz);  // 顶面
        addFace(new double[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
                botU, botV, botU + Ws, botV + Ds, cx, cy, cz);  // 底面
    }

    private void addFace(double[] p, double u0, double v0, double u1, double v1,
                         double cx, double cy, double cz) {
        // 法线指向内部就翻转
        double ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        double bx = p[9] - p[0], by = p[10] - p[1], bz = p[11] - p[2];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double fcx = (p[0] + p[3] + p[6] + p[9]) / 4.0 - cx;
        double fcy = (p[1] + p[4] + p[7] + p[10]) / 4.0 - cy;
        double fcz = (p[2] + p[5] + p[8] + p[11]) / 4.0 - cz;
        if (nx * fcx + ny * fcy + nz * fcz < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-9) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        faces.add(new Face(p, u0, v0, u1, v1, nx, ny, nz));
    }

    /** 判断 Alex 细手臂，看像素 54,20 是否透明。 */
    private boolean isSlimPixel() {
        if (skinW < 64 || skinH < 64 || pixels == null) return false;
        int x = (int) Math.round(54 * texScale), y = (int) Math.round(20 * texScale);
        if (x >= skinW || y >= skinH) return false;
        return ((pixels[y * skinW + x] >>> 24) & 0xFF) == 0;
    }

    /** 一个待绘制面。 */
    private static final class Face {
        final double[] p;
        final double u0, v0, u1, v1;
        final double nx, ny, nz;

        Face(double[] p, double u0, double v0, double u1, double v1,
             double nx, double ny, double nz) {
            this.p = p;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    /** 每帧临时的投影结果。 */
    private static final class Proj {
        Face face;
        double[] cx = new double[12];   // 4 顶点旋转后坐标
        double[] depth = new double[4]; // 4 顶点相机深度
        double[] sx = new double[4];
        double[] sy = new double[4];
        double z;  // 平均深度，画家算法排序用
    }

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.clearRect(0, 0, w, h);
        if (skin == null || pixels == null || faces.isEmpty()) return;

        double rad = Math.PI / 180;
        double cp = Math.cos(pitch * rad), sp = Math.sin(pitch * rad);
        double cyw = Math.cos(yaw * rad), syw = Math.sin(yaw * rad);
        double dist = CAM_DISTANCE / Math.max(0.35, zoom);
        // FOV 按横向算
        this.focal = (w / 2) / Math.tan(FOV_DEG / 2 * rad);
        this.ox = w / 2;
        this.oy = h / 2;

        // 逐面旋转和剔除
        List<Proj> draw = new ArrayList<>(faces.size());
        for (Face f : faces) {
            Proj pr = new Proj();
            pr.face = f;
            for (int i = 0; i < 4; i++) {
                double x = f.p[i * 3], y = f.p[i * 3 + 1] - PIVOT_Y, z = f.p[i * 3 + 2];
                // 绕 X 旋转
                double y1 = y * cp - z * sp;
                double z1 = y * sp + z * cp;
                // 绕 Y 旋转
                double x2 = x * cyw + z1 * syw;
                double z2 = -x * syw + z1 * cyw;
                pr.cx[i * 3] = x2;
                pr.cx[i * 3 + 1] = y1;
                pr.cx[i * 3 + 2] = z2;
            }
            double mx = 0, my = 0, mz = 0;
            for (int i = 0; i < 4; i++) {
                mx += pr.cx[i * 3];
                my += pr.cx[i * 3 + 1];
                mz += pr.cx[i * 3 + 2];
            }
            mx /= 4;
            my /= 4;
            mz /= 4;
            // 法线同步旋转，只旋转不平移
            double nx, ny, nz;
            {
                double y1 = f.ny * cp - f.nz * sp;
                double z1 = f.ny * sp + f.nz * cp;
                nx = f.nx * cyw + z1 * syw;
                ny = y1;
                nz = -f.nx * syw + z1 * cyw;
            }
            // 面向相机才画
            double facing = -nx * mx - ny * my + nz * (dist - mz);
            boolean near = true;
            for (int i = 0; i < 4; i++) {
                if (dist - pr.cx[i * 3 + 2] < 1) {
                    near = false;
                    break;
                }
            }
            if (!near || facing <= 0) continue;
            // 记下相机深度
            for (int i = 0; i < 4; i++) {
                double depth = dist - pr.cx[i * 3 + 2];
                pr.depth[i] = depth;
                pr.sx[i] = ox + pr.cx[i * 3] * focal / depth;
                pr.sy[i] = oy + pr.cx[i * 3 + 1] * focal / depth;
            }
            pr.z = dist - mz; // 平均深度，越大越远，按此降序
            draw.add(pr);
        }

        // 远的先画
        draw.sort(Comparator.comparingDouble((Proj p) -> p.z).reversed());

        // 逐面贴图
        for (Proj pr : draw) drawFace(g, pr, dist);
    }

    /** 本帧投影参数。 */
    private double focal, ox, oy;

    /** 按 texel 拆块填充，保证透视正确。 */
    private void drawFace(GraphicsContext g, Proj pr, double dist) {
        Face f = pr.face;
        if (f == null) return;
        int u0 = (int) Math.round(f.u0), u1 = (int) Math.round(f.u1);
        int v0 = (int) Math.round(f.v0), v1 = (int) Math.round(f.v1);

        // 远处用粗块，省填充次数
        double edge = Math.hypot(pr.sx[1] - pr.sx[0], pr.sy[1] - pr.sy[0]);
        double pxPerTexel = edge / Math.max(1, Math.abs(f.u1 - f.u0));
        int step = (int) Math.ceil(1.0 / Math.max(0.2, pxPerTexel));
        step = Math.max(1, Math.min(4, step));

        double[] cam = pr.cx;
        double[] q = new double[12];
        double[] qx = new double[4], qy = new double[4];

        for (int tv = v0; tv < v1; tv += step) {
            for (int tu = u0; tu < u1; tu += step) {
                int texel = clampInt(tv, 0, skinH - 1) * skinW + clampInt(tu, 0, skinW - 1);
                if ((pixels[texel] >>> 24) == 0) continue; // 全透明跳过
                double s0 = (tu - f.u0) / (f.u1 - f.u0);
                double s1 = (Math.min(tu + step, u1) - f.u0) / (f.u1 - f.u0);
                double t0 = (tv - f.v0) / (f.v1 - f.v0);
                double t1 = (Math.min(tv + step, v1) - f.v0) / (f.v1 - f.v0);
                // 四角双线性插值
                bilinear(cam, s0, t0, q, 0);
                bilinear(cam, s1, t0, q, 3);
                bilinear(cam, s1, t1, q, 6);
                bilinear(cam, s0, t1, q, 9);
                boolean ok = true;
                for (int i = 0; i < 4; i++) {
                    double depth = dist - q[i * 3 + 2];
                    if (depth < 1) {
                        ok = false;
                        break;
                    }
                    qx[i] = ox + q[i * 3] * focal / depth;
                    qy[i] = oy + q[i * 3 + 1] * focal / depth;
                }
                if (!ok) continue;
                // 外扩 0.5px 盖住块间亮缝
                outset(qx, qy, 0.5);
                g.setFill(palette[texel]);
                g.fillPolygon(qx, qy, 4);
            }
        }
    }

    /** 四边形向外扩 d 像素。 */
    private static void outset(double[] xs, double[] ys, double d) {
        double cx = 0, cy = 0;
        for (int i = 0; i < 4; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= 4;
        cy /= 4;
        for (int i = 0; i < 4; i++) {
            double dx = xs[i] - cx, dy = ys[i] - cy;
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) continue;
            double k = (len + d) / len;
            xs[i] = cx + dx * k;
            ys[i] = cy + dy * k;
        }
    }

    /** 相机空间双线性插值。 */
    private static void bilinear(double[] cam, double s, double t, double[] out, int at) {
        for (int i = 0; i < 3; i++) {
            double p0 = cam[i], p1 = cam[3 + i], p2 = cam[6 + i], p3 = cam[9 + i];
            out[at + i] = (1 - s) * (1 - t) * p0 + s * (1 - t) * p1 + s * t * p2 + (1 - s) * t * p3;
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double norm360(double v) {
        double r = v % 360;
        return r < 0 ? r + 360 : r;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
