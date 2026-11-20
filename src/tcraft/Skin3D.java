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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Skin3D extends Region {

    private static final double FOV_DEG = 45;

    private static final double CAM_DISTANCE = 40;

    private static final double PIVOT_Y = 2;

    private static final int AUTO_TICK_MS = 40;
    private static final double AUTO_STEP_DEG = 0.5;

    private final Canvas canvas;
    private final Timeline autoTimer;

    private double yaw = 22, pitch = 6, zoom = 1;
    private boolean dragging;
    private boolean wantRun = true;
    private double dragOriginX, dragOriginY, yawOrigin, pitchOrigin;

    private Image skin;
    private int[] pixels;
    private Color[] palette;
    private int skinW, skinH;

    private double texScale = 1;
    private boolean legacySkin;

    private List<Face> faces = new ArrayList<>();

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
        // scene 变 null 即停表
        sceneProperty().addListener((o, oldScene, newScene) -> applyRunState());
        autoTimer.play();
    }

    private void applyRunState() {
        if (getScene() != null) {
            if (wantRun) autoTimer.play();
        } else {
            autoTimer.stop();
        }
    }

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

    public void resetView() {
        yaw = 22;
        pitch = 6;
        zoom = 1;
        render();
    }

    public void setSkin(Image image) {
        if (image == null) {
            clearSkin();
            return;
        }
        // 图片可能还在后台解码，此时读像素只会得到整片白色
        if (image.getProgress() < 1) {
            javafx.beans.value.ChangeListener<Number> wait = new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> o,
                                    Number before, Number after) {
                    if (after.doubleValue() < 1) return;
                    image.progressProperty().removeListener(this);
                    applySkin(image);
                }
            };
            image.progressProperty().addListener(wait);
            return;
        }
        applySkin(image);
    }

    private void applySkin(Image image) {
        double iw = image.getWidth(), ih = image.getHeight();
        boolean valid = !image.isError() && iw >= 64 && iw % 64 == 0 && (ih == iw || ih == iw / 2);
        // palette 每像素一个 Color，贴图上限 512x512
        if (!valid || (long) iw * (long) ih > 512 * 512) {
            Data.get().log("3D 皮肤预览：贴图 " + (int) iw + "x" + (int) ih + " 不是有效皮肤，已跳过");
            clearSkin();
            return;
        }
        // 旧版 64x32 皮肤先镜像补成 64x64 再建模
        legacySkin = ih < iw;
        if (legacySkin) image = upgradeLegacy(image, (int) iw, (int) ih);
        this.skin = image;
        readPixels();
        rebuild();
    }

    // 镜像复制右肢贴图补全左肢
    private static Image upgradeLegacy(Image src, int w, int h) {
        javafx.scene.image.WritableImage out = new javafx.scene.image.WritableImage(w, w);
        javafx.scene.image.PixelReader in = src.getPixelReader();
        javafx.scene.image.PixelWriter dst = out.getPixelWriter();
        int u = w / 64;
        copyPixels(in, dst, 0, 0, w, h / 2, 0, 0, false);
        copyPixels(in, dst, 0, 16 * u, 16 * u, 16 * u, 0, 16 * u, false);
        copyPixels(in, dst, 16 * u, 16 * u, 24 * u, 16 * u, 16 * u, 16 * u, false);
        copyPixels(in, dst, 40 * u, 16 * u, 16 * u, 16 * u, 40 * u, 16 * u, false);
        mirrorLimb(in, dst, u, 0, 16 * u, 16 * u, 48 * u);
        mirrorLimb(in, dst, u, 40 * u, 16 * u, 32 * u, 48 * u);
        return out;
    }

    private static void mirrorLimb(javafx.scene.image.PixelReader in,
                                   javafx.scene.image.PixelWriter dst, int u,
                                   int sx, int sy, int tx, int ty) {
        int w = 4 * u, h = 12 * u, d = 4 * u;
        copyPixels(in, dst, sx + d, sy, w, d, tx + d, ty, true);
        copyPixels(in, dst, sx + d + w, sy, w, d, tx + d + w, ty, true);
        copyPixels(in, dst, sx, sy + d, d, h, tx + w + d, ty + d, true);
        copyPixels(in, dst, sx + w + d, sy + d, d, h, tx, ty + d, true);
        copyPixels(in, dst, sx + d, sy + d, w, h, tx + d, ty + d, true);
        copyPixels(in, dst, sx + w + 2 * d, sy + d, w, h, tx + w + 2 * d, ty + d, true);
    }

    private static void copyPixels(javafx.scene.image.PixelReader in,
                                   javafx.scene.image.PixelWriter dst, int sx, int sy,
                                   int w, int h, int tx, int ty, boolean mirrorX) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setArgb(tx + x, ty + y, in.getArgb(sx + (mirrorX ? w - 1 - x : x), sy + y));
            }
        }
    }

    public void clearSkin() {
        skin = null;
        pixels = null;
        palette = null;
        legacySkin = false;
        faces = new ArrayList<>();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    public void setSkinFile(String resName) {
        try {
            Image img = Theme.loadImage(resName);
            if (img == null || img.isError()) {
                clearSkin();
                return;
            }
            setSkin(img);
        } catch (Exception e) {
            Data.get().log("3D 皮肤预览加载失败：" + resName + " " + Log.describe(e));
        }
    }

    public void dispose() {
        wantRun = false;
        autoTimer.stop();
    }

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
        texScale = skinW / 64.0;
        boolean slim = !legacySkin && isSlimPixel();
        int armW = slim ? 3 : 4;
        double armX = (8 + armW) / 2.0;

        addBox(8, 8, 8, 0, 0, 0, -10, 0, 0);
        addBox(8, 12, 4, 16, 16, 0, 0, 0, 0);
        addBox(armW, 12, 4, 40, 16, -armX, 0, 0, 0);
        addBox(armW, 12, 4, 32, 48, armX, 0, 0, 0);
        addBox(4, 12, 4, 0, 16, -2, 12, 0, 0);
        addBox(4, 12, 4, 16, 48, 2, 12, 0, 0);
        addBox(8, 8, 8, 32, 0, 0, -10, 0, 0.5);
        addBox(8, 12, 4, 16, 32, 0, 0, 0, 0.5);
        addBox(armW, 12, 4, 40, 32, -armX, 0, 0, 0.5);
        addBox(armW, 12, 4, 48, 48, armX, 0, 0, 0.5);
        addBox(4, 12, 4, 0, 32, -2, 12, 0, 0.5);
        addBox(4, 12, 4, 0, 48, 2, 12, 0, 0.5);
        render();
    }

    private void addBox(int W, int H, int D, int sx, int sy,
                        double cx, double cy, double cz, double e) {
        double hw = (W + e) / 2.0, hh = (H + e) / 2.0, hd = (D + e) / 2.0;
        double x0 = cx - hw, x1 = cx + hw;
        double y0 = cy - hh, y1 = cy + hh;
        double z0 = cz - hd, z1 = cz + hd;

        double topU = (sx + D) * texScale, topV = sy * texScale;
        double botU = (sx + D + W) * texScale, botV = sy * texScale;
        double rightU = sx * texScale, rightV = (sy + D) * texScale;
        double frontU = (sx + D) * texScale, frontV = (sy + D) * texScale;
        double leftU = (sx + D + W) * texScale, leftV = (sy + D) * texScale;
        double backU = (sx + 2 * D + W) * texScale, backV = (sy + D) * texScale;
        double Ws = W * texScale, Hs = H * texScale, Ds = D * texScale;

        addFace(new double[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                frontU, frontV, frontU + Ws, frontV + Hs, cx, cy, cz);
        addFace(new double[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                backU, backV, backU + Ws, backV + Hs, cx, cy, cz);
        addFace(new double[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                rightU, rightV, rightU + Ds, rightV + Hs, cx, cy, cz);
        addFace(new double[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                leftU, leftV, leftU + Ds, leftV + Hs, cx, cy, cz);
        addFace(new double[]{x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0},
                topU, topV, topU + Ws, topV + Ds, cx, cy, cz);
        addFace(new double[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
                botU, botV, botU + Ws, botV + Ds, cx, cy, cz);
    }

    private void addFace(double[] p, double u0, double v0, double u1, double v1,
                         double cx, double cy, double cz) {
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

    private boolean isSlimPixel() {
        if (skinW < 64 || skinH < 64 || pixels == null) return false;
        int x = (int) Math.round(54 * texScale), y = (int) Math.round(20 * texScale);
        if (x >= skinW || y >= skinH) return false;
        return ((pixels[y * skinW + x] >>> 24) & 0xFF) == 0;
    }

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

    private static final class Proj {
        Face face;
        double[] cx = new double[12];
        double[] depth = new double[4];
        double[] sx = new double[4];
        double[] sy = new double[4];
        double z;
        double nx, ny, nz;
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
        this.focal = (w / 2) / Math.tan(FOV_DEG / 2 * rad);
        this.ox = w / 2;
        this.oy = h / 2;

        List<Proj> draw = new ArrayList<>(faces.size());
        for (Face f : faces) {
            Proj pr = new Proj();
            pr.face = f;
            for (int i = 0; i < 4; i++) {
                double x = f.p[i * 3], y = f.p[i * 3 + 1] - PIVOT_Y, z = f.p[i * 3 + 2];
                double y1 = y * cp - z * sp;
                double z1 = y * sp + z * cp;
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
            pr.nx = nx;
            pr.ny = ny;
            pr.nz = nz;
            double facing = -nx * mx - ny * my + nz * (dist - mz);
            boolean near = true;
            for (int i = 0; i < 4; i++) {
                if (dist - pr.cx[i * 3 + 2] < 1) {
                    near = false;
                    break;
                }
            }
            if (!near || facing <= 0) continue;
            for (int i = 0; i < 4; i++) {
                double depth = dist - pr.cx[i * 3 + 2];
                pr.depth[i] = depth;
                pr.sx[i] = ox + pr.cx[i * 3] * focal / depth;
                pr.sy[i] = oy + pr.cx[i * 3 + 1] * focal / depth;
            }
            pr.z = dist - mz;
            draw.add(pr);
        }

        draw.sort(Comparator.comparingDouble((Proj p) -> p.z).reversed());

        for (Proj pr : draw) drawFace(g, pr, dist);
    }

    private double focal, ox, oy;

    private void drawFace(GraphicsContext g, Proj pr, double dist) {
        Face f = pr.face;
        if (f == null) return;
        int u0 = (int) Math.round(f.u0), u1 = (int) Math.round(f.u1);
        int v0 = (int) Math.round(f.v0), v1 = (int) Math.round(f.v1);

        double edge = Math.hypot(pr.sx[1] - pr.sx[0], pr.sy[1] - pr.sy[0]);
        double pxPerTexel = edge / Math.max(1, Math.abs(f.u1 - f.u0));
        int step = (int) Math.ceil(1.0 / Math.max(0.2, pxPerTexel));
        step = Math.max(1, Math.min(4, step));

        // 固定左上前方光源，白色皮肤也能看出各面
        double lx = -0.35, ly = -0.55, lz = 0.78;
        double ln = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double lambert = Math.max(0, (pr.nx * lx + pr.ny * ly + pr.nz * lz) / ln);
        double shade = 0.52 + 0.48 * lambert;

        // 极淡的面轮廓，纯色皮肤也能分清各部位
        g.setStroke(Color.color(0, 0, 0, 0.18));
        g.setLineWidth(1.0);
        g.strokePolygon(pr.sx, pr.sy, 4);

        double[] cam = pr.cx;
        double[] q = new double[12];
        double[] qx = new double[4], qy = new double[4];

        for (int tv = v0; tv < v1; tv += step) {
            for (int tu = u0; tu < u1; tu += step) {
                int texel = clampInt(tv, 0, skinH - 1) * skinW + clampInt(tu, 0, skinW - 1);
                if ((pixels[texel] >>> 24) == 0) continue;
                double s0 = (tu - f.u0) / (f.u1 - f.u0);
                double s1 = (Math.min(tu + step, u1) - f.u0) / (f.u1 - f.u0);
                double t0 = (tv - f.v0) / (f.v1 - f.v0);
                double t1 = (Math.min(tv + step, v1) - f.v0) / (f.v1 - f.v0);
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
                outset(qx, qy, 0.5);
                Color base = palette[texel];
                g.setFill(Color.color(base.getRed() * shade, base.getGreen() * shade,
                        base.getBlue() * shade, base.getOpacity()));
                g.fillPolygon(qx, qy, 4);
            }
        }
    }

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
