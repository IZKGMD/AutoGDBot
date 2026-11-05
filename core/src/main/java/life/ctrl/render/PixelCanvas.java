package life.ctrl.render;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;

/** Stable 480×270 layout with a native-resolution framebuffer and high-density assets. */
public final class PixelCanvas implements Disposable {
  public static final int W = 480, H = 270;
  public final SpriteBatch batch = new SpriteBatch();
  private final OrthographicCamera camera = new OrthographicCamera();
  private FrameBuffer frame;
  private int density;
  private final ArrayDeque<com.badlogic.gdx.math.Rectangle> clips = new ArrayDeque<>();
  private final Set<String> highDensity = new HashSet<>();
  private final Map<String, Color> colors = new HashMap<>();

  private Color color(String hex) {
    return colors.computeIfAbsent(hex, Color::valueOf);
  }

  public float smallWidth(String text) {
    layout.setText(small, text);
    return layout.width;
  }

  private final Map<String, Texture> textures = new HashMap<>();
  private final Map<String, Texture> roundedMasks = new HashMap<>();
  private final Map<String, TextureRegion[][]> actors = new HashMap<>();
  private final WorldArt worldArt = new WorldArt(this);
  private final Texture white;
  public final BitmapFont font, small, title;
  private final GlyphLayout layout = new GlyphLayout();
  public float scale = 1;
  public int offsetX, offsetY;

  public PixelCanvas() {
    Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    p.setColor(Color.WHITE);
    p.fill();
    white = new Texture(p);
    p.dispose();
    font = new BitmapFont(Gdx.files.internal("fonts/hd/ui.fnt"));
    small = new BitmapFont(Gdx.files.internal("fonts/hd/small.fnt"));
    title = new BitmapFont(Gdx.files.internal("fonts/hd/title.fnt"));
    for (BitmapFont f : new BitmapFont[] {font, small, title}) {
      f.getData().setScale(.25f);
      f.setUseIntegerPositions(false);
      f.getRegion()
          .getTexture()
          .setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }
    camera.setToOrtho(false, W, H);
  }

  public void preload() {
    for (String n :
        new String[] {
          "desk_ssd", "chair_upgrade", "light_amber", "light_monitor", "light_window", "shadow"
        }) texture(n);
    for (String n :
        new String[] {
          "room",
          "stair",
          "street",
          "desk",
          "bed",
          "wardrobe",
          "kitchen",
          "plant",
          "boxes",
          "bench",
          "tree",
          "lamp",
          "mailbox",
          "notice",
          "letter",
          "boxes_open",
          "plant_watered",
          "mailbox_signed",
          "portrait_hero",
          "portrait_mira",
          "portrait_arkady",
          "portrait_lev",
          "portrait_note",
          "glow_warm",
          "glow_cool",
          "hero",
          "hero_1",
          "hero_2",
          "mira",
          "arkady",
          "lev"
        }) texture(n);
    for (String n :
        new String[] {
          "cafe",
          "diner",
          "workshop",
          "park",
          "counter",
          "table",
          "fridge",
          "crt_spare",
          "bookcase",
          "portrait_asya",
          "portrait_nika",
          "portrait_tamara",
          "asya",
          "nika",
          "tamara"
        }) texture(n);
    for (String n :
        new String[] {
          "hero", "hero_1", "hero_2", "mira", "arkady", "lev", "asya", "nika", "tamara"
        }) {
      Texture t = texture(n);
      int d = highDensity.contains(n) ? 2 : 1;
      actors.put(n, TextureRegion.split(t, 16 * d, 24 * d));
    }
    worldArt.sprite("desk");
  }

  public Texture texture(String name) {
    return textures.computeIfAbsent(
        name,
        n -> {
          var hd = Gdx.files.internal("art/hd/" + n + ".png");
          if (hd.exists()) highDensity.add(n);
          Texture t = new Texture(hd.exists() ? hd : Gdx.files.internal("art/" + n + ".png"));
          var filter =
              n.startsWith("illustrations/") || n.startsWith("light_") || n.equals("shadow")
                  ? Texture.TextureFilter.Linear
                  : Texture.TextureFilter.Nearest;
          t.setFilter(filter, filter);
          return t;
        });
  }

  public void begin() {
    int target =
        Math.max(
            1,
            Math.min(
                (int) Math.ceil(Gdx.graphics.getBackBufferWidth() / (float) W),
                (int) Math.ceil(Gdx.graphics.getBackBufferHeight() / (float) H)));
    if (frame == null || target != density) {
      if (frame != null) frame.dispose();
      density = target;
      frame = new FrameBuffer(Pixmap.Format.RGBA8888, W * density, H * density, false);
      frame
          .getColorBufferTexture()
          .setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }
    clips.clear();
    frame.begin();
    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glClearColor(.075f, .1f, .14f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.setColor(Color.WHITE);
  }

  public void end() {
    if (!clips.isEmpty()) throw new IllegalStateException("Unbalanced render clips");
    batch.end();
    frame.end();
    int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
    var viewport = CanvasViewport.fit(w, h);
    scale = viewport.scale();
    offsetX = viewport.x();
    offsetY = viewport.y();
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.045f, .06f, .085f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    camera.setToOrtho(false, w, h);
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.setColor(Color.WHITE);
    batch.draw(frame.getColorBufferTexture(), offsetX, offsetY, W * scale, H * scale, 0, 0, 1, 1);
    batch.end();
    camera.setToOrtho(false, W, H);
  }

  public float artWidth(String name) {
    Texture t = texture(name);
    return t.getWidth() / (highDensity.contains(name) ? 2f : 1f);
  }

  public float artHeight(String name) {
    Texture t = texture(name);
    return t.getHeight() / (highDensity.contains(name) ? 2f : 1f);
  }

  /** Nested framebuffer scissor; every layer is intersected with its parent. */
  public void clip(float x, float y, float w, float h) {
    batch.flush();
    float x0 = Math.max(0, x),
        y0 = Math.max(0, y),
        x1 = Math.min(W, x + w),
        y1 = Math.min(H, y + h);
    if (!clips.isEmpty()) {
      var p = clips.peek();
      x0 = Math.max(x0, p.x);
      y0 = Math.max(y0, p.y);
      x1 = Math.min(x1, p.x + p.width);
      y1 = Math.min(y1, p.y + p.height);
    }
    clips.push(
        new com.badlogic.gdx.math.Rectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)));
    applyClip();
  }

  private void applyClip() {
    if (clips.isEmpty()) {
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      return;
    }
    var p = clips.peek();
    int x0 = (int) Math.ceil(p.x * density), y0 = (int) Math.ceil(p.y * density);
    int x1 = (int) Math.floor((p.x + p.width) * density),
        y1 = (int) Math.floor((p.y + p.height) * density);
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glScissor(x0, H * density - y1, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
  }

  public void unclip() {
    batch.flush();
    if (clips.isEmpty()) throw new IllegalStateException("Missing render clip");
    clips.pop();
    applyClip();
  }

  public void fitted(String text, float x, float y, float width, String color, boolean compact) {
    BitmapFont f = compact ? small : font;
    layout.setText(f, text);
    if (layout.width > width) {
      int end = text.length();
      do {
        end--;
        layout.setText(f, text.substring(0, end) + "…");
      } while (end > 0 && layout.width > width);
      text = text.substring(0, end) + "…";
    }
    clip(x, y - 1, width, compact ? 13 : 16);
    if (compact) small(text, x, y, color);
    else text(text, x, y, color);
    unclip();
  }

  public void glow(String name, float x, float y, float strength) {
    batch.setColor(1, 1, 1, Math.max(0, Math.min(1, strength)));
    art(name, x, y);
    batch.setColor(Color.WHITE);
  }

  public void rect(float x, float y, float w, float h, Color color) {
    batch.setColor(color);
    batch.draw(white, x, H - y - h, w, h);
    batch.setColor(Color.WHITE);
  }

  public void rect(float x, float y, float w, float h, String color) {
    rect(x, y, w, h, color(color));
  }

  /**
   * Smooth high-density rounded panels. A cached three-times-resolution signed- distance mask
   * avoids the chunky stepped corners of the low-res prototype. The mask is white and can be tinted
   * to any colour by the SpriteBatch.
   */
  public void roundRect(float x, float y, float w, float h, int radius, String hex) {
    int r = Math.max(0, Math.min(radius, (int) Math.min(w, h) / 2));
    if (r == 0 || w < 1 || h < 1) {
      rect(x, y, w, h, hex);
      return;
    }
    int pw = Math.max(2, Math.round(w * 3)), ph = Math.max(2, Math.round(h * 3));
    String key = pw + ":" + ph + ":" + r;
    Texture mask = roundedMasks.get(key);
    if (mask == null) {
      Pixmap p = new Pixmap(pw, ph, Pixmap.Format.RGBA8888);
      float radiusPx = r * 3f;
      float bx = pw / 2f - radiusPx;
      float by = ph / 2f - radiusPx;
      for (int py = 0; py < ph; py++) {
        float qy = Math.abs(py + .5f - ph / 2f) - by;
        for (int px = 0; px < pw; px++) {
          float qx = Math.abs(px + .5f - pw / 2f) - bx;
          float ax = Math.max(0, qx), ay = Math.max(0, qy);
          float distance =
              (float) Math.sqrt(ax * ax + ay * ay) + Math.min(0, Math.max(qx, qy)) - radiusPx;
          int alpha = Math.round(Math.max(0, Math.min(1, .5f - distance)) * 255);
          p.drawPixel(px, py, 0xffffff00 | alpha);
        }
      }
      mask = new Texture(p);
      mask.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      p.dispose();
      roundedMasks.put(key, mask);
    }
    batch.setColor(color(hex));
    batch.draw(mask, x, H - y - h, w, h);
    batch.setColor(Color.WHITE);
  }

  public void panel(int x, int y, int w, int h) {
    rect(x + 1, y + 2, w, h, "07141cc0");
    rect(x, y, w, h, "526b70");
    rect(x + .5f, y + .5f, w - 1, h - 1, "172e3bf9");
    rect(x + 1, y + 1, w - 2, .5f, "829b9160");
  }

  public void material(
      int tile, float x, float y, float w, float h, float ox, float oy, float size, float alpha) {
    worldArt.material(tile, x, y, w, h, ox, oy, size, alpha);
  }

  public void decorative(String name, float x, float y, float w, float h) {
    var sprite = worldArt.sprite(name);
    if (sprite != null) batch.draw(sprite, x, H - y - h, w, h);
  }

  public void art(String name, float x, float y) {
    var sprite = worldArt.sprite(name);
    if (sprite != null) {
      float width = artWidth(name), height = artHeight(name);
      // Chairs belong to the table silhouette; preserve its broad tabletop instead of stretching
      // it.
      if (name.equals("table")) {
        height = 26;
        y += 7;
      }
      batch.draw(sprite, Math.round(x), H - Math.round(y) - height, width, height);
      return;
    }
    Texture t = texture(name);
    batch.draw(
        t, Math.round(x), H - Math.round(y) - artHeight(name), artWidth(name), artHeight(name));
  }

  /** Crop an atlas panel to a viewport without stretching or sampling adjacent panels. */
  public void portrait(String id, float x, float y, float width, float height) {
    Texture t = texture("illustrations/relationships");
    int column =
        switch (id) {
          case "mira" -> 0;
          case "asya" -> 1;
          default -> 2;
        };
    int panel = t.getWidth() / 3;
    int sw = panel - 2, sh = t.getHeight() - 2;
    float aspect = width / height;
    if (sw / (float) sh > aspect) sw = Math.round(sh * aspect);
    else sh = Math.round(sw / aspect);
    int sx = column * panel + (panel - sw) / 2, sy = 1;
    batch.draw(t, x, H - y - height, width, height, sx, sy, sw, sh, false, false);
  }

  public float paragraphHeight(String text, float width, boolean compact) {
    layout.setText(
        compact ? small : font, text, Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
    return layout.height;
  }

  /** Measured paragraphs used by narrative screens; visual QA rejects clipping. */
  public void paragraph(
      String text, float x, float y, float width, float height, String tint, boolean compact) {
    BitmapFont f = compact ? small : font;
    layout.setText(f, text, Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
    if (Boolean.getBoolean("ctrl.uiAudit") && layout.height > height + 1)
      throw new IllegalStateException(
          "Narrative text clipped: " + text + " height=" + layout.height + " available=" + height);
    clip(x, y - 1, width, height + 2);
    f.setColor(color(tint));
    f.draw(batch, text, x, H - y, width, com.badlogic.gdx.utils.Align.left, true);
    unclip();
  }

  public java.util.List<String> pages(String text, float width, float height, boolean compact) {
    BitmapFont f = compact ? small : font;
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    StringBuilder page = new StringBuilder();
    for (String word : text.split("(?<=\\s)")) {
      String next = page.toString() + word;
      layout.setText(f, next, Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
      if (layout.height > height && !page.isEmpty()) {
        result.add(page.toString().strip());
        page.setLength(0);
      }
      page.append(word);
    }
    if (!page.isEmpty()) result.add(page.toString().strip());
    if (result.isEmpty()) result.add("");
    return result;
  }

  public void scaledArt(String name, float x, float y, float width, float height) {
    var sprite = worldArt.sprite(name);
    if (sprite != null)
      batch.draw(sprite, Math.round(x), H - Math.round(y) - height, width, height);
    else batch.draw(texture(name), Math.round(x), H - Math.round(y) - height, width, height);
  }

  public void actor(String name, int dir, int animation, float x, float feetY, int outfit) {
    if (name.equals("hero") && outfit > 0) name = "hero_" + outfit;
    batch.draw(actors.get(name)[dir][animation], Math.round(x) - 8, H - Math.round(feetY), 16, 24);
    batch.setColor(Color.WHITE);
  }

  public void text(String text, float x, float y, Color color) {
    font.setColor(color);
    font.draw(batch, text, Math.round(x), H - Math.round(y));
  }

  public void text(String text, float x, float y, String color) {
    text(text, x, y, color(color));
  }

  public void small(String text, float x, float y, String color) {
    small.setColor(color(color));
    small.draw(batch, text, Math.round(x), H - Math.round(y));
  }

  public void title(String text, float x, float y, String color) {
    title.setColor(color(color));
    title.draw(batch, text, Math.round(x), H - Math.round(y));
  }

  public void wrap(String text, float x, float y, float width, String color) {
    wrap(text, x, y, width, H - y - 8, color);
  }

  public void wrap(String text, float x, float y, float width, float height, String color) {
    clip(x, y - 1, Math.min(width, W - x - 12), height);
    font.setColor(color(color));
    font.draw(batch, text, x, H - y, width, com.badlogic.gdx.utils.Align.left, true);
    unclip();
  }

  public float width(String text) {
    layout.setText(font, text);
    return layout.width;
  }

  public void center(String text, float y, String color) {
    text(text, Math.round((W - width(text)) / 2), y, color);
  }

  public int screenX(float x) {
    return Math.round(offsetX + x * scale);
  }

  public int screenY(float y) {
    return Math.round(offsetY + y * scale);
  }

  public float logicalX(int x) {
    return (x - offsetX) / scale;
  }

  public float logicalY(int y) {
    return (y - offsetY) / scale;
  }

  public float mouseX() {
    return logicalX(Gdx.input.getX());
  }

  public float mouseY() {
    return logicalY(Gdx.input.getY());
  }

  public void screenshot(String path) {
    Pixmap pix =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    PixmapIO.writePNG(Gdx.files.absolute(path), pix, -1, true);
    pix.dispose();
  }

  public void dispose() {
    font.dispose();
    small.dispose();
    title.dispose();
    textures.values().forEach(Texture::dispose);
    roundedMasks.values().forEach(Texture::dispose);
    white.dispose();
    if (frame != null) frame.dispose();
    batch.dispose();
  }
}
