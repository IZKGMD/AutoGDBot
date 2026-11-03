package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveTest {
  @TempDir Path dir;
  final Content content = Content.load();

  @Test
  void roundTripRetainsMapFlagsAppearanceAndClock() throws Exception {
    var saves = new SaveStore(dir, content);
    var s = new GameState();
    s.name = "Ёжик";
    s.outfit = 2;
    s.elapsed = 81.25;
    s.map = "street";
    s.x = 675;
    s.y = 310;
    s.hasLetter = true;
    s.inspectedPc = true;
    s.metMira = true;
    s.visited.add("street");
    s.direction = 2;
    saves.save(s);
    var copy = saves.load().state();
    assertEquals(s.name, copy.name);
    assertEquals(s.map, copy.map);
    assertEquals(s.x, copy.x);
    assertEquals(s.y, copy.y);
    assertEquals(s.elapsed, copy.elapsed);
    assertEquals(s.direction, copy.direction);
    assertEquals(s.outfit, copy.outfit);
    assertTrue(copy.hasLetter);
    assertTrue(copy.inspectedPc);
    assertEquals(s.visited, copy.visited);
  }

  @Test
  void backupRecoversCorruptPrimary() throws Exception {
    var saves = new SaveStore(dir, content);
    var s = new GameState();
    s.name = "Первый";
    saves.save(s);
    s.name = "Второй";
    saves.save(s);
    Files.writeString(dir.resolve("save.json"), "broken");
    var loaded = saves.load();
    assertTrue(loaded.fromBackup());
    assertEquals("Первый", loaded.state().name);
  }

  @Test
  void corruptPrimaryDoesNotOverwriteGoodBackup() throws Exception {
    var saves = new SaveStore(dir, content);
    var s = new GameState();
    saves.save(s);
    s.name = "Мира";
    saves.save(s);
    Files.writeString(dir.resolve("save.json"), "broken");
    s.name = "Лев";
    saves.save(s);
    Files.writeString(dir.resolve("save.json"), "broken again");
    assertEquals("Саша", saves.load().state().name);
  }

  @Test
  void corruptOrFutureSaveDoesNotCrashCaller() throws Exception {
    var saves = new SaveStore(dir, content);
    Files.writeString(dir.resolve("save.json"), "{\"version\":999,\"state\":{}}");
    assertThrows(java.io.IOException.class, saves::load);
  }

  @Test
  void savingInEveryMapWorks() throws Exception {
    var saves = new SaveStore(dir, content);
    for (var m : content.maps) {
      var s = new GameState();
      s.map = m.id;
      s.x = m.spawnX;
      s.y = m.spawnY;
      saves.save(s);
      assertEquals(m.id, saves.load().state().map);
    }
  }

  @Test
  void invalidPositionDoesNotReplaceSave() throws Exception {
    var saves = new SaveStore(dir, content);
    var s = new GameState();
    saves.save(s);
    s.x = -100;
    assertThrows(IllegalArgumentException.class, () -> saves.save(s));
    assertEquals(240, saves.load().state().x);
  }

  @Test
  void settingsRoundTripAndBadFileFallback() throws Exception {
    var s = new Settings();
    s.music = .25f;
    s.reducedEffects = true;
    s.scale = 2;
    s.bind(0, com.badlogic.gdx.Input.Keys.T);
    s.save(dir);
    var copy = Settings.load(dir);
    assertEquals(s.up, copy.up);
    assertEquals(s.music, copy.music);
    assertTrue(copy.reducedEffects);
    Files.writeString(dir.resolve("settings.json"), "bad");
    assertEquals(3, Settings.load(dir).scale);
  }

  @Test
  void reservedAndDuplicateBindingsRejected() {
    var s = new Settings();
    assertFalse(s.bind(0, com.badlogic.gdx.Input.Keys.ESCAPE));
    assertFalse(s.bind(0, s.down));
    assertTrue(s.bind(0, com.badlogic.gdx.Input.Keys.T));
  }
}
