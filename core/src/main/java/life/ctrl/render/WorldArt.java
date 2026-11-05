package life.ctrl.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.HashMap;
import java.util.Map;

/** Authored atlases sampled inside their cells; sprite dimensions remain gameplay dimensions. */
final class WorldArt {
  private final PixelCanvas canvas;
  private final Map<String, TextureRegion> sprites = new HashMap<>();
  private final TextureRegion[] materials = new TextureRegion[9];
  private boolean initialized;

  WorldArt(PixelCanvas canvas) {
    this.canvas = canvas;
  }

  private void load() {
    if (initialized) return;
    initialized = true;
    atlas("furniture", new String[] {"desk", "bed", "wardrobe", "table"});
    atlas("outdoors", new String[] {"tree", "shrub", "lamp", "bench"});
    atlas(
        "utility",
        new String[] {"counter", "bookcase", "fridge", "boxes", "crt_spare", "chair_upgrade"},
        2,
        3);
    atlas("states", new String[] {"mailbox", "notice", "boxes_open", "desk_ssd"});
    atlas("pond", new String[] {"pond"}, 1, 1);
    if (Gdx.files.internal("art/illustrations/materials.png").exists()) {
      Texture t = canvas.texture("illustrations/materials");
      for (int i = 0; i < 9; i++) {
        int x = i % 3 * t.getWidth() / 3, y = i / 3 * t.getHeight() / 3;
        int right = (i % 3 + 1) * t.getWidth() / 3, bottom = (i / 3 + 1) * t.getHeight() / 3;
        materials[i] = new TextureRegion(t, x + 2, y + 2, right - x - 4, bottom - y - 4);
      }
    }
  }

  private void atlas(String name, String[] names) {
    atlas(name, names, 2, 2);
  }

  private void atlas(String name, String[] names, int columns, int rows) {
    var file = Gdx.files.internal("art/illustrations/" + name + ".png");
    if (!file.exists()) return;
    Texture texture = canvas.texture("illustrations/" + name);
    Pixmap pixels = new Pixmap(file);
    try {
      for (int i = 0; i < names.length; i++) {
        int x0 = i % columns * pixels.getWidth() / columns,
            y0 = i / columns * pixels.getHeight() / rows;
        int x1 = (i % columns + 1) * pixels.getWidth() / columns,
            y1 = (i / columns + 1) * pixels.getHeight() / rows;
        int left = x1, top = y1, right = x0, bottom = y0;
        for (int y = y0 + 2; y < y1 - 2; y++)
          for (int x = x0 + 2; x < x1 - 2; x++)
            if ((pixels.getPixel(x, y) & 255) > 16) {
              left = Math.min(left, x);
              right = Math.max(right, x);
              top = Math.min(top, y);
              bottom = Math.max(bottom, y);
            }
        if (right > left && bottom > top)
          sprites.put(
              names[i], new TextureRegion(texture, left, top, right - left + 1, bottom - top + 1));
      }
    } finally {
      pixels.dispose();
    }
  }

  TextureRegion sprite(String name) {
    load();
    return sprites.get(name);
  }

  void material(
      int tile, float x, float y, float w, float h, float ox, float oy, float size, float alpha) {
    load();
    var region = materials[tile];
    if (region == null
        || w <= 0
        || h <= 0
        || x + w <= ox
        || y + h <= oy
        || x >= ox + PixelCanvas.W
        || y >= oy + PixelCanvas.H) return;
    canvas.clip(x - ox, y - oy, w, h);
    canvas.batch.setColor(1, 1, 1, alpha);
    int sx = (int) Math.floor(x / size), sy = (int) Math.floor(y / size);
    int ex = (int) Math.ceil((x + w) / size), ey = (int) Math.ceil((y + h) / size);
    for (int iy = sy; iy < ey; iy++)
      for (int ix = sx; ix < ex; ix++)
        canvas.batch.draw(region, ix * size - ox, PixelCanvas.H - (iy + 1) * size + oy, size, size);
    canvas.batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    canvas.unclip();
  }
}
