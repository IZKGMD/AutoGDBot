package life.ctrl.ui;

/** Scene2D coordinates; desktop bars remain reachable even on a smaller display. */
public final class PinuxGeometry {
  private PinuxGeometry() {}

  public record Bounds(float x, float y, float width, float height) {}

  public static Bounds fit(
      float x, float y, float width, float height, float screenWidth, float screenHeight) {
    float margin = 8,
        bottom = Math.min(72, screenHeight * .14f),
        top = Math.min(36, screenHeight * .07f);
    float availableW = Math.max(1, screenWidth - 2 * margin),
        availableH = Math.max(1, screenHeight - bottom - top);
    float w = Math.max(Math.min(460, availableW), Math.min(width, availableW));
    float h = Math.max(Math.min(300, availableH), Math.min(height, availableH));
    return new Bounds(
        Math.max(margin, Math.min(x, screenWidth - margin - w)),
        Math.max(bottom, Math.min(y, screenHeight - top - h)),
        w,
        h);
  }

  public static Bounds maximized(float width, float height) {
    return fit(8, 72, width - 16, height - 108, width, height);
  }
}
