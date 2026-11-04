package life.ctrl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import life.ctrl.ui.PinuxWindowManager.App;

/**
 * True-resolution Scene2D desktop for Pinux Astra.
 *
 * The pixel-art world retains its 480x270 framebuffer; only the desktop
 * user interface renders after the game's framebuffer at native window size.
 * Scene2D owns layout, pointer hit testing, hover, focus, and animations.
 */
public final class PinuxDesktopStage {
  private final PinuxAstra shell;
  // The desktop owns game-IDE typing while Astra Code is focused. This prevents
  // Scene2D controls from swallowing tactile ByteScript input on Windows.
  private final Stage stage = new Stage(new ScreenViewport()) {
    @Override public boolean keyDown(int keycode) {
      if (shell != null && shell.codeTypingKey(keycode)) return true;
      return super.keyDown(keycode);
    }
    @Override public boolean keyTyped(char character) {
      if (shell != null && shell.isCodeFront()) return true;
      return super.keyTyped(character);
    }
  };
  private final Skin skin = new Skin();
  private Table topBar, dock, shortcuts, launcher;
  private Label clock, current;
  private Image wallpaper, wallpaperDim;
  private PinuxNativeWindows windows;
  private boolean visible;
  private float scale = 1f;

  public PinuxDesktopStage(PinuxAstra shell, Texture desktopTexture) {
    this.shell = shell;
    BitmapFont ui = font("fonts/hd/ui.fnt", .48f);
    BitmapFont small = font("fonts/hd/small.fnt", .48f);
    skin.add("ui-font", ui);
    skin.add("small-font", small);
    skin.add("dock-bg", patch("dock-bg", 11, 30, 34, 45, 242, 0), Drawable.class);
    skin.add("wallpaper-dim", patch("wallpaper-dim", 0, 12, 17, 25, 137, 0), Drawable.class);
    skin.add("shortcut-idle", patch("shortcut-idle", 7, 36, 41, 51, 0, 0), Drawable.class);
    skin.add("shortcut-hover", patch("shortcut-hover", 7, 55, 63, 75, 145, 0), Drawable.class);
    skin.add("dock-idle", patch("dock-idle", 5, 36, 41, 51, 0, 0), Drawable.class);
    skin.add("dock-hover", patch("dock-hover", 5, 65, 77, 91, 180, 0), Drawable.class);
    skin.add("top-bg", patch("top-bg", 0, 25, 28, 36, 247, 0), Drawable.class);
    skin.add("hover-bg", patch("hover-bg", 12, 20, 49, 100, 86, 154), Drawable.class);
    skin.add("button-bg", patch("button-bg", 13, 20, 43, 105, 90, 159), Drawable.class);
    skin.add("launcher-bg", patch("launcher-bg", 9, 26, 30, 39, 252, 0), Drawable.class);
    skin.add("window-bg", patch("window-bg", 7, 30, 33, 40, 253, 0), Drawable.class);
    skin.add("pane-bg", patch("pane-bg", 11, 10, 18, 35, 246, 0), Drawable.class);
    skin.add("input-bg", patch("input-bg", 8, 20, 32, 53, 245, 0), Drawable.class);
    skin.add("window-header", patch("window-header", 4, 41, 45, 55, 255, 0), Drawable.class);
    skin.add("title-button", patch("title-button", 7, 32, 40, 60, 176, 0), Drawable.class);
    skin.add("title-button-hover", patch("title-button-hover", 7, 61, 71, 105, 235, 0), Drawable.class);
    skin.add("title-close-hover", patch("title-close-hover", 7, 123, 52, 74, 248, 0), Drawable.class);
    // OneDark / Archcraft-inspired terminal palette. Keep separate from the
    // desktop and other applications until this single design is approved.
    skin.add("term-frame", patch("term-frame", 12, 46, 50, 60, 255, 0), Drawable.class);
    skin.add("term-header", patch("term-header", 7, 41, 45, 55, 255, 0), Drawable.class);
    skin.add("term-surface", patch("term-surface", 8, 30, 33, 40, 255, 0), Drawable.class);
    skin.add("term-toolbar", patch("term-toolbar", 5, 35, 39, 48, 255, 0), Drawable.class);
    skin.add("term-tab", patch("term-tab", 5, 47, 52, 62, 255, 0), Drawable.class);
    skin.add("term-prompt", patch("term-prompt", 6, 41, 45, 55, 255, 0), Drawable.class);
    skin.add("term-title-button", patch("term-title-button", 5, 41, 45, 55, 180, 0), Drawable.class);
    skin.add("term-title-hover", patch("term-title-hover", 5, 72, 81, 97, 255, 0), Drawable.class);
    skin.add("term-title-close", patch("term-title-close", 5, 154, 59, 68, 255, 0), Drawable.class);

    addIcon("code", 93, 72, 188);
    addIcon("terminal", 60, 69, 111);
    addIcon("files", 193, 139, 87);
    addIcon("mail", 137, 94, 196);
    addIcon("settings", 91, 96, 139);

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

  private NinePatchDrawable patch(String id, int r, int red, int green, int blue,
                                  int alpha, int unused) {
    // NinePatch texture created at 3x target radius to keep silhouettes smooth.
    int size = 96, border = 32, radius = Math.min(38, r * 2);
    Pixmap pix = new Pixmap(size, size, Pixmap.Format.RGBA8888);
    for (int y = 0; y < size; y++) {
      float ay = Math.abs(y + .5f - size / 2f) - (size / 2f - radius);
      for (int x = 0; x < size; x++) {
        float ax = Math.abs(x + .5f - size / 2f) - (size / 2f - radius);
        float distance = (float)Math.hypot(Math.max(ax, 0), Math.max(ay, 0))
            + Math.min(0, Math.max(ax, ay)) - radius;
        float a = Math.max(0, Math.min(1, .5f - distance)) * alpha / 255f;
        pix.drawPixel(x, y, (red << 24) | (green << 16) | (blue << 8) | Math.round(a * 255));
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
    // Archcraft uses understated monochrome symbols rather than oversized
    // glossy application cards. Leave the icon texture transparent.
    pix.setColor(0xf5f0ffff);
    // Crisp, recognizable native icons; no emoji font fallbacks.
    switch(name) {
      case "code" -> {
        stroke(pix,41,47,29,63,4); stroke(pix,29,63,41,79,4);
        stroke(pix,83,47,95,63,4); stroke(pix,95,63,83,79,4);
        stroke(pix,71,44,54,83,4);
      }
      case "terminal" -> {
        stroke(pix,30,47,49,62,4);stroke(pix,49,62,30,77,4);
        pix.fillRectangle(57,77,33,4);
      }
      case "files" -> {
        stroke(pix,29,53,29,90,5);
        stroke(pix,29,90,99,90,5);
        stroke(pix,99,90,99,53,5);
        stroke(pix,99,53,63,53,5);
        stroke(pix,63,53,55,44,5);
        stroke(pix,55,44,29,44,5);
        stroke(pix,29,44,29,53,5);
      }
      case "mail" -> {
        stroke(pix,28,47,99,47,4); stroke(pix,28,47,63,75,4);
        stroke(pix,99,47,63,75,4); stroke(pix,28,47,28,86,4);
        stroke(pix,99,47,99,86,4); stroke(pix,28,86,99,86,4);
      }
      case "settings" -> {
        for (int rad=21; rad<=25; rad++) pix.drawCircle(64,64,rad);
        for (int rad=7; rad<=9; rad++) pix.drawCircle(64,64,rad);
        for(int i=0;i<8;i++){
          double angle=i*Math.PI/4;
          int ax=64+(int)(24*Math.cos(angle)), ay=64+(int)(24*Math.sin(angle));
          int bx=64+(int)(33*Math.cos(angle)), by=64+(int)(33*Math.sin(angle));
          stroke(pix,ax,ay,bx,by,5);
        }
      }
    }
    Texture t = new Texture(pix);
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    pix.dispose();
    skin.add("icon-" + name, t);
  }

  private static void stroke(Pixmap p, int x, int y, int ex, int ey, int size) {
    for (int dx = -size/2; dx <= size/2; dx++)
      for (int dy = -size/2; dy <= size/2; dy++)
        p.drawLine(x+dx, y+dy, ex+dx, ey+dy);
  }

  private Label label(String text, boolean secondary) {
    Label.LabelStyle style = new Label.LabelStyle(
        skin.getFont(secondary ? "small-font" : "ui-font"),
        Color.valueOf(secondary ? "c6bfd8" : "f5efff"));
    Label l = new Label(text, style);
    l.setAlignment(Align.center);
    return l;
  }

  private Image icon(String name) {
    Image img = new Image(new TextureRegionDrawable(new TextureRegion(skin.get("icon-" + name, Texture.class))));
    img.setScaling(Scaling.fit);
    return img;
  }

  private Button button(Runnable action) {
    Button.ButtonStyle style = new Button.ButtonStyle();
    style.up = skin.getDrawable("button-bg");
    style.over = skin.getDrawable("hover-bg");
    style.down = skin.getDrawable("hover-bg");
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

  private Button shortcut(String icon, String title, App app) {
    Button.ButtonStyle style = new Button.ButtonStyle();
    style.up = skin.getDrawable("shortcut-idle");
    style.over = skin.getDrawable("shortcut-hover");
    style.down = skin.getDrawable("shortcut-hover");
    Button b = new Button(style);
    b.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent e, Actor actor) { shell.openApp(app); }
    });
    b.pad(3);
    b.add(this.icon(icon)).size(35,35).center();
    b.row();
    b.add(label(title, true)).minWidth(90).padTop(1).center();
    return b;
  }

  private Button dockButton(String icon, App app) {
    Button.ButtonStyle style = new Button.ButtonStyle();
    style.up = skin.getDrawable("dock-idle");
    style.over = skin.getDrawable("dock-hover");
    style.down = skin.getDrawable("dock-hover");
    Button b = new Button(style);
    b.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent e, Actor actor) { shell.openApp(app); }
    });
    b.pad(4);
    b.add(this.icon(icon)).size(27,27);
    b.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      @Override public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent e,
                                  float x, float y, int pointer, Actor fromActor) {
        b.clearActions();
        b.addAction(Actions.scaleTo(1.07f, 1.07f, .13f));
      }
      @Override public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent e,
                                 float x, float y, int pointer, Actor toActor) {
        b.clearActions();
        b.addAction(Actions.scaleTo(1,1,.15f));
      }
    });
    b.setTransform(true);
    return b;
  }

  private void build() {
    // Archcraft/Openbox-inspired Polybar: compact 30 px, workspace indicators,
    // calm secondary text and a real application launcher on the left.
    topBar = new Table();
    topBar.setBackground(skin.getDrawable("top-bg"));
    topBar.left().padLeft(20).padRight(20);
    Label menuIcon = label("A", false);
    menuIcon.setColor(Color.valueOf("5294E2"));
    topBar.add(menuIcon).width(20).left();
    Label distro = label("pinux", true);
    distro.setColor(Color.valueOf("DBE1E9"));
    topBar.add(distro).padLeft(5).padRight(26).left();
    Label workspaces = label("01    02    03", true);
    workspaces.setColor(Color.valueOf("8492A3"));
    topBar.add(workspaces).padRight(16);
    topBar.add().expandX();
    current = label("Рабочий стол", true);
    current.setColor(Color.valueOf("929BAB"));
    topBar.add(current).padRight(23);
    Label status = label("●", true);
    status.setColor(Color.valueOf("98C379"));
    topBar.add(status).padRight(10);
    Label session = label("ASTRA", true);
    session.setColor(Color.valueOf("8191A4"));
    topBar.add(session).padRight(15);
    clock = label("20:47", true);
    clock.setColor(Color.valueOf("D5DBE4"));
    topBar.add(clock);
    stage.addActor(topBar);

    // Desktop shortcuts remain usable but do not look like mobile app cards.
    shortcuts = new Table();
    shortcuts.top().left();
    shortcuts.add(shortcut("code", "Astra Code", App.CODE)).size(94,79).padBottom(2);
    shortcuts.row();
    shortcuts.add(shortcut("terminal", "Terminal", App.TERMINAL)).size(94,79).padBottom(2);
    shortcuts.row();
    shortcuts.add(shortcut("files", "Files", App.FILES)).size(94,79).padBottom(2);
    shortcuts.row();
    shortcuts.add(shortcut("mail", "TaskMail", App.MAIL)).size(94,79);
    stage.addActor(shortcuts);

    // Small centered dock: icons have no coloured square behind them.
    dock = new Table();
    dock.setBackground(skin.getDrawable("dock-bg"));
    dock.pad(6, 10, 6, 10);
    dock.defaults().padLeft(5).padRight(5);
    dock.add(dockButton("code", App.CODE)).size(40);
    dock.add(dockButton("terminal", App.TERMINAL)).size(40);
    dock.add(dockButton("files", App.FILES)).size(40);
    dock.add(dockButton("mail", App.MAIL)).size(40);
    dock.add(dockButton("settings", App.SETTINGS)).size(40);
    stage.addActor(dock);

    launcher = new Table();
    launcher.top().left();
    launcher.setBackground(skin.getDrawable("launcher-bg"));
    launcher.pad(16);
    Label heading = label("ПРИЛОЖЕНИЯ", true);
    heading.setColor(Color.valueOf("9FAAB8"));
    launcher.add(heading).width(255).height(28).padBottom(11).left();
    launcher.row();
    for (var entry : new Object[][] {
        {"code", "Astra Code", App.CODE},
        {"terminal", "Terminal", App.TERMINAL},
        {"files", "Files", App.FILES},
        {"mail", "TaskMail", App.MAIL},
        {"settings", "Settings", App.SETTINGS}
    }) {
      String ico = (String)entry[0], title = (String)entry[1];
      App app = (App)entry[2];
      Button b = button(() -> { launcher.setVisible(false); shell.openApp(app); });
      b.add(icon(ico)).size(22).pad(5);
      Label txt = label(title, true); txt.setAlignment(Align.left);
      b.add(txt).width(203).left().padLeft(8);
      launcher.add(b).width(267).height(40).padBottom(4);
      launcher.row();
    }
    launcher.setVisible(false);
    stage.addActor(launcher);
    topBar.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
        if (x <= 152) {
          boolean show = !launcher.isVisible();
          launcher.setVisible(show);
          launcher.toFront();
        }
      }
    });
  }

  public Stage inputStage() { return stage; }
  public boolean isVisible() { return visible; }

  public void openNativeWindow(App app) {
    windows.open(app);
    current.setText(switch (app) {
      case TERMINAL -> "Terminal";
      case FILES -> "Files";
      case MAIL -> "TaskMail";
      case SETTINGS -> "Settings";
      case CODE -> "Astra Code";
    });
    raiseChrome();
  }

  public void raiseChrome() {
    topBar.toFront();
    dock.toFront();
    launcher.toFront();
  }

  public void setDesktopVisible(boolean showDesktop) {
    wallpaper.setVisible(showDesktop);
    wallpaperDim.setVisible(showDesktop);
    shortcuts.setVisible(showDesktop);
  }

  public boolean hasNativeWindows() { return windows.hasOpen(); }

  public boolean isCodeFront() { return windows.isFrontCode(); }

  public void cycleNativeWindows() { windows.cycleFocus(); }

  public void closeFrontNativeWindow() { windows.closeFront(); }

  public void resetNativeWindows() { windows.reset(); }

  public void setVisible(boolean show) {
    visible = show;
    stage.getRoot().setVisible(show);
    if (!show && launcher != null) launcher.setVisible(false);
  }

  public void resize(int w, int h) {
    stage.getViewport().update(w, h, true);
    float width = stage.getWidth(), height = stage.getHeight();
    scale = Math.max(.67f, Math.min(1.35f, height / 810f));
    skin.getFont("ui-font").getData().setScale(.37f * scale);
    skin.getFont("small-font").getData().setScale(.39f * scale);
    wallpaper.setBounds(0, 0, width, height);
    wallpaperDim.setBounds(0, 0, width, height);
    topBar.setBounds(0, height - 30 * scale, width, 30 * scale);
    topBar.padLeft(20 * scale);
    topBar.padRight(20 * scale);
    shortcuts.setTransform(true);
    shortcuts.setSize(94, 334);
    shortcuts.setScale(scale);
    shortcuts.setPosition(22 * scale, Math.max(74 * scale, height - 385 * scale));
    dock.invalidateHierarchy();
    dock.setTransform(true);
    dock.setSize(275, 50);
    dock.setScale(scale);
    dock.setPosition((width - 275 * scale) / 2, 14 * scale);
    launcher.setTransform(true);
    launcher.setSize(302, 325);
    launcher.setScale(scale);
    launcher.setPosition(18 * scale, height - 30 * scale - 335 * scale);
    launcher.invalidateHierarchy();
    if (windows != null) windows.resize(w, h);
  }

  public void show() {
    if (windows != null) windows.reset();
    setVisible(true);
    stage.getRoot().setColor(1,1,1,0);
    stage.getRoot().addAction(Actions.fadeIn(.22f));
  }

  public void draw(float delta) {
    if (!visible) return;
    clock.setText(shell.clockText());
    windows.tick();
    stage.act(Math.min(delta, .05f));
    stage.draw();
  }

  public void dispose() {
    stage.dispose();
    skin.dispose();
  }
}
