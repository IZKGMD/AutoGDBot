package life.ctrl.model;

import java.util.ArrayList;

/** Durable desktop preferences and normalized window geometry, independent of screen resolution. */
public final class PinuxSession {
  public boolean warmTheme, reducedMotion;
  public ArrayList<Placement> windows = new ArrayList<>();

  public static final class Placement {
    public String app;
    public float x, y, width, height;
    public boolean maximized;

    public Placement() {}

    public Placement(String app, float x, float y, float width, float height, boolean maximized) {
      this.app = app;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.maximized = maximized;
    }
  }

  public void validate() {
    if (windows == null || windows.size() > 5) throw new IllegalArgumentException("Pinux windows");
    java.util.HashSet<String> ids = new java.util.HashSet<>();
    for (var p : windows) {
      if (p == null
          || p.app == null
          || !java.util.Set.of("CODE", "TERMINAL", "FILES", "MAIL", "SETTINGS").contains(p.app)
          || !ids.add(p.app)
          || !Float.isFinite(p.x)
          || !Float.isFinite(p.y)
          || !Float.isFinite(p.width)
          || !Float.isFinite(p.height)
          || p.x < 0
          || p.y < 0
          || p.width <= 0
          || p.height <= 0
          || p.x + p.width > 1.001f
          || p.y + p.height > 1.001f) throw new IllegalArgumentException("Pinux window placement");
    }
  }
}
