package life.ctrl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.EnumMap;
import java.util.Locale;
import life.ctrl.ui.PinuxWindowManager.App;

/**
 * True-resolution Scene2D desktop for Pinux Astra (Archcraft / Polybar / Rofi Edition).
 *
 * The pixel-art world retains its 480x270 framebuffer; only the desktop
 * user interface renders after the game's framebuffer at native window size.
 * Scene2D owns layout, pointer hit testing, hover, focus, and animations.
 */
public final class PinuxDesktopStage {
  private static final float TOP_BAR_H = 28f;
  private static final float DOCK_W = 248f;
  private static final float DOCK_H = 44f;
  private static final float LAUNCHER_W = 484f;
  private static final float LAUNCHER_H = 346f;
  private static final float SHORTCUTS_W = 86f;
  private static final float SHORTCUTS_H = 352f;

  private static final App[] APPS = {
    App.CODE, App.TERMINAL, App.FILES, App.MAIL, App.SETTINGS
  };

  private final PinuxAstra shell;

  // The desktop owns game-IDE typing while Astra Code is focused. This prevents
  // Scene2D controls from swallowing tactile ByteScript input on Windows.
  private final Stage stage = new Stage(new ScreenViewport()) {
    @Override public boolean keyDown(int keycode) {
      // The launcher is modal: its search/shortcuts must take priority over
      // tactile ByteScript typing and global shell hotkeys.
      if (visible && launcher != null && launcher.isVisible()) {
        if (handleLauncherKey(keycode)) return true;
        return true;
      }
      if (shell != null && shell.codeTypingKey(keycode)) return true;
      return super.keyDown(keycode);
    }

    @Override public boolean keyTyped(char character) {
      if (visible && launcher != null && launcher.isVisible()) {
        handleLauncherChar(character);
        return true;
      }
      if (shell != null && shell.isCodeFront()) return true;
      return super.keyTyped(character);
    }
  };

  private final Skin skin = new Skin();
  private final EnumMap<App, Image> dockDots = new EnumMap<>(App.class);
  private final EnumMap<App, Button> dockButtons = new EnumMap<>(App.class);
  private final EnumMap<App, Button> wsPills = new EnumMap<>(App.class);
  private final EnumMap<App, Label> wsLabels = new EnumMap<>(App.class);

  private Table topBar, dock, shortcuts, launcher, launcherList;
  private Label clock, current, filterLabel, filterCountLabel, activeBadge;
  private Image wallpaper, wallpaperDim, launcherBackdrop, currentIcon;
  private Button menuTrigger;
  private PinuxNativeWindows windows;

  private App displayedFront = null;
  private int displayedOpenMask = -1;
  private boolean visible;
  private float scale = 1f;
  private String rofiQuery = "";
  private int rofiSelected = 0;
  private final App[] filteredApps = new App[APPS.length];
  private int filteredCount = APPS.length;

  public PinuxDesktopStage(PinuxAstra shell, Texture desktopTexture) {
    this.shell = shell;
    BitmapFont ui = font("fonts/hd/ui.fnt", .48f);
    BitmapFont small = font("fonts/hd/small.fnt", .48f);
    skin.add("ui-font", ui);
    skin.add("small-font", small);

    // Core Archcraft / TokyoNight Dark Surfaces & 1px Crisp Borders
    skin.add("top-bg", borderedPatch("top-bg", 0, 15, 18, 25, 246, 36, 43, 58, 210), Drawable.class);
    skin.add("dock-bg", borderedPatch("dock-bg", 8, 17, 20, 28, 242, 46, 54, 72, 220), Drawable.class);
    skin.add("wallpaper-dim", patch("wallpaper-dim", 0, 9, 12, 18, 118, 0), Drawable.class);
    skin.add("launcher-backdrop", patch("launcher-backdrop", 0, 6, 8, 13, 96, 0), Drawable.class);

    // Desktop Shortcuts
    skin.add("shortcut-idle", patch("shortcut-idle", 6, 20, 24, 34, 0, 0), Drawable.class);
    skin.add("shortcut-hover", borderedPatch("shortcut-hover", 6, 28, 34, 48, 185, 86, 156, 232, 150), Drawable.class);
    skin.add("shortcut-down", borderedPatch("shortcut-down", 6, 36, 44, 62, 225, 104, 174, 248, 210), Drawable.class);
    skin.add("shortcut-badge", borderedPatch("shortcut-badge", 6, 20, 24, 34, 195, 45, 53, 70, 180), Drawable.class);

    // Dock Items & Indicators
    skin.add("dock-idle", patch("dock-idle", 6, 24, 28, 38, 0, 0), Drawable.class);
    skin.add("dock-hover", borderedPatch("dock-hover", 6, 34, 40, 56, 215, 78, 92, 122, 185), Drawable.class);
    skin.add("dock-active", borderedPatch("dock-active", 6, 28, 35, 50, 220, 82, 148, 226, 165), Drawable.class);
    skin.add("dock-sep", patch("dock-sep", 0, 46, 54, 72, 170, 0), Drawable.class);
    skin.add("dot-off", patch("dot-off", 1, 0, 0, 0, 0, 0), Drawable.class);
    skin.add("dot-open", patch("dot-open", 2, 118, 132, 158, 220, 0), Drawable.class);
    skin.add("dot-focus", patch("dot-focus", 2, 88, 166, 255, 255, 0), Drawable.class);

    // Topbar Pills & Buttons
    skin.add("pill-idle", borderedPatch("pill-idle", 4, 22, 26, 36, 215, 40, 47, 63, 180), Drawable.class);
    skin.add("pill-hover", borderedPatch("pill-hover", 4, 32, 39, 54, 240, 82, 148, 226, 200), Drawable.class);
    skin.add("pill-active", borderedPatch("pill-active", 4, 29, 44, 68, 245, 88, 166, 255, 235), Drawable.class);
    skin.add("ws-idle", patch("ws-idle", 3, 22, 26, 36, 130, 0), Drawable.class);
    skin.add("ws-occupied", borderedPatch("ws-occupied", 3, 25, 30, 42, 210, 52, 62, 82, 190), Drawable.class);
    skin.add("ws-active", borderedPatch("ws-active", 3, 32, 52, 82, 245, 88, 166, 255, 240), Drawable.class);

    // Rofi Launcher & Shared Buttons
    skin.add("launcher-bg", borderedPatch("launcher-bg", 8, 16, 19, 27, 250, 56, 67, 90, 235), Drawable.class);
    skin.add("rofi-search", borderedPatch("rofi-search", 5, 11, 14, 20, 250, 46, 56, 76, 220), Drawable.class);
    skin.add("rofi-row-idle", patch("rofi-row-idle", 5, 22, 26, 36, 0, 0), Drawable.class);
    skin.add("rofi-row-sel", borderedPatch("rofi-row-sel", 5, 28, 43, 66, 235, 88, 166, 255, 220), Drawable.class);
    skin.add("rofi-tag", borderedPatch("rofi-tag", 3, 22, 27, 38, 220, 48, 58, 78, 180), Drawable.class);
    skin.add("divider-line", patch("divider-line", 0, 42, 50, 66, 165, 0), Drawable.class);

    // Required shared skin keys for PinuxNativeWindows compatibility
    skin.add("hover-bg", borderedPatch("hover-bg", 5, 34, 48, 72, 235, 88, 166, 255, 210), Drawable.class);
    skin.add("button-bg", borderedPatch("button-bg", 5, 24, 29, 40, 225, 48, 57, 76, 190), Drawable.class);
    skin.add("window-bg", borderedPatch("window-bg", 7, 20, 23, 32, 252, 48, 56, 74, 235), Drawable.class);
    skin.add("pane-bg", borderedPatch("pane-bg", 6, 13, 16, 24, 248, 36, 43, 58, 210), Drawable.class);
    skin.add("input-bg", borderedPatch("input-bg", 5, 15, 20, 30, 248, 46, 56, 78, 220), Drawable.class);
    skin.add("window-header", borderedPatch("window-header", 5, 26, 30, 41, 255, 46, 54, 72, 220), Drawable.class);
    skin.add("title-button", patch("title-button", 5, 34, 40, 54, 185, 0), Drawable.class);
    skin.add("title-button-hover", patch("title-button-hover", 5, 58, 68, 92, 240, 0), Drawable.class);
    skin.add("title-close-hover", patch("title-close-hover", 5, 196, 68, 82, 248, 0), Drawable.class);

    // OneDark / Archcraft-inspired terminal palette (kept intact for PinuxNativeWindows)
    skin.add("term-frame", borderedPatch("term-frame", 8, 34, 39, 50, 255, 58, 67, 86, 240), Drawable.class);
    skin.add("term-header", patch("term-header", 6, 26, 30, 39, 255, 0), Drawable.class);
    skin.add("term-surface", patch("term-surface", 6, 18, 21, 28, 255, 0), Drawable.class);
    skin.add("term-toolbar", patch("term-toolbar", 4, 23, 27, 36, 255, 0), Drawable.class);
    skin.add("term-tab", patch("term-tab", 4, 34, 40, 52, 255, 0), Drawable.class);
    skin.add("term-prompt", patch("term-prompt", 5, 28, 33, 44, 255, 0), Drawable.class);
    skin.add("term-title-button", patch("term-title-button", 4, 34, 39, 50, 185, 0), Drawable.class);
    skin.add("term-title-hover", patch("term-title-hover", 4, 62, 72, 90, 255, 0), Drawable.class);
    skin.add("term-title-close", patch("term-title-close", 4, 186, 62, 74, 255, 0), Drawable.class);

    addIcon("code", 104, 174, 255);
    addIcon("terminal", 122, 216, 166);
    addIcon("files", 232, 178, 104);
    addIcon("mail", 188, 138, 248);
    addIcon("settings", 142, 162, 194);
    addIcon("astra", 88, 166, 255);
    addIcon("search", 132, 148, 176);

    wallpaper = new Image(new TextureRegionDrawable(new TextureRegion(desktopTexture)));
    wallpaper.setScaling(Scaling.stretch);
    wallpaper.setTouchable(Touchable.disabled);
    stage.addActor(wallpaper);

    wallpaperDim = new Image(skin.getDrawable("wallpaper-dim"));
    wallpaperDim.setTouchable(Touchable.disabled);
    stage.addActor(wallpaperDim);

    build();
    windows = new PinuxNativeWindows(stage, skin, shell);
    setVisible(false);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private BitmapFont font(String path, float amount) {
    BitmapFont f = new BitmapFont(Gdx.files.internal(path));
    f.getData().setScale(amount);
    f.setUseIntegerPositions(false);
    f.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    return f;
  }

  private Drawable patch(String id, int r, int red, int green, int blue,
                         int alpha, int unused) {
    return borderedPatch(id, r, red, green, blue, alpha, red, green, blue, 0);
  }

  private Drawable borderedPatch(String id, int r, int red, int green, int blue, int alpha,
                                 int bRed, int bGreen, int bBlue, int bAlpha) {
    // Do not use a 72px NinePatch for 1px separators / 3px indicators:
    // its 24px stretch borders made those elements draw as huge blocks.
    if (r <= 3) {
      Pixmap dot = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      dot.drawPixel(0, 0, (red << 24) | (green << 16) | (blue << 8) | alpha);
      Texture tex = new Texture(dot);
      dot.dispose();
      skin.add(id + "-texture", tex);
      return new TextureRegionDrawable(new TextureRegion(tex));
    }
    // A compact NinePatch supports controls as short as 19px high.
    int size = 32, border = 8, radius = Math.min(8, r);
    Pixmap pix = new Pixmap(size, size, Pixmap.Format.RGBA8888);
    float strokeW = bAlpha > 0 ? 1.35f : 0f;
    for (int y = 0; y < size; y++) {
      float ay = Math.abs(y + .5f - size / 2f) - (size / 2f - radius);
      for (int x = 0; x < size; x++) {
        float ax = Math.abs(x + .5f - size / 2f) - (size / 2f - radius);
        float distance = (float) Math.hypot(Math.max(ax, 0), Math.max(ay, 0))
            + Math.min(0, Math.max(ax, ay)) - radius;
        float outerA = Math.max(0f, Math.min(1f, .5f - distance));
        if (outerA <= 0f) continue;

        int fr = red, fg = green, fb = blue;
        float fa = (alpha / 255f) * outerA;

        if (strokeW > 0f) {
          float innerFactor = Math.max(0f, Math.min(1f, .5f - (distance + strokeW)));
          float borderMix = 1f - innerFactor;
          float ba = (bAlpha / 255f) * outerA;
          fr = Math.round(red * innerFactor + bRed * borderMix);
          fg = Math.round(green * innerFactor + bGreen * borderMix);
          fb = Math.round(blue * innerFactor + bBlue * borderMix);
          fa = (alpha / 255f) * innerFactor * outerA + ba * borderMix;
        }
        int outA = Math.max(0, Math.min(255, Math.round(fa * 255f)));
        pix.drawPixel(x, y, (fr << 24) | (fg << 16) | (fb << 8) | outA);
      }
    }
    Texture t = new Texture(pix);
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    pix.dispose();
    skin.add(id + "-texture", t);
    NinePatch patch = new NinePatch(t, border, border, border, border);
    return new NinePatchDrawable(patch);
  }

  private void addIcon(String name, int r, int g, int b) {
    int n = 128;
    Pixmap pix = new Pixmap(n, n, Pixmap.Format.RGBA8888);
    int mainCol = (r << 24) | (g << 16) | (b << 8) | 0xFF;
    int brightCol = 0xEEF4FFFF;
    int softCol = (r << 24) | (g << 16) | (b << 8) | 0x44;

    switch (name) {
      case "code" -> {
        pix.setColor(softCol);
        fillRoundRect(pix, 16, 22, 96, 84, 14);
        pix.setColor(mainCol);
        stroke(pix, 44, 46, 28, 64, 5);
        stroke(pix, 28, 64, 44, 82, 5);
        stroke(pix, 84, 46, 100, 64, 5);
        stroke(pix, 100, 64, 84, 82, 5);
        pix.setColor(brightCol);
        stroke(pix, 70, 42, 56, 86, 5);
      }
      case "terminal" -> {
        pix.setColor(softCol);
        fillRoundRect(pix, 16, 24, 96, 80, 14);
        pix.setColor(mainCol);
        stroke(pix, 34, 46, 54, 62, 5);
        stroke(pix, 54, 62, 34, 78, 5);
        pix.setColor(brightCol);
        pix.fillRectangle(62, 75, 30, 5);
      }
      case "files" -> {
        pix.setColor(softCol);
        fillRoundRect(pix, 20, 36, 88, 60, 10);
        pix.setColor(mainCol);
        stroke(pix, 24, 46, 24, 90, 5);
        stroke(pix, 24, 90, 104, 90, 5);
        stroke(pix, 104, 90, 104, 48, 5);
        stroke(pix, 104, 48, 62, 48, 5);
        stroke(pix, 62, 48, 52, 38, 5);
        stroke(pix, 52, 38, 24, 38, 5);
        stroke(pix, 24, 38, 24, 48, 5);
        pix.setColor(brightCol);
        pix.fillRectangle(36, 62, 56, 4);
      }
      case "mail" -> {
        pix.setColor(softCol);
        fillRoundRect(pix, 18, 34, 92, 60, 10);
        pix.setColor(mainCol);
        stroke(pix, 24, 42, 104, 42, 5);
        stroke(pix, 24, 42, 24, 88, 5);
        stroke(pix, 104, 42, 104, 88, 5);
        stroke(pix, 24, 88, 104, 88, 5);
        pix.setColor(brightCol);
        stroke(pix, 26, 44, 64, 70, 5);
        stroke(pix, 102, 44, 64, 70, 5);
      }
      case "settings" -> {
        pix.setColor(mainCol);
        for (int rad = 22; rad <= 27; rad++) pix.drawCircle(64, 64, rad);
        for (int i = 0; i < 8; i++) {
          double angle = i * Math.PI / 4;
          int ax = 64 + (int) (25 * Math.cos(angle)), ay = 64 + (int) (25 * Math.sin(angle));
          int bx = 64 + (int) (36 * Math.cos(angle)), by = 64 + (int) (36 * Math.sin(angle));
          stroke(pix, ax, ay, bx, by, 6);
        }
        pix.setColor(brightCol);
        for (int rad = 8; rad <= 11; rad++) pix.drawCircle(64, 64, rad);
      }
      case "astra" -> {
        pix.setColor(mainCol);
        stroke(pix, 64, 22, 26, 98, 7);
        stroke(pix, 64, 22, 102, 98, 7);
        pix.setColor(brightCol);
        stroke(pix, 42, 74, 86, 74, 6);
        pix.fillCircle(64, 52, 6);
      }
      case "search" -> {
        pix.setColor(mainCol);
        for (int rad = 20; rad <= 24; rad++) pix.drawCircle(56, 56, rad);
        pix.setColor(brightCol);
        stroke(pix, 73, 73, 98, 98, 6);
      }
    }
    Texture t = new Texture(pix);
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    pix.dispose();
    skin.add("icon-" + name, t);
  }

  private static void fillRoundRect(Pixmap p, int x, int y, int w, int h, int r) {
    p.fillRectangle(x + r, y, w - 2 * r, h);
    p.fillRectangle(x, y + r, r, h - 2 * r);
    p.fillRectangle(x + w - r, y + r, r, h - 2 * r);
    p.fillCircle(x + r, y + r, r);
    p.fillCircle(x + w - r, y + r, r);
    p.fillCircle(x + r, y + h - r, r);
    p.fillCircle(x + w - r, y + h - r, r);
  }

  private static void stroke(Pixmap p, int x, int y, int ex, int ey, int size) {
    for (int dx = -size / 2; dx <= size / 2; dx++)
      for (int dy = -size / 2; dy <= size / 2; dy++)
        p.drawLine(x + dx, y + dy, ex + dx, ey + dy);
  }

  private Label label(String text, boolean secondary) {
    return label(text, secondary, secondary ? "B8C4D8" : "EEF4FF", Align.center);
  }

  private Label label(String text, boolean secondary, String hexColor, int align) {
    Label.LabelStyle style = new Label.LabelStyle(
        skin.getFont(secondary ? "small-font" : "ui-font"),
        Color.valueOf(hexColor));
    Label l = new Label(text, style);
    l.setAlignment(align);
    return l;
  }

  private Image icon(String name) {
    Image img = new Image(new TextureRegionDrawable(new TextureRegion(skin.get("icon-" + name, Texture.class))));
    img.setScaling(Scaling.fit);
    return img;
  }

  private Button button(Runnable action) {
    return styledButton("button-bg", "hover-bg", "hover-bg", action);
  }

  private Button styledButton(String upKey, String overKey, String downKey, Runnable action) {
    Button.ButtonStyle style = new Button.ButtonStyle();
    style.up = skin.getDrawable(upKey);
    style.over = skin.getDrawable(overKey);
    style.down = skin.getDrawable(downKey);
    Button b = new Button(style);
    b.setProgrammaticChangeEvents(false);
    b.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent e, Actor actor) {
        if (!b.isChecked()) return;
        b.setChecked(false);
        action.run();
      }
    });
    return b;
  }

  private Button shortcut(String iconName, String title, String tag, App app) {
    Button b = styledButton("shortcut-idle", "shortcut-hover", "shortcut-down", () -> launchApp(app));
    b.pad(5, 4, 5, 4);

    Table badge = new Table();
    badge.setBackground(skin.getDrawable("shortcut-badge"));
    badge.add(icon(iconName)).size(28, 28).center();

    Label titleLbl = label(title, true, "E4ECF8", Align.center);
    Label tagLbl = label(tag, true, "6E7E98", Align.center);

    b.add(badge).size(42, 42).center();
    b.row();
    b.add(titleLbl).minWidth(76).padTop(4).center();
    b.row();
    b.add(tagLbl).padTop(0).center();

    b.setTransform(true);
    b.setOrigin(SHORTCUTS_W / 2f, 40f);
    b.addListener(new ClickListener() {
      @Override public void enter(InputEvent e, float x, float y, int pointer, Actor fromActor) {
        if (pointer != -1) return;
        b.clearActions();
        b.addAction(Actions.scaleTo(1.03f, 1.03f, .1f, Interpolation.pow2Out));
        titleLbl.setColor(Color.valueOf("FFFFFF"));
        tagLbl.setColor(Color.valueOf("88B8F8"));
      }

      @Override public void exit(InputEvent e, float x, float y, int pointer, Actor toActor) {
        if (pointer != -1) return;
        b.clearActions();
        b.addAction(Actions.scaleTo(1f, 1f, .12f, Interpolation.pow2Out));
        titleLbl.setColor(Color.valueOf("E4ECF8"));
        tagLbl.setColor(Color.valueOf("6E7E98"));
      }
    });
    return b;
  }

  private Button dockButton(String iconName, App app) {
    Button b = styledButton("dock-idle", "dock-hover", "dock-active", () -> launchApp(app));
    b.pad(4, 5, 3, 5);
    Image ic = icon(iconName);
    Image dot = new Image(skin.getDrawable("dot-off"));
    dockDots.put(app, dot);
    dockButtons.put(app, b);

    b.add(ic).size(24, 24).center();
    b.row();
    b.add(dot).size(10, 3).padTop(3).center();

    b.setTransform(true);
    b.setOrigin(19f, 19f);
    b.addListener(new ClickListener() {
      @Override public void enter(InputEvent e, float x, float y, int pointer, Actor fromActor) {
        if (pointer != -1) return;
        b.clearActions();
        b.addAction(Actions.sequence(
            Actions.scaleTo(1.09f, 1.09f, .11f, Interpolation.pow2Out)));
      }

      @Override public void exit(InputEvent e, float x, float y, int pointer, Actor toActor) {
        if (pointer != -1) return;
        b.clearActions();
        b.addAction(Actions.scaleTo(1f, 1f, .13f, Interpolation.pow2Out));
      }
    });
    return b;
  }

  private void build() {
    // 1. POLYBAR TOP PANEL (28px, modular left / center / right architecture)
    topBar = new Table();
    topBar.setBackground(skin.getDrawable("top-bg"));
    topBar.left().padLeft(10).padRight(10);

    // Left module: Archcraft Rofi Launcher Trigger + Interactive Workspace Tags
    menuTrigger = styledButton("pill-idle", "pill-hover", "pill-active", this::toggleLauncher);
    menuTrigger.pad(2, 8, 2, 9);
    menuTrigger.add(icon("astra")).size(14, 14).padRight(6);
    Label distro = label("ASTRA", true, "E8F0FC", Align.left);
    menuTrigger.add(distro);
    topBar.add(menuTrigger).height(21).padRight(8);

    Image sepLeft = new Image(skin.getDrawable("dock-sep"));
    topBar.add(sepLeft).width(1).height(14).padRight(8);

    // Interactive workspace pills mapped to core apps (01..05)
    Table wsBar = new Table();
    String[] wsCodes = {"01", "02", "03", "04", "05"};
    for (int i = 0; i < APPS.length; i++) {
      final App wsApp = APPS[i];
      Button wsBtn = styledButton("ws-idle", "pill-hover", "ws-active", () -> launchApp(wsApp));
      wsBtn.pad(1, 6, 1, 6);
      Label wsLbl = label(wsCodes[i], true, "7C8AA2", Align.center);
      wsBtn.add(wsLbl);
      wsPills.put(wsApp, wsBtn);
      wsLabels.put(wsApp, wsLbl);
      wsBar.add(wsBtn).height(19).padRight(3);
    }
    topBar.add(wsBar).left();

    // Center module: Active Window Focus Chip (click cycles open windows)
    topBar.add().expandX();
    Button focusChip = styledButton("pill-idle", "pill-hover", "pill-active", () -> {
      if (windows != null && windows.hasOpen()) {
        windows.cycleFocus();
        raiseChrome();
      } else {
        toggleLauncher();
      }
    });
    focusChip.pad(2, 10, 2, 10);
    currentIcon = icon("astra");
    focusChip.add(currentIcon).size(13, 13).padRight(6);
    current = label("Рабочий стол", true, "C8D4E8", Align.center);
    focusChip.add(current);
    topBar.add(focusChip).height(21);
    topBar.add().expandX();

    // Right module: Window Switcher / Close Quick Actions + Session Status + Clock
    Button cycleBtn = styledButton("ws-idle", "pill-hover", "pill-active", () -> {
      if (windows != null && windows.hasOpen()) {
        windows.cycleFocus();
        raiseChrome();
      }
    });
    cycleBtn.pad(1, 7, 1, 7);
    activeBadge = label("WM:IDLE", true, "7E8EA8", Align.center);
    cycleBtn.add(activeBadge);
    topBar.add(cycleBtn).height(19).padRight(6);

    Table statusPill = new Table();
    statusPill.setBackground(skin.getDrawable("pill-idle"));
    statusPill.pad(1, 8, 1, 8);
    Label dot = label("●", true, "7AD8A6", Align.center);
    statusPill.add(dot).padRight(5);
    Label session = label("pinux", true, "98A8C0", Align.center);
    statusPill.add(session);
    topBar.add(statusPill).height(20).padRight(6);

    Button clockPill = styledButton("pill-idle", "pill-hover", "pill-active", this::toggleLauncher);
    clockPill.pad(1, 9, 1, 9);
    clock = label("20:47", true, "EEF4FF", Align.center);
    clockPill.add(clock);
    topBar.add(clockPill).height(20);

    stage.addActor(topBar);

    // 2. DESKTOP SHORTCUTS (Compact vertical grid with crisp framing & hierarchy)
    shortcuts = new Table();
    shortcuts.top().left();
    shortcuts.add(shortcut("code", "Astra Code", "IDE", App.CODE)).size(84, 82).padBottom(5);
    shortcuts.row();
    shortcuts.add(shortcut("terminal", "Terminal", "SH", App.TERMINAL)).size(84, 82).padBottom(5);
    shortcuts.row();
    shortcuts.add(shortcut("files", "Files", "FM", App.FILES)).size(84, 82).padBottom(5);
    shortcuts.row();
    shortcuts.add(shortcut("mail", "TaskMail", "MSG", App.MAIL)).size(84, 82);
    stage.addActor(shortcuts);

    // 3. FLOATING ARCHCRAFT DOCK (Compact, separator before settings, live running indicators)
    dock = new Table();
    dock.setBackground(skin.getDrawable("dock-bg"));
    dock.pad(4, 8, 4, 8);
    dock.add(dockButton("code", App.CODE)).size(38, 36).padLeft(2).padRight(2);
    dock.add(dockButton("terminal", App.TERMINAL)).size(38, 36).padLeft(2).padRight(2);
    dock.add(dockButton("files", App.FILES)).size(38, 36).padLeft(2).padRight(2);
    dock.add(dockButton("mail", App.MAIL)).size(38, 36).padLeft(2).padRight(4);

    Image dockSep = new Image(skin.getDrawable("dock-sep"));
    dock.add(dockSep).width(1).height(22).padLeft(2).padRight(4);

    dock.add(dockButton("settings", App.SETTINGS)).size(38, 36).padLeft(2).padRight(2);
    stage.addActor(dock);

    // 4. ROFI APPLICATION LAUNCHER (Backdrop click-catcher + keyboard/search-ready modal)
    launcherBackdrop = new Image(skin.getDrawable("launcher-backdrop"));
    launcherBackdrop.setVisible(false);
    launcherBackdrop.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) {
        hideLauncher();
      }
    });
    stage.addActor(launcherBackdrop);

    launcher = new Table();
    launcher.top().left();
    launcher.setBackground(skin.getDrawable("launcher-bg"));
    launcher.pad(12, 14, 12, 14);

    // Rofi Prompt Header Bar
    Table searchBar = new Table();
    searchBar.setBackground(skin.getDrawable("rofi-search"));
    searchBar.pad(5, 10, 5, 10);
    searchBar.add(icon("search")).size(14, 14).padRight(7);
    Label promptPrefix = label("drun :", true, "58A6FF", Align.left);
    searchBar.add(promptPrefix).padRight(6);
    filterLabel = label("поиск приложений...", true, "7E8EA8", Align.left);
    searchBar.add(filterLabel).expandX().left();
    filterCountLabel = label("5/5", true, "6E7E98", Align.right);
    searchBar.add(filterCountLabel).right();

    launcher.add(searchBar).growX().height(30).padBottom(8);
    launcher.row();

    launcherList = new Table();
    launcherList.top().left();
    launcher.add(launcherList).grow().top();
    launcher.row();

    // Rofi Footer Hints
    Image footerLine = new Image(skin.getDrawable("divider-line"));
    launcher.add(footerLine).growX().height(1).padTop(6).padBottom(5);
    launcher.row();

    Table footer = new Table();
    footer.add(label("↑↓ выбор    Enter запуск    1–5 быстро    Esc закрыть", true, "8293AB", Align.left))
        .expandX().left();
    launcher.add(footer).growX().height(16);

    rebuildLauncherList();
    launcher.setVisible(false);
    stage.addActor(launcher);
  }

  private void rebuildLauncherList() {
    launcherList.clearChildren();
    filteredCount = 0;
    String q = rofiQuery.trim().toLowerCase(Locale.ROOT);

    for (App app : APPS) {
      String title = appTitle(app);
      String desc = appSubtitle(app);
      String cmd = appBind(app);
      if (q.isEmpty()
          || title.toLowerCase(Locale.ROOT).contains(q)
          || desc.toLowerCase(Locale.ROOT).contains(q)
          || cmd.toLowerCase(Locale.ROOT).contains(q)) {
        filteredApps[filteredCount++] = app;
      }
    }

    if (rofiSelected >= filteredCount) rofiSelected = Math.max(0, filteredCount - 1);

    if (filterLabel != null) {
      if (q.isEmpty()) {
        filterLabel.setText("поиск приложений...");
        filterLabel.setColor(Color.valueOf("7E8EA8"));
      } else {
        filterLabel.setText(rofiQuery + "_");
        filterLabel.setColor(Color.valueOf("EEF4FF"));
      }
    }
    if (filterCountLabel != null) {
      filterCountLabel.setText(filteredCount + "/" + APPS.length);
    }

    for (int i = 0; i < filteredCount; i++) {
      final int idx = i;
      final App app = filteredApps[i];
      boolean selected = (i == rofiSelected);

      Button rowBtn = styledButton(
          selected ? "rofi-row-sel" : "rofi-row-idle",
          "rofi-row-sel",
          "rofi-row-sel",
          () -> launchApp(app));
      rowBtn.pad(5, 8, 5, 8);

      rowBtn.add(icon(appIcon(app))).size(20, 20).padRight(9);

      Table textCol = new Table();
      Label nameLbl = label(appTitle(app), true, selected ? "FFFFFF" : "DCE6F6", Align.left);
      Label subLbl = label(appSubtitle(app), true, selected ? "9CC4FF" : "7A8A9E", Align.left);
      nameLbl.setEllipsis(true);
      subLbl.setEllipsis(true);
      textCol.add(nameLbl).left().width(300);
      textCol.row();
      textCol.add(subLbl).left().width(300).padTop(2);
      rowBtn.add(textCol).growX().minWidth(0).left();

      Table tagBox = new Table();
      tagBox.setBackground(skin.getDrawable("rofi-tag"));
      tagBox.pad(1, 6, 1, 6);
      boolean isRunning = windows != null && windows.isVisible(app);
      Label tagLbl = label(isRunning ? "RUN" : appBind(app), true,
          isRunning ? "7AD8A6" : "8A9AB2", Align.center);
      tagBox.add(tagLbl);
      rowBtn.add(tagBox).height(18).right();

      rowBtn.addListener(new ClickListener() {
        @Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
          if (pointer != -1 || rofiSelected == idx) return;
          rofiSelected = idx;
        }
      });

      launcherList.add(rowBtn).growX().height(42).padBottom(3);
      launcherList.row();
    }
  }

  private boolean handleLauncherKey(int keycode) {
    if (keycode == Input.Keys.ESCAPE) {
      hideLauncher();
      return true;
    }
    if (keycode == Input.Keys.UP || keycode == Input.Keys.TAB && Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
      if (filteredCount > 0) {
        rofiSelected = (rofiSelected - 1 + filteredCount) % filteredCount;
        rebuildLauncherList();
      }
      return true;
    }
    if (keycode == Input.Keys.DOWN || keycode == Input.Keys.TAB) {
      if (filteredCount > 0) {
        rofiSelected = (rofiSelected + 1) % filteredCount;
        rebuildLauncherList();
      }
      return true;
    }
    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
      if (filteredCount > 0 && rofiSelected >= 0 && rofiSelected < filteredCount) {
        launchApp(filteredApps[rofiSelected]);
      }
      return true;
    }
    if (keycode == Input.Keys.BACKSPACE) {
      if (!rofiQuery.isEmpty()) {
        rofiQuery = rofiQuery.substring(0, rofiQuery.length() - 1);
        rofiSelected = 0;
        rebuildLauncherList();
      }
      return true;
    }
    if (rofiQuery.isEmpty() && keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_5) {
      int idx = keycode - Input.Keys.NUM_1;
      if (idx >= 0 && idx < APPS.length) {
        launchApp(APPS[idx]);
        return true;
      }
    }
    return false;
  }

  private boolean handleLauncherChar(char ch) {
    if (ch == 27 || ch == '\r' || ch == '\n' || ch == '\t' || ch == '\b') return false;
    if (rofiQuery.isEmpty() && ch >= '1' && ch <= '5') return true;
    if (!Character.isISOControl(ch) && rofiQuery.length() < 18) {
      rofiQuery += ch;
      rofiSelected = 0;
      rebuildLauncherList();
      return true;
    }
    return false;
  }

  private void toggleLauncher() {
    if (launcher.isVisible()) {
      hideLauncher();
    } else {
      showLauncher();
    }
  }

  private void showLauncher() {
    rofiQuery = "";
    rofiSelected = 0;
    rebuildLauncherList();
    launcherBackdrop.setVisible(true);
    launcher.setVisible(true);
    raiseChrome();
    launcher.clearActions();
    launcher.getColor().a = 0f;
    launcher.setScale(scale * .96f);
    launcher.addAction(Actions.parallel(
        Actions.fadeIn(.12f, Interpolation.pow2Out),
        Actions.scaleTo(scale, scale, .14f, Interpolation.pow2Out)));
  }

  private void hideLauncher() {
    if (launcher == null || !launcher.isVisible()) return;
    launcherBackdrop.setVisible(false);
    launcher.clearActions();
    launcher.setVisible(false);
  }

  private void launchApp(App app) {
    hideLauncher();
    shell.openApp(app);
  }

  private void syncChromeState() {
    // Derive actual focus/open state from VisWindows, not a parallel list of
    // historic opens. Windows can also be closed or focused by their own chrome.
    App focused = windows == null ? null : windows.frontApp();
    int openMask = 0;
    if (windows != null) {
      for (int i = 0; i < APPS.length; i++) {
        if (windows.isVisible(APPS[i])) openMask |= 1 << i;
      }
    }
    // The UI is redrawn at 60 FPS; avoid allocating Color/Drawable wrappers
    // and resetting table styles every frame when focus has not changed.
    if (displayedFront == focused && displayedOpenMask == openMask) return;
    displayedFront = focused;
    displayedOpenMask = openMask;

    if (current != null) current.setText(focused == null ? "Рабочий стол" : appTitle(focused));
    if (currentIcon != null) {
      currentIcon.setDrawable(new TextureRegionDrawable(new TextureRegion(
          skin.get("icon-" + (focused == null ? "astra" : appIcon(focused)), Texture.class))));
    }
    if (activeBadge != null) {
      int count = Integer.bitCount(openMask);
      activeBadge.setText(count == 0 ? "WM:IDLE" : "WM:" + count);
      activeBadge.setColor(Color.valueOf(count == 0 ? "7E8EA8" : "88B8F8"));
    }

    for (int i = 0; i < APPS.length; i++) {
      App app = APPS[i];
      boolean isFocus = app == focused;
      boolean isOpen = (openMask & (1 << i)) != 0;
      Image dot = dockDots.get(app);
      if (dot != null) {
        dot.setDrawable(skin.getDrawable(isFocus ? "dot-focus" : (isOpen ? "dot-open" : "dot-off")));
      }
      Button dBtn = dockButtons.get(app);
      if (dBtn != null) dBtn.getStyle().up = skin.getDrawable(isFocus ? "dock-active" : "dock-idle");
      Button wsBtn = wsPills.get(app);
      Label wsLbl = wsLabels.get(app);
      if (wsBtn != null && wsLbl != null) {
        wsBtn.getStyle().up = skin.getDrawable(isFocus ? "ws-active" : (isOpen ? "ws-occupied" : "ws-idle"));
        wsLbl.setColor(Color.valueOf(isFocus ? "FFFFFF" : (isOpen ? "C2D2EA" : "7C8AA2")));
      }
    }
    if (launcher != null && launcher.isVisible()) rebuildLauncherList();
  }

  private static String appTitle(App app) {
    return switch (app) {
      case TERMINAL -> "Terminal";
      case FILES -> "Files";
      case MAIL -> "TaskMail";
      case SETTINGS -> "Settings";
      case CODE -> "Astra Code";
    };
  }

  private static String appSubtitle(App app) {
    return switch (app) {
      case CODE -> "ByteScript IDE & отладчик";
      case TERMINAL -> "Командная оболочка Astra SH";
      case FILES -> "Файловый менеджер системы";
      case MAIL -> "Входящие задачи и контракты";
      case SETTINGS -> "Параметры среды Pinux";
    };
  }

  private static String appIcon(App app) {
    return switch (app) {
      case CODE -> "code";
      case TERMINAL -> "terminal";
      case FILES -> "files";
      case MAIL -> "mail";
      case SETTINGS -> "settings";
    };
  }

  private static String appBind(App app) {
    return switch (app) {
      case CODE -> "#1";
      case TERMINAL -> "#2";
      case FILES -> "#3";
      case MAIL -> "#4";
      case SETTINGS -> "#5";
    };
  }

  public Stage inputStage() { return stage; }
  public boolean isVisible() { return visible; }

  public void openNativeWindow(App app) {
    windows.open(app);
    syncChromeState();
    raiseChrome();
  }

  public void raiseChrome() {
    topBar.toFront();
    dock.toFront();
    launcherBackdrop.toFront();
    launcher.toFront();
  }

  public void setDesktopVisible(boolean showDesktop) {
    wallpaper.setVisible(showDesktop);
    wallpaperDim.setVisible(showDesktop);
    // Desktop launchers remain behind open applications but are not visually
    // duplicated inside maximized application workspaces.
    shortcuts.setVisible(showDesktop && !windows.hasOpen());
  }

  public boolean hasNativeWindows() { return windows.hasOpen(); }

  public boolean isCodeFront() { return windows.isFrontCode(); }

  public void cycleNativeWindows() {
    windows.cycleFocus();
    syncChromeState();
    raiseChrome();
  }

  public void closeFrontNativeWindow() {
    windows.closeFront();
    syncChromeState();
  }

  public void resetNativeWindows() {
    windows.reset();
    syncChromeState();
  }

  public void setVisible(boolean show) {
    visible = show;
    stage.getRoot().setVisible(show);
    if (!show) hideLauncher();
  }

  public void resize(int w, int h) {
    stage.getViewport().update(w, h, true);
    float width = stage.getWidth(), height = stage.getHeight();
    scale = Math.max(.67f, Math.min(1.35f, height / 810f));
    skin.getFont("ui-font").getData().setScale(.40f * scale);
    skin.getFont("small-font").getData().setScale(.40f * scale);

    wallpaper.setBounds(0, 0, width, height);
    wallpaperDim.setBounds(0, 0, width, height);
    launcherBackdrop.setBounds(0, 0, width, height);

    float barH = TOP_BAR_H * scale;
    topBar.setBounds(0, height - barH, width, barH);
    topBar.padLeft(12 * scale);
    topBar.padRight(12 * scale);

    shortcuts.setTransform(true);
    shortcuts.setSize(SHORTCUTS_W, SHORTCUTS_H);
    shortcuts.setScale(scale);
    shortcuts.setPosition(18 * scale, Math.max(68 * scale, height - barH - (SHORTCUTS_H + 18) * scale));

    dock.invalidateHierarchy();
    dock.setTransform(true);
    dock.setSize(DOCK_W, DOCK_H);
    dock.setScale(scale);
    dock.setPosition((width - DOCK_W * scale) / 2f, 12 * scale);

    launcher.setTransform(true);
    launcher.setSize(LAUNCHER_W, LAUNCHER_H);
    launcher.setOrigin(LAUNCHER_W / 2f, LAUNCHER_H / 2f);
    launcher.setScale(scale);
    launcher.setPosition(
        (width - LAUNCHER_W * scale) / 2f,
        Math.max(72 * scale, (height - LAUNCHER_H * scale) / 2f + 18 * scale));
    launcher.invalidateHierarchy();

    if (windows != null) windows.resize(w, h);
  }

  public void show() {
    if (windows != null) windows.reset();
    syncChromeState();
    setVisible(true);
    stage.getRoot().setColor(1, 1, 1, 0);
    stage.getRoot().addAction(Actions.fadeIn(.20f, Interpolation.pow2Out));
  }

  public void draw(float delta) {
    if (!visible) return;
    clock.setText(shell.clockText());
    windows.tick();
    syncChromeState();
    stage.act(Math.min(delta, .05f));
    stage.draw();
  }

  public void dispose() {
    if (windows != null) windows.dispose();
    stage.dispose();
    skin.dispose();
  }
}

