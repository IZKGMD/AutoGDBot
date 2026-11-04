package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.model.*;

/** Explicit relationship fixtures; player actions are sent through the real InputProcessor. */
public final class RelationshipSmoke extends CtrlLife {
  record Step(String title, Runnable action) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  int settleFrames;
  boolean failed;

  public static void main(String[] args) throws Exception {
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
    var g = new RelationshipSmoke();
    new Lwjgl3Application(g, cfg);
    if (g.failed) throw new AssertionError("Relationship UI scenario failed");
  }

  void step(String title, Runnable run) {
    steps.add(new Step(title, run));
  }

  void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  void click(int x, int y) {
    int sx = canvas().screenX(x), sy = canvas().screenY(y);
    Gdx.input.getInputProcessor().touchDown(sx, sy, 0, Input.Buttons.LEFT);
    Gdx.input.getInputProcessor().touchUp(sx, sy, 0, Input.Buttons.LEFT);
  }

  void check(boolean condition, String reason) {
    if (!condition) throw new AssertionError(reason);
  }

  void shot(String name) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/relationships-" + name + ".png");
  }

  void continueScene() {
    step(
        "continue scene",
        () -> {
          check(mode() == Mode.SCENE, "Missing scene");
          click(300, 247);
        });
  }

  @Override
  public void create() {
    super.create();
    step("new session", () -> tap(Keys.ENTER));
    step(
        "create player",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> tap(Keys.ENTER));
    for (String id : List.of("mira", "asya", "nika")) {
      step(
          id + " early relationship fixture",
          () -> {
            var s = world().state;
            var g = lifeScreens().data.girl(id);
            s.map = g.homeMap;
            s.x = 240;
            s.y = 190;
            s.partnerId = "";
            s.plannedGirl = "";
            s.plannedVenue = "";
            s.gameMinutes += 1440;
            s.energy = 90;
            s.money = 10000;
            Social.meet(s, g);
            var r = s.relation(id);
            r.dates = 1;
            r.affinity = 25;
            r.trust = 25;
            refreshState();
            lifeScreens().openSocial(id, false);
          });
      step(
          id + " home",
          () -> {
            shot(id + "-home");
            click(230, 210);
          });
      step(
          id + " journey",
          () -> {
            check(lifeScreens().socialPage.equals("journey"), "Missing journey");
            shot(id + "-journey");
            click(230, 174);
          });
      step(
          id + " chapter",
          () -> {
            check(lifeScreens().socialPage.equals("chapter"), "Missing chapter");
            shot(id + "-chapter-1");
            click(300, 177);
          });
      step(
          id + " durable choice",
          () -> check(world().state.relation(id).arcStep == 1, "Choice did not advance"));
      continueScene();
      step(id + " choose shared activity", () -> click(390, 174));
      step(
          id + " activity UI",
          () -> {
            shot(id + "-activities");
            click(300, 139);
          });
      step(
          id + " activity completed",
          () ->
              check(
                  world().state.relation(id).activities.size() == 1, "Activity did not complete"));
      continueScene();
      step(id + " back home", () -> click(300, 246));
      step(id + " history hub", () -> click(230, 210));
      step(id + " journal", () -> click(230, 202));
      step(
          id + " memory screenshot",
          () -> {
            check(lifeScreens().socialPage.equals("memories"), "Journal missing");
            shot(id + "-memory");
            click(420, 246);
          });
      step(
          id + " meeting fixture",
          () -> {
            var g = lifeScreens().data.girl(id);
            Social.invite(world().state, g, "park");
            lifeScreens().openSocial(id, true);
          });
      step(id + " meeting controls", () -> click(390, 210));
      step(
          id + " meeting screenshot",
          () -> {
            shot(id + "-meeting");
            click(300, 158);
          });
      continueScene();
      step(
          id + " tomorrow",
          () -> {
            check(world().state.plannedDay == world().state.day() + 1, "Tomorrow not scheduled");
            click(300, 214);
          });
      continueScene();
      step(
          id + " cancel",
          () -> check(world().state.plannedGirl.isEmpty(), "Meeting cancellation failed"));
    }
    step(
        "save",
        () -> {
          switchMode(Mode.WORLD);
          tap(Keys.F5);
        });
    step(
        "reload durable choices",
        () -> {
          try {
            var s =
                new life.ctrl.save.SaveStore(
                        life.ctrl.save.SaveStore.defaultDirectory(),
                        life.ctrl.content.Content.load())
                    .load()
                    .state();
            for (String id : List.of("mira", "asya", "nika")) {
              check(s.relation(id).arcStep == 1, "Chapter not saved");
              check(!s.relation(id).memories.isEmpty(), "Memories not saved");
            }
          } catch (java.io.IOException e) {
            throw new RuntimeException(e);
          }
          lifeScreens().openSocial("mira", true);
          lifeScreens().socialPage = "journey";
        });
    for (int width : new int[] {960, 1920}) {
      step("resize " + width, () -> Gdx.graphics.setWindowedMode(width, width * 9 / 16));
      step("settle", () -> {});
      step("capture", () -> shot("ui-" + width));
    }
    step(
        "complete",
        () -> {
          System.out.println(
              "RELATIONSHIPS_SMOKE_PASS: three authored arcs, choices, shared activities, memories,"
                  + " reschedule/cancel, durable save, 960/1920");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    settleFrames++;
    if (timer < .25f || settleFrames < 3 || steps.isEmpty()) return;
    settleFrames = 0;
    timer = 0;
    var step = steps.remove();
    try {
      step.action.run();
      System.out.println("RELATIONSHIPS: " + step.title);
    } catch (Throwable e) {
      failed = true;
      shot("FAILED");
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
