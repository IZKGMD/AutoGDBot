package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import life.ctrl.CtrlLife;
import life.ctrl.model.*;

/**
 * Actual input and rendering for the complete first day. Later intimacy UI uses an explicit
 * fixture.
 */
public final class ChapterSmoke extends CtrlLife {
  private record Step(String label, BooleanSupplier run) {}

  private final ArrayDeque<Step> steps = new ArrayDeque<>();
  private final ArrayList<float[]> route = new ArrayList<>();
  private int frames;
  private float timer, total;
  private boolean failed;
  private String current = "startup";
  private float beforeX, beforeY;
  private double pauseTime;

  public static void main(String[] args) throws Exception {
    Path dir = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(dir);
    for (String n : List.of("save.json", "save.backup.json", "settings.json"))
      Files.deleteIfExists(dir.resolve(n));
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    var config = new Lwjgl3ApplicationConfiguration();
    config.setWindowedMode(1440, 810);
    config.setTitle("CTRL+LIFE 0.2 — test");
    config.disableAudio(Boolean.getBoolean("ctrl.noAudio"));
    config.setForegroundFPS(Integer.getInteger("ctrl.fps", 60));
    config.useVsync(false);
    ChapterSmoke game = new ChapterSmoke();
    new Lwjgl3Application(game, config);
    if (game.failed) throw new AssertionError(game.current);
    if (Boolean.getBoolean("ctrl.menuOnly")) {
      if (Files.exists(dir.resolve("save.json"))) throw new AssertionError("Menu created a save");
      System.out.println("MENU_ONLY_PASS");
    }
  }

  private void add(String name, BooleanSupplier f) {
    steps.add(new Step(name, f));
  }

  private void once(String name, Runnable f) {
    add(
        name,
        () -> {
          if (timer < .08f) return false;
          f.run();
          return true;
        });
  }

  private void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  private void click(int x, int y) {
    Gdx.input
        .getInputProcessor()
        .touchDown(
            canvas().screenX(x),
            canvas().screenY(y),
            0,
            Input.Buttons.LEFT);
  }

  private void stop() {
    for (int k : new int[] {Keys.W, Keys.A, Keys.S, Keys.D}) Gdx.input.getInputProcessor().keyUp(k);
  }

  private void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private void shot(String name) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/" + name + ".png");
  }

  private void waitFor(Mode mode) {
    add("wait " + mode, () -> mode() == mode);
  }

  private void pressE() {
    once(
        "interact",
        () -> {
          stop();
          tap(Keys.E);
        });
  }

  private void closeScene() {
    waitFor(Mode.SCENE);
    once("continue scene", () -> click(240, 237));
    waitFor(Mode.WORLD);
  }

  private void walk(float x, float y) {
    once(
        "plan " + x + "," + y,
        () -> {
          route.clear();
          route.addAll(Navigation.route(world(), x, y));
          check(
              !route.isEmpty() || Math.hypot(world().state.x - x, world().state.y - y) < 29,
              "No route");
        });
    add(
        "walk " + x + "," + y,
        () -> {
          stop();
          if (route.isEmpty()) return true;
          var p = route.get(0);
          float dx = p[0] - world().state.x, dy = p[1] - world().state.y;
          if (Math.abs(dx) < 1.6 && Math.abs(dy) < 1.6) {
            route.remove(0);
            return route.isEmpty();
          }
          if (Math.abs(dx) >= 1.3) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
          if (Math.abs(dy) >= 1.3) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
          return false;
        });
  }

  private void approachMira() {
    add(
        "approach Mira",
        () -> {
          stop();
          var npc =
              world().npcs().stream().filter(n -> n.id.equals("mira")).findFirst().orElseThrow();
          var a = new World.Actor();
          world().position(npc, a);
          float dx = a.x - world().state.x, dy = a.y - world().state.y;
          if (Math.hypot(dx, dy) < 22) {
            tap(Keys.E);
            return true;
          }
          if (Math.abs(dx) > 3) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
          if (Math.abs(dy) > 3) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
          return false;
        });
    waitFor(Mode.SOCIAL);
  }

  @Override
  public void create() {
    super.create();
    add("boot", () -> frames > 4);
    if (Boolean.getBoolean("ctrl.menuOnly")) {
      once("settings", () -> click(300, 193));
      waitFor(Mode.SETTINGS);
      once("close settings without game", () -> Gdx.app.exit());
      return;
    }
    once(
        "menu",
        () -> {
          shot("01-menu");
          tap(Keys.ENTER);
        });
    waitFor(Mode.CREATE);
    once(
        "new game",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    waitFor(Mode.INTRO);
    once(
        "arrival",
        () -> {
          shot("02-arrival");
          tap(Keys.ENTER);
        });
    once("budget introduction", () -> tap(Keys.ENTER));
    once(
        "controls introduction",
        () -> {
          shot("03-onboarding");
          tap(Keys.ENTER);
        });
    waitFor(Mode.WORLD);
    once("poor room", () -> shot("04-room"));
    walk(187, 156);
    pressE();
    waitFor(Mode.IDE);
    once(
        "job board",
        () -> {
          shot("05-jobs");
          click(240, 122);
        });
    once("leave before algorithm", () -> tap(Keys.ESCAPE));
    waitFor(Mode.WORLD);
    pressE();
    waitFor(Mode.IDE);
    once(
        "reopen unstarted order",
        () -> {
          check(lifeScreens().codePage == 0, "Unstarted order stuck in editor");
          click(240, 122);
        });
    once(
        "algorithm choice",
        () -> {
          shot("06-algorithm");
          click(240, 160);
        });
    once(
        "typing start",
        () -> {
          beforeX = world().state.x;
          beforeY = world().state.y;
          for (int i = 0; i < 25; i++) tap(i % 2 == 0 ? Keys.W : Keys.E);
        });
    once(
        "typing isolated",
        () -> {
          check(beforeX == world().state.x && beforeY == world().state.y, "IDE moved player");
          click(180, 236);
        });
    once(
        "real compiler error",
        () -> {
          check(
              lifeScreens().report != null && !lifeScreens().report.compiled(),
              "No semantic error");
          shot("07-error");
          click(285, 236);
        });
    once("repair", () -> click(240, 166));
    once("retest", () -> click(180, 236));
    once(
        "tests pass",
        () -> {
          check(lifeScreens().report.passed(), "Repair did not fix real code");
          shot("08-tests");
          click(290, 236);
        });
    once(
        "preview",
        () -> {
          shot("09-preview");
          click(145, 175);
        });
    once("deliver", () -> click(240, 209));
    closeScene();
    once("payment once", () -> check(world().state.money == 10650, "Wrong payment"));
    walk(230, 247);
    pressE();
    once("stair", () -> check(world().state.map.equals("stair"), "Wrong door"));
    walk(246, 262);
    pressE();
    add("settled street", () -> timer > .3f);
    once("street guide", () -> shot("10-street"));
    walk(814, 230);
    pressE();
    once("diner", () -> check(world().state.map.equals("diner"), "Wrong diner door"));
    walk(159, 153);
    pressE();
    waitFor(Mode.SHOP);
    once(
        "food prices",
        () -> {
          shot("11-food");
          click(240, 92);
        });
    waitFor(Mode.SCENE);
    once("return counter", () -> click(240, 237));
    waitFor(Mode.SHOP);
    once("leave counter", () -> tap(Keys.ESCAPE));
    waitFor(Mode.WORLD);
    once("eat meal", () -> tap(Keys.I));
    waitFor(Mode.BAG);
    once("eat soup", () -> click(240, 83));
    waitFor(Mode.SCENE);
    once("back pockets", () -> click(240, 237));
    waitFor(Mode.BAG);
    once("close pockets", () -> tap(Keys.ESCAPE));
    waitFor(Mode.WORLD);
    once(
        "needs updated",
        () -> {
          check(world().state.ateFirstMeal, "Food not eaten");
          check(world().state.money == 10430, "Food price mismatch");
        });
    walk(240, 255);
    pressE();
    walk(174, 230);
    pressE();
    approachMira();
    once(
        "unique girl",
        () -> {
          shot("12-mira");
          click(130, 161);
        });
    once("personal conversation", () -> click(240, 161));
    closeScene();
    approachMira();
    once("invite", () -> click(130, 187));
    once("free date", () -> click(240, 162));
    closeScene();
    once("date planned", () -> check(world().state.plannedGirl.equals("mira"), "No planned date"));
    walk(246, 262);
    pressE();
    walk(900, 447);
    pressE();
    add("settled park", () -> timer > .3f);
    once(
        "park",
        () -> {
          check(world().state.map.equals("park"), "Wrong park");
          shot("13-park");
        });
    walk(240, 153);
    pressE();
    waitFor(Mode.SOCIAL);
    once(
        "date choices",
        () -> {
          shot("14-date");
          click(240, 162);
        });
    closeScene();
    once(
        "relationship changed",
        () -> check(world().state.relation("mira").dates == 1, "Date not counted"));
    once("map", () -> tap(Keys.M));
    waitFor(Mode.MAP);
    once(
        "actual map",
        () -> {
          shot("15-map");
          tap(Keys.ESCAPE);
        });
    waitFor(Mode.WORLD);
    walk(240, 255);
    pressE();
    walk(174, 230);
    pressE();
    walk(160, 77);
    pressE();
    walk(266, 164);
    pressE();
    waitFor(Mode.REST);
    once("sleep", () -> click(240, 127));
    closeScene();
    once(
        "new morning",
        () -> {
          check(world().state.day() >= 2 && world().state.energy > 90, "Sleep failed");
          tap(Keys.B);
        });
    waitFor(Mode.BUDGET);
    once(
        "budget",
        () -> {
          shot("16-budget");
          tap(Keys.ESCAPE);
        });
    waitFor(Mode.WORLD);
    once(
        "pause",
        () -> {
          tap(Keys.ESCAPE);
          pauseTime = world().state.gameMinutes;
        });
    waitFor(Mode.PAUSE);
    add("paused time", () -> timer > .3);
    once(
        "pause respects time",
        () -> {
          check(world().state.gameMinutes == pauseTime, "Pause consumed time");
          click(240, 199);
        });
    waitFor(Mode.MENU);
    once("continue", () -> tap(Keys.ENTER));
    waitFor(Mode.WORLD);
    once(
        "persistence",
        () -> {
          check(
              world().state.paidJobs.size() == 1 && world().state.relation("mira").dates == 1,
              "Progress lost");
          check(world().state.introSeen, "Intro lost");
        });
    // Explicit late-relationship fixture, separate from the real first-day route above.
    once(
        "consent fixture",
        () -> {
          var s = world().state;
          var r = s.relation("mira");
          s.gameMinutes = 3 * 1440 + 20 * 60;
          s.partnerId = "mira";
          r.met = true;
          r.affinity = 75;
          r.trust = 75;
          r.dates = 4;
          r.committedDay = 3;
          refreshState();
        });
    pressE();
    waitFor(Mode.REST);
    once("night invitation", () -> click(240, 179));
    waitFor(Mode.SOCIAL);
    once(
        "consent",
        () -> {
          shot("17-consent");
          click(120, 216);
        });
    waitFor(Mode.NIGHT);
    once(
        "fade to black",
        () -> {
          shot("18-night");
          click(240, 225);
        });
    closeScene();
    once(
        "morning memory",
        () ->
            check(
                world().state.relation("mira").sharedNights == 1,
                "Night consent outcome not saved"));
    once("contacts", () -> tap(Keys.R));
    waitFor(Mode.CONTACTS);
    once(
        "contacts portraits",
        () -> {
          shot("19-contacts");
          tap(Keys.ESCAPE);
        });
    waitFor(Mode.WORLD);
    once("small window", () -> Gdx.graphics.setWindowedMode(960, 540));
    add("resize", () -> timer > .3);
    once("960 shot", () -> shot("20-window-960"));
    once("large window", () -> Gdx.graphics.setWindowedMode(1920, 1080));
    add("resize large", () -> timer > .3);
    once("1080 shot", () -> shot("21-window-1080"));
    add(
        "soak",
        () -> {
          check(!world().blocked(world().state.x, world().state.y), "Invalid world position");
          return total >= Float.parseFloat(System.getProperty("ctrl.soakSeconds", "0"));
        });
    once(
        "complete",
        () -> {
          System.out.println(
              "CHAPTER_SMOKE_PASS frames="
                  + frames
                  + " seconds="
                  + total
                  + " first day + labelled consent fixture");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    frames++;
    float dt = Gdx.graphics.getDeltaTime();
    timer += dt;
    total += dt;
    if (steps.isEmpty()) return;
    var step = steps.peek();
    current = step.label;
    try {
      if (step.run.getAsBoolean()) {
        System.out.println("GUI: " + current);
        steps.remove();
        timer = 0;
      } else if (timer > 60 && !current.equals("soak"))
        throw new AssertionError("Timeout " + current);
    } catch (Throwable e) {
      failed = true;
      shot("FAILED");
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
