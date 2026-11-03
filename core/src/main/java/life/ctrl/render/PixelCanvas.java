package life.ctrl.render;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import java.util.*;

/** Render at 480 x 270, then scale only by integer factors. All API coordinates are top-down. */
public final class PixelCanvas implements Disposable {
  public static final int W = 480, H = 270;
  public final SpriteBatch batch = new SpriteBatch();
  private final OrthographicCamera camera = new OrthographicCamera();
  private final FrameBuffer frame = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);
  private final Map<String, Color> colors = new HashMap<>();

  private Color color(String hex) {
    return colors.computeIfAbsent(hex, Color::valueOf);
  }

  public float smallWidth(String text) {
    layout.setText(small, text);
    return layout.width;
  }

  private final Map<String, Texture> textures = new HashMap<>();
  private final Map<String, TextureRegion[][]> actors = new HashMap<>();
  private final Texture white;
  public final BitmapFont font, small, title;
  private final GlyphLayout layout = new GlyphLayout();
  public int scale = 1, offsetX, offsetY;

  public PixelCanvas() {
    Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    p.setColor(Color.WHITE);
    p.fill();
    white = new Texture(p);
    p.dispose();
    font = new BitmapFont(Gdx.files.internal("fonts/ui.fnt"));
    small = new BitmapFont(Gdx.files.internal("fonts/small.fnt"));
    title = new BitmapFont(Gdx.files.internal("fonts/title.fnt"));
    for (BitmapFont f : new BitmapFont[] {font, small, title}) {
      f.setUseIntegerPositions(true);
      f.getRegion()
          .getTexture()
          .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }
    frame
        .getColorBufferTexture()
        .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    camera.setToOrtho(false, W, H);
  }

  public void preload() {
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
        }) actors.put(n, TextureRegion.split(texture(n), 16, 24));
  }

  public Texture texture(String name) {
    return textures.computeIfAbsent(
        name,
        n -> {
          Texture t = new Texture(Gdx.files.internal("art/" + n + ".png"));
          t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
          return t;
        });
  }

  public void begin() {
    frame.begin();
    Gdx.gl.glClearColor(.075f, .1f, .14f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    batch.setProjectionMatrix(camera.combined);
    batch.begin();
    batch.setColor(Color.WHITE);
  }

  public void end() {
    batch.end();
    frame.end();
    int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
    scale = Math.max(1, Math.min(w / W, h / H));
    offsetX = (w - W * scale) / 2;
    offsetY = (h - H * scale) / 2;
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

  public void rect(float x, float y, float w, float h, Color color) {
    batch.setColor(color);
    batch.draw(white, Math.round(x), H - Math.round(y) - h, w, h);
    batch.setColor(Color.WHITE);
  }

  public void rect(float x, float y, float w, float h, String color) {
    rect(x, y, w, h, color(color));
  }

  public void panel(int x, int y, int w, int h) {
    rect(x + 2, y + 3, w, h, "101d2ac0");
    rect(x, y, w, h, "81918a");
    rect(x + 1, y + 1, w - 2, h - 2, "202f3d");
    rect(x + 3, y + 3, w - 6, h - 6, "263847");
    rect(x + 4, y + 4, w - 8, 1, "40515b");
  }

  public void art(String name, float x, float y) {
    Texture t = texture(name);
    batch.draw(t, Math.round(x), H - Math.round(y) - t.getHeight());
  }

  public void scaledArt(String name, float x, float y, float width, float height) {
    batch.draw(texture(name), Math.round(x), H - Math.round(y) - height, width, height);
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
    font.setColor(color(color));
    font.draw(
        batch,
        text,
        Math.round(x),
        H - Math.round(y),
        width,
        com.badlogic.gdx.utils.Align.left,
        true);
  }

  public float width(String text) {
    layout.setText(font, text);
    return layout.width;
  }

  public void center(String text, float y, String color) {
    text(text, Math.round((W - width(text)) / 2), y, color);
  }

  public float logicalX(int x) {
    return (x - offsetX) / (float) scale;
  }

  public float logicalY(int y) {
    return (y - offsetY) / (float) scale;
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
    white.dispose();
    frame.dispose();
    batch.dispose();
  }
}
