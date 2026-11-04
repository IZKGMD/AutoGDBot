package life.ctrl.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import life.ctrl.CtrlLife;
import life.ctrl.model.Coding;

/** Visual regression matrix: every IDE page, windowed/maximized, across four resolutions. */
public final class AstraLayoutSmoke extends CtrlLife {
  record Step(String name, Runnable run) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  boolean failed;
  String current = "boot";
  int captures;

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
    var g = new AstraLayoutSmoke();
    new Lwjgl3Application(g, cfg);
    if (g.failed) throw new AssertionError(g.current);
  }

  void step(String n, Runnable r) {
    steps.add(new Step(n, r));
  }

  void tap(int key) {
    var i = Gdx.input.getInputProcessor();
    i.keyDown(key);
    i.keyUp(key);
  }

  Window code() {
    for (Actor a : lifeScreens().pcNativeStage().getActors())
      if (a instanceof Window w
          && w.isVisible()
          && w.getTitleLabel().getText().toString().equals("Astra Code")) return w;
    throw new AssertionError("Missing Astra Code window");
  }

  Actor find(Actor a, String text) {
    if (a instanceof Label l && l.getText().toString().equals(text)) return l;
    if (a instanceof Group g)
      for (Actor child : g.getChildren()) {
        Actor result = find(child, text);
        if (result != null) return result;
      }
    return null;
  }

  void click(Actor a) {
    if (a == null) throw new AssertionError("Missing control");
    Vector2 p = a.localToStageCoordinates(new Vector2(a.getWidth() / 2, a.getHeight() / 2));
    int x = Math.round(p.x), y = Gdx.graphics.getHeight() - Math.round(p.y);
    var i = Gdx.input.getInputProcessor();
    i.touchDown(x, y, 0, Input.Buttons.LEFT);
    i.touchUp(x, y, 0, Input.Buttons.LEFT);
  }

  void validate(Actor a) {
    if (!a.isVisible() || a.getWidth() <= 0 || a.getHeight() <= 0) return;
    if (a instanceof Label || a instanceof TextButton) {
      boolean scrolled = false;
      for (Actor parent = a.getParent(); parent != null; parent = parent.getParent())
        if (parent instanceof ScrollPane) scrolled = true;
      if (!scrolled) {
        Vector2 p = a.localToStageCoordinates(new Vector2());
        Window w = code();
        if (p.x < w.getX() - 2
            || p.y < w.getY() - 2
            || p.x + a.getWidth() > w.getRight() + 2
            || p.y + a.getHeight() > w.getTop() + 2)
          throw new AssertionError(
              "Control outside window: "
                  + a
                  + " x="
                  + p.x
                  + " right="
                  + (p.x + a.getWidth())
                  + " windowRight="
                  + w.getRight());
      }
    }
    if (a instanceof Label l) {
      l.validate();
      float textHeight = l.getGlyphLayout().height;
      if (l.getGlyphLayout().width > l.getWidth() + 2)
        throw new AssertionError(
            "Text exceeds allocated width: "
                + l.getText()
                + " text="
                + l.getGlyphLayout().width
                + " bounds="
                + l.getWidth());
      if (textHeight > l.getHeight() + 2)
        throw new AssertionError(
            "Text exceeds allocated height: "
                + l.getText()
                + " text="
                + textHeight
                + " bounds="
                + l.getHeight());
    }
    if (a instanceof TextButton b) {
      b.validate();
      Label l = b.getLabel();
      float available = b.getHeight() - b.getPadTop() - b.getPadBottom();
      if (l.getGlyphLayout().height > available + 2)
        throw new AssertionError(
            "Caption overflows button: "
                + b.getText()
                + " text="
                + l.getGlyphLayout().height
                + " available="
                + available);
      if (b.getWidth() < l.getGlyphLayout().width + b.getPadLeft() + b.getPadRight() - 2)
        throw new AssertionError("Caption exceeds button width: " + b.getText());
    }
    if (a instanceof Group g) for (Actor child : g.getChildren()) validate(child);
  }

  void shot(String name) {
    code().validate();
    validate(code());
    Actor exit = find(code(), "⏻ Выйти из ПК");
    if (exit == null) throw new AssertionError("Missing exit action");
    Vector2 point = exit.localToStageCoordinates(new Vector2(0, 0));
    if (point.y < code().getY() || point.y + exit.getHeight() > code().getTop())
      throw new AssertionError("Sidebar exit is clipped outside window");
    canvas().screenshot(System.getProperty("ctrl.shots") + "/layout-" + name + ".png");
    captures++;
  }

  void fixture(int page) {
    var s = world().state;
    var j = lifeScreens().data.job("total");
    lifeScreens().report = null;
    if (page == 1 || page == 2 || page == 3) {
      s.activeJob = "";
      s.jobStage = 0;
      Coding.accept(s, j, 0, true, true);
      if (page == 3) {
        Coding.completeTyping(s);
        lifeScreens().report = Coding.test(s, j);
      }
    }
    if (page == 4 || page == 5 || page == 6) {
      Coding.repair(s, j, 0);
      lifeScreens().report = Coding.test(s, j);
      lifeScreens().previewInput = 2;
      if (page == 6) Coding.commit(s);
    }
    if (page == 5) {
      click(find(code(), "Лаборатория"));
      return;
    }
    if (page == 11) {
      if (!s.shipped.contains("total")) s.shipped.add("total");
    }
    lifeScreens().codePage = page;
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
        "open computer",
        () -> {
          lifeScreens().openCode();
          tap(Keys.F1);
        });
    step("settle", () -> {});
    for (int width : new int[] {960, 1920, 1440, 1280}) {
      int height = width == 1280 ? 800 : width * 9 / 16;
      step("resize " + width, () -> Gdx.graphics.setWindowedMode(width, height));
      step("settle resize", () -> {});
      for (boolean max : new boolean[] {false, true}) {
        String key = width + (max ? "-max" : "-window");
        step(
            "window fixture " + key,
            () -> {
              if (max) {
                click(find(code(), "[]"));
              } else
                code()
                    .setBounds(
                        24,
                        76,
                        Math.min(1180, width == 960 ? 800 : width - 48),
                        width == 960 ? 360 : height - 120);
            });
        step("settle geometry", () -> {});
        for (int page : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10}) {
          step("page fixture " + key + "-" + page, () -> fixture(page));
          step("settle layout", () -> {});
          step("capture " + key + "-" + page, () -> shot(key + "-page-" + page));
          if (page == 2) {
            step(
                "compiler error fixture",
                () -> {
                  Coding.completeTyping(world().state);
                  lifeScreens().report =
                      Coding.test(world().state, lifeScreens().data.job("total"));
                });
            step("settle error", () -> {});
            step("error capture", () -> shot(key + "-editor-error"));
            step(
                "passed tests fixture",
                () -> {
                  Coding.repair(world().state, lifeScreens().data.job("total"), 0);
                  lifeScreens().report =
                      Coding.test(world().state, lifeScreens().data.job("total"));
                });
            step("settle pass", () -> {});
            step("pass capture", () -> shot(key + "-editor-pass"));
          }
        }
        if (max) {
          step("restore", () -> click(find(code(), "[]")));
          step("settle restore", () -> {});
        }
      }
    }
    step(
        "done",
        () -> {
          System.out.println(
              "ASTRA_LAYOUT_PASS: "
                  + captures
                  + " checked captures; 12 pages, four sizes, restored/maximized, glyph and button"
                  + " bounds");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    if (timer < .12f || steps.isEmpty()) return;
    timer = 0;
    var step = steps.remove();
    current = step.name;
    try {
      step.run.run();
      System.out.println("LAYOUT: " + current);
    } catch (Throwable e) {
      failed = true;
      canvas().screenshot(System.getProperty("ctrl.shots") + "/layout-FAILED.png");
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
