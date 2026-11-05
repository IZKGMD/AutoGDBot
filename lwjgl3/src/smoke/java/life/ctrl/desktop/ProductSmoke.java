package life.ctrl.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import life.ctrl.CtrlLife;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;

/** Fresh-player work cycle, then labelled fixtures for complete inventory and chapter UI. */
public final class ProductSmoke extends CtrlLife {
  record Step(String name, Runnable action) {}

  final ArrayDeque<Step> steps = new ArrayDeque<>();
  float timer;
  int frames, captures;
  boolean failed;
  String current;

  public static void main(String[] args) throws Exception {
    System.setProperty("ctrl.uiAudit", "true");
    var dir = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(dir);
    Files.createDirectories(Path.of(System.getProperty("ctrl.shots")));
    for (String f : List.of("save.json", "save.backup.json", "settings.json"))
      Files.deleteIfExists(dir.resolve(f));
    var config = new Lwjgl3ApplicationConfiguration();
    config.setWindowedMode(1440, 810);
    config.disableAudio(true);
    config.useVsync(false);
    config.setForegroundFPS(60);
    var game = new ProductSmoke();
    new Lwjgl3Application(game, config);
    if (game.failed) throw new AssertionError(game.current);
  }

  void step(String n, Runnable r) {
    steps.add(new Step(n, r));
  }

  void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  void key(int k) {
    var i = Gdx.input.getInputProcessor();
    i.keyDown(k);
    i.keyUp(k);
  }

  void pixel(int x, int y) {
    var i = Gdx.input.getInputProcessor();
    i.touchDown(canvas().screenX(x), canvas().screenY(y), 0, 0);
    i.touchUp(canvas().screenX(x), canvas().screenY(y), 0, 0);
  }

  Window code() {
    for (var a : lifeScreens().pcNativeStage().getActors())
      if (a instanceof Window w
          && w.isVisible()
          && w.getTitleLabel().getText().toString().equals("Astra Code")) return w;
    throw new AssertionError("Code window");
  }

  Actor find(Actor a, String text) {
    if (a instanceof Label l && l.getText().toString().equals(text)) return l;
    if (a instanceof Group g)
      for (var child : g.getChildren()) {
        var found = find(child, text);
        if (found != null) return found;
      }
    return null;
  }

  void reveal(String text) {
    var a = find(code(), text);
    check(a != null, "Missing " + text);
    for (var parent = a.getParent(); parent != null; parent = parent.getParent())
      if (parent instanceof ScrollPane pane) {
        var p = a.localToAscendantCoordinates(pane.getWidget(), new Vector2());
        pane.scrollTo(p.x, p.y, a.getWidth(), a.getHeight(), false, true);
        pane.updateVisualScroll();
        return;
      }
  }

  void click(String text) {
    var a = find(code(), text);
    check(a != null, "Missing " + text);
    var p = a.localToStageCoordinates(new Vector2(a.getWidth() / 2, a.getHeight() / 2));
    var i = Gdx.input.getInputProcessor();
    i.touchDown(Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y), 0, 0);
    i.touchUp(Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y), 0, 0);
  }

  void type() {
    for (int i = 0; i < 100; i++) key(Keys.A);
  }

  void shot(String n) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/product-" + n + ".png");
    captures++;
  }

  int walkingSteps;

  void travel(String map, String prop) {
    check(++walkingSteps < 1200, "Walking route stalled: " + map + "/" + prop);
    for (int k : new int[] {Keys.W, Keys.A, Keys.S, Keys.D}) Gdx.input.getInputProcessor().keyUp(k);
    var target =
        Arrays.stream(world().content.map(map).objects)
            .filter(x -> x.id.equals(prop))
            .findFirst()
            .orElseThrow();
    var local = Navigation.localGoal(world(), new Navigation.Goal(map, target.x, target.y, "", ""));
    var state = world().state;
    if (Math.hypot(state.x - local.x(), state.y - local.y()) < 21) {
      if (state.map.equals(map)) {
        walkingSteps = 0;
        return;
      }
      key(Keys.E);
      check(mode() == Mode.WORLD, "Door triggered unexpected mode " + mode());
    } else {
      var route = Navigation.route(world(), local.x(), local.y());
      check(!route.isEmpty(), "No walking path");
      var point = route.get(Math.min(2, route.size() - 1));
      float dx = point[0] - state.x, dy = point[1] - state.y;
      if (Math.abs(dx) > 1) Gdx.input.getInputProcessor().keyDown(dx > 0 ? Keys.D : Keys.A);
      if (Math.abs(dy) > 1) Gdx.input.getInputProcessor().keyDown(dy > 0 ? Keys.S : Keys.W);
    }
    steps.addFirst(new Step("walk " + map + "/" + prop, () -> travel(map, prop)));
  }

  @Override
  public void create() {
    super.create();
    step("new", () -> key(Keys.ENTER));
    step(
        "create",
        () -> {
          key(Keys.DOWN);
          key(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) step("intro", () -> key(Keys.ENTER));
    step("walk to computer", () -> travel("room", "pc"));
    step(
        "computer",
        () -> {
          key(Keys.E);
        });
    step(
        "open code",
        () -> {
          check(mode() == Mode.IDE, "PC interaction");
          key(Keys.F1);
        });
    step("contract", () -> click("Открыть заказ"));
    step("algorithm", () -> click(lifeScreens().data.job("total").algorithms[0]));
    step("type draft", this::type);
    step("test draft", () -> click("▶ Run / Test"));
    step("patches", () -> click("Исправления"));
    step("patch", () -> click(lifeScreens().data.job("total").patches[0]));
    step("type patch", this::type);
    step("test patch", () -> click("▶ Run / Test"));
    step(
        "passed",
        () -> {
          check(lifeScreens().report.passed(), "Fresh player tests");
          click("Предпросмотр");
        });
    step("reveal delivery", () -> reveal("Сдать клиенту • 650 ₽"));
    step("deliver", () -> click("Сдать клиенту • 650 ₽"));
    step(
        "payment",
        () -> {
          check(world().state.money == 10650, "Fresh-player payment");
          check(mode() == Mode.SCENE, "Payment receipt");
          shot("first-payment");
        });
    step("receipt close", () -> pixel(240, 237));
    step("walk to diner", () -> travel("diner", "counter"));
    step("diner interaction", () -> key(Keys.E));
    step(
        "first soup",
        () -> {
          check(mode() == Mode.SHOP, "Diner counter");
          pixel(200, 92);
        });
    step("close purchase", () -> pixel(240, 237));
    step("leave shop", () -> key(Keys.ESCAPE));
    step("open pockets", () -> key(Keys.I));
    step("eat soup", () -> pixel(200, 76));
    step(
        "first meal",
        () -> {
          check(world().state.ateFirstMeal, "Bought food was not eaten");
          shot("first-meal");
          pixel(240, 237);
        });
    step("leave pockets", () -> key(Keys.ESCAPE));
    for (int width : new int[] {960, 1280, 1920}) {
      step(
          "resize",
          () -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      step(
          "shop",
          () -> {
            switchMode(Mode.SHOP);
            lifeScreens().shopPage = 0;
          });
      step("food capture", () -> shot(width + "-food"));
      step("buy food", () -> pixel(200, 92));
      step(
          "purchased",
          () -> {
            check(world().state.count("soup") > 0, "Soup purchase");
            pixel(240, 237);
          });
      step("gifts", () -> pixel(120, 207));
      step("gifts capture", () -> shot(width + "-gifts"));
      step(
          "owned items fixture",
          () -> {
            for (var item : lifeScreens().data.items)
              if (world().state.count(item.id) == 0) Life.buy(world().state, item);
            switchMode(Mode.BAG);
          });
      step("bag capture", () -> shot(width + "-bag"));
      step("bag page", () -> pixel(240, 205));
      step("bag second capture", () -> shot(width + "-bag-last"));
      step("journal", () -> switchMode(Mode.JOURNAL));
      step("progress open", () -> pixel(240, 132));
      for (int page = 0; page < 4; page++) {
        int p = page;
        step("progress capture", () -> shot(width + "-progress-" + p));
        if (page < 3) step("progress next", () -> pixel(130, 194));
      }
      step("progress back", () -> pixel(340, 194));
    }
    step(
        "long receipt",
        () ->
            lifeScreens()
                .scene(
                    "Письмо клиента",
                    ("Проверка длинного сообщения: каждая строка должна остаться доступной"
                                + " читателю. ")
                            .repeat(35)
                        + "КОНЕЦ ПИСЬМА.",
                    Mode.WORLD));
    for (int i = 0; i < 15; i++) {
      int p = i;
      step(
          "read long receipt",
          () -> {
            if (mode() == Mode.SCENE) {
              shot("letter-" + p);
              pixel(240, 237);
            }
          });
    }
    step("letter finished", () -> check(mode() == Mode.WORLD, "Long message inaccessible"));
    step(
        "completed fixture",
        () -> {
          var s = world().state;
          s.gameMinutes = 7 * 1440 + 480;
          s.rentThroughDay = 10;
          s.money = 12000;
          s.ateFirstMeal = true;
          s.firstDateFinished = true;
          s.relation("mira").met = true;
          s.codingXp = 8;
          s.shipped.addAll(List.of("board", "remind", "support"));
          s.upgrades.addAll(List.of("ssd", "chair"));
          LifeProgress.record(s);
          check(s.chapterOneFinished, "Complete fixture requirements");
          refreshState();
          switchMode(Mode.JOURNAL);
        });
    step("completed progress", () -> pixel(240, 132));
    step("completed capture", () -> shot("completed"));
    step("finale", () -> pixel(240, 227));
    step(
        "finale capture",
        () -> {
          check(mode() == Mode.SCENE, "Chapter finale");
          shot("finale");
          pixel(240, 237);
        });
    step(
        "reload",
        () -> {
          try {
            var loaded =
                new SaveStore(SaveStore.defaultDirectory(), world().content).load().state();
            check(loaded.chapterOneFinished, "Completion lost");
            check(loaded.completedOrders == 1, "Paid order lost");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    step("new session menu", () -> switchMode(Mode.MENU));
    step("new session choose", () -> pixel(120, 220));
    step(
        "new session confirm",
        () -> {
          check(mode() == Mode.CONFIRM_NEW, "Overwrite confirmation missing");
          pixel(160, 175);
        });
    step(
        "new session create",
        () -> {
          check(mode() == Mode.CREATE, "Create missing");
          pixel(170, 212);
        });
    for (int i = 0; i < 3; i++) step("new session intro", () -> key(Keys.ENTER));
    step("new session journal", () -> key(Keys.J));
    step("new session progress", () -> pixel(240, 132));
    step(
        "new session clean",
        () -> {
          check(
              !world().state.chapterOneFinished
                  && world().state.money == 10000
                  && LifeProgress.count(world().state) == 0,
              "Previous session leaked");
          shot("new-session");
        });
    step(
        "done",
        () -> {
          System.out.println(
              "PRODUCT_SMOKE_PASS: "
                  + captures
                  + " captures; fresh-player contract to payment, purchases, all owned items,"
                  + " chapter pages, long messages and persistence");
          Gdx.app.exit();
        });
  }

  @Override
  public void render() {
    try {
      super.render();
      timer += Gdx.graphics.getDeltaTime();
      if (++frames < 3 || timer < .15 || steps.isEmpty()) return;
      timer = 0;
      frames = 0;
      var step = steps.remove();
      current = step.name();
      step.action().run();
      System.out.println("PRODUCT: " + current);
    } catch (Throwable e) {
      failed = true;
      e.printStackTrace();
      steps.clear();
      Gdx.app.exit();
    }
  }
}
