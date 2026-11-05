package life.ctrl.desktop;

import java.nio.file.*;
import java.util.Comparator;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;

/** Portable-package diagnostic. Never touches the player's actual save directory. */
final class InstallationCheck {
  static void run() {
    Path directory = null;
    try {
      var content = Content.load();
      var life = LifeContent.load();
      if (life.girls.length != 3
          || java.util.Arrays.stream(DateContent.load().girls)
                  .mapToInt(g -> g.episodes.length)
                  .sum()
              != 9) throw new IllegalStateException("Missing story data");
      RelationshipContent.load();
      try (var in =
          InstallationCheck.class.getResourceAsStream("/art/illustrations/relationships.png")) {
        if (in == null || in.readNBytes(8).length != 8)
          throw new IllegalStateException("Missing artwork");
      }
      directory = Files.createTempDirectory("CTRL LIFE проверка ");
      var state = new GameState();
      state.name = "Проверка";
      var world = new World(content, state);
      Navigation.goal(world, life);
      var store = new SaveStore(directory, content);
      store.save(state);
      var archive = store.archiveCurrent();
      if (archive == null || !Files.isRegularFile(archive.resolve("save.json")))
        throw new IllegalStateException("Story archive failed");
      var loaded = store.load().state();
      if (!loaded.name.equals(state.name) || loaded.money != state.money)
        throw new IllegalStateException("Save round-trip failed");
      var job = life.job("total");
      Coding.accept(loaded, job, 0);
      Coding.repair(loaded, job, 0);
      if (!Coding.test(loaded, job).passed())
        throw new IllegalStateException("ByteScript check failed");
      System.out.println(
          "INSTALLATION_CHECK_PASS: Java "
              + Runtime.version()
              + "; content, artwork, ByteScript, Unicode paths and saves");
    } catch (Exception e) {
      throw new IllegalStateException("Installation check failed", e);
    } finally {
      if (directory != null)
        try (var files = Files.walk(directory)) {
          for (var path : files.sorted(Comparator.reverseOrder()).toList())
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
          /* The temporary diagnostic directory is safe to leave behind. */
        }
    }
  }
}
