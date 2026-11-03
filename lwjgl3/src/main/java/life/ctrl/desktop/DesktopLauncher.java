package life.ctrl.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import life.ctrl.CtrlLife;

public final class DesktopLauncher {
  public static void main(String[] args) {
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("CTRL+LIFE — Нулевой билд");
    config.setWindowedMode(1440, 810);
    config.setWindowIcon("art/icon32.png", "art/icon64.png", "art/icon128.png");
    config.setWindowSizeLimits(960, 540, -1, -1);
    config.useVsync(true);
    config.setForegroundFPS(60);
    config.setIdleFPS(15);
    if (Boolean.getBoolean("ctrl.noAudio")) config.disableAudio(true);
    var game = new CtrlLife();
    config.setWindowListener(
        new Lwjgl3WindowAdapter() {
          @Override
          public void focusLost() {
            game.focusChanged(false);
          }

          @Override
          public void focusGained() {
            game.focusChanged(true);
          }
        });
    new Lwjgl3Application(game, config);
  }
}
