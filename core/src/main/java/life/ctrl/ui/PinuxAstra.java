package life.ctrl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.model.Coding;
import life.ctrl.model.GameState;
import life.ctrl.render.PixelCanvas;

/** A contained, fictional desktop. Commands never execute on the host OS. */
public final class PinuxAstra {
  private enum App { DESKTOP, CODE, TERMINAL, FILES }
  private final CtrlLife game;
  private final LifeScreens life;
  private final Workbench workbench;
  private final PixelCanvas c;
  private final ArrayDeque<String> output = new ArrayDeque<>();
  private final List<String> commands = new ArrayList<>();
  private App app = App.DESKTOP;
  private String input = "";
  private boolean terminalDock;
  private int historyCursor;
  private float uptime;
  private static final String BG = "0b1725", PANEL = "101f30", EDGE = "314a63";
  private static final String TEXT = "deebf4", MUTED = "95aabd", CYAN = "82d3e8", GREEN = "a9deba";

  public PinuxAstra(CtrlLife game, LifeScreens life, Workbench workbench, PixelCanvas canvas) {
    this.game = game;
    this.life = life;
    this.workbench = workbench;
    this.c = canvas;
    log("Pinux Astra 0.1 / x86_64 / local shell");
    log("Введите help для списка команд.");
  }

  private GameState s() { return game.world().state; }

  public void open() {
    app = App.DESKTOP;
    terminalDock = false;
    uptime = 0;
  }

  public void draw() {
    uptime += Math.min(.1f, Gdx.graphics.getDeltaTime());
    if (uptime < .6f) {
      c.rect(0, 0, 480, 270, "070f1a");
      c.title("PINUX ASTRA", 151, 104, CYAN);
      c.small("Загрузка рабочего окружения...", 153, 136, MUTED);
      c.rect(152, 159, 176, 3, "2a4158");
      c.rect(152, 159, 176 * uptime / .6f, 3, CYAN);
      return;
    }
    switch (app) {
      case DESKTOP -> desktop();
      case CODE -> workbench.draw();
      case TERMINAL -> terminal(false);
      case FILES -> files();
    }
    if (app == App.CODE && terminalDock) terminal(true);
    topBar();
    dock();
  }

  private void launch(App next) {
    app = next;
    if (next != App.CODE) terminalDock = false;
    game.resetSelection();
  }

  private void button(String text, int x, int y, int width, int height, Runnable action) {
    game.uiButton(text, x, y, width, height, true, action);
  }

  private void topBar() {
    c.rect(0, 0, 480, 16, "08121df8");
    c.rect(0, 15, 480, 1, EDGE);
    c.small("P  PINUX ASTRA", 8, 4, CYAN);
    c.small(switch (app) {
      case DESKTOP -> "Рабочий стол";
      case CODE -> "Astra Code";
      case TERMINAL -> "Terminal";
      case FILES -> "Файлы";
    }, 142, 4, TEXT);
    c.small("●  " + s().clock() + "  |  ДЕНЬ " + s().day(), 348, 4, MUTED);
  }

  private void dock() {
    c.rect(0, 251, 480, 19, "09121ef8");
    c.rect(0, 250, 480, 1, EDGE);
    button("P", 4, 252, 25, 16, () -> launch(App.DESKTOP));
    button("Code", 34, 252, 62, 16, () -> launch(App.CODE));
    button("Files", 101, 252, 62, 16, () -> launch(App.FILES));
    button("Terminal", 168, 252, 83, 16, () -> launch(App.TERMINAL));
    if (app == App.CODE)
      button(terminalDock ? "Hide console" : "Console", 258, 252, 98, 16,
          () -> { terminalDock = !terminalDock; game.resetSelection(); });
    button("Встать", 399, 252, 76, 16, () -> { game.switchMode(Mode.WORLD); game.refreshState(); });
  }

  private void desktop() {
    c.rect(0, 0, 480, 270, BG);
    for (int i = 0; i < 7; i++)
      c.rect(i * 74 - 170, 30 + i * 24, 660, 1, "21476b64");
    c.rect(0, 135, 480, 116, "10283f");
    c.rect(15, 34, 170, 58, "0e2236dd");
    c.title("Pinux Astra", 25, 44, TEXT);
    c.small("ВАША РАБОЧАЯ СТАНЦИЯ", 26, 72, CYAN);
    c.small("Локальная среда / offline", 26, 85, MUTED);
    c.small("ПРИЛОЖЕНИЯ", 21, 114, MUTED);
    button("</> Astra Code", 20, 134, 136, 27, () -> launch(App.CODE));
    button(">_ Terminal", 171, 134, 136, 27, () -> launch(App.TERMINAL));
    button("[ ] Файлы", 322, 134, 136, 27, () -> launch(App.FILES));
    c.small("F1 — редактор  /  F2 — консоль  /  F3 — файлы", 25, 224, MUTED);
  }

  private void files() {
    c.rect(0, 0, 480, 270, BG);
    c.panel(14, 24, 452, 218);
    c.rect(19, 29, 442, 22, "172f46");
    c.text("Файлы", 29, 33, TEXT);
    c.small("/home/dev/projects", 28, 60, CYAN);
    c.rect(22, 76, 435, 1, EDGE);
    c.small("НАИМЕНОВАНИЕ", 28, 87, MUTED);
    c.small("ТИП", 342, 87, MUTED);
    c.small("projects/", 28, 110, TEXT);
    c.small("Папка", 342, 110, MUTED);
    c.small("src/", 28, 130, TEXT);
    c.small("Папка", 342, 130, MUTED);
    String filename = s().activeJob.isEmpty() ? "main.bs (нет активного проекта)" : "main.bs";
    c.small(filename, 28, 150, CYAN);
    c.small("ByteScript", 342, 150, MUTED);
    c.small("README.md", 28, 170, TEXT);
    c.small("Документ", 342, 170, MUTED);
    button("Открыть проект в Astra Code", 28, 200, 256, 26, () -> launch(App.CODE));
  }

  private void terminal(boolean compact) {
    int left = compact ? 155 : 18;
    int top = compact ? 165 : 29;
    int width = compact ? 310 : 444;
    int height = compact ? 64 : 212;
    c.rect(left, top, width, height, PANEL);
    c.rect(left, top, width, 1, EDGE);
    c.rect(left, top + 1, 1, height - 1, EDGE);
    c.rect(left + width - 1, top + 1, 1, height - 1, EDGE);
    c.rect(left, top + height - 1, width, 1, EDGE);
    c.rect(left + 1, top + 1, width - 2, 16, "1c3348");
    c.small(compact ? "TERMINAL  /  shell" : "Terminal  /  bash-like local console", left + 9, top + 4, CYAN);
    int visible = compact ? 2 : 10;
    String[] lines = output.toArray(String[]::new);
    int from = Math.max(0, lines.length - visible);
    c.clip(left + 6, top + 20, width - 12, height - 36);
    for (int i = from; i < lines.length; i++) {
      String line = lines[i];
      c.fitted(line, left + 8, top + 20 + (i - from) * 14, width - 18,
          line.startsWith("$") ? GREEN : MUTED, true);
    }
    c.unclip();
    c.rect(left + 6, top + height - 19, width - 12, 15, "0a1929");
    c.fitted("dev@astra:~$ " + input + ((System.nanoTime() / 500_000_000L) % 2 == 0 ? "_" : ""),
        left + 9, top + height - 17, width - 20, TEXT, true);
  }

  private void log(String line) {
    output.addLast(line);
    while (output.size() > 80) output.removeFirst();
  }

  private void execute() {
    String raw = input.strip();
    input = "";
    if (raw.isEmpty()) return;
    log("$ " + raw);
    commands.add(raw);
    if (commands.size() > 50) commands.remove(0);
    historyCursor = commands.size();
    String cmd = raw.toLowerCase(Locale.ROOT);
    if (cmd.equals("clear")) { output.clear(); return; }
    if (cmd.equals("help")) {
      log("help, clear, ls, pwd, whoami, date, neofetch");
      log("cat README.md, cat main.bs, git status, git log");
      log("jobs, test, echo <text>, code, files, desktop, exit");
    } else if (cmd.equals("pwd")) log("/home/dev/projects");
    else if (cmd.equals("whoami")) log("dev");
    else if (cmd.equals("date")) log("День " + s().day() + ", " + s().clock());
    else if (cmd.equals("ls") || cmd.equals("ls -la")) log("src/   main.bs   README.md   .git/");
    else if (cmd.equals("neofetch")) {
      log("Pinux Astra 0.1 | Java 21 / ByteScript");
      log("Shell: Astra Terminal | User: dev");
    } else if (cmd.equals("cat readme.md")) log("Код, тесты, работа. Открой Astra Code (F1).");
    else if (cmd.equals("cat main.bs")) {
      if (s().activeJob.isEmpty()) log("Нет активного проекта.");
      else for (String line : s().codeSource.split("\n")) log(line);
    } else if (cmd.equals("git status")) {
      log(s().activeJob.isEmpty() ? "nothing to commit" : "On branch main | project: " + s().activeJob);
      log("Local commits: " + s().revisions.size());
    } else if (cmd.equals("git log")) {
      if (s().revisions.isEmpty()) log("No commits yet.");
      else for (var r : s().revisions) log("commit " + r.label);
    } else if (cmd.equals("jobs")) log("Active contract: " + (s().activeJob.isEmpty() ? "none" : s().activeJob));
    else if (cmd.equals("test")) {
      if (s().activeJob.isEmpty()) log("Нет открытого заказа. Выбери его в Astra Code.");
      else {
        life.report = Coding.test(s(), life.data.job(s().activeJob));
        log(life.report.passed() ? "PASS: " + life.report.message() : "FAIL: " + life.report.message());
        for (String line : life.report.cases()) log(line);
        game.refreshState();
      }
    } else if (cmd.startsWith("echo ")) log(raw.substring(5));
    else if (cmd.equals("code")) launch(App.CODE);
    else if (cmd.equals("files")) launch(App.FILES);
    else if (cmd.equals("desktop") || cmd.equals("exit")) launch(App.DESKTOP);
    else log("command not found: " + raw + " (help)");
  }

  public boolean keyDown(int key) {
    if (key == Keys.F1) { launch(App.CODE); return true; }
    if (key == Keys.F2) { launch(App.TERMINAL); return true; }
    if (key == Keys.F3) { launch(App.FILES); return true; }
    if (key == Keys.ESCAPE) {
      if (terminalDock) terminalDock = false;
      else if (app != App.DESKTOP) launch(App.DESKTOP);
      else { game.switchMode(Mode.WORLD); game.refreshState(); }
      return true;
    }
    if (app == App.TERMINAL || (app == App.CODE && terminalDock)) {
      if (key == Keys.ENTER) execute();
      else if (key == Keys.BACKSPACE && !input.isEmpty()) input = input.substring(0, input.length() - 1);
      else if (key == Keys.UP && !commands.isEmpty()) {
        historyCursor = Math.max(0, historyCursor - 1);
        input = commands.get(historyCursor);
      } else if (key == Keys.DOWN && !commands.isEmpty()) {
        historyCursor = Math.min(commands.size(), historyCursor + 1);
        input = historyCursor == commands.size() ? "" : commands.get(historyCursor);
      }
      return true;
    }
    if (app == App.CODE) {
      // Only actual typing keys count: function keys, modifiers and navigation must not print code.
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
    if (!(app == App.TERMINAL || (app == App.CODE && terminalDock))) return;
    if (ch >= 32 && ch != 127 && input.length() < 100) input += ch;
  }
}
