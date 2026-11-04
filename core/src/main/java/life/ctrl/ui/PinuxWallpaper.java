package life.ctrl.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.Random;

/**
 * A self-contained, deterministic Pinux desktop wallpaper. Native libGDX texture,
 * generated once on the rendering thread; no downloaded art or web view.
 *
 * 960x540 source is deliberately 2x the logical 480x270 game canvas so silhouettes
 * and light gradients survive high-DPI scaling while keeping the image inexpensive.
 */
public final class PinuxWallpaper {
  private static final int W = 960, H = 540;
  private Texture city;
  private final Random random = new Random(0xA57A21);

  public Texture texture() {
    if (city == null) create();
    return city;
  }

  public void draw(SpriteBatch batch, boolean duskTheme) {
    if (city == null) create();
    batch.setColor(1f, duskTheme ? .89f : 1f, duskTheme ? .79f : 1f, 1f);
    batch.draw(city, 0, 0, 480, 270);
    batch.setColor(1f, 1f, 1f, 1f);
  }

  private void create() {
    Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
    paintSky(p);
    paintClouds(p);
    paintSun(p);
    // Quiet skyline kept to the lower half: room for icons and application windows.
    // Three intentionally sparse parallax layers instead of a wall of identical towers.
    paintCity(p, 410, 33, 31, 16, 0x67436e, 0x533862, .17f);
    paintCity(p, 469, 47, 63, 22, 0x332f59, 0x252742, .16f);
    paintCity(p, 513, 68, 49, 16, 0x1b2640, 0x171e35, .13f);
    paintForeground(p);
    city = new Texture(p);
    city.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    p.dispose();
  }

  private void paintSky(Pixmap p) {
    final float[][] stops = {
      {0f, 12, 19, 49},
      {.26f, 54, 37, 89},
      {.49f, 117, 66, 136},
      {.63f, 215, 105, 146},
      {.76f, 173, 86, 137},
      {1f, 35, 39, 79}
    };
    for (int y = 0; y < H; y++) {
      float t = y / (float)(H - 1);
      int band = 0;
      while (band < stops.length - 2 && t > stops[band + 1][0]) band++;
      float[] a = stops[band], b = stops[band + 1];
      float v = Math.max(0, Math.min(1, (t - a[0]) / (b[0] - a[0])));
      v = v * v * (3 - 2 * v);
      for (int x = 0; x < W; x++) {
        float dist = (float) Math.sqrt(
            Math.pow((x - 730) / 320f, 2) + Math.pow((y - 288) / 155f, 2));
        float glow = Math.max(0, 1f - dist);
        glow = glow * glow * .24f;
        float grain = (((x * 17 + y * 29) % 19) - 9) * .095f;
        int r = (int) (mix(a[1], b[1], v) * (1 - glow) + 250 * glow + grain);
        int g = (int) (mix(a[2], b[2], v) * (1 - glow) + 153 * glow + grain);
        int blue = (int) (mix(a[3], b[3], v) * (1 - glow) + 150 * glow + grain);
        p.drawPixel(x, y, rgba(r, g, blue, 255));
      }
    }
  }

  private void paintClouds(Pixmap p) {
    // Painterly wisps (broad, translucent and slightly curved), never random scanlines.
    for (int i = 0; i < 8; i++) {
      int centerX = 120 + random.nextInt(800);
      int centerY = 48 + random.nextInt(210);
      int span = 65 + random.nextInt(145);
      int tint = centerY > 145 ? 0xdf83ab : 0x66509b;
      int rr = (tint >> 16) & 255, gg = (tint >> 8) & 255, bb = tint & 255;
      for (int dx = -span; dx <= span; dx++) {
        float along = Math.abs(dx) / (float)span;
        float edge = Math.max(0f, 1f - along * along);
        int y0 = centerY + (int)(3 * Math.sin(dx / 42.0 + i));
        int x = centerX + dx;
        if (x < 0 || x >= W) continue;
        for (int dy = -3; dy <= 3; dy++) {
          int y = y0 + dy;
          if (y < 0 || y >= H) continue;
          float falloff = (1f - Math.abs(dy) / 4f) * edge;
          float alpha = falloff * .12f;
          p.drawPixel(x, y, blend(p.getPixel(x, y), rgba(rr, gg, bb, 255), alpha));
        }
      }
    }
  }

  private void paintSun(Pixmap p) {
    int cx = 745, cy = 311, radius = 46;
    for (int y = cy - radius; y <= cy + radius; y++) {
      for (int x = cx - radius; x <= cx + radius; x++) {
        if (x < 0 || y < 0 || x >= W || y >= H) continue;
        float dist = (float)Math.hypot(x - cx, y - cy);
        if (dist >= radius) continue;
        float t = (y - (cy - radius)) / (radius * 2f);
        int r = 255, g = (int)mix(200, 132, t), b = (int)mix(182, 171, t);
        // Continuous glowing disk: deliberately avoid dark scanlines that read as bugs.
        float rim = Math.min(1f, (radius - dist) / 3.5f);
        p.drawPixel(x, y, blend(p.getPixel(x, y), rgba(r, g, b, 255), rim * .86f));
      }
    }
  }

  private void paintCity(Pixmap p, int horizon, int minWidth, int maxHeight, int variance,
                         int topColor, int bottomColor, float windows) {
    int x = -24;
    while (x < W) {
      int width = minWidth + 9 + random.nextInt(minWidth + 25);
      int roof = horizon - (10 + random.nextInt(maxHeight) + random.nextInt(variance));
      // Leave a glowing pocket around the setting sun.
      if (x < 810 && x + width > 665 && horizon < 500) roof += 19;
      roof = Math.max(230, Math.min(horizon - 12, roof));
      int r1 = (topColor >>> 16) & 255, g1 = (topColor >>> 8) & 255, b1 = topColor & 255;
      int r2 = (bottomColor >>> 16) & 255, g2 = (bottomColor >>> 8) & 255, b2 = bottomColor & 255;
      int colorRoof = rgba(r1, g1, b1, 255);
      // Small architectural variations: setback roofs, chimneys, narrow towers.
      int type = random.nextInt(5);
      p.setColor(colorRoof);
      if (type == 0) {
        p.fillRectangle(x + 7, roof - 7, Math.max(8, width - 14), 7);
      } else if (type == 1) {
        p.fillRectangle(x + width / 2, roof - 12, 2, 12);
        p.fillRectangle(x + width / 2 - 2, roof - 2, 6, 2);
      } else if (type == 2) {
        p.fillRectangle(x + 5, roof - 4, Math.max(9, width / 3), 4);
      }
      for (int yy = roof; yy <= H - 30; yy++) {
        float t = Math.max(0f, Math.min(1f, (yy - roof) / (float)(H - roof)));
        // Slightly lighter left façade gives buildings actual depth.
        int col = rgba((int)mix(r1, r2, t), (int)mix(g1, g2, t),
            (int)mix(b1, b2, t), 255);
        p.setColor(col);
        p.fillRectangle(x, yy, width, 1);
      }
      p.setColor(rgba(Math.min(255, r1 + 11), Math.min(255, g1 + 7),
          Math.min(255, b1 + 12), 255));
      p.fillRectangle(x, roof + 2, 2, H - roof - 35);
      int windowStep = minWidth >= 65 ? 14 : minWidth >= 47 ? 12 : 10;
      for (int wy = roof + 13; wy < H - 34; wy += 14 + random.nextInt(4)) {
        for (int wx = x + 9; wx < x + width - 6; wx += windowStep) {
          if (wx < 1 || wx + 2 >= W || random.nextFloat() > windows) continue;
          int col = random.nextBoolean()
              ? rgba(242, 165, 152, 255) : rgba(178, 135, 192, 255);
          p.setColor(col);
          p.fillRectangle(wx, wy, 2, 4);
        }
      }
      x += width + 3 + random.nextInt(10);
    }
  }

  private void paintForeground(Pixmap p) {
    // Dark architectural framing and layered near-field foliage. Visible only in
    // the peripheral wallpaper so it does not compete with application windows.
    p.setColor(rgba(13, 20, 39, 255));
    p.fillRectangle(0, 480, W, 60);
    p.setColor(rgba(79, 62, 102, 255));
    p.fillRectangle(0, 480, W, 3);
    for (int i = 0; i < 18; i++) {
      int x = i * 58;
      int y = 482 + random.nextInt(27);
      p.setColor(rgba(28 + i % 3 * 2, 28, 51, 255));
      p.fillRectangle(x, y, 1 + random.nextInt(3), H - y);
    }
    p.setColor(rgba(17, 21, 42, 255));
    p.fillRectangle(0, 0, 17, H);
    p.fillRectangle(W - 18, 0, 18, H);

    // Keep the sky uncluttered: no cartoon tree blobs in the upper corners.
    p.setColor(rgba(15, 20, 39, 255));
    p.fillRectangle(0, 475, W, 9);
  }

  private void drawBranch(Pixmap p, int x, int y, int length, int direction, int depth) {
    int sign = direction == 0 ? 1 : -1;
    p.setColor(rgba(19, 20, 40, 255));
    for (int i = 0; i < length; i++) {
      int xx = x + sign * i / 3;
      int yy = y - i;
      if (xx < 18 || xx >= W - 18 || yy < 15) break;
      p.fillRectangle(xx, yy, Math.max(1, 6 - i / 33), 3);
    }
    for (int i = 0; i < 11 + depth; i++) {
      int bx = x + sign * (12 + random.nextInt(85));
      int by = y - 20 - random.nextInt(length - 30);
      p.setColor(rgba(22 + i % 3 * 6, 24, 49 + i % 3 * 6, 255));
      p.fillCircle(bx, by, 5 + random.nextInt(12));
    }
  }

  private static float mix(float a, float b, float t) { return a + (b - a) * t; }

  private static int rgba(int r, int g, int b, int a) {
    return (clamp(r) << 24) | (clamp(g) << 16) | (clamp(b) << 8) | clamp(a);
  }

  private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

  private static int blend(int base, int overlay, float alpha) {
    float o = Math.max(0, Math.min(1, alpha));
    return rgba(
        (int)mix((base >>> 24) & 255, (overlay >>> 24) & 255, o),
        (int)mix((base >>> 16) & 255, (overlay >>> 16) & 255, o),
        (int)mix((base >>> 8) & 255, (overlay >>> 8) & 255, o), 255);
  }

  public void dispose() {
    if (city != null) {
      city.dispose();
      city = null;
    }
  }
}
