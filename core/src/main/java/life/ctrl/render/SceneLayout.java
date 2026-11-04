package life.ctrl.render;

/** Visible cutaway boundaries in gameplay units. Shared by renderer and visual QA. */
public final class SceneLayout {
  private SceneLayout() {}

  public record Bounds(int x, int y, int width, int height) {}

  public static Bounds bounds(String map) {
    return switch (map) {
      case "room" -> new Bounds(137, 57, 206, 206);
      case "stair" -> new Bounds(90, 25, 301, 254);
      case "cafe", "diner", "workshop" -> new Bounds(89, 41, 302, 230);
      case "park" -> new Bounds(24, 56, 432, 213);
      case "street" -> new Bounds(16, 48, 928, 417);
      default -> throw new IllegalArgumentException("Unknown scene: " + map);
    };
  }
}
