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
  }

  public void draw() {
    float delta = Math.min(.1f, Gdx.graphics.getDeltaTime());
    time += delta;
    boot += delta;
    wm.tick(quietMotion ? Math.max(delta, .2f) : delta);
    noticeTime = Math.max(0, noticeTime - delta);
    if (boot < .55f && !quietMotion) {
      bootSplash();
      return;
    }
    desktopWallpaper();
    wm.draw(c, this::paintWindow);
    drawTopBar();
    drawDock();
    if (menuOpen) drawApplicationMenu();
    if (noticeTime > 0) drawNotification();
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

  private void desktopWallpaper() {
    String base = warmTheme ? "161d2b" : "081724";
    c.rect(0, 0, 480, 270, base);
    c.rect(0, 16, 480, 233, warmTheme ? "263044" : "102d47");
    for (int i = 0; i < 15; i++) {
      float y = 18 + i * 15;
      c.rect(0, y, 480, 15, i % 2 == 0 ? "0c263a10" : "102b4515");
    }
    c.rect(25, 53, 430, 145, warmTheme ? "5861761c" : "28639a22");
    c.rect(36, 75, 408, 100, "112d4b36");
    for (int i = 0; i < 8; i++) {
      int x = 56 + i * 49;
      c.rect(x, 83 + (i % 3) * 13, 1, 90, warmTheme ? "9a979040" : "70b3dd33");
    }
    c.rect(0, 194, 480, 56, warmTheme ? "2a3548b0" : "071827aa");
    c.title("PINUX ASTRA", 24, 199, "d9e9f3");
    c.small("CREATE / COMPILE / LIVE", 26, 227, accent());
    if (wm.openCount() == 0) {
      desktopIcon("</>  ASTRA CODE", 23, 45, App.CODE);
      desktopIcon(">_   TERMINAL", 23, 75, App.TERMINAL);
      desktopIcon("▣   FILES", 23, 105, App.FILES);
      desktopIcon("✉   TASKMAIL", 23, 135, App.MAIL);
    }
  }

  private void desktopIcon(String title, int x, int y, App app) {
    c.rect(x, y, 130, 25, "08182ec0");
    c.rect(x, y + 24, 130, 1, "4c799466");
    button(title, x + 1, y + 1, 128, 23, () -> launch(app));
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
    c.rect(0, 0, 480, 17, "050e19f5");
    c.rect(0, 16, 480, 1, "38627b");
    button(menuOpen ? "×" : "P", 3, 1, 21, 14, () -> menuOpen = !menuOpen);
    c.small("PINUX ASTRA   /   " + (wm.front() == null ? "DESKTOP" : wm.front().title),
        30, 4, TEXT);
    c.small("●  " + s().clock() + "  D" + s().day(), 373, 4, accent());
  }

  private void drawDock() {
    c.rect(0, 249, 480, 21, "071321f2");
    c.rect(0, 249, 480, 1, "416781");
    int x = 5;
    dockItem("▣", null, x, 27, () -> menuOpen = !menuOpen); x += 32;
    dockItem("CODE", App.CODE, x, 66, () -> launch(App.CODE)); x += 71;
    dockItem(">_", App.TERMINAL, x, 47, () -> launch(App.TERMINAL)); x += 52;
    dockItem("FILES", App.FILES, x, 58, () -> launch(App.FILES)); x += 63;
    dockItem("MAIL", App.MAIL, x, 56, () -> launch(App.MAIL)); x += 61;
    dockItem("⚙", App.SETTINGS, x, 32, () -> launch(App.SETTINGS));
    c.small("CTRL+LIFE", 373, 257, SUB);
    button("EXIT", 429, 251, 45, 16, () -> { game.switchMode(Mode.WORLD); game.refreshState(); });
  }

  private void dockItem(String label, App app, int x, int width, Runnable action) {
    boolean active = app != null && wm.isFront(app);
    boolean open = app != null && wm.get(app).visible;
    c.rect(x, 251, width, 17, active ? "23465d" : "132739");
    if (open) c.rect(x + 7, 267, width - 14, 1, active ? accent() : "577c91");
    button(label, x, 251, width, 16, action);
  }

  private void drawApplicationMenu() {
    c.rect(6, 20, 189, 196, "020912aa");
    c.rect(7, 21, 187, 193, "162d41");
    c.rect(8, 22, 185, 38, "27485e");
    c.text("Pinux Astra", 18, 32, TEXT);
    c.small("APPLICATIONS", 17, 67, accent());
    button("Astra Code   /   IDE", 14, 83, 171, 21, () -> launch(App.CODE));
    button("Terminal", 14, 109, 171, 21, () -> launch(App.TERMINAL));
    button("Files", 14, 135, 171, 21, () -> launch(App.FILES));
    button("TaskMail", 14, 161, 171, 21, () -> launch(App.MAIL));
    button("Settings", 14, 187, 171, 21, () -> launch(App.SETTINGS));
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
    wm.open(app);
    if (app == App.CODE && !wm.get(app).maximized) wm.toggleMaximize(App.CODE);
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
      if (menuOpen) menuOpen = false;
      else if (wm.front() != null) wm.closeFront();
      else { game.switchMode(Mode.WORLD); game.refreshState(); }
      return true;
    }
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
  public void cancelPointer() { wm.cancelDrag(); }
}
