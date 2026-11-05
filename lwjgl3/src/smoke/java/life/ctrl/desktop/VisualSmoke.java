package life.ctrl.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.Pixmap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import life.ctrl.CtrlLife;

/** Explicit screenshot fixtures for every scene, lighting state and supported window size. */
public final class VisualSmoke extends CtrlLife {
  record Step(String name, Runnable run) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  boolean failed;
  int shots;
  String current = "boot";

  public static void main(String[] args) throws Exception {
    Path dir = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(dir);
    for (String f : List.of("save.json", "save.backup.json", "settings.json"))
      Files.deleteIfExists(dir.resolve(f));
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    var cfg = new Lwjgl3ApplicationConfiguration();
    cfg.setWindowedMode(1440, 810);
    cfg.setForegroundFPS(60);
    cfg.useVsync(false);
    cfg.disableAudio(true);
    var g = new VisualSmoke();
    new Lwjgl3Application(g, cfg);
    if (g.failed) throw new AssertionError(g.current);
  }

  void step(String n, Runnable r) {
    steps.add(new Step(n, r));
  }

  void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  void shot(String n) {
    int error = Gdx.gl.glGetError();
    if (error != 0) throw new AssertionError("OpenGL error at " + n + ": " + error);
    canvas().screenshot(System.getProperty("ctrl.shots") + "/v4-" + n + ".png");
    shots++;
  }

  void fixture(String map, int hour, float x, float y) {
    var s = world().state;
    s.map = map;
    s.x = x;
    s.y = y;
    s.gameMinutes = 2 * 1440 + hour * 60;
    s.introSeen = true;
    s.health = 85;
    s.hunger = 75;
    s.energy = 80;
    switchMode(Mode.WORLD);
    new life.ctrl.model.World(life.ctrl.content.Content.load(), s);
    refreshState();
  }

  void noLightInVoid() {
    Pixmap p =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      for (int x : new int[] {80, 400}) {
        int sx = canvas().screenX(x), sy = p.getHeight() - 1 - (canvas().screenY(135));
        int rgb = p.getPixel(sx, sy) >>> 8;
        if (rgb != 0x0e1a24)
          throw new AssertionError(
              "Effect leaked into scene void at " + x + ": " + Integer.toHexString(rgb));
      }
    } finally {
      p.dispose();
    }
  }

  @Override
  public void create() {
    super.create();
    if (!materialLightingAvailable())
      throw new AssertionError("Material lighting shader did not compile");
    step("menu", () -> shot("menu"));
    step("new", () -> tap(Keys.ENTER));
    step(
        "create",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> tap(Keys.ENTER));
    // Let the actual new-game toast expire before the material reference captures.
    for (int i = 0; i < 8; i++) step("settle welcome", () -> {});
    for (String map : List.of("room", "stair", "cafe", "diner", "workshop", "park", "street")) {
      for (int hour : new int[] {12, 22}) {
        String label = map + "-" + hour;
        step(
            "fixture " + label,
            () ->
                fixture(
                    map,
                    hour,
                    map.equals("street") ? 174 : 240,
                    map.equals("stair") ? 120 : map.equals("street") ? 250 : 190));
        step("settle " + label, () -> {});
        step(
            "capture " + label,
            () -> {
              shot(label);
              if (List.of("room", "cafe", "diner", "workshop").contains(map)) noLightInVoid();
            });
      }
    }
    for (float x : new float[] {470, 814, 900}) {
      step("street camera " + x, () -> fixture("street", 22, x, x == 900 ? 430 : 250));
      step("settle", () -> {});
      step("street capture", () -> shot("street-" + (int) x));
    }
    step("behind fridge", () -> fixture("diner", 12, 350, 133));
    step("settle occlusion", () -> {});
    step("fridge behind capture", () -> shot("fridge-behind"));
    step("in front of fridge", () -> fixture("diner", 12, 350, 160));
    step("settle occlusion", () -> {});
    step("fridge front capture", () -> shot("fridge-front"));
    step("behind tree", () -> fixture("street", 12, 735, 225));
    step("settle tree", () -> {});
    step("tree behind capture", () -> shot("tree-behind"));
    step("in front of tree", () -> fixture("street", 12, 735, 305));
    step("settle tree", () -> {});
    step("tree front capture", () -> shot("tree-front"));
    step(
        "unpacked room",
        () -> {
          world().state.unpacked = true;
          fixture("room", 12, 240, 190);
        });
    step("settle boxes", () -> {});
    step("boxes capture", () -> shot("room-unpacked"));
    step(
        "gear",
        () -> {
          world().state.upgrades.addAll(List.of("ssd", "chair"));
          fixture("room", 22, 240, 190);
        });
    step("settle", () -> {});
    step("room upgraded", () -> shot("upgraded"));
    step("upgraded desk approach", () -> fixture("room", 22, 187, 156));
    step("settle desk approach", () -> {});
    step("upgraded desk capture", () -> shot("upgraded-at-pc"));
    step(
        "keepsakes fixture",
        () -> {
          world().state.relation("mira").dateExperiences.put("mira-margins", 1);
          world().state.relation("asya").dateExperiences.put("asya-contact", 1);
          world().state.relation("nika").dateExperiences.put("nika-offline", 1);
          fixture("room", 22, 240, 190);
        });
    step("settle keepsakes", () -> {});
    step(
        "keepsakes capture",
        () -> {
          shot("room-keepsakes");
          noLightInVoid();
        });
    step(
        "clear park",
        () -> {
          fixture("park", 18, 240, 190);
          world().state.gameMinutes = 1440 + 18 * 60;
          refreshState();
        });
    step("settle clear park", () -> {});
    step("clear park capture", () -> shot("park-clear-evening"));
    step("effects settings", () -> switchMode(Mode.SETTINGS));
    step(
        "reduce effects",
        () -> {
          for (int i = 0; i < 3; i++) tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    step(
        "verify reduced effects",
        () -> {
          if (!life.ctrl.model.Settings.load(Path.of(System.getProperty("ctrl.dataDir")))
              .reducedEffects)
            throw new AssertionError("Reduced effects were not enabled through settings");
        });
    step("reduced street", () -> fixture("street", 22, 470, 250));
    step("settle reduced", () -> {});
    step("reduced capture", () -> shot("street-reduced-effects"));
    step("effects settings", () -> switchMode(Mode.SETTINGS));
    step(
        "restore effects",
        () -> {
          for (int i = 0; i < 3; i++) tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    step(
        "verify full effects",
        () -> {
          if (life.ctrl.model.Settings.load(Path.of(System.getProperty("ctrl.dataDir")))
              .reducedEffects) throw new AssertionError("Full effects were not restored");
        });
    for (int width : new int[] {960, 1280, 1920}) {
      step(
          "world resize " + width,
          () -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      for (String map : List.of("room", "stair", "cafe", "diner", "workshop", "park", "street")) {
        step(
            "world fixture " + width + " " + map,
            () ->
                fixture(
                    map, 22, map.equals("street") ? 470 : 240, map.equals("street") ? 250 : 190));
        step("settle world size", () -> {});
        step(
            "world size capture",
            () -> {
              shot(map + "-size-" + width);
              if (List.of("room", "cafe", "diner", "workshop").contains(map)) noLightInVoid();
            });
      }
    }
    step("return park", () -> fixture("park", 18, 240, 190));
    step("open map", () -> tap(Keys.M));
    step(
        "map geometry",
        () -> {
          if (mode() != Mode.MAP) throw new AssertionError("Map did not open");
          shot("map");
          tap(Keys.ESCAPE);
        });
    step("workbench", () -> lifeScreens().openCode());
    step("workbench capture", () -> shot("workbench"));
    step(
        "stress long label",
        () -> {
          lifeScreens().codePage = 7;
          world().state.lastClientMessage =
              "Работа принята. Спасибо за внимательность к деталям и за быстрый результат!";
        });
    step("career capture", () -> shot("career"));
    for (int width : new int[] {960, 1280, 1920}) {
      step(
          "resize " + width,
          () -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      step("settle resize", () -> {});
      step("size " + width, () -> shot("ui-" + width));
    }
    step(
        "done",
        () -> {
          System.out.println(
              "VISUAL_SMOKE_PASS: "
                  + shots
                  + " screenshots; all seven maps, day/night, camera edges, upgrades, UI,"
                  + " 960/1280/1920; shader active; no GL errors; full/reduced effects; no lighting"
                  + " outside cutaways");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    if (timer < .6f || steps.isEmpty()) return;
    timer = 0;
    var s = steps.remove();
    current = s.name;
    try {
      s.run.run();
      System.out.println("VISUAL: " + current);
    } catch (Throwable e) {
      failed = true;
      shot("FAILED");
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
