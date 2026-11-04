package life.ctrl.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PinuxGeometryTest {
  @TempDir Path folder;

  @Test
  void largeWindowsFitAllSupportedDisplaysAndLeaveBarsReachable() {
    for (int[] screen : new int[][] {{960, 540}, {1280, 800}, {1440, 810}, {1920, 1080}}) {
      var b = PinuxGeometry.fit(668, 142, 1180, 720, screen[0], screen[1]);
      assertTrue(b.x() >= 0 && b.y() >= 50);
      assertTrue(b.x() + b.width() <= screen[0]);
      assertTrue(b.y() + b.height() <= screen[1] - 28);
      var max = PinuxGeometry.maximized(screen[0], screen[1]);
      assertTrue(max.y() + max.height() <= screen[1] - 28);
    }
  }

  @Test
  void extremeDragAndResizeNeverLoseWindowControls() {
    for (float coordinate : new float[] {-10000, 0, 10000}) {
      var b = PinuxGeometry.fit(coordinate, coordinate, 100000, 100000, 960, 540);
      assertTrue(b.x() >= 0 && b.y() >= 0 && b.x() + b.width() <= 960 && b.y() + b.height() <= 540);
    }
  }

  @Test
  void desktopSettingsAndWindowPlacementSurviveSave() throws Exception {
    var s = new GameState();
    s.pinux.warmTheme = true;
    s.pinux.reducedMotion = true;
    s.pinux.windows.add(new PinuxSession.Placement("TERMINAL", .1f, .2f, .5f, .5f, true));
    var store = new SaveStore(folder, Content.load());
    store.save(s);
    var loaded = store.load().state();
    assertTrue(loaded.pinux.warmTheme);
    assertTrue(loaded.pinux.reducedMotion);
    assertTrue(loaded.pinux.windows.get(0).maximized);
    assertEquals(.5f, loaded.pinux.windows.get(0).width);
  }

  @Test
  void oldVersionFourSavesGetDefaultDesktopWithoutLosingMoney() throws Exception {
    Files.writeString(folder.resolve("save.json"), "{\"version\":4,\"state\":{\"money\":4321}}");
    var s = new SaveStore(folder, Content.load()).load().state();
    assertEquals(4321, s.money);
    assertNotNull(s.pinux);
    assertTrue(s.pinux.windows.isEmpty());
  }

  @Test
  void invalidSessionGeometryIsRejected() {
    var s = new PinuxSession();
    s.windows.add(new PinuxSession.Placement("CODE", Float.NaN, 0, .5f, .5f, false));
    assertThrows(IllegalArgumentException.class, s::validate);
  }
}
