package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import life.ctrl.CtrlLife;
import life.ctrl.model.Settings;
import life.ctrl.model.World;

/** Runs actual LWJGL3 rendering and keyboard input. Requires an OpenGL-capable display. */
public final class GuiSmoke extends CtrlLife {
  private final ArrayDeque<Step> script = new ArrayDeque<>();
  private final ArrayList<float[]> path = new ArrayList<>();
  private long started;
  private float stepTime, total, beforeMove;
  private double beforePause;
  private int frames;
  private boolean failed, soakRight = true;
  private String current = "startup";

  private record Step(String name, BooleanSupplier run) {}

  public static void main(String[] args) throws Exception {
    Path save = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(save);
    for (String f : new String[] {"save.json", "save.backup.json", "settings.json"})
      Files.deleteIfExists(save.resolve(f));
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("CTRL+LIFE — GUI smoke");
    config.setWindowedMode(1440, 810);
    config.disableAudio(Boolean.getBoolean("ctrl.noAudio"));
    config.setForegroundFPS(Integer.getInteger("ctrl.fps", 60));
    config.useVsync(false);
    GuiSmoke game = new GuiSmoke();
    new Lwjgl3Application(game, config);
    if (game.failed) throw new IllegalStateException("GUI smoke failed: " + game.current);
    if (Boolean.getBoolean("ctrl.menuOnly")) {
      if (Files.exists(save.resolve("save.json")))
        throw new AssertionError("Menu-only session created a save");
      System.out.println(
          "GUI_MENU_ONLY_PASS: closing settings without starting does not create a save");
    }
  }

  private void add(String name, BooleanSupplier f) {
    script.add(new Step(name, f));
  }

  private void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  private void click(int x, int y) {
    Gdx.input
        .getInputProcessor()
        .touchDown(
            canvas().offsetX + x * canvas().scale,
            canvas().offsetY + y * canvas().scale,
            0,
            Input.Buttons.LEFT);
  }

  private void stop() {
    for (int k : new int[] {Keys.W, Keys.A, Keys.S, Keys.D}) Gdx.input.getInputProcessor().keyUp(k);
  }

  private void shot(String name) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/" + name + ".png");
  }

  private void check(boolean value, String error) {
    if (!value) throw new AssertionError(error);
  }

  private void once(String name, Runnable action) {
    add(
        name,
        () -> {
          action.run();
          return true;
        });
  }

  private void waitMode(Mode mode) {
    add("wait " + mode, () -> mode() == mode);
  }

  private void walk(float x, float y) {
    once("path to " + x + "," + y, () -> plan(x, y));
    add(
        "walk to " + x + "," + y,
        () -> {
          stop();
          if (path.isEmpty()) return true;
          var target = path.get(0);
          float dx = target[0] - world().state.x, dy = target[1] - world().state.y;
          if (Math.abs(dx) < 1.6 && Math.abs(dy) < 1.6) {
            path.remove(0);
            return path.isEmpty();
          }
          if (Math.abs(dx) > 1) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
          if (Math.abs(dy) > 1) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
          return false;
        });
  }

  private void choose(int n) {
    once("choose " + n, () -> tap(Keys.NUM_1 + n - 1));
  }

  private void interact() {
    once(
        "interact",
        () -> {
          stop();
          tap(Keys.E);
        });
  }

  private void plan(float tx, float ty) {
    path.clear();
    World w = world();
    int grid = 4, cols = w.map().width / grid, rows = w.map().height / grid;
    int start = Math.round(w.state.y / grid) * cols + Math.round(w.state.x / grid);
    int[] prev = new int[cols * rows];
    Arrays.fill(prev, -1);
    prev[start] = start;
    ArrayDeque<Integer> q = new ArrayDeque<>();
    q.add(start);
    int goal = -1;
    while (!q.isEmpty()) {
      int at = q.remove(), x = at % cols, y = at / cols;
      if (Math.hypot(x * grid - tx, y * grid - ty) < 3) {
        goal = at;
        break;
      }
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        int nx = x + d[0], ny = y + d[1];
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
        int next = ny * cols + nx;
        if (prev[next] < 0
            && !w.blocked(nx * grid - 2, ny * grid - 2)
            && !w.blocked(nx * grid + 2, ny * grid - 2)
            && !w.blocked(nx * grid - 2, ny * grid + 2)
            && !w.blocked(nx * grid + 2, ny * grid + 2)) {
          prev[next] = at;
          q.add(next);
        }
      }
    }
    check(goal >= 0, "No path to " + tx + "," + ty);
    ArrayList<float[]> reverse = new ArrayList<>();
    for (int at = goal; at != start; at = prev[at])
      reverse.add(new float[] {at % cols * grid, at / cols * grid});
    Collections.reverse(reverse);
    path.addAll(reverse);
  }

  @Override
  public void create() {
    super.create();
    started = System.nanoTime();
    check(Boolean.getBoolean("ctrl.noAudio") || audioReady(), "Audio assets did not load");
    if (Boolean.getBoolean("ctrl.menuOnly")) {
      add("wait boot", () -> frames > 5);
      once("open settings", () -> click(300, 193));
      waitMode(Mode.SETTINGS);
      once("close without playing", () -> Gdx.app.exit());
      return;
    }
    add("wait boot", () -> frames > 5);
    once(
        "menu screenshot",
        () -> {
          shot("01-menu");
          tap(Keys.ENTER);
        });
    waitMode(Mode.CREATE);
    once(
        "create screenshot",
        () -> {
          shot("02-create");
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    waitMode(Mode.WORLD);
    once("room screenshot", () -> shot("03-room"));
    walk(392, 116);
    interact();
    waitMode(Mode.DIALOGUE);
    choose(1);
    waitMode(Mode.WORLD);
    once("plant watered", () -> check(world().state.wateredPlant, "Watering did not persist"));
    walk(155, 240);
    interact();
    waitMode(Mode.DIALOGUE);
    choose(1);
    waitMode(Mode.WORLD);
    once("boxes unpacked", () -> check(world().state.unpacked, "Unpacking did not persist"));
    walk(110, 196);
    interact();
    once(
        "outfit changed", () -> check(world().state.outfit == 1, "Wardrobe did not change outfit"));
    walk(128, 140);
    interact();
    waitMode(Mode.DIALOGUE);
    once("computer screenshot", () -> shot("04-computer-dialogue"));
    choose(1);
    choose(1);
    waitMode(Mode.WORLD);
    once("computer flag", () -> check(world().state.inspectedPc, "PC was not inspected"));
    walk(243, 252);
    interact();
    once(
        "entered stair",
        () -> check(world().state.map.equals("stair"), "Door did not enter stair"));
    // Approach the moving NPC through actual movement; dialogue freezes the world clock.
    add(
        "approach Mira",
        () -> {
          stop();
          var a = new World.Actor();
          world().position(world().map().npcs[0], a);
          float dx = a.x - world().state.x, dy = a.y - world().state.y;
          if (Math.hypot(dx, dy) < 23) {
            tap(Keys.E);
            return true;
          }
          if (Math.abs(dx) > 4) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
          if (Math.abs(dy) > 4) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
          return false;
        });
    waitMode(Mode.DIALOGUE);
    once("Mira screenshot", () -> shot("05-mira"));
    choose(1);
    choose(1);
    waitMode(Mode.WORLD);
    once("Mira request", () -> check(world().state.askedLetter, "Mira quest not accepted"));
    walk(244, 254);
    interact();
    once(
        "street screenshot",
        () -> {
          check(world().state.map.equals("street"), "Door did not enter street");
          shot("06-street-home");
        });
    walk(480, 287);
    once("street middle screenshot", () -> shot("07-street-workshop"));
    walk(675, 310);
    interact();
    waitMode(Mode.DIALOGUE);
    choose(1);
    waitMode(Mode.WORLD);
    once(
        "letter collected",
        () -> {
          check(world().state.hasLetter, "Letter not collected");
          shot("08-street-bench");
          tap(Keys.I);
        });
    waitMode(Mode.BAG);
    once(
        "inventory screenshot",
        () -> {
          shot("09-inventory");
          tap(Keys.ESCAPE);
        });
    waitMode(Mode.WORLD);
    once("map", () -> tap(Keys.M));
    waitMode(Mode.MAP);
    once(
        "map screenshot",
        () -> {
          shot("10-map");
          tap(Keys.ESCAPE);
        });
    walk(174, 235);
    interact();
    once("return stair", () -> check(world().state.map.equals("stair"), "Return door failed"));
    add(
        "return to Mira",
        () -> {
          stop();
          var a = new World.Actor();
          world().position(world().map().npcs[0], a);
          float dx = a.x - world().state.x, dy = a.y - world().state.y;
          if (Math.hypot(dx, dy) < 23) {
            tap(Keys.E);
            return true;
          }
          if (Math.abs(dx) > 4) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
          if (Math.abs(dy) > 4) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
          return false;
        });
    waitMode(Mode.DIALOGUE);
    choose(1);
    choose(1);
    waitMode(Mode.WORLD);
    once(
        "letter delivered",
        () -> check(world().state.deliveredLetter && !world().state.hasLetter, "Delivery failed"));
    walk(160, 87);
    interact();
    once(
        "home again",
        () -> {
          check(world().state.map.equals("room"), "Home return failed");
          tap(Keys.J);
        });
    waitMode(Mode.JOURNAL);
    once(
        "journal screenshot",
        () -> {
          shot("11-journal");
          tap(Keys.ESCAPE);
        });
    once(
        "focus loss",
        () -> {
          float x = world().state.x;
          Gdx.input.getInputProcessor().keyDown(Keys.D);
          focusChanged(false);
          check(mode() == Mode.PAUSE, "Focus did not pause");
          check(world().state.x == x, "Unexpected movement");
          focusChanged(true);
        });
    once("paused time start", () -> beforePause = world().state.elapsed);
    add("pause time", () -> stepTime > .2);
    once(
        "paused time check",
        () -> check(world().state.elapsed == beforePause, "Time advanced during pause"));
    once(
        "pause screenshot",
        () -> {
          shot("12-pause");
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    waitMode(Mode.SETTINGS);
    once("music mouse click", () -> click(240, 68));
    once(
        "music changed",
        () ->
            check(
                Settings.load(Path.of(System.getProperty("ctrl.dataDir"))).music > .35f,
                "Mouse button did not change volume"));
    once(
        "settings screenshot",
        () -> {
          shot("13-settings");
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    waitMode(Mode.CONTROLS);
    once(
        "controls screenshot",
        () -> {
          shot("14-controls");
          tap(Keys.ENTER);
        });
    once("bind T", () -> tap(Keys.T));
    once("back controls", () -> tap(Keys.ESCAPE));
    waitMode(Mode.SETTINGS);
    once("back settings", () -> tap(Keys.ESCAPE));
    waitMode(Mode.PAUSE);
    once(
        "back menu",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    waitMode(Mode.MENU);
    once("continue", () -> tap(Keys.ENTER));
    waitMode(Mode.WORLD);
    once(
        "restored",
        () -> {
          check(world().state.deliveredLetter, "Continue lost quest state");
          check(
              world().state.wateredPlant && world().state.unpacked && world().state.outfit == 1,
              "Continue lost object changes");
          check(world().state.map.equals("room"), "Continue lost map");
        });
    once(
        "move with remapped T",
        () -> {
          beforeMove = world().state.y;
          Gdx.input.getInputProcessor().keyDown(Keys.T);
        });
    add("hold remapped key", () -> stepTime > .25);
    once(
        "remapped movement works",
        () -> {
          Gdx.input.getInputProcessor().keyUp(Keys.T);
          check(world().state.y < beforeMove - 8, "Remapped key did not move player");
        });
    once("960 resize", () -> Gdx.graphics.setWindowedMode(960, 540));
    add("resize settle", () -> stepTime > .3);
    once("small screenshot", () -> shot("15-window-960"));
    once("1080 resize", () -> Gdx.graphics.setWindowedMode(1920, 1080));
    add("large settle", () -> stepTime > .3);
    once("1080 screenshot", () -> shot("16-window-1080"));
    once("restore size", () -> Gdx.graphics.setWindowedMode(1440, 810));
    add(
        "soak",
        () -> {
          check(!world().blocked(world().state.x, world().state.y), "Invalid position during soak");
          stop();
          if (total >= Float.parseFloat(System.getProperty("ctrl.soakSeconds", "0"))) return true;
          if (world().state.x > 275) soakRight = false;
          if (world().state.x < 180) soakRight = true;
          Gdx.input.getInputProcessor().keyDown(soakRight ? Keys.D : Keys.A);
          return false;
        });
    once(
        "finished",
        () -> {
          System.out.println(
              "GUI_SMOKE_PASS: movement, 3 maps, dialogues, quest, save/continue, focus, menus,"
                  + " resize. Frames="
                  + frames
                  + " seconds="
                  + total);
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    frames++;
    float dt = Gdx.graphics.getDeltaTime();
    stepTime += dt;
    total = (System.nanoTime() - started) / 1e9f;
    if (script.isEmpty()) return;
    Step step = script.peek();
    current = step.name;
    try {
      if (step.run.getAsBoolean()) {
        System.out.println("GUI: " + current);
        script.remove();
        stepTime = 0;
      } else if (stepTime > 45 && !current.equals("soak"))
        throw new AssertionError("Timeout " + current);
    } catch (Throwable e) {
      failed = true;
      shot("FAILED");
      e.printStackTrace();
      Gdx.app.exit();
      script.clear();
    }
  }
}
