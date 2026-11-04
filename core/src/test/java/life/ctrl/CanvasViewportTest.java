package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import life.ctrl.render.CanvasViewport;
import org.junit.jupiter.api.Test;

class CanvasViewportTest {
  @Test
  void nonIntegerResolutionUsesAvailableWidthWithoutStretching() {
    var v = CanvasViewport.fit(1280, 800);
    assertEquals(1280, v.width());
    assertEquals(720, v.height());
    assertEquals(0, v.x());
    assertEquals(40, v.y());
    assertEquals(640, v.screenX(240));
    assertEquals(400, v.screenY(135));
  }

  @Test
  void edgesRemainInsideScreenAndPointerCoordinatesRoundTrip() {
    for (int[] size :
        new int[][] {
          {960, 540},
          {1280, 800},
          {1366, 768},
          {1440, 810},
          {1920, 1080},
          {3440, 1440},
          {640, 360},
          {320, 240}
        }) {
      var v = CanvasViewport.fit(size[0], size[1]);
      assertTrue(v.x() >= 0 && v.y() >= 0);
      assertTrue(v.x() + v.width() <= size[0] && v.y() + v.height() <= size[1]);
      assertTrue(v.x() <= 1 || v.y() <= 1);
      assertEquals(16f / 9, v.width() / (float) v.height(), .004);
      for (int x : new int[] {0, 12, 176, 300, 479})
        assertEquals(x, v.logicalX(v.screenX(x)), .51 / v.scale());
      for (int y : new int[] {0, 12, 143, 247, 269})
        assertEquals(y, v.logicalY(v.screenY(y)), .51 / v.scale());
    }
  }

  @Test
  void invalidWindowDimensionsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> CanvasViewport.fit(0, 800));
    assertThrows(IllegalArgumentException.class, () -> CanvasViewport.fit(800, -1));
  }
}
