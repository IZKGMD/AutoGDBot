package life.ctrl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.model.Career;
import life.ctrl.model.Coding;
import life.ctrl.model.GameState;
import life.ctrl.render.PixelCanvas;
import life.ctrl.ui.PinuxWindowManager.App;
import life.ctrl.ui.PinuxWindowManager.Window;

/**
 * Pinux Astra: a fictional, isolated GUI shell entirely rendered inside libGDX.
 * It cannot read files or execute commands on the host machine.
 */
public final class PinuxAstra {
  private final CtrlLife game;
  private final LifeScreens life;
  private final Workbench workbench;
  private final PixelCanvas c;
  private final PinuxWindowManager wm = new PinuxWindowManager();
  private final PinuxWallpaper wallpaper = new PinuxWallpaper();
  private final PinuxDesktopStage nativeDesktop;
  private final ArrayDeque<String> output = new ArrayDeque<>();
  private final ArrayList<String> commandHistory = new ArrayList<>();
  private int historyIndex;
  private String command = "";
  private float boot, time, noticeTime;
  private String notice = "";
  private boolean menuOpen, warmTheme, quietMotion;
  private static final String TEXT = "e5edf3", SUB = "9cb1bf";
  private static final String SKY = "8bcfe9", GREEN = "a8dfb5";
  private static final String GOLD = "f0cb8a", DARK = "08121e";

  public PinuxAstra(CtrlLife game, LifeScreens life, Workbench workbench, PixelCanvas canvas) {
    this.game = game;
    this.life = life;
    this.workbench = workbench;
    c = canvas;
    nativeDesktop = new PinuxDesktopStage(this, wallpaper.texture());
    log("Pinux Astra / shell 0.5 / x86_64");
    log("Type 'help' to see available commands.");
  }

  private GameState s() { return game.world().state; }
  private String accent() { return warmTheme ? GOLD : SKY; }

  public void open() {
    wm.reset();
    menuOpen = false;
    boot = 0;
    notice = "";
    noticeTime = 0;
    nativeDesktop.show();
  }

  public com.badlogic.gdx.scenes.scene2d.Stage inputStage() { return nativeDesktop.inputStage(); }

  public String clockText() { return s().clock(); }
  public boolean isLegacyEditorOpen() { return wm.isFront(App.CODE); }

  public void openApp(App app) { launch(app); }

  public void openLegacyEditor() {
    wm.open(App.CODE);
    if (!wm.get(App.CODE).maximized) wm.toggleMaximize(App.CODE);
    nativeDesktop.raiseChrome();
    game.resetSelection();
  }

  public String terminalHistory() { return String.join("\n", output); }

  public String terminalSubmit(String line) {
    command = line;
    execute();
    return terminalHistory();
  }

  public String[] offerSummaries() {
    return Career.offers(s(), life.data).stream()
        .map(j -> j.title + "  /  " + j.pay + " ₽")
        .toArray(String[]::new);
  }

  public void openJobBoard() {
    life.codePage = 0;
    openLegacyEditor();
  }

  public void toggleTheme() { warmTheme = !warmTheme; }
  public void toggleMotion() { quietMotion = !quietMotion; }

  public void resizeNative(int w, int h) { nativeDesktop.resize(w, h); }

  public void renderNativeDesktop(float delta) {
    nativeDesktop.setDesktopVisible(wm.front() == null);
    nativeDesktop.draw(delta);
  }

  public void draw() {
    float delta = Math.min(.1f, Gdx.graphics.getDeltaTime());
    time += delta;
    boot += delta;
    wm.tick(quietMotion ? Math.max(delta, .2f) : delta);
    noticeTime = Math.max(0, noticeTime - delta);
    if (wm.front() == null) return; // Desktop and native windows are Scene2D actors.
    if (boot < .55f && !quietMotion) {
      bootSplash();
      return;
    }
    // Legacy ByteScript IDE is retained underneath the new high-resolution window layer.
    // All desktop chrome and new windows are drawn by Scene2D after this framebuffer.
    if (wm.isFront(App.CODE)) workbench.draw();
  }

  private void bootSplash() {
    c.rect(0, 0, 480, 270, "07101e");
    c.rect(176, 64, 128, 74, "18314b");
    c.rect(187, 76, 106, 2, accent());
    c.title("PINUX", 188, 86, TEXT);
    c.text("ASTRA", 194, 110, accent());
    c.small("Preparing desktop environment...", 130, 165, SUB);
    c.rect(132, 189, 216, 3, "2d465a");
    c.rect(132, 189, 216 * Math.min(1f, boot / .55f), 3, accent());
  }

  /** Desktop artwork is authored independently from the window and application UI. */
  private void desktopWallpaper() {
    wallpaper.draw(c.batch, warmTheme);
    if (wm.openCount() == 0) {
      desktopIcon("<>", "Astra Code", 18, 32, App.CODE, "6555b6");
      desktopIcon(">_", "Terminal", 18, 81, App.TERMINAL, "354364");
      desktopIcon("[]", "Files", 18, 130, App.FILES, "a37c54");
      desktopIcon("@", "TaskMail", 18, 179, App.MAIL, "675188");
    }
  }

  public void dispose() {
    nativeDesktop.dispose();
    wallpaper.dispose();
  }

  private boolean hovered(int x, int y, int w, int h) {
    return c.mouseX() >= x && c.mouseX() < x + w
        && c.mouseY() >= y && c.mouseY() < y + h;
  }

  private void hotspot(String label, int x, int y, int w, int h, Runnable action) {
    game.uiHotspot(label, x, y, w, h, action);
  }

  /** Desktop shortcuts are composed, not rendered as generic game-menu buttons. */
  private void desktopIcon(String symbol, String label, int x, int y, App app, String color) {
    boolean hover = hovered(x, y, 75, 49);
    if (hover) c.roundRect(x, y, 75, 49, 6, "20213c91");
    // One soft icon surface; the old thick dark outline is deliberately gone.
    c.roundRect(x + 22, y + (hover ? 2 : 3), 29, 27, 7, "18172d55");
    c.roundRect(x + 23, y + (hover ? 2 : 3), 27, 26, 7, color);
    c.small(symbol, x + 35 - c.smallWidth(symbol) / 2f,
        y + (hover ? 8 : 9), "f4efff");
    float textX = x + (75 - c.smallWidth(label)) / 2f;
    c.small(label, textX, y + 35, "f2ebfa");
    hotspot(label, x, y, 75, 49, () -> launch(app));
  }

  private void paintWindow(Window w, boolean active) {
    if (w.app == App.CODE) {
      if (wm.isMaximizedCode()) {
        workbench.draw(); // Existing career/ByteScript actions remain authoritative.
      } else codePreview(w, active);
      return;
    }
    switch (w.app) {
      case TERMINAL -> terminal(w);
      case FILES -> files(w, active);
      case MAIL -> mail(w, active);
      case SETTINGS -> settings(w, active);
      default -> {}
    }
  }

  private void codePreview(Window w, boolean active) {
    int x = w.x + 9, y = w.y + 24, width = w.width - 20;
    c.small("ASTRA CODE  /  WORKSPACE", x, y, accent());
    c.rect(x, y + 15, width, 1, "38576b");
    c.small("EXPLORER       src/  main.bs", x + 4, y + 26, SUB);
    String source = s().activeJob.isEmpty()
        ? "// Open TaskMail or expand to start a job"
        : s().codeSource.replace('\n', ' ');
    c.fitted(source, x + 4, y + 44, width - 8, GREEN, true);
    c.small(s().activeJob.isEmpty() ? "No active contract" : "Project: " + s().activeJob,
        x + 4, y + 63, GOLD);
    if (active) {
      button("EXPAND IDE", x + 4, w.y + w.height - 27, 112, 18,
          () -> wm.toggleMaximize(App.CODE));
      button("TASKS", x + 121, w.y + w.height - 27, 77, 18, () -> {
        life.codePage = 0;
        wm.toggleMaximize(App.CODE);
      });
    }
  }

  private void files(Window w, boolean active) {
    int x = w.x + 12, y = w.y + 28;
    c.rect(x, y, w.width - 24, 21, "1c3549");
    c.fitted("‹   /home/dev/projects", x + 8, y + 4, w.width - 43, TEXT, true);
    c.small("PLACES", x + 2, y + 33, accent());
    c.small("⌂  Home", x + 6, y + 48, SUB);
    c.small("▣  Projects", x + 6, y + 66, SUB);
    c.rect(x + 104, y + 30, 1, w.height - 92, "345266");
    c.small("src/", x + 117, y + 35, TEXT);
    c.small("main.bs", x + 117, y + 54, GREEN);
    c.small("README.md", x + 117, y + 73, SUB);
    c.small(".git/", x + 117, y + 92, SUB);
    if (active)
      button("Open in Astra Code", x + 115, w.y + w.height - 26,
          Math.min(167, w.width - 135), 18, () -> launch(App.CODE));
  }

  private void mail(Window w, boolean active) {
    int x = w.x + 12, y = w.y + 26;
    c.small("INBOX  /  JOBS", x, y, accent());
    c.small("Client contracts  •  Day " + s().day(), x, y + 17, SUB);
    c.rect(x, y + 34, w.width - 24, 1, "34566b");
    var offers = Career.offers(s(), life.data);
    int take = Math.min(3, offers.size());
    for (int i = 0; i < take; i++) {
      var j = offers.get(i);
      int yy = y + 40 + i * 29;
      c.rect(x, yy, w.width - 24, 27, i % 2 == 0 ? "152b3d" : "132636");
      c.fitted("●  " + j.title, x + 8, yy + 4, w.width - 98, TEXT, true);
      c.small(j.pay + " ₽", w.right() - 79, yy + 5, GOLD);
    }
    if (active)
      button("Open job board", x, w.bottom() - 25, Math.min(172, w.width - 24), 18, () -> {
        life.codePage = 0;
        launch(App.CODE);
      });
  }

  private void settings(Window w, boolean active) {
    int x = w.x + 14, y = w.y + 30;
    c.small("PERSONALIZATION", x, y, accent());
    c.small("Desktop wallpaper", x, y + 24, TEXT);
    c.small(warmTheme ? "DUSK / AMBER" : "MIDNIGHT / BLUE", x, y + 42, SUB);
    c.small("Animation and motion", x, y + 70, TEXT);
    c.small(quietMotion ? "REDUCED" : "SMOOTH", x, y + 88, SUB);
    if (active) {
      button("Change theme", x, y + 114, 116, 17, () -> warmTheme = !warmTheme);
      button("Toggle motion", x + 123, y + 114, 123, 17, () -> quietMotion = !quietMotion);
    }
  }

  private void terminal(Window w) {
    int x = w.x + 9, y = w.y + 23;
    int width = w.width - 18, height = w.height - 26;
    c.rect(x, y, width, height, "071421");
    c.small("PINUX ASTRA  |  bash-like interactive shell", x + 6, y + 5, accent());
    c.rect(x + 5, y + 20, width - 10, 1, "294b62");
    int rows = Math.max(1, (height - 53) / 13);
    String[] lines = output.toArray(String[]::new);
    int from = Math.max(0, lines.length - rows);
    for (int i = from; i < lines.length; i++)
      c.fitted(lines[i], x + 7, y + 25 + (i - from) * 13, width - 17,
          lines[i].startsWith("$") ? GREEN : SUB, true);
    c.rect(x + 5, y + height - 22, width - 10, 17, "0d2434");
    c.fitted("dev@astra:~$ " + command + (blink() ? "▌" : ""), x + 8,
        y + height - 20, width - 19, TEXT, true);
  }

  private boolean blink() { return (int) (time * 2.2f) % 2 == 0; }

  private void drawTopBar() {
    c.rect(0, 0, 480, 16, "0b1126db");
    c.rect(0, 15, 480, 1, "ac86de22");
    boolean menuHover = hovered(7, 1, 18, 14);
    c.roundRect(7, 1, 17, 14, 5, menuHover ? "51427eae" : "53428a76");
    c.small("A", 12, 2, "ded1ff");
    c.small("Pinux Astra", 31, 3, "e3dcef");
    String appName = wm.front() == null ? "Рабочий стол" : wm.front().title;
    c.fitted(appName, 153, 3, 171, "ac9dc4", true);
    c.small("●", 365, 3, "a7c6bf");
    c.small(s().clock() + "   •   " + s().day() + " день", 382, 3, "e8dff3");
    hotspot("Pinux menu", 7, 1, 18, 14, () -> menuOpen = !menuOpen);
  }

  private void drawDock() {
    // Compact floating dock inspired by contemporary desktop environments.
    c.rect(0, 242, 480, 28, "080d2277");
    c.roundRect(129, 244, 223, 24, 10, "030716b0");
    c.roundRect(130, 245, 221, 22, 9, "292444d7");
    dockItem("A", App.CODE, 143, () -> launch(App.CODE), "6a55b4");
    dockItem(">_", App.TERMINAL, 180, () -> launch(App.TERMINAL), "313b5d");
    dockItem("[]", App.FILES, 217, () -> launch(App.FILES), "aa7d4f");
    dockItem("@", App.MAIL, 254, () -> launch(App.MAIL), "705891");
    dockItem("S", App.SETTINGS, 291, () -> launch(App.SETTINGS), "404d69");
    boolean exitHover = hovered(425, 249, 48, 17);
    c.roundRect(422, 248, 52, 17, 7, exitHover ? "573c60dd" : "211c34a8");
    c.small("Выйти", 427, 251, exitHover ? "ffffff" : "c3b8d0");
    hotspot("Встать из-за компьютера", 422, 248, 52, 17,
        () -> { game.switchMode(Mode.WORLD); game.refreshState(); });
  }

  private void dockItem(String glyph, App app, int x, Runnable action, String tint) {
    boolean active = wm.isFront(app);
    boolean opened = wm.get(app).visible;
    boolean hover = hovered(x, 245, 30, 22);
    if (hover) c.roundRect(x - 2, 245, 33, 21, 6, "7564b479");
    c.roundRect(x + 3, hover ? 246 : 247, 23, 18, 5, active ? "715aba" : tint);
    c.small(glyph, x + 14.5f - c.smallWidth(glyph) / 2,
        hover ? 247 : 248, "f8f3ff");
    if (opened) c.roundRect(x + 11, 265, 7, 2, 1, "c6aaff");
    hotspot(app.toString(), x - 2, 245, 33, 22, action);
  }

  private void drawApplicationMenu() {
    c.rect(10, 23, 188, 198, "04071970");
    c.roundRect(11, 24, 183, 192, 9, "16192de9");
    c.roundRect(13, 26, 179, 38, 7, "292442ef");
    c.text("Pinux Astra", 24, 35, "f3ecff");
    c.small("ПРИЛОЖЕНИЯ", 25, 72, "b9a9d6");
    menuItem("A", "Astra Code", 91, App.CODE);
    menuItem(">_", "Terminal", 115, App.TERMINAL);
    menuItem("[]", "Files", 139, App.FILES);
    menuItem("@", "TaskMail", 163, App.MAIL);
    menuItem("S", "Settings", 187, App.SETTINGS);
  }

  private void menuItem(String glyph, String title, int y, App app) {
    boolean hover = hovered(19, y - 4, 169, 22);
    if (hover) c.roundRect(19, y - 4, 169, 22, 5, "52447a9b");
    c.roundRect(24, y - 1, 19, 17, 5, hover ? "8165c2" : "353654");
    c.small(glyph, 33.5f - c.smallWidth(glyph) / 2, y + 1, "f2ebff");
    c.small(title, 54, y, hover ? "ffffff" : "dfd6ed");
    hotspot(title, 19, y - 4, 169, 22, () -> launch(app));
  }

  private void drawNotification() {
    c.rect(257, 21, 217, 38, "07121de8");
    c.rect(258, 22, 215, 36, "18384d");
    c.small("PINUX ASTRA  •  SYSTEM", 264, 26, accent());
    c.fitted(notice, 264, 42, 200, TEXT, true);
  }

  private void notify(String message) {
    notice = message;
    noticeTime = 3.4f;
  }

  private void button(String text, int x, int y, int width, int height, Runnable action) {
    game.uiButton(text, x, y, width, height, true, action);
  }

  private void launch(App app) {
    menuOpen = false;
    if (app == App.CODE) openLegacyEditor();
    else nativeDesktop.openNativeWindow(app);
    game.resetSelection();
  }

  private void log(String text) {
    output.addLast(text);
    while (output.size() > 90) output.removeFirst();
  }

  private void execute() {
    String raw = command.strip();
    command = "";
    if (raw.isEmpty()) return;
    log("$ " + raw);
    commandHistory.add(raw);
    if (commandHistory.size() > 50) commandHistory.remove(0);
    historyIndex = commandHistory.size();
    String cmd = raw.toLowerCase(Locale.ROOT);
    if (cmd.equals("clear")) { output.clear(); return; }
    switch (cmd) {
      case "help" -> {
        log("ls pwd cat whoami date neofetch clear echo");
        log("git status | git log | jobs | test | code | files | mail");
        log("desktop | settings | exit");
      }
      case "pwd" -> log("/home/dev/projects");
      case "whoami" -> log("dev");
      case "date" -> log("Day " + s().day() + ", " + s().clock());
      case "ls", "ls -la" -> log("src/  main.bs  README.md  .git/");
      case "neofetch" -> {
        log("◆ PINUX ASTRA 0.5  |  Java 21 / libGDX");
        log("User: dev  |  shell: astra-sh  |  runtime: offline");
      }
      case "cat readme.md" -> log("CTRL+LIFE programming workspace. Run: test");
      case "cat main.bs" -> {
        if (s().activeJob.isEmpty()) log("No active project.");
        else for (String line : s().codeSource.split("\n")) log(line);
      }
      case "git status" -> {
        log(s().activeJob.isEmpty() ? "On branch main; clean working tree" :
            "On branch main; project: " + s().activeJob);
        log("Local revisions: " + s().revisions.size());
      }
      case "git log" -> {
        if (s().revisions.isEmpty()) log("No local commits.");
        else for (var r : s().revisions) log("commit " + r.label);
      }
      case "jobs" -> log("Active: " + (s().activeJob.isEmpty() ? "none" : s().activeJob));
      case "test" -> {
        if (s().activeJob.isEmpty()) log("No project. Open Astra Code first.");
        else {
          life.report = Coding.test(s(), life.data.job(s().activeJob));
          log((life.report.passed() ? "PASS  " : "FAIL  ") + life.report.message());
          for (String caseResult : life.report.cases()) log(caseResult);
          game.refreshState();
          notify(life.report.passed() ? "All tests passed" : "Tests found issues");
        }
      }
      case "code" -> launch(App.CODE);
      case "files" -> launch(App.FILES);
      case "mail" -> launch(App.MAIL);
      case "settings" -> launch(App.SETTINGS);
      case "desktop", "exit" -> wm.close(App.TERMINAL);
      default -> {
        if (cmd.startsWith("echo ")) log(raw.substring(5));
        else log("command not found: " + raw + "  [help]");
      }
    }
  }

  public boolean keyDown(int key) {
    if (key == Keys.F1) { launch(App.CODE); return true; }
    if (key == Keys.F2) { launch(App.TERMINAL); return true; }
    if (key == Keys.F3) { launch(App.FILES); return true; }
    if (key == Keys.F4) { launch(App.MAIL); return true; }
    if (key == Keys.F6) { launch(App.SETTINGS); return true; }
    if (key == Keys.TAB) { wm.cycleFocus(); return true; }
    if (key == Keys.ESCAPE) {
      if (nativeDesktop.hasNativeWindows()) nativeDesktop.closeFrontNativeWindow();
      else if (menuOpen) menuOpen = false;
      else if (wm.front() != null) wm.closeFront();
      else { game.switchMode(Mode.WORLD); game.refreshState(); }
      return true;
    }
    if (nativeDesktop.hasNativeWindows()) return true;
    if (wm.isFront(App.TERMINAL)) {
      if (key == Keys.ENTER) execute();
      else if (key == Keys.BACKSPACE && !command.isEmpty())
        command = command.substring(0, command.length() - 1);
      else if (key == Keys.UP && !commandHistory.isEmpty()) {
        historyIndex = Math.max(0, historyIndex - 1);
        command = commandHistory.get(historyIndex);
      } else if (key == Keys.DOWN && !commandHistory.isEmpty()) {
        historyIndex = Math.min(commandHistory.size(), historyIndex + 1);
        command = historyIndex == commandHistory.size() ? "" : commandHistory.get(historyIndex);
      }
      return true;
    }
    if (wm.isFront(App.CODE) && wm.isMaximizedCode()) {
      if ((key >= Keys.A && key <= Keys.Z)
          || (key >= Keys.NUM_0 && key <= Keys.NUM_9)
          || (key >= Keys.NUMPAD_0 && key <= Keys.NUMPAD_9)
          || key == Keys.SPACE || key == Keys.COMMA || key == Keys.PERIOD
          || key == Keys.SLASH || key == Keys.SEMICOLON || key == Keys.MINUS
          || key == Keys.EQUALS || key == Keys.APOSTROPHE || key == Keys.BACKSLASH
          || key == Keys.LEFT_BRACKET || key == Keys.RIGHT_BRACKET)
        life.typeKey();
      return true;
    }
    return true;
  }

  public void keyTyped(char ch) {
    if (!wm.isFront(App.TERMINAL)) return;
    if (ch >= 32 && ch != 127 && command.length() < 120) command += ch;
  }

  public boolean pointerDown(int x, int y) {
    if (menuOpen && x > 195 && y > 18) menuOpen = false;
    if (menuOpen && x < 196 && y > 19 && y < 216) return false;
    return wm.pointerDown(x, y);
  }

  public void pointerMove(int x, int y) { wm.pointerMove(x, y); }
  public boolean pointerDragged(int x, int y) { wm.pointerDragged(x, y); wm.pointerMove(x, y); return true; }
  public boolean pointerUp(int x, int y) { return wm.pointerUp(x, y); }
  public void cancelPointer() { wm.cancelDrag(); nativeDesktop.setVisible(false); }
}
