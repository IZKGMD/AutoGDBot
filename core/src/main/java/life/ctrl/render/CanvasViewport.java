package life.ctrl.render;

/** Fit the authored 16:9 canvas without stretching or discarding usable screen space. */
public record CanvasViewport(float scale, int x, int y, int width, int height) {
  public static CanvasViewport fit(int screenWidth, int screenHeight) {
    if (screenWidth < 1 || screenHeight < 1) throw new IllegalArgumentException("Viewport size");
    float scale =
        Math.min(screenWidth / (float) PixelCanvas.W, screenHeight / (float) PixelCanvas.H);
    int width = Math.min(screenWidth, Math.round(PixelCanvas.W * scale));
    int height = Math.min(screenHeight, Math.round(PixelCanvas.H * scale));
    return new CanvasViewport(
        scale, (screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
  }

  public int screenX(float logical) {
    return Math.round(x + logical * scale);
  }

  public int screenY(float logical) {
    return Math.round(y + logical * scale);
  }

  public float logicalX(int screen) {
    return (screen - x) / scale;
  }

  public float logicalY(int screen) {
    return (screen - y) / scale;
  }
}
