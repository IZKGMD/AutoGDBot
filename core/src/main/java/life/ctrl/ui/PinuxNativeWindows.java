package life.ctrl.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisWindow;
import java.util.EnumMap;
import life.ctrl.ui.PinuxWindowManager.App;

/**
 * Native-resolution windows using actual VisUI windows (not manually painted fake
 * rectangles). Layout and resizing belong to Scene2D. App state is simulated and
 * can never touch the host computer's filesystem or execute host commands.
 *
 * The architecture follows familiar KDE-style desktop patterns: one window per
 * application, z-order on focus, a task switcher/dock and independent contents.
 */
final class PinuxNativeWindows {
  private static final Color PRIMARY = Color.valueOf("e8eaf5");
  private static final Color MUTED = Color.valueOf("a9b5cd");
  private static final Color ACCENT = Color.valueOf("b8acff");
  // Archcraft bspwm/OneDark chroma, adapted rather than copying Linux UI files.
  private static final Color TERM_FG = Color.valueOf("ABB2BF");
  private static final Color TERM_MUTED = Color.valueOf("7F8998");
  private static final Color TERM_BLUE = Color.valueOf("5294E2");
  private static final Color TERM_GREEN = Color.valueOf("98C379");

  private final Stage stage;
  private final Skin skin;
  private final PinuxAstra shell;
  private final EnumMap<App, VisWindow> windows = new EnumMap<>(App.class);
  private final EnumMap<App, Boolean> maximized = new EnumMap<>(App.class);
  private final EnumMap<App, float[]> restored = new EnumMap<>(App.class);
  private final Window.WindowStyle windowStyle;
  private final Window.WindowStyle terminalWindowStyle;
  private VisTextField terminalInput;
  private AstraCodeApp codeApp;
  private Label terminalOutput;
  private float scale = 1;
  private int sequence;

  PinuxNativeWindows(Stage stage, Skin skin, PinuxAstra shell) {
    this.stage = stage;
    this.skin = skin;
    this.shell = shell;
    if (!VisUI.isLoaded()) VisUI.load();
    windowStyle = new Window.WindowStyle(
        skin.getFont("ui-font"), PRIMARY, skin.getDrawable("window-bg"));
    terminalWindowStyle = new Window.WindowStyle(
        skin.getFont("ui-font"), TERM_FG, skin.getDrawable("term-frame"));
  }

  private Label text(String value, boolean secondary) {
    Label result = new Label(value, new Label.LabelStyle(
        skin.getFont(secondary ? "small-font" : "ui-font"), secondary ? MUTED : PRIMARY));
    result.setAlignment(Align.left);
    return result;
  }

  private void heading(Table table, String overline, String title, String subtitle) {
    Label over = text(overline, true);
    over.setColor(ACCENT);
    table.add(over).left().padBottom(9);
    table.row();
    table.add(text(title, false)).left().padBottom(5);
    table.row();
    table.add(text(subtitle, true)).left().padBottom(22);
    table.row();
  }

  private TextButton action(String text, Runnable run) {
    TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
    style.font = skin.getFont("small-font");
    style.fontColor = PRIMARY;
    style.overFontColor = Color.WHITE;
    style.up = skin.getDrawable("button-bg");
    style.over = skin.getDrawable("hover-bg");
    style.down = skin.getDrawable("hover-bg");
    TextButton button = new TextButton(text, style);
    button.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) { run.run(); }
    });
    return button;
  }

  private TextButton chrome(String glyph, Runnable run, boolean danger, boolean terminal) {
    TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
    style.font = skin.getFont("small-font");
    style.fontColor = terminal ? TERM_MUTED
        : danger ? Color.valueOf("e6bac4") : Color.valueOf("acb9ce");
    style.overFontColor = Color.WHITE;
    style.up = skin.getDrawable(terminal ? "term-title-button" : "title-button");
    style.over = skin.getDrawable(terminal
        ? danger ? "term-title-close" : "term-title-hover"
        : danger ? "title-close-hover" : "title-button-hover");
    style.down = style.over;
    TextButton b = new TextButton(glyph, style);
    b.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) { run.run(); }
    });
    return b;
  }

  private VisWindow make(App app, String title, int width, int height, int x, int y) {
    boolean terminal = app == App.TERMINAL;
    VisWindow window = new VisWindow(title, terminal ? terminalWindowStyle : windowStyle);
    window.setMovable(true);
    window.setResizable(true);
    window.setResizeBorder(12);
    window.setKeepWithinStage(true);
    window.padTop(terminal ? 41 : 44);
    window.padLeft(terminal ? 10 : 19);
    window.padRight(terminal ? 10 : 19);
    window.padBottom(terminal ? 10 : 18);
    window.getTitleLabel().setColor(terminal ? TERM_FG : PRIMARY);
    window.getTitleLabel().setAlignment(Align.left);
    window.getTitleTable().setBackground(skin.getDrawable(
        terminal ? "term-header" : "window-header"));
    window.getTitleTable().add().expandX();
    window.getTitleTable().add(chrome("—", () -> minimize(app), false, terminal))
        .width(terminal ? 30 : 33).height(25).padRight(5);
    window.getTitleTable().add(chrome("[]", () -> toggleMaximize(app), false, terminal))
        .width(terminal ? 30 : 33).height(25).padRight(5);
    window.getTitleTable().add(chrome("×", () -> close(app), true, terminal))
        .width(terminal ? 30 : 33).height(25).padRight(5);
    window.setBounds(x, y, width, height);
    // Explicit resize capture fixes a libGDX/VisWindow corner hit-test conflict
    // when the content Table fills the body. The rest of the window stays native.
    window.addCaptureListener(new InputListener() {
      float startStageX, startStageY, initialX, initialY, initialW, initialH;
      boolean resizing;

      @Override public boolean touchDown(InputEvent e, float localX, float localY,
                                         int pointer, int button) {
        if (button != Input.Buttons.LEFT || localX < window.getWidth() - 24 || localY > 24)
          return false;
        resizing = true;
        startStageX = e.getStageX();
        startStageY = e.getStageY();
        initialX = window.getX();
        initialY = window.getY();
        initialW = window.getWidth();
        initialH = window.getHeight();
        e.stop();
        return true;
      }

      @Override public void touchDragged(InputEvent e, float localX, float localY, int pointer) {
        if (!resizing) return;
        float dx = e.getStageX() - startStageX;
        float dy = e.getStageY() - startStageY;
        float newW = Math.max(app == App.CODE ? 800 : 460,
            Math.min(stage.getWidth() - initialX - 10, initialW + dx));
        float desiredY = Math.max(95, initialY + dy);
        float newH = Math.max(app == App.CODE ? 470 : 300,
            initialH + initialY - desiredY);
        window.setBounds(initialX, desiredY, newW, newH);
        window.invalidateHierarchy();
      }

      @Override public void touchUp(InputEvent e, float localX, float localY,
                                    int pointer, int button) {
        resizing = false;
      }
    });
    windows.put(app, window);
    maximized.put(app, false);
    return window;
  }

  private VisWindow create(App app) {
    int offset = sequence++ % 4;
    int x = 195 + offset * 30, y = 122 + offset * 14;
    switch (app) {
      case CODE -> {
        // All gameplay panels are native Scene2D actors; no PixelCanvas fallback.
        int width = Math.max(800, Math.min(1180, (int)stage.getWidth() - 80));
        int height = Math.max(470, Math.min(720, (int)stage.getHeight() - 112));
        VisWindow w = make(app, "Astra Code", width, height, 50,
            Math.max(45, ((int)stage.getHeight() - height) / 2));
        codeApp = new AstraCodeApp(shell.game(), shell.life(), skin);
        w.add(codeApp.view()).grow().minWidth(0).minHeight(0);
        return w;
      }
      case TERMINAL -> {
        // Archcraft/OneDark: a quiet, flat kitty-style console with a real
        // Scene2D text field. Only this application receives the new skin.
        VisWindow w = make(app, "Terminal", 838, 537, 195, 130);
        Table surface = new Table();
        surface.top().left();
        surface.setBackground(skin.getDrawable("term-surface"));

        // A single quiet tab strip, as in Archcraft's terminal layouts.
        Table toolbar = new Table();
        toolbar.left().padLeft(15).padRight(18);
        toolbar.setBackground(skin.getDrawable("term-toolbar"));
        Table shellTab = new Table();
        shellTab.setBackground(skin.getDrawable("term-tab"));
        Label dot = text("●", true);
        dot.setColor(TERM_GREEN);
        shellTab.add(dot).padLeft(14).padRight(9);
        Label activeSession = text("shell  /  dev@astra", true);
        activeSession.setColor(TERM_FG);
        shellTab.add(activeSession).padRight(15);
        toolbar.add(shellTab).height(30).left();
        toolbar.add().expandX();
        Label workingDirectory = text("~ / projects", true);
        workingDirectory.setColor(TERM_MUTED);
        toolbar.add(workingDirectory).padRight(14);
        Label indicator = text("●", true);
        indicator.setColor(TERM_GREEN);
        toolbar.add(indicator);
        surface.add(toolbar).height(44).growX();
        surface.row();

        terminalOutput = text(shell.terminalHistory(), false);
        terminalOutput.setStyle(new Label.LabelStyle(skin.getFont("small-font"), TERM_FG));
        terminalOutput.setWrap(true);
        terminalOutput.setAlignment(Align.topLeft);
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        ScrollPane scroll = new ScrollPane(terminalOutput, scrollStyle);
        scroll.setFadeScrollBars(true);
        scroll.setOverscroll(false, false);
        scroll.setScrollingDisabled(true, false);
        surface.add(scroll).grow().padLeft(26).padRight(23).padTop(12).padBottom(16);
        surface.row();

        Table prompt = new Table();
        prompt.left().padLeft(18).padRight(11);
        prompt.setBackground(skin.getDrawable("term-prompt"));
        Label user = text("dev", true);
        user.setColor(TERM_GREEN);
        prompt.add(user);
        Label atHost = text("@astra", true);
        atHost.setColor(TERM_BLUE);
        prompt.add(atHost);
        Label directory = text("  ~  >", true);
        directory.setColor(TERM_FG);
        prompt.add(directory).padRight(13);

        VisTextField.VisTextFieldStyle inputStyle = new VisTextField.VisTextFieldStyle();
        inputStyle.font = skin.getFont("small-font");
        inputStyle.fontColor = TERM_FG;
        inputStyle.messageFontColor = TERM_MUTED;
        inputStyle.cursor = skin.newDrawable("term-title-button", TERM_BLUE);
        inputStyle.selection = skin.newDrawable("term-title-hover", TERM_BLUE);
        terminalInput = new VisTextField("", inputStyle);
        terminalInput.setMessageText("Type a command...");
        terminalInput.setEnterKeyFocusTraversal(false);
        terminalInput.setTextFieldListener((field, ch) -> {
          if (ch == '\r' || ch == '\n') {
            terminalOutput.setText(shell.terminalSubmit(field.getText()));
            field.setText("");
            scroll.layout();
            scroll.setScrollPercentY(1);
          }
        });
        prompt.add(terminalInput).growX().height(37).padRight(8);
        surface.add(prompt).height(48).growX().padLeft(22).padRight(22).padBottom(20);
        w.add(surface).grow();
        return w;
      }
      case FILES -> {
        VisWindow w = make(app, "Files", 705, 505, 668, 142);
        Table content = new Table();
        content.setBackground(skin.getDrawable("pane-bg"));
        content.top().left().pad(21);
        heading(content, "FILES   /   HOME", "Your workspace", "/home/dev/projects");
        for (String entry : new String[] {"▸   Projects", "▸   src", "◇   main.bs", "▤   README.md", "▸   .git"}) {
          content.add(text(entry, false)).height(44).left().growX();
          content.row();
        }
        content.add(action("OPEN IN ASTRA CODE", () -> shell.openApp(App.CODE)))
            .height(44).width(260).left().padTop(12);
        w.add(content).grow();
        return w;
      }
      case MAIL -> {
        VisWindow w = make(app, "TaskMail", 735, 518, 420, 142);
        Table inbox = new Table();
        inbox.setBackground(skin.getDrawable("pane-bg"));
        inbox.top().left().pad(22);
        heading(inbox, "INBOX   /   AVAILABLE CONTRACTS", "Find your next project", "Client requests and freelance jobs");
        String[] names = shell.offerSummaries();
        for (int i = 0; i < Math.min(4, names.length); i++) {
          inbox.add(text("●   " + names[i], false)).left().height(42).growX();
          inbox.row();
        }
        inbox.add(action("VIEW ALL CONTRACTS", shell::openJobBoard))
            .width(270).height(46).left().padTop(14);
        w.add(inbox).grow();
        return w;
      }
      case SETTINGS -> {
        VisWindow w = make(app, "Settings", 635, 455, 594, 160);
        Table pane = new Table();
        pane.setBackground(skin.getDrawable("pane-bg"));
        pane.top().left().pad(23);
        heading(pane, "SYSTEM   /   PERSONALIZATION", "Make it yours", "Display and accessibility");
        pane.add(text("Appearance", false)).left().padBottom(14);
        pane.row();
        pane.add(action("SWITCH DESKTOP THEME", shell::toggleTheme))
            .left().width(280).height(45).padBottom(24);
        pane.row();
        pane.add(text("Motion", false)).left().padBottom(14);
        pane.row();
        pane.add(action("TOGGLE ANIMATIONS", shell::toggleMotion))
            .left().width(280).height(45);
        w.add(pane).grow();
        return w;
      }
      default -> throw new IllegalArgumentException("Native window not implemented for " + app);
    }
  }

  void open(App app) {
    VisWindow w = windows.get(app);
    if (w == null) w = create(app);
    if (w.getStage() == null) stage.addActor(w);
    w.setVisible(true);
    if (app == App.CODE && codeApp != null) codeApp.invalidate();
    if (app != App.TERMINAL) stage.setKeyboardFocus(null);
    w.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
    w.toFront();
    w.clearActions();
    w.getColor().a = 0f;
    w.addAction(Actions.fadeIn(.16f));
    if (app == App.TERMINAL && terminalInput != null) {
      terminalOutput.setText(shell.terminalHistory());
      stage.setKeyboardFocus(terminalInput);
    }
  }

  void close(App app) {
    VisWindow w = windows.get(app);
    if (w == null) return;
    stage.unfocus(w);
    w.clearActions();
    w.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    w.addAction(Actions.sequence(Actions.fadeOut(.12f), Actions.visible(false)));
  }

  void minimize(App app) {
    VisWindow w = windows.get(app);
    if (w == null) return;
    w.setVisible(false);
    stage.unfocus(w);
  }

  void closeFront() {
    Actor top = front();
    for (var entry : windows.entrySet())
      if (entry.getValue() == top) { close(entry.getKey()); return; }
  }

  Actor front() {
    Actor result = null;
    int index = -1;
    for (var e : windows.entrySet()) {
      VisWindow w = e.getValue();
      if (w.getStage() == null || !w.isVisible()) continue;
      int i = stage.getRoot().getChildren().indexOf(w, true);
      if (i > index) { index = i; result = w; }
    }
    return result;
  }

  boolean hasOpen() { return front() != null; }

  /** Actual frontmost native app, including mouse-driven focus changes. */
  App frontApp() {
    Actor top = front();
    if (top == null) return null;
    for (var entry : windows.entrySet()) {
      if (entry.getValue() == top) return entry.getKey();
    }
    return null;
  }

  boolean isFrontCode() { return frontApp() == App.CODE; }

  void tick() { if (codeApp != null && isVisible(App.CODE)) codeApp.tick(); }

  void cycleFocus() {
    Actor current = front();
    VisWindow candidate = null;
    int lowest = Integer.MAX_VALUE;
    for (VisWindow w : windows.values()) {
      if (w.getStage() != null && w.isVisible() && w != current) {
        int index = stage.getRoot().getChildren().indexOf(w, true);
        if (index < lowest) { lowest = index; candidate = w; }
      }
    }
    if (candidate != null) candidate.toFront();
  }

  boolean isVisible(App app) {
    VisWindow w = windows.get(app);
    return w != null && w.isVisible();
  }

  void toggleMaximize(App app) {
    VisWindow w = windows.get(app);
    if (w == null) return;
    boolean isMax = maximized.getOrDefault(app, false);
    if (!isMax) {
      restored.put(app, new float[] {w.getX(), w.getY(), w.getWidth(), w.getHeight()});
      float h = stage.getHeight(), width = stage.getWidth();
      w.setBounds(24, 102, width - 48, h - 174);
    } else {
      float[] p = restored.get(app);
      if (p != null) w.setBounds(p[0], p[1], p[2], p[3]);
    }
    maximized.put(app, !isMax);
    w.invalidateHierarchy();
  }

  void resize(int width, int height) {
    for (VisWindow w : windows.values()) {
      w.setPosition(
          Math.max(2, Math.min(w.getX(), width - w.getWidth() - 8)),
          Math.max(95, Math.min(w.getY(), height - w.getHeight() - 52)));
    }
  }

  void reset() {
    for (App app : windows.keySet()) {
      VisWindow w = windows.get(app);
      w.clearActions();
      w.setVisible(false);
      w.setColor(1, 1, 1, 1);
    }
  }
}
