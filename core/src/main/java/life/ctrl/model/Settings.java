package life.ctrl.model;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import life.ctrl.save.SaveStore;

public final class Settings {
  public float music = .35f, sfx = .6f;
  public boolean reducedEffects = false, fullscreen = false;
  public int scale = 3;
  public int up = Keys.W,
      down = Keys.S,
      left = Keys.A,
      right = Keys.D,
      interact = Keys.E,
      journal = Keys.J;

  public int key(int index) {
    return switch (index) {
      case 0 -> up;
      case 1 -> down;
      case 2 -> left;
      case 3 -> right;
      case 4 -> interact;
      default -> journal;
    };
  }

  public boolean bind(int index, int key) {
    if (key == Keys.ESCAPE
        || key == Keys.ENTER
        || key == Keys.F11
        || key == Keys.F5
        || key == Keys.I
        || key == Keys.M
        || key == Keys.TAB
        || key == Keys.UNKNOWN
        || key == Keys.UP
        || key == Keys.DOWN
        || key == Keys.LEFT
        || key == Keys.RIGHT) return false;
    for (int i = 0; i < 6; i++) if (i != index && key(i) == key) return false;
    switch (index) {
      case 0 -> up = key;
      case 1 -> down = key;
      case 2 -> left = key;
      case 3 -> right = key;
      case 4 -> interact = key;
      case 5 -> journal = key;
      default -> throw new IllegalArgumentException();
    }
    return true;
  }

  public static Settings load(Path dir) {
    try {
      Settings s =
          new Json().fromJson(Settings.class, Files.readString(dir.resolve("settings.json")));
      if (s == null
          || !Float.isFinite(s.music)
          || !Float.isFinite(s.sfx)
          || s.music < 0
          || s.music > 1
          || s.sfx < 0
          || s.sfx > 1
          || s.scale < 2
          || s.scale > 4) throw new IOException();
      HashSet<Integer> keys = new HashSet<>();
      for (int i = 0; i < 6; i++)
        if (s.key(i) <= 0 || s.key(i) > 255 || !keys.add(s.key(i)) || !s.bind(i, s.key(i)))
          throw new IOException();
      return s;
    } catch (Exception e) {
      return new Settings();
    }
  }

  public void save(Path dir) throws IOException {
    Files.createDirectories(dir);
    Path tmp = dir.resolve("settings.tmp.json");
    Files.writeString(tmp, new Json().prettyPrint(this));
    SaveStore.replace(tmp, dir.resolve("settings.json"));
  }
}
