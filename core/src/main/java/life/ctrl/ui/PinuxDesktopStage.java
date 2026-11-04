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
  private final Stage stage = new Stage(new ScreenViewport());
  private final Skin skin = new Skin();
  private Table topBar, dock, shortcuts, launcher;
  private Label clock, current;
  private Image wallpaper;
  private PinuxNativeWindows windows;
  private boolean visible;
  private float scale = 1f;

  public PinuxDesktopStage(PinuxAstra shell, Texture desktopTexture) {
    this.shell = shell;
    BitmapFont ui = font("fonts/hd/ui.fnt", .48f);
    BitmapFont small = font("fonts/hd/small.fnt", .48f);
    skin.add("ui-font", ui);
    skin.add("small-font", small);
    skin.add("dock-bg", patch("dock-bg", 28, 31, 49, 83, 226, 244), Drawable.class);
    skin.add("top-bg", patch("top-bg", 12, 16, 34, 80, 78, 113), Drawable.class);
    skin.add("hover-bg", patch("hover-bg", 12, 20, 49, 100, 86, 154), Drawable.class);
    skin.add("button-bg", patch("button-bg", 13, 20, 43, 105, 90, 159), Drawable.class);
    skin.add("launcher-bg", patch("launcher-bg", 25, 29, 52, 95, 86, 128), Drawable.class);
    skin.add("window-bg", patch("window-bg", 17, 14, 21, 39, 253, 0), Drawable.class);
    skin.add("pane-bg", patch("pane-bg", 11, 10, 18, 35, 246, 0), Drawable.class);
    skin.add("input-bg", patch("input-bg", 8, 20, 32, 53, 245, 0), Drawable.class);
    skin.add("window-header", patch("window-header", 10, 23, 30, 49, 255, 0), Drawable.class);
    skin.add("title-button", patch("title-button", 7, 32, 40, 60, 176, 0), Drawable.class);
    skin.add("title-button-hover", patch("title-button-hover", 7, 61, 71, 105, 235, 0), Drawable.class);
    skin.add("title-close-hover", patch("title-close-hover", 7, 123, 52, 74, 248, 0), Drawable.class);

    addIcon("code", 93, 72, 188);
    addIcon("terminal", 60, 69, 111);
    addIcon("files", 193, 139, 87);
    addIcon("mail", 137, 94, 196);
    addIcon("settings", 91, 96, 139);

    wallpaper = new Image(new TextureRegionDrawable(new TextureRegion(desktopTexture)));
    wallpaper.setScaling(Scaling.stretch);
    wallpaper.setTouchable(Touchable.disabled);
    stage.addActor(wallpaper);
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
    for (int y = 0; y < n; y++) {
      for (int x = 0; x < n; x++) {
        float xx = Math.abs(x + .5f - n/2f), yy = Math.abs(y + .5f - n/2f);
        float d = (float)Math.hypot(Math.max(xx - 41, 0), Math.max(yy - 41, 0))
            + Math.min(0, Math.max(xx-41, yy-41)) - 21;
        float a = Math.max(0, Math.min(1, .5f - d));
        int red = Math.min(255, r + (int)(30 * (1 - y/(float)n)));
        int gr = Math.min(255, g + (int)(25 * (1 - y/(float)n)));
        int bl = Math.min(255, b + (int)(18 * (1 - y/(float)n)));
        pix.drawPixel(x, y, (red<<24)|(gr<<16)|(bl<<8)|Math.round(242*a));
      }
    }
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
        pix.fillRectangle(28,50,72,47);
        pix.fillRectangle(28,42,38,12);
        pix.setColor(0xf0d5afff);
        pix.fillRectangle(29,55,70,6);
      }
      case "mail" -> {
        stroke(pix,28,47,99,47,4); stroke(pix,28,47,63,75,4);
        stroke(pix,99,47,63,75,4); stroke(pix,28,47,28,86,4);
        stroke(pix,99,47,99,86,4); stroke(pix,28,86,99,86,4);
      }
      case "settings" -> {
        pix.drawCircle(64,64,24); pix.drawCircle(64,64,22);
        pix.drawCircle(64,64,8);
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
    Button b = button(() -> shell.openApp(app));
    b.pad(9);
    b.add(this.icon(icon)).size(56,56).center();
    b.row();
    b.add(label(title, true)).minWidth(106).padTop(5).center();
    return b;
  }

  private Button dockButton(String icon, App app) {
    Button b = button(() -> shell.openApp(app));
    b.pad(5);
    b.add(this.icon(icon)).size(42,42);
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
    topBar = new Table();
    topBar.setBackground(skin.getDrawable("top-bg"));
    topBar.left().padLeft(21).padRight(23);
    Label logo = label("◆", false);
    logo.setColor(Color.valueOf("bca9ff"));
    topBar.add(logo).width(23);
    Label name = label("Pinux Astra", false);
    topBar.add(name).padLeft(9).left();
    topBar.add().expandX();
    current = label("Рабочий стол", true);
    topBar.add(current).padRight(25);
    Label status = label("●", true);
    status.setColor(Color.valueOf("9ee5bf"));
    topBar.add(status).padRight(8);
    clock = label("20:47", false);
    topBar.add(clock).padRight(8);
    stage.addActor(topBar);

    shortcuts = new Table();
    shortcuts.top().left();
    shortcuts.add(shortcut("code", "Astra Code", App.CODE)).size(120,113).padBottom(12);
    shortcuts.row();
    shortcuts.add(shortcut("terminal", "Терминал", App.TERMINAL)).size(120,113).padBottom(12);
    shortcuts.row();
    shortcuts.add(shortcut("files", "Файлы", App.FILES)).size(120,113).padBottom(12);
    shortcuts.row();
    shortcuts.add(shortcut("mail", "TaskMail", App.MAIL)).size(120,113);
    stage.addActor(shortcuts);

    dock = new Table();
    dock.setBackground(skin.getDrawable("dock-bg"));
    dock.pad(12,16,12,16);
    dock.defaults().padLeft(7).padRight(7);
    dock.add(dockButton("code", App.CODE)).size(56);
    dock.add(dockButton("terminal", App.TERMINAL)).size(56);
    dock.add(dockButton("files", App.FILES)).size(56);
    dock.add(dockButton("mail", App.MAIL)).size(56);
    dock.add(dockButton("settings", App.SETTINGS)).size(56);
    stage.addActor(dock);

    launcher = new Table();
    launcher.top().left();
    launcher.setBackground(skin.getDrawable("launcher-bg"));
    launcher.pad(20);
    Label heading = label("Приложения", false);
    heading.setAlignment(Align.left);
    launcher.add(heading).width(270).padBottom(16).left();
    launcher.row();
    for (var entry : new Object[][] {
        {"code", "Astra Code", App.CODE},
        {"terminal", "Терминал", App.TERMINAL},
        {"files", "Файлы", App.FILES},
        {"mail", "TaskMail", App.MAIL},
        {"settings", "Настройки", App.SETTINGS}
    }) {
      String ico = (String)entry[0], title = (String)entry[1];
      App app = (App)entry[2];
      Button b = button(() -> { launcher.setVisible(false); shell.openApp(app); });
      b.add(icon(ico)).size(38).pad(6);
      Label txt = label(title, false); txt.setAlignment(Align.left);
      b.add(txt).width(190).left().padLeft(9);
      launcher.add(b).width(278).height(52).padBottom(6);
      launcher.row();
    }
    launcher.setVisible(false);
    stage.addActor(launcher);

    topBar.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
        if (x <= 190) {
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
    raiseChrome();
  }

  public void raiseChrome() {
    topBar.toFront();
    dock.toFront();
    launcher.toFront();
  }

  public void setDesktopVisible(boolean showDesktop) {
    wallpaper.setVisible(showDesktop);
    shortcuts.setVisible(showDesktop);
  }

  public boolean hasNativeWindows() { return windows.hasOpen(); }

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
    skin.getFont("ui-font").getData().setScale(.48f * scale);
    skin.getFont("small-font").getData().setScale(.48f * scale);
    wallpaper.setBounds(0, 0, width, height);
    topBar.setBounds(0, height - 52 * scale, width, 52 * scale);
    topBar.padLeft(22 * scale);
    topBar.padRight(24 * scale);
    shortcuts.setTransform(true);
    shortcuts.setSize(125, 550);
    shortcuts.setScale(scale);
    shortcuts.setPosition(26 * scale, Math.max(95 * scale, height - 600 * scale));
    dock.invalidateHierarchy();
    dock.setTransform(true);
    dock.setSize(415, 80);
    dock.setScale(scale);
    dock.setPosition((width - 415 * scale) / 2, 17 * scale);
    launcher.setTransform(true);
    launcher.setSize(325, 444);
    launcher.setScale(scale);
    launcher.setPosition(16 * scale, height - 52 * scale - 450 * scale);
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
    stage.act(Math.min(delta, .05f));
    stage.draw();
  }

  public void dispose() {
    stage.dispose();
    skin.dispose();
  }
}
