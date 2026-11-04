package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.model.*;

/** Explicit mid-career fixtures; all workbench actions use actual mouse input and rendering. */
public final class WorkbenchSmoke extends CtrlLife {
  record Step(String name, Runnable run) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  boolean failed;
  String current = "boot";

  public static void main(String[] args) throws Exception {
    Path dir = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(dir);
    for (String name : List.of("save.json", "save.backup.json", "settings.json"))
      Files.deleteIfExists(dir.resolve(name));
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    var cfg = new Lwjgl3ApplicationConfiguration();
    cfg.setWindowedMode(1440, 810);
    cfg.setForegroundFPS(60);
    cfg.useVsync(false);
    cfg.disableAudio(Boolean.getBoolean("ctrl.noAudio"));
    var game = new WorkbenchSmoke();
    new Lwjgl3Application(game, cfg);
    if (game.failed) throw new AssertionError(game.current);
  }

  void step(String name, Runnable r) {
    steps.add(new Step(name, r));
  }

  void click(int x, int y) {
    Gdx.input
        .getInputProcessor()
        .touchDown(
            canvas().offsetX + x * canvas().scale,
            canvas().offsetY + y * canvas().scale,
            0,
            Input.Buttons.LEFT);
  }

  void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }

  void shot(String name) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/v3-" + name + ".png");
  }

  void close() {
    step(
        "close result",
        () -> {
          check(mode() == Mode.SCENE, "Missing scene");
          click(240, 237);
        });
  }

  void project(int stage, int patch) {
    step("open side project " + stage, () -> lifeScreens().openCode());
    step("project tab", () -> click(280, 209));
    step("project brief", () -> click(240, 202));
    step("start stage", () -> click(240, 161));
    step("stage tests", () -> click(180, 237));
    step("stage debug", () -> click(280, 237));
    step("stage patch", () -> click(240, 161 + patch * 28));
    step("stage re-test", () -> click(180, 237));
    step(
        "stage preview",
        () -> {
          check(lifeScreens().report.passed(), "Stage tests failed");
          click(280, 237);
        });
    step("ship stage", () -> click(240, 210));
    close();
    step(
        "stage saved",
        () -> check(Career.projectStage(world().state) == stage, "Wrong side-project stage"));
  }

  @Override
  public void create() {
    super.create();
    step("new game", () -> tap(Keys.ENTER));
    step(
        "create",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> tap(Keys.ENTER));
    step(
        "career fixture",
        () -> {
          var s = world().state;
          s.codingXp = 8;
          s.completedOrders = 8;
          s.gameMinutes = 3 * 1440 + 480;
          s.rentThroughDay = 10;
          lifeScreens().openCode();
        });
    step("advanced listings", () -> click(70, 209));
    step(
        "rotated offers",
        () -> {
          shot("contracts");
          click(240, 122);
        });
    step("profile brief", () -> click(240, 161));
    step("slow program tests", () -> click(180, 237));
    step(
        "slow program rejected",
        () -> {
          check(
              lifeScreens().report.compiled() && !lifeScreens().report.passed(),
              "Slow correct code passed");
          shot("slow-code");
          click(340, 66);
        });
    step("custom input", () -> click(365, 86));
    step("probe one", () -> click(90, 237));
    step("custom input two", () -> click(420, 86));
    step("probe two", () -> click(90, 237));
    step(
        "novel cases counted",
        () -> {
          check(world().state.customCases.size() == 2, "Probe bonus count");
          shot("lab-before");
          click(380, 237);
        });
    step("profile patch view", () -> click(280, 237));
    step("select multiplication", () -> click(240, 189));
    step("git open", () -> click(425, 66));
    step("commit", () -> click(300, 66));
    close();
    step(
        "two revisions",
        () -> {
          check(world().state.revisions.size() == 2, "Missing commit");
          shot("git");
          click(120, 90);
        });
    close();
    step("back from git", () -> click(240, 240));
    step("old code tests", () -> click(180, 237));
    step(
        "rollback actually changed execution",
        () -> {
          check(!lifeScreens().report.passed(), "Rollback did not restore slow code");
          click(425, 66);
        });
    step("restore optimized commit", () -> click(130, 110));
    close();
    step("back restored", () -> click(240, 240));
    step("optimized tests", () -> click(180, 237));
    step(
        "optimized profile",
        () -> {
          check(lifeScreens().report.passed(), "Optimization failed");
          click(340, 66);
        });
    step(
        "profile screenshot",
        () -> {
          shot("lab-after");
          click(380, 237);
        });
    step("preview optimized", () -> click(280, 237));
    step("deliver optimized", () -> click(240, 210));
    close();
    step(
        "correct pay",
        () -> {
          check(world().state.money == 11550, "Expected 1450 + 100 bonus");
          lifeScreens().openCode();
        });
    step("upgrade tab", () -> click(390, 209));
    step("buy SSD", () -> click(390, 100));
    close();
    step("buy chair", () -> click(390, 174));
    close();
    step(
        "owned upgrades",
        () -> {
          check(world().state.money == 4550, "Upgrade costs");
          check(world().state.upgrades.size() == 2, "Upgrades absent");
          shot("upgrades");
          click(340, 240);
        });
    step(
        "upgraded desk",
        () -> {
          world().state.gameMinutes = 4 * 1440 + 21 * 60;
          refreshState();
        });
    step("night room", () -> shot("room-night"));
    project(1, 0);
    project(2, 1);
    project(3, 2);
    step("launched bot page", () -> lifeScreens().openCode());
    step("launched bot tab", () -> click(280, 209));
    step(
        "bot screenshot",
        () -> {
          shot("bot-live");
          click(340, 240);
        });
    step("save complete career", () -> tap(Keys.F5));
    step("pause", () -> tap(Keys.ESCAPE));
    step("return menu", () -> click(240, 190));
    step("continue", () -> tap(Keys.ENTER));
    step(
        "career survives load",
        () -> {
          check(mode() == Mode.WORLD, "Did not continue");
          check(Career.projectStage(world().state) == 3, "Project lost");
          check(world().state.upgrades.size() == 2, "Upgrades lost");
          lifeScreens().openCode();
        });
    step("career tab", () -> click(175, 209));
    step(
        "portfolio screenshot",
        () -> {
          shot("career");
          click(200, 213);
        });
    step(
        "portfolio list",
        () -> {
          check(lifeScreens().codePage == 11, "Portfolio did not open");
          shot("portfolio");
          click(200, 99);
        });
    step(
        "portfolio feedback",
        () -> {
          check(mode() == Mode.SCENE, "Project feedback did not open");
          click(240, 240);
        });
    step("back to career", () -> click(300, 230));
    step("leave career", () -> click(340, 240));
    step(
        "street daylight fixture",
        () -> {
          var s = world().state;
          s.map = "street";
          s.x = 174;
          s.y = 242;
          s.gameMinutes = 4 * 1440 + 12 * 60;
          refreshState();
        });
    step("camera settles", () -> {});
    step("street day", () -> shot("street-day"));
    step("street evening fixture", () -> world().state.gameMinutes = 4 * 1440 + 21 * 60);
    step("street night", () -> shot("street-night"));
    step(
        "complete",
        () -> {
          System.out.println(
              "WORKBENCH_SMOKE_PASS: mid-career fixture, profiler, custom inputs, commit/rollback,"
                  + " payout, gear, all project stages, persistence, lighting");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    if (timer < .25f || steps.isEmpty()) return;
    timer = 0;
    var step = steps.remove();
    current = step.name;
    try {
      step.run.run();
      System.out.println("WORKBENCH: " + current);
    } catch (Throwable e) {
      failed = true;
      shot("FAILED");
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
