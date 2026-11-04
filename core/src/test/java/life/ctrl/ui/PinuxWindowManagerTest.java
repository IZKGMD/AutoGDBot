package life.ctrl.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PinuxWindowManagerTest {
  @Test
  void windowsRetainIndependentFocusAndMinimization() {
    var wm = new PinuxWindowManager();
    assertNull(wm.front());
    wm.open(PinuxWindowManager.App.CODE);
    wm.open(PinuxWindowManager.App.TERMINAL);
    assertTrue(wm.isFront(PinuxWindowManager.App.TERMINAL));
    wm.minimize(PinuxWindowManager.App.TERMINAL);
    assertTrue(wm.isFront(PinuxWindowManager.App.CODE));
    assertTrue(wm.get(PinuxWindowManager.App.TERMINAL).visible);
    wm.open(PinuxWindowManager.App.TERMINAL);
    assertFalse(wm.get(PinuxWindowManager.App.TERMINAL).minimized);
    wm.close(PinuxWindowManager.App.TERMINAL);
    assertTrue(wm.isFront(PinuxWindowManager.App.CODE));
    assertFalse(wm.get(PinuxWindowManager.App.TERMINAL).visible);
  }

  @Test
  void draggingRestoresMaximizedWindowAndKeepsCaptionVisible() {
    var wm = new PinuxWindowManager();
    wm.open(PinuxWindowManager.App.CODE);
    wm.toggleMaximize(PinuxWindowManager.App.CODE);
    assertTrue(wm.isMaximizedCode());
    var w = wm.get(PinuxWindowManager.App.CODE);
    assertTrue(wm.pointerDown(w.x + 56, w.y + 8));
    assertTrue(wm.pointerDragged(265, 115));
    assertTrue(wm.pointerUp(265, 115));
    assertFalse(w.maximized);
    assertTrue(w.y >= PinuxWindowManager.TOP + 1);
    assertTrue(w.x >= 0);
    assertTrue(w.x + w.width <= 480);
    assertTrue(w.y + 25 <= PinuxWindowManager.BOTTOM);
  }

  @Test
  void captionButtonsMinimizeMaximizeAndCloseWithoutClickThrough() {
    var wm = new PinuxWindowManager();
    wm.open(PinuxWindowManager.App.FILES);
    var w = wm.get(PinuxWindowManager.App.FILES);
    assertTrue(wm.pointerDown(w.right() - 22, w.y + 8));
    assertTrue(w.maximized);
    assertTrue(wm.pointerDown(w.right() - 36, w.y + 8));
    assertTrue(w.minimized);
    wm.open(PinuxWindowManager.App.FILES);
    assertTrue(wm.pointerDown(w.right() - 7, w.y + 8));
    assertFalse(w.visible);
  }

  @Test
  void cornerResizeClampsToDesktopAndEdgeDraggingSnaps() {
    var wm = new PinuxWindowManager();
    wm.open(PinuxWindowManager.App.TERMINAL);
    var w = wm.get(PinuxWindowManager.App.TERMINAL);
    int initialWidth = w.width;
    assertTrue(wm.pointerDown(w.right() - 3, w.bottom() - 3));
    assertTrue(wm.pointerDragged(w.right() + 50, w.bottom() + 15));
    assertTrue(wm.pointerUp(w.right(), w.bottom()));
    assertTrue(w.width >= initialWidth);
    assertTrue(w.right() <= 480);
    assertTrue(w.bottom() <= PinuxWindowManager.BOTTOM);

    assertTrue(wm.pointerDown(w.x + 40, w.y + 8));
    assertTrue(wm.pointerDragged(1, 90));
    assertTrue(wm.pointerUp(1, 90));
    assertEquals(2, w.x);
    assertEquals(236, w.width);
  }

  @Test
  void clickingBackgroundWindowFocusesBeforeItsControls() {
    var wm = new PinuxWindowManager();
    wm.open(PinuxWindowManager.App.CODE);
    wm.open(PinuxWindowManager.App.FILES);
    var code = wm.get(PinuxWindowManager.App.CODE);
    // Far-right lower region belongs to CODE but is not covered by FILES.
    assertTrue(wm.pointerDown(code.right() - 8, code.bottom() - 12));
    assertTrue(wm.isFront(PinuxWindowManager.App.CODE));
  }
}
