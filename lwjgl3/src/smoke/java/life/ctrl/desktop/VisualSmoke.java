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
    canvas().screenshot(System.getProperty("ctrl.shots") + "/v4-" + n + ".png");
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
        int sx = canvas().offsetX + x * canvas().scale,
            sy = p.getHeight() - 1 - (canvas().offsetY + 135 * canvas().scale);
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
    step("menu", () -> shot("menu"));
    step("new", () -> tap(Keys.ENTER));
    step(
        "create",
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> tap(Keys.ENTER));
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
    step(
        "gear",
        () -> {
          world().state.upgrades.addAll(List.of("ssd", "chair"));
          fixture("room", 22, 240, 190);
        });
    step("settle", () -> {});
    step("room upgraded", () -> shot("upgraded"));
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
              "VISUAL_SMOKE_PASS: all seven maps, day/night, camera edges, upgrades, UI,"
                  + " 960/1280/1920, no lighting outside cutaways");
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
