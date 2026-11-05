package life.ctrl.render;

import java.util.ArrayList;
import java.util.List;
import life.ctrl.model.GameState;

/** Surface rectangles are kept inside the established walkable geometry, below sorted props. */
public final class WorldMaterials {
  private WorldMaterials() {}

  private record Facade(boolean brick, List<Patch> patches) {}

  private static final List<Facade> FACADES =
      List.of(
          facade(65, 288, 174, false), facade(295, 392, 355, false),
          facade(399, 608, 470, true), facade(615, 858, 814, false));

  private static void surface(
      PixelCanvas c,
      int tile,
      float x,
      float y,
      float w,
      float h,
      float ox,
      float oy,
      float size,
      float alpha) {
    c.material(tile, x, y, w, h, ox, oy, size, alpha);
  }

  public static void draw(PixelCanvas c, GameState s, float ox, float oy) {
    switch (s.map) {
      case "room" -> surface(c, 0, 144, 96, 192, 160, ox, oy, 64, 1);
      case "cafe" -> {
        surface(c, 1, 96, 80, 288, 114, ox, oy, 96, 1);
        surface(c, 1, 96, 194, 7, 38, ox, oy, 96, 1);
        surface(c, 1, 182, 194, 202, 38, ox, oy, 96, 1);
        surface(c, 1, 96, 232, 288, 32, ox, oy, 96, 1);
      }
      case "diner" -> surface(c, 2, 96, 80, 288, 184, ox, oy, 64, 1);
      case "workshop" -> surface(c, 5, 96, 80, 288, 184, ox, oy, 64, 1);
      case "stair" -> {
        surface(c, 2, 97, 80, 287, 59, ox, oy, 64, .9f);
        surface(c, 2, 97, 139, 159, 110, ox, oy, 64, .9f);
        surface(c, 2, 97, 249, 287, 23, ox, oy, 64, .9f);
      }
      case "street" -> {
        surface(c, 6, 16, 221, 928, 102, ox, oy, 96, .96f);
        surface(c, 8, 16, 324, 928, 58, ox, oy, 96, .96f);
        surface(c, 6, 16, 383, 928, 82, ox, oy, 96, .96f);
        // Painted lane dashes and kerbs remain legible over the authored asphalt.
        for (int x = 26; x < 944; x += 38) c.rect(x - ox, 352 - oy, 17, 1, "a4aa8d88");
        c.rect(16 - ox, 322 - oy, 928, 1, "9aab9d");
        c.rect(16 - ox, 382 - oy, 928, 1, "9aab9d");
      }
      case "park" -> {
        surface(c, 7, 24, 56, 192, 118, ox, oy, 80, 1);
        surface(c, 7, 276, 56, 180, 118, ox, oy, 80, .95f);
        surface(c, 7, 24, 205, 192, 59, ox, oy, 80, .95f);
        surface(c, 7, 276, 205, 180, 59, ox, oy, 80, .95f);
        surface(c, 6, 216, 56, 60, 208, ox, oy, 64, .85f);
        surface(c, 6, 24, 174, 432, 31, ox, oy, 64, .85f);
        // Coping encloses the existing blocked water rectangle; no collision changes.
        c.decorative("pond", 88 - ox, 81 - oy, 105, 84);
        for (float[] p : new float[][] {{30, 66}, {390, 64}, {34, 208}, {385, 220}, {181, 57}})
          c.decorative("shrub", p[0] - ox, p[1] - oy, 36, 29);
      }
    }
    wallWear(c, s.map, ox, oy);
  }

  private static void wallWear(PixelCanvas c, String map, float ox, float oy) {
    if (map.equals("room")) {
      surface(c, 3, 145, 65, 29, 24, ox, oy, 64, .40f);
      surface(c, 3, 304, 65, 30, 24, ox, oy, 64, .40f);
    } else if (map.equals("cafe") || map.equals("diner") || map.equals("workshop")) {
      surface(c, 3, 97, 49, 16, 26, ox, oy, 64, .30f);
      surface(c, 3, 356, 49, 26, 26, ox, oy, 64, .30f);
    } else if (map.equals("street")) {
      for (var f : FACADES)
        for (var p : f.patches)
          surface(c, f.brick ? 4 : 3, p.x, p.y, p.w, p.h, ox, oy, 80, f.brick ? .66f : .27f);
    }
  }

  private record Patch(float x, float y, float w, float h) {}

  /** Texture only exposed masonry; frames, panes, pipes, signs and doorways stay untouched. */
  private static Facade facade(int left, int right, int door, boolean brick) {
    List<Patch> patches = new ArrayList<>();
    patches.add(new Patch(left + 1, 74, right - left - 7, 135));
    for (int y : new int[] {80, 119})
      for (int x = left + 13; x < right - 31; x += 52)
        patches = subtract(patches, new Patch(x - 4, y - 3, 37, 32));
    if (right - left > 120) {
      patches = subtract(patches, new Patch(left + 8, 154, 45, 47));
      if (door < right - 65) patches = subtract(patches, new Patch(right - 56, 154, 45, 47));
    }
    patches = subtract(patches, new Patch(door - 18, 170, 36, 50));
    patches = subtract(patches, new Patch(left, 145, right - left, 12));
    return new Facade(brick, List.copyOf(patches));
  }

  private static List<Patch> subtract(List<Patch> source, Patch cut) {
    List<Patch> result = new ArrayList<>();
    for (var p : source) {
      float x0 = Math.max(p.x, cut.x), y0 = Math.max(p.y, cut.y);
      float x1 = Math.min(p.x + p.w, cut.x + cut.w), y1 = Math.min(p.y + p.h, cut.y + cut.h);
      if (x0 >= x1 || y0 >= y1) {
        result.add(p);
        continue;
      }
      if (y0 > p.y) result.add(new Patch(p.x, p.y, p.w, y0 - p.y));
      if (y1 < p.y + p.h) result.add(new Patch(p.x, y1, p.w, p.y + p.h - y1));
      if (x0 > p.x) result.add(new Patch(p.x, y0, x0 - p.x, y1 - y0));
      if (x1 < p.x + p.w) result.add(new Patch(x1, y0, p.x + p.w - x1, y1 - y0));
    }
    return result;
  }
}
