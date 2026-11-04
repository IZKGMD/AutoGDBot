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
import life.ctrl.model.WorkRhythm;
import life.ctrl.model.RelationshipJourney;
import life.ctrl.save.SaveStore;

/** Visual regression matrix: every IDE page, windowed/maximized, across four resolutions. */
public final class AstraLayoutSmoke extends CtrlLife {
  record Step(String name, Runnable run) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  boolean failed;
  String current = "boot";
  int captures, settleFrames;

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

  void reveal(String text) {
    Actor a = find(code(), text);
    if (a == null) throw new AssertionError("Missing control " + text);
    for (Actor parent = a.getParent(); parent != null; parent = parent.getParent()) {
      if (parent instanceof ScrollPane pane) {
        Vector2 p = a.localToAscendantCoordinates(pane.getWidget(), new Vector2());
        pane.scrollTo(p.x, p.y, a.getWidth(), a.getHeight(), false, true);
        pane.updateVisualScroll();
        return;
      }
    }
  }

  void pixelClick(int x, int y) {
    var input = Gdx.input.getInputProcessor();
    input.touchDown(canvas().screenX(x), canvas().screenY(y), 0, Input.Buttons.LEFT);
    input.touchUp(canvas().screenX(x), canvas().screenY(y), 0, Input.Buttons.LEFT);
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
    step("rhythm open", () -> lifeScreens().codePage = 12);
    step("rhythm reveal sprint", () -> reveal("Рывок · время 75% · усталость +34"));
    step("rhythm select sprint", () -> click(find(code(), "Рывок · время 75% · усталость +34")));
    step("rhythm sprint persisted", () -> {
      if (WorkRhythm.pace(world().state) != WorkRhythm.Pace.SPRINT) throw new AssertionError("Pace click");
      try {
        var saved = new SaveStore(SaveStore.defaultDirectory(), world().content).load().state();
        if (!saved.workPace.equals("SPRINT")) throw new AssertionError("Pace not saved");
      } catch (java.io.IOException e) { throw new RuntimeException(e); }
      world().state.workFatigue = 60;
    });
    step("rhythm reveal break", () -> reveal("Перерыв без экрана · 30 минут"));
    step("rhythm take break", () -> click(find(code(), "Перерыв без экрана · 30 минут")));
    step("rhythm break result", () -> {
      if (world().state.workFatigue != 40) throw new AssertionError("Break click");
      shot("rhythm-break");
    });
    step("dismiss break notice", () -> click(code().findActor("notice-dismiss")));
    step("rhythm reveal steady", () -> reveal("Обычный · время 100% · усталость +18"));
    step("rhythm restore steady", () -> click(find(code(), "Обычный · время 100% · усталость +18")));
    step("rhythm verify steady", () -> {
      if (WorkRhythm.pace(world().state) != WorkRhythm.Pace.STEADY) throw new AssertionError("Steady click");
    });
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
        for (int page : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 10, 12}) {
          step("page fixture " + key + "-" + page, () -> fixture(page));
          step("settle layout", () -> {});
          step("capture " + key + "-" + page, () -> shot(key + "-page-" + page));
          if (page == 4 || page == 12) {
            String caption = page == 4 ? "Темп работы и перерыв →" : "Сверх резерва: 6560 ₽.";
            step("reveal lower controls", () -> reveal(caption));
            step("lower capture", () -> shot(key + "-page-" + page + "-lower"));
          }
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
    for (int width : new int[] {960, 1280, 1920}) {
      step("life resize", () -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      step("rest fixture", () -> switchMode(Mode.REST));
      step("rest capture", () -> canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-" + width + "-rest.png"));
      step("journal fixture", () -> {
        var s = world().state;
        s.plannedGirl = "mira"; s.plannedVenue = "cafe"; s.plannedDay = s.day();
        for (var girl : lifeScreens().data.girls) {
          var r = s.relation(girl.id); r.met = true;
          r.promise = RelationshipJourney.DATA.arc(girl.id).activities[0].id;
          r.promiseDue = s.day() + 2;
        }
        switchMode(Mode.JOURNAL);
      });
      step("journal route capture", () -> canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-" + width + "-journal.png"));
      step("journal agenda", () -> pixelClick(250, 240));
      step("journal agenda capture", () -> canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-" + width + "-agenda.png"));
      step("journal next", () -> pixelClick(130, 214));
      step("journal next capture", () -> canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-" + width + "-agenda-next.png"));
      step("journal last page", () -> pixelClick(130, 214));
      step("journal last capture", () -> canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-" + width + "-agenda-last.png"));
      step("journal route", () -> pixelClick(340, 214));
    }
    step("nap setup", () -> {
      var s = world().state; s.gameMinutes = 470; s.energy = 0; s.workFatigue = 90;
      switchMode(Mode.REST);
    });
    step("nap click", () -> pixelClick(220, 153));
    step("nap result", () -> {
      if (mode() != Mode.SCENE || world().state.minutes() != 560 || Math.abs(world().state.energy - 28.8) > .01)
        throw new AssertionError("Nap did not consume 90 minutes");
      canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-nap-result.png");
      world().state.gameMinutes = 470; world().state.energy = 0;
      switchMode(Mode.REST);
    });
    step("short sleep click", () -> pixelClick(220, 127));
    step("short sleep result", () -> {
      if (mode() != Mode.SCENE || world().state.minutes() != 480 || Math.abs(world().state.energy - 3.2) > .01)
        throw new AssertionError("Short sleep exploit returned");
      canvas().screenshot(System.getProperty("ctrl.shots") + "/rhythm-short-sleep.png");
    });
    step(
        "done",
        () -> {
          System.out.println(
              "ASTRA_LAYOUT_PASS: "
                  + captures
                  + " checked captures; 13 pages, four sizes, restored/maximized, glyph and button"
                  + " bounds");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    if (++settleFrames < 3 || timer < .12f || steps.isEmpty()) return;
    settleFrames = 0;
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
