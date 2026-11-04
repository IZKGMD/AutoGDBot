package life.ctrl.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
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
    for (int i = 0; i < text.length(); i++)
      Gdx.input.getInputProcessor().keyTyped(text.charAt(i));
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
      if (actor instanceof Window w && w.isVisible()
          && w.getTitleLabel().getText().toString().equals(name)) return w;
    }
    throw new AssertionError("Missing native VisUI window: " + name);
  }

  private void screenshot(String name) {
    canvas().screenshot(System.getProperty("ctrl.shots") + "/pinux-" + (++nextShot) + "-" + name + ".png");
  }

  @Override
  public void create() {
    super.create();
    // A test fixture: enter the computer directly, without navigating the prologue.
    lifeScreens().openCode();
    for (int i = 0; i < 4; i++) steps.add(() -> {});
    steps.add(() -> screenshot("desktop"));
    steps.add(() -> {
      // Verify an actual native Scene2D shortcut click at 1440x810.
      click(30, 27);
      if (lifeScreens().pcNativeStage().getRoot().isVisible())
        throw new AssertionError("Scene2D desktop shortcut did not open Astra Code");
    });
    steps.add(() -> screenshot("astra-code"));
    steps.add(() -> click(220, 122)); // Open first contract.
    steps.add(() -> screenshot("job-brief"));
    steps.add(() -> click(220, 161)); // Choose algorithm for the cash-register job.
    steps.add(() -> {
      if (lifeScreens().codePage != 2) throw new AssertionError("Astra Code editor not opened");
      screenshot("editor-typing");
    });
    steps.add(() -> { for (int i = 0; i < 80; i++) tap(Keys.A); });
    steps.add(() -> click(176, 235)); // Run the real ByteScript tests.
    steps.add(() -> {
      if (lifeScreens().report == null || lifeScreens().report.passed())
        throw new AssertionError("Expected the initial typo to fail tests");
      screenshot("editor-first-error");
    });
    steps.add(() -> { for (int i = 0; i < 80; i++) tap(Keys.S); });
    steps.add(() -> click(176, 235)); // Re-test corrected code.
    steps.add(() -> {
      if (lifeScreens().report == null || !lifeScreens().report.passed())
        throw new AssertionError("Automatic typed fix did not pass tests");
      screenshot("editor-fixed-tests");
    });
    steps.add(() -> tap(Keys.F2));
    steps.add(() -> {
      Window terminal = findWindow("Terminal");
      if (!terminal.isMovable() || !terminal.isResizable())
        throw new AssertionError("The native terminal must support drag and resize");
      type("help\r");
    });
    steps.add(() -> screenshot("visui-terminal"));
    steps.add(() -> {
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
    steps.add(() -> tap(Keys.F3));
    steps.add(() -> tap(Keys.F4));
    steps.add(() -> tap(Keys.F6));
    steps.add(() -> screenshot("multitasking"));
    steps.add(() -> tap(Keys.ESCAPE));
    steps.add(() -> tap(Keys.F1));
    steps.add(() -> screenshot("return-to-code"));
    steps.add(() -> {
      if (mode() != Mode.IDE) throw new AssertionError("Shell unexpectedly left IDE mode");
      System.out.println("PINUX_SMOKE_PASS: shell, code, terminal input, window drag, files, mail, settings, focus");
      Gdx.app.exit();
    });
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
