package life.ctrl.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import life.ctrl.render.PixelCanvas;

/**
 * Windowing rules for the fictional Pinux Astra desktop. Coordinates use the
 * 480x270 logical canvas, not the host OS pixel coordinates.
 */
public final class PinuxWindowManager {
  public enum App { CODE, TERMINAL, FILES, MAIL, SETTINGS }
  public static final int TOP = 17, BOTTOM = 250;

  public static final class Window {
    public final App app;
    public final String title;
    public int x, y, width, height;
    public int restoreX, restoreY, restoreWidth, restoreHeight;
    public boolean visible, minimized, maximized;
    public float animation;
    private Window(App app, String title, int x, int y, int width, int height) {
      this.app = app;
      this.title = title;
      this.x = x; this.y = y; this.width = width; this.height = height;
      restoreX = x; restoreY = y; restoreWidth = width; restoreHeight = height;
    }
    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public int contentY() { return y + 20; }
    public int contentHeight() { return height - 23; }
  }

  @FunctionalInterface
  public interface ContentPainter { void paint(Window window, boolean active); }

  private final EnumMap<App, Window> windows = new EnumMap<>(App.class);
  private final ArrayList<Window> zOrder = new ArrayList<>();
  private Window dragging;
  private int grabX, grabY, dragStartX, dragStartY;
  private float time;
  private long lastClickAt;
  private App lastClickApp;
  private boolean moved;
  private static final String EDGE = "4e7087", TEXT = "dceaf4", ACCENT = "8dcce0";

  public PinuxWindowManager() {
    add(App.CODE, "Astra Code  |  main.bs", 10, 18, 460, 231);
    add(App.TERMINAL, "Terminal  |  dev@astra", 99, 40, 353, 181);
    add(App.FILES, "Files  |  /home/dev", 49, 34, 370, 193);
    add(App.MAIL, "TaskMail  |  inbox", 74, 35, 352, 194);
    add(App.SETTINGS, "Settings  |  Appearance", 115, 48, 296, 176);
  }

  private void add(App app, String title, int x, int y, int w, int h) {
    windows.put(app, new Window(app, title, x, y, w, h));
  }

  public Window get(App app) { return windows.get(app); }
  public int openCount() { return (int) zOrder.stream().filter(w -> !w.minimized && w.visible).count(); }

  public Window front() {
    for (int i = zOrder.size() - 1; i >= 0; i--) {
      Window w = zOrder.get(i);
      if (w.visible && !w.minimized) return w;
    }
    return null;
  }

  public boolean isFront(App app) { Window w = front(); return w != null && w.app == app; }
  public boolean isMaximizedCode() {
    Window w = front();
    return w != null && w.app == App.CODE && w.maximized;
  }

  public void open(App app) {
    Window w = get(app);
    if (!w.visible) {
      w.animation = 0;
      w.visible = true;
    }
    w.minimized = false;
    focus(app);
  }

  public void focus(App app) {
    Window w = get(app);
    if (w == null || !w.visible) return;
    w.minimized = false;
    zOrder.remove(w);
    zOrder.add(w);
  }

  public void minimize(App app) {
    Window w = get(app);
    if (w == null) return;
    w.minimized = true;
    if (dragging == w) dragging = null;
  }

  public void close(App app) {
    Window w = get(app);
    if (w == null) return;
    w.visible = false;
    w.minimized = false;
    w.animation = 0;
    if (dragging == w) dragging = null;
    zOrder.remove(w);
  }

  public void closeFront() { Window w = front(); if (w != null) close(w.app); }

  public void toggleMaximize(App app) {
    Window w = get(app);
    if (w == null) return;
    if (!w.maximized) {
      w.restoreX = w.x; w.restoreY = w.y;
      w.restoreWidth = w.width; w.restoreHeight = w.height;
      w.x = 10; w.y = TOP + 1; w.width = 460; w.height = BOTTOM - TOP - 1;
      w.maximized = true;
    } else {
      w.x = w.restoreX; w.y = w.restoreY;
      w.width = w.restoreWidth; w.height = w.restoreHeight;
      w.maximized = false;
      clamp(w);
    }
  }

  /** Handles window caption, system buttons, focus and dragging. */
  public boolean pointerDown(int x, int y) {
    if (x < 0 || x >= PixelCanvas.W || y < TOP || y >= BOTTOM) return false;
    for (int i = zOrder.size() - 1; i >= 0; i--) {
      Window w = zOrder.get(i);
      if (!w.visible || w.minimized || !inside(w, x, y)) continue;
      boolean wasFront = front() == w;
      focus(w.app);
      if (y < w.y + 19) {
        // Three caption buttons: minimize, maximize and close.
        if (x >= w.right() - 15) { close(w.app); return true; }
        if (x >= w.right() - 30) { toggleMaximize(w.app); return true; }
        if (x >= w.right() - 45) { minimize(w.app); return true; }
        long now = System.nanoTime();
        if (lastClickApp == w.app && now - lastClickAt < 350_000_000L) {
          toggleMaximize(w.app);
          lastClickAt = 0;
          return true;
        }
        lastClickAt = now;
        lastClickApp = w.app;
        dragging = w;
        moved = false;
        grabX = x - w.x; grabY = y - w.y;
        dragStartX = x; dragStartY = y;
        return true;
      }
      // A lower window consumes the first click to focus it, preventing click-through.
      return !wasFront;
    }
    return false;
  }

  public boolean pointerDragged(int x, int y) {
    if (dragging == null) return false;
    Window w = dragging;
    if (Math.abs(x - dragStartX) + Math.abs(y - dragStartY) > 3) moved = true;
    if (!moved) return true;
    if (w.maximized) {
      toggleMaximize(w.app);
      grabX = Math.min(w.width - 30, Math.max(18, grabX));
      grabY = 9;
    }
    w.x = x - grabX; w.y = y - grabY;
    clamp(w);
    return true;
  }

  public boolean pointerUp(int x, int y) {
    if (dragging == null) return false;
    Window w = dragging;
    dragging = null;
    if (moved && y <= TOP + 4 && !w.maximized) toggleMaximize(w.app);
    moved = false;
    return true;
  }

  public void cancelDrag() { dragging = null; moved = false; }

  private static boolean inside(Window w, int x, int y) {
    return x >= w.x && x < w.right() && y >= w.y && y < w.bottom();
  }

  private static void clamp(Window w) {
    w.x = Math.max(0, Math.min(PixelCanvas.W - w.width, w.x));
    w.y = Math.max(TOP + 1, Math.min(BOTTOM - 29, w.y));
    if (w.y + 25 > BOTTOM) w.y = BOTTOM - 25;
  }

  public void tick(float delta) {
    time += delta;
    for (Window w : windows.values()) {
      float target = w.visible && !w.minimized ? 1 : 0;
      float speed = Math.min(1, delta * 13);
      w.animation += (target - w.animation) * speed;
      if (Math.abs(w.animation - target) < .008f) w.animation = target;
    }
  }

  public void draw(PixelCanvas c, ContentPainter painter) {
    for (Window w : zOrder) {
      if (!w.visible || w.minimized || w.animation < .03f) continue;
      if (isMaximizedCode() && w.app != App.CODE) continue; // completely covered
      boolean active = front() == w;
      if (isMaximizedCode() && w.app == App.CODE) {
        // Legacy Workbench screens are drawn at the native 480x270 layout.
        painter.paint(w, true);
        drawCaption(c, w, true);
        continue;
      }
      float p = w.animation * w.animation * (3 - 2 * w.animation);
      int inset = Math.round((1 - p) * 13);
      int xx = w.x + inset, yy = w.y + inset;
      int ww = w.width - inset * 2, hh = w.height - inset * 2;
      if (ww < 50 || hh < 30) continue;
      c.rect(xx + 3, yy + 4, ww, hh, "020912a0");
      c.rect(xx, yy, ww, hh, active ? EDGE : "344a5e");
      c.rect(xx + 1, yy + 1, ww - 2, hh - 2, "0e1d2d");
      c.rect(xx + 1, yy + 1, ww - 2, 18, active ? "244359" : "1b2f41");
      if (w.animation > .94f) {
        c.clip(w.x + 2, w.y + 20, w.width - 4, w.height - 22);
        painter.paint(w, active);
        c.unclip();
        drawCaption(c, w, active);
      }
    }
  }

  private void drawCaption(PixelCanvas c, Window w, boolean active) {
    int y = w.y;
    c.rect(w.x, y, w.width, 18, active ? "25475e" : "1b3042");
    c.rect(w.x, y + 17, w.width, 1, active ? "5591a9" : "334a5c");
    c.fitted("◆  " + w.title, w.x + 8, y + 4, w.width - 65,
        active ? TEXT : "9eb3c0", true);
    int start = w.right() - 43;
    for (int i = 0; i < 3; i++) {
      int x = start + i * 15;
      c.rect(x, y + 3, 12, 12, i == 2 ? "704850" : "34576a");
      c.small(i == 0 ? "_" : i == 1 ? "□" : "×", x + 3, y + 2,
          i == 2 ? "ffd5d1" : ACCENT);
    }
  }

  public void reset() {
    zOrder.clear();
    dragging = null;
    for (Window w : windows.values()) {
      w.visible = false; w.minimized = false; w.maximized = false; w.animation = 0;
      // Preserve each window's restored size/position across visits to the in-game PC.
      w.x = w.restoreX; w.y = w.restoreY;
      w.width = w.restoreWidth; w.height = w.restoreHeight;
    }
  }

  public List<App> taskbar() {
    return zOrder.stream().filter(w -> w.visible).map(w -> w.app).toList();
  }

  public void cycleFocus() {
    if (zOrder.isEmpty()) return;
    List<Window> candidates = zOrder.stream().filter(w -> w.visible).toList();
    if (candidates.size() < 2) return;
    Window next = candidates.get(Math.max(0, candidates.size() - 2));
    focus(next.app);
  }
}
