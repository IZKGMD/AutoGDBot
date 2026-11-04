package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;

/**
 * Actual input, measured paragraphs, all authored dates and the illustrated social screen matrix.
 */
public final class DateSmoke extends CtrlLife {
  record Step(String name, Runnable run) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  int settleFrames;
  boolean failed;
  String current = "boot";
  int captures;

  public static void main(String[] args) throws Exception {
    System.setProperty("ctrl.uiAudit", "true");
    Path dir = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(dir);
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    for (String f : List.of("save.json", "save.backup.json", "settings.json"))
      Files.deleteIfExists(dir.resolve(f));
    var cfg = new Lwjgl3ApplicationConfiguration();
    cfg.setWindowedMode(1440, 810);
    cfg.setForegroundFPS(60);
    cfg.useVsync(false);
    cfg.disableAudio(true);
    var game = new DateSmoke();
    new Lwjgl3Application(game, cfg);
    if (game.failed) throw new AssertionError(game.current);
  }

  void step(String n, Runnable r) {
    steps.add(new Step(n, r));
  }

  void tap(int key) {
    var i = Gdx.input.getInputProcessor();
    i.keyDown(key);
    i.keyUp(key);
  }

  void click(int x, int y) {
    int sx = canvas().screenX(x), sy = canvas().screenY(y);
    var i = Gdx.input.getInputProcessor();
    i.touchDown(sx, sy, 0, Input.Buttons.LEFT);
    i.touchUp(sx, sy, 0, Input.Buttons.LEFT);
  }

  void check(boolean v, String m) {
    if (!v) throw new AssertionError(m);
  }

  void shot(String n) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/dates-" + n + ".png");
    captures++;
  }

  void fixture(String id, int episode) {
    var s = world().state;
    var g = lifeScreens().data.girl(id);
    s.date = new DateStory.Session();
    s.plannedGirl = "";
    s.plannedVenue = "";
    s.plannedDay = -1;
    s.partnerId = "";
    s.map = "park";
    s.x = 240;
    s.y = 190;
    s.gameMinutes += 1440;
    s.energy = 95;
    s.money = 10000;
    s.hunger = 80;
    var r = s.relation(id);
    r.met = true;
    r.affinity = 45;
    r.trust = 45;
    r.dates = episode;
    r.lastDateDay = -1;
    Social.invite(s, g, "park");
    refreshState();
    lifeScreens().openDate();
  }

  @Override
  public void create() {
    super.create();
    step("new", () -> tap(Keys.ENTER));
    step(
        "create",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> tap(Keys.ENTER));
    for (String id : List.of("mira", "asya", "nika"))
      for (int episode = 0; episode < 3; episode++) {
        int index = episode;
        String key = id + "-" + episode;
        step("fixture " + key, () -> fixture(id, index));
        step("settle", () -> {});
        step(
            "arrival " + key,
            () -> {
              shot(key + "-arrival");
              click(300, 165);
            });
        step("started", () -> check(world().state.date.active(), "Date did not start"));
        for (int beat = 0; beat < 3; beat++) {
          int moment = beat;
          step(
              "choice " + key + "-" + beat,
              () -> {
                check(!world().state.date.waiting, "Still awaiting response");
                shot(key + "-choice-" + moment);
                click(300, 206);
              });
          step(
              "response " + key + "-" + beat,
              () -> {
                check(world().state.date.waiting, "Response not shown");
                shot(key + "-response-" + moment);
                click(300, 215);
              });
        }
        step(
            "completed " + key,
            () -> {
              check(!world().state.date.active(), "Date still active");
              check(world().state.relation(id).dates == index + 1, "Date not counted");
              check(mode() == Mode.SCENE, "Summary not shown");
              shot(key + "-summary");
            });
        for (int i = 0; i < 4; i++)
          step(
              "read summary",
              () -> {
                if (mode() == Mode.SCENE) click(300, 247);
              });
      }
    step(
        "reload",
        () -> {
          try {
            var saved =
                new SaveStore(SaveStore.defaultDirectory(), life.ctrl.content.Content.load())
                    .load()
                    .state();
            for (String id : List.of("mira", "asya", "nika")) {
              check(saved.relation(id).knownDetails.size() == 9, "Details not saved");
              check(saved.relation(id).dateExperiences.size() == 3, "Experiences not saved");
            }
          } catch (java.io.IOException e) {
            throw new RuntimeException(e);
          }
        });
    for (int width : new int[] {960, 1280, 1920}) {
      step(
          "resize " + width,
          () -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      step("settle resize", () -> {});
      for (String id : List.of("mira", "asya", "nika")) {
        step(
            "social fixture",
            () -> {
              var s = world().state;
              var g = lifeScreens().data.girl(id);
              s.map = g.homeMap;
              s.x = 240;
              s.y = 190;
              s.partnerId = id;
              s.date = new DateStory.Session();
              s.plannedGirl = "";
              s.plannedVenue = "";
              var r = s.relation(id);
              r.arcStep = 0;
              r.decisions.clear();
              r.promise = "";
              r.brokenPromise = "";
              r.lastArcDay = -1;
              r.lastActivityDay = -1;
              r.lastPlanDay = -1;
              lifeScreens().openSocial(id, false);
              for (var item : lifeScreens().data.items)
                if (item.kind.equals("gift")) s.items.put(item.id, 1);
              refreshState();
            });
        for (String page :
            List.of(
                "home",
                "talk",
                "gift",
                "invite",
                "journey",
                "chapter",
                "activities",
                "memories",
                "details",
                "plans",
                "meeting",
                "commit",
                "breakup",
                "night")) {
          step(
              "page " + page,
              () -> {
                lifeScreens().socialPage = page;
                resetSelection();
              });
          step(
              "capture " + width + "-" + id + "-" + page,
              () -> shot(width + "-" + id + "-" + page));
        }
      }
      step("contacts", () -> switchMode(Mode.CONTACTS));
      step("contacts capture", () -> shot(width + "-contacts"));
    }
    step(
        "paid fixture",
        () -> {
          fixture("asya", 0);
          var s = world().state;
          s.plannedVenue = "cafe";
          s.map = "cafe";
          refreshState();
          lifeScreens().openDate();
        });
    step("paid start", () -> click(300, 165));
    step(
        "paid check",
        () -> {
          check(world().state.money == 9150, "Paid date did not charge exactly once");
          tap(Keys.ESCAPE);
        });
    step("resume", () -> lifeScreens().openDate());
    step(
        "resume capture",
        () -> {
          check(world().state.date.active(), "Paused date lost");
          shot("paid-resume");
        });
    step(
        "done",
        () -> {
          System.out.println(
              "DATES_SMOKE_PASS: "
                  + captures
                  + " captures; nine dates, 27 decisions, durable details, social screens"
                  + " 960/1280/1920, paid resume, measured text");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    try {
      super.render();
      timer += Gdx.graphics.getDeltaTime();
      settleFrames++;
      if (timer < .12f || settleFrames < 3 || steps.isEmpty()) return;
      timer = 0;
      settleFrames = 0;
      var step = steps.remove();
      current = step.name;
      step.run.run();
      System.out.println("DATES: " + current);
    } catch (Throwable e) {
      failed = true;
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
