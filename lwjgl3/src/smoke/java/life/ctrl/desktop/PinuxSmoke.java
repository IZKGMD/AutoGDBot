package life.ctrl.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import life.ctrl.CtrlLife;

/** Exercises a live Pinux desktop with GLFW input, rendering and window transitions. */
public final class PinuxSmoke extends CtrlLife {
  private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
  private float timer;
  private boolean failed;
  private int nextShot;

  public static void main(String[] args) throws Exception {
    Path save = Path.of(System.getProperty("ctrl.dataDir"));
    Files.createDirectories(save);
    for (String name : new String[] {"save.json", "save.backup.json", "settings.json"})
      Files.deleteIfExists(save.resolve(name));
    Path shots = Path.of(System.getProperty("ctrl.shots"));
    Files.createDirectories(shots);
    var config = new Lwjgl3ApplicationConfiguration();
    config.setWindowedMode(1440, 810);
    config.setForegroundFPS(60);
    config.useVsync(false);
    config.disableAudio(true);
    var test = new PinuxSmoke();
    new Lwjgl3Application(test, config);
    if (test.failed) throw new AssertionError("Pinux Astra window smoke failed");
  }

  private void tap(int key) {
    Gdx.input.getInputProcessor().keyDown(key);
    Gdx.input.getInputProcessor().keyUp(key);
  }

  private void type(String text) {
    for (int i = 0; i < text.length(); i++) Gdx.input.getInputProcessor().keyTyped(text.charAt(i));
  }

  private void mouse(int operation, int x, int y) {
    int sx = canvas().offsetX + x * canvas().scale;
    int sy = canvas().offsetY + y * canvas().scale;
    switch (operation) {
      case 0 -> Gdx.input.getInputProcessor().touchDown(sx, sy, 0, Input.Buttons.LEFT);
      case 1 -> Gdx.input.getInputProcessor().touchDragged(sx, sy, 0);
      case 2 -> Gdx.input.getInputProcessor().touchUp(sx, sy, 0, Input.Buttons.LEFT);
      default -> throw new AssertionError("Unknown pointer operation");
    }
  }

  private void click(int x, int y) {
    mouse(0, x, y);
    mouse(2, x, y);
  }

  private void mouseAbsolute(int operation, int sx, int sy) {
    switch (operation) {
      case 0 -> Gdx.input.getInputProcessor().touchDown(sx, sy, 0, Input.Buttons.LEFT);
      case 1 -> Gdx.input.getInputProcessor().touchDragged(sx, sy, 0);
      case 2 -> Gdx.input.getInputProcessor().touchUp(sx, sy, 0, Input.Buttons.LEFT);
    }
  }

  private Window findWindow(String name) {
    for (Actor actor : lifeScreens().pcNativeStage().getRoot().getChildren()) {
      if (actor instanceof Window w
          && w.isVisible()
          && w.getTitleLabel().getText().toString().equals(name)) return w;
    }
    throw new AssertionError("Missing native VisUI window: " + name);
  }

  private TextButton findButton(Actor actor, String text) {
    if (actor instanceof TextButton b && b.getText().toString().equals(text)) return b;
    if (actor instanceof Group g)
      for (Actor child : g.getChildren()) {
        TextButton result = findButton(child, text);
        if (result != null) return result;
      }
    return null;
  }

  private void nativeAction(String text) {
    TextButton b = findButton(findWindow("Astra Code"), text);
    if (b == null || b.isDisabled()) throw new AssertionError("Missing native action: " + text);
    b.fire(new ChangeListener.ChangeEvent());
  }

  private com.badlogic.gdx.scenes.scene2d.ui.Label findLabel(Actor actor, String text) {
    if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label l
        && l.getText().toString().equals(text)) return l;
    if (actor instanceof Group g)
      for (Actor child : g.getChildren()) {
        com.badlogic.gdx.scenes.scene2d.ui.Label result = findLabel(child, text);
        if (result != null) return result;
      }
    return null;
  }

  private void nativeClickLabel(String title) {
    com.badlogic.gdx.scenes.scene2d.ui.Label l = findLabel(findWindow("Astra Code"), title);
    if (l == null) throw new AssertionError("Missing native navigation: " + title);
    Vector2 p = l.localToStageCoordinates(new Vector2(l.getWidth() / 2, l.getHeight() / 2));
    mouseAbsolute(0, Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y));
    mouseAbsolute(2, Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y));
  }

  private void nativeClick(String text) {
    TextButton b = findButton(findWindow("Astra Code"), text);
    if (b == null || b.isDisabled()) throw new AssertionError("Missing native button: " + text);
    Vector2 p = b.localToStageCoordinates(new Vector2(b.getWidth() / 2, b.getHeight() / 2));
    mouseAbsolute(0, Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y));
    mouseAbsolute(2, Math.round(p.x), Gdx.graphics.getHeight() - Math.round(p.y));
  }

  private void screenshot(String name) {
    canvas()
        .screenshot(
            System.getProperty("ctrl.shots") + "/pinux-" + (++nextShot) + "-" + name + ".png");
  }

  @Override
  public void create() {
    super.create();
    // Start a real isolated session so F5 and persistence exercise production guards.
    steps.add(() -> tap(Keys.ENTER));
    steps.add(
        () -> {
          tap(Keys.DOWN);
          tap(Keys.ENTER);
        });
    for (int i = 0; i < 3; i++) steps.add(() -> tap(Keys.ENTER));
    steps.add(() -> lifeScreens().openCode());
    for (int i = 0; i < 4; i++) steps.add(() -> {});
    steps.add(() -> screenshot("desktop"));
    // Rofi-style launcher: mouse open, text filter and Escape close.
    steps.add(
        () -> {
          mouseAbsolute(0, 47, 13);
          mouseAbsolute(2, 47, 13);
        });
    steps.add(() -> screenshot("launcher"));
    steps.add(() -> type("term"));
    steps.add(() -> screenshot("launcher-filter"));
    steps.add(() -> tap(Keys.ESCAPE));
    steps.add(
        () -> {
          // Verify an actual native Scene2D shortcut click at 1440x810.
          click(30, 27);
          if (!lifeScreens().isLegacyEditorOpen())
            throw new AssertionError("Scene2D desktop shortcut did not open Astra Code");
        });
    steps.add(() -> screenshot("astra-code"));
    steps.add(() -> nativeClick("Открыть заказ")); // Real native hit test.
    steps.add(() -> screenshot("job-brief"));
    steps.add(() -> nativeAction(lifeScreens().data.job(world().state.activeJob).algorithms[0]));
    steps.add(
        () -> {
          if (lifeScreens().codePage != 2) throw new AssertionError("Astra Code editor not opened");
          screenshot("editor-typing");
          // Inspector is an actual responsive tool pane, not a decorative mockup.
          if (findLabel(
                  findWindow("Astra Code"),
                  "CONTRACT  /  " + world().state.activeJob.toUpperCase(java.util.Locale.ROOT))
              == null) throw new AssertionError("Astra Code contract inspector missing");
        });
    steps.add(() -> nativeAction("▤ Details"));
    steps.add(() -> screenshot("editor-inspector-collapsed"));
    steps.add(() -> nativeAction("▤ Details"));
    // While Astra Code is focused, launcher text input must never type ByteScript.
    steps.add(
        () -> {
          mouseAbsolute(0, 47, 13);
          mouseAbsolute(2, 47, 13);
        });
    steps.add(() -> screenshot("launcher-over-code"));
    steps.add(
        () -> {
          int before = world().state.typedCount;
          tap(Keys.F);
          type("fi");
          if (world().state.typedCount != before)
            throw new AssertionError("Launcher typing leaked into ByteScript");
          tap(Keys.ESCAPE);
        });
    steps.add(
        () -> {
          for (int i = 0; i < 80; i++) tap(Keys.A);
        });
    steps.add(() -> nativeAction("▶ Run / Test")); // Run ByteScript tests.
    steps.add(
        () -> {
          if (lifeScreens().report == null || lifeScreens().report.passed())
            throw new AssertionError("Expected the initial typo to fail tests");
          screenshot("editor-first-error");
        });
    steps.add(
        () -> {
          for (int i = 0; i < 80; i++) tap(Keys.S);
        });
    steps.add(() -> nativeAction("▶ Run / Test")); // Re-test corrected code.
    steps.add(
        () -> {
          if (lifeScreens().report == null || !lifeScreens().report.passed())
            throw new AssertionError("Automatic typed fix did not pass tests");
          screenshot("editor-fixed-tests");
        });
    steps.add(() -> nativeAction("▷ Превью"));
    steps.add(() -> screenshot("code-preview"));
    steps.add(() -> nativeAction("← Вернуться к исходнику"));
    steps.add(() -> nativeClickLabel("Лаборатория"));
    steps.add(() -> screenshot("code-profiler"));
    steps.add(() -> nativeAction("← К исходнику"));
    steps.add(() -> nativeClickLabel("Git / версии"));
    steps.add(() -> screenshot("code-versions"));
    steps.add(() -> tap(Keys.F2));
    steps.add(
        () -> {
          Window terminal = findWindow("Terminal");
          if (!terminal.isMovable() || !terminal.isResizable())
            throw new AssertionError("The native terminal must support drag and resize");
          type("help\r");
        });
    steps.add(() -> screenshot("visui-terminal"));
    steps.add(
        () -> {
          Window terminal = findWindow("Terminal");
          float before = terminal.getX();
          int sx = Math.round(terminal.getX() + 110);
          int sy = Gdx.graphics.getHeight() - Math.round(terminal.getTop() - 17);
          mouseAbsolute(0, sx, sy);
          mouseAbsolute(1, sx + 75, sy + 26);
          mouseAbsolute(2, sx + 75, sy + 26);
          if (Math.abs(terminal.getX() - before) < 20)
            throw new AssertionError("Window title drag did not move VisWindow");
        });
    steps.add(() -> screenshot("visui-terminal-dragged"));
    steps.add(
        () -> {
          Window terminal = findWindow("Terminal");
          float oldWidth = terminal.getWidth(), oldHeight = terminal.getHeight();
          int sx = Math.round(terminal.getX() + terminal.getWidth() - 5);
          int sy = Gdx.graphics.getHeight() - Math.round(terminal.getY() + 5);
          mouseAbsolute(0, sx, sy);
          mouseAbsolute(1, sx + 64, sy + 30);
          mouseAbsolute(2, sx + 64, sy + 30);
          float dx = terminal.getWidth() - oldWidth, dy = terminal.getHeight() - oldHeight;
          if (Math.abs(dx) < 12 && Math.abs(dy) < 12)
            throw new AssertionError(
                "Native window resize handle inactive. Initial="
                    + oldWidth
                    + "x"
                    + oldHeight
                    + ", final="
                    + terminal.getWidth()
                    + "x"
                    + terminal.getHeight()
                    + ", stage-point="
                    + sx
                    + ","
                    + sy);
        });
    steps.add(() -> screenshot("visui-terminal-resized"));
    steps.add(() -> tap(Keys.F3));
    steps.add(() -> tap(Keys.F4));
    steps.add(() -> tap(Keys.F6));
    steps.add(() -> screenshot("multitasking"));
    steps.add(() -> tap(Keys.ESCAPE));
    steps.add(() -> tap(Keys.F1));
    steps.add(() -> screenshot("return-to-code"));
    steps.add(
        () -> {
          tap(Keys.F2);
          if (lifeScreens().pcNativeStage().getKeyboardFocus() == null
              || !lifeScreens()
                  .pcNativeStage()
                  .getKeyboardFocus()
                  .getClass()
                  .getSimpleName()
                  .equals("VisTextField")) throw new AssertionError("Terminal lost keyboard focus");
          type("echo focus-restored\r");
          tap(Keys.UP);
          Actor field = lifeScreens().pcNativeStage().getKeyboardFocus();
          if (!fieldText(field).equals("echo focus-restored"))
            throw new AssertionError("History navigation failed");
          tap(Keys.DOWN);
          if (!fieldText(field).isEmpty())
            throw new AssertionError("History did not return to empty prompt");
        });
    steps.add(
        () -> {
          tap(Keys.F6);
          TextButton theme = findButton(findWindow("Settings"), "Тема: холодная");
          if (theme == null) throw new AssertionError("Theme state missing");
          theme.fire(new ChangeListener.ChangeEvent());
          TextButton motion = findButton(findWindow("Settings"), "Движение: плавное");
          if (motion == null) throw new AssertionError("Motion state missing");
          motion.fire(new ChangeListener.ChangeEvent());
          tap(Keys.F5);
          if (!world().state.pinux.warmTheme || !world().state.pinux.reducedMotion)
            throw new AssertionError("Preferences did not update");
        });
    for (int width : new int[] {960, 1280, 1920}) {
      steps.add(() -> Gdx.graphics.setWindowedMode(width, width == 1280 ? 800 : width * 9 / 16));
      steps.add(() -> {});
      steps.add(
          () -> {
            for (Actor a : lifeScreens().pcNativeStage().getRoot().getChildren())
              if (a instanceof Window w && w.isVisible()) {
                if (w.getX() < 0
                    || w.getY() < 0
                    || w.getRight() > Gdx.graphics.getWidth() + 1
                    || w.getTop() > Gdx.graphics.getHeight() + 1)
                  throw new AssertionError(
                      "Window outside viewport: " + w.getTitleLabel().getText());
              }
            screenshot("viewport-" + width);
          });
    }
    steps.add(
        () -> {
          tap(Keys.F5);
          try {
            var saved =
                new life.ctrl.save.SaveStore(
                        life.ctrl.save.SaveStore.defaultDirectory(),
                        life.ctrl.content.Content.load())
                    .load()
                    .state();
            if (!saved.pinux.warmTheme
                || !saved.pinux.reducedMotion
                || saved.pinux.windows.isEmpty())
              throw new AssertionError("Desktop session was not saved");
          } catch (java.io.IOException e) {
            throw new RuntimeException(e);
          }
        });
    steps.add(
        () -> {
          gameExitAndReopen();
          tap(Keys.F2);
          if (!world().state.pinux.reducedMotion || !world().state.pinux.warmTheme)
            throw new AssertionError("Preferences reset on re-entry");
        });
    steps.add(() -> screenshot("restored-session"));
    steps.add(
        () -> {
          if (mode() != Mode.IDE) throw new AssertionError("Shell unexpectedly left IDE mode");
          System.out.println(
              "PINUX_SMOKE_PASS: shell, code, terminal input, window drag, files, mail, settings,"
                  + " focus");
          Gdx.app.exit();
        });
  }

  private String fieldText(Actor field) {
    try {
      return (String) field.getClass().getMethod("getText").invoke(field);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private void gameExitAndReopen() {
    switchMode(Mode.WORLD);
    refreshState();
    lifeScreens().openCode();
  }

  @Override
  public void render() {
    super.render();
    timer += Gdx.graphics.getDeltaTime();
    if (steps.isEmpty() || timer < .15f) return;
    timer = 0;
    try {
      steps.removeFirst().run();
    } catch (Throwable t) {
      failed = true;
      t.printStackTrace();
      Gdx.app.exit();
    }
  }
}
