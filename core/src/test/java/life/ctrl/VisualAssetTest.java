package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import life.ctrl.content.Content;
import life.ctrl.render.SceneLayout;
import org.junit.jupiter.api.Test;

class VisualAssetTest {
  BufferedImage image(String path) throws Exception {
    try (var in = getClass().getResourceAsStream("/" + path + ".png")) {
      assertNotNull(in, path);
      return ImageIO.read(in);
    }
  }

  @Test
  void highDensityGeometryMatchesEveryMapAndProp() throws Exception {
    for (var map : Content.load().maps) {
      var hd = image("art/hd/" + map.id);
      assertEquals(map.width * 2, hd.getWidth());
      assertEquals(map.height * 2, hd.getHeight());
      var b = SceneLayout.bounds(map.id);
      assertTrue(
          b.x() >= 0
              && b.y() >= 0
              && b.x() + b.width() <= map.width
              && b.y() + b.height() <= map.height);
      for (var prop : map.objects)
        if (prop.art != null) {
          var old = image("art/" + prop.art);
          var p = image("art/hd/" + prop.art);
          assertEquals(old.getWidth() * 2, p.getWidth(), prop.art);
          assertEquals(old.getHeight() * 2, p.getHeight(), prop.art);
        }
    }
  }

  @Test
  void animationFramesCannotBleedIntoAdjacentCells() throws Exception {
    for (String id :
        List.of("hero", "hero_1", "hero_2", "mira", "asya", "nika", "tamara", "arkady", "lev")) {
      var p = image("art/hd/" + id);
      assertEquals(128, p.getWidth());
      assertEquals(192, p.getHeight());
      for (int row = 0; row < 4; row++)
        for (int col = 0; col < 4; col++) {
          for (int x = 0; x < 32; x++) {
            assertEquals(0, p.getRGB(col * 32 + x, row * 48) >>> 24, id);
            assertEquals(0, p.getRGB(col * 32 + x, row * 48 + 47) >>> 24, id);
          }
          for (int y = 0; y < 48; y++) {
            assertEquals(0, p.getRGB(col * 32, row * 48 + y) >>> 24, id);
            assertEquals(0, p.getRGB(col * 32 + 31, row * 48 + y) >>> 24, id);
          }
        }
    }
  }

  @Test
  void LightAndShadowTexturesFadeToTransparentAtAllEdges() throws Exception {
    for (String id : List.of("light_amber", "light_monitor", "light_window", "shadow")) {
      var p = image("art/hd/" + id);
      for (int x = 0; x < p.getWidth(); x++) {
        assertEquals(0, p.getRGB(x, 0) >>> 24, id);
        assertEquals(0, p.getRGB(x, p.getHeight() - 1) >>> 24, id);
      }
      for (int y = 0; y < p.getHeight(); y++) {
        assertEquals(0, p.getRGB(0, y) >>> 24, id);
        assertEquals(0, p.getRGB(p.getWidth() - 1, y) >>> 24, id);
      }
    }
  }

  @Test
  void cutawayVoidIsOpaqueAndUnpainted() throws Exception {
    for (String id : List.of("room", "stair", "cafe", "diner", "workshop", "park")) {
      var p = image("art/hd/" + id);
      var b = SceneLayout.bounds(id);
      for (int y = 0; y < p.getHeight(); y++)
        for (int x = 0; x < p.getWidth(); x++)
          if (x < b.x() * 2
              || y < b.y() * 2
              || x >= (b.x() + b.width()) * 2
              || y >= (b.y() + b.height()) * 2)
            assertEquals(0xff0e1a24, p.getRGB(x, y), id + " scene escaped its boundary");
    }
  }

  @Test
  void highDensityFontAtlasesFitBaselineGpuAndGlyphsStayInsideAtlas() throws Exception {
    for (String id : List.of("ui", "small", "title")) {
      var p = image("fonts/hd/" + id);
      assertTrue(p.getWidth() <= 2048 && p.getHeight() <= 2048, id);
      try (var in = getClass().getResourceAsStream("/fonts/hd/" + id + ".fnt")) {
        String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var match =
            java.util.regex.Pattern.compile(
                    "char id=([0-9]+) x=([0-9]+) y=([0-9]+) width=([0-9]+) height=([0-9]+)")
                .matcher(text);
        int count = 0;
        while (match.find()) {
          int x = Integer.parseInt(match.group(2)), y = Integer.parseInt(match.group(3));
          assertTrue(
              x + Integer.parseInt(match.group(4)) <= p.getWidth(),
              id + " glyph " + match.group(1));
          assertTrue(
              y + Integer.parseInt(match.group(5)) <= p.getHeight(),
              id + " glyph " + match.group(1));
          count++;
        }
        assertTrue(count >= 200, id + " Cyrillic glyphs missing");
      }
    }
  }

  @Test
  void oldSaveInsideNewFurnitureMovesToEntranceWithoutLosingProgress() throws Exception {
    java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("ctrl-visual-migration");
    try {
      java.nio.file.Files.writeString(
          folder.resolve("save.json"),
          "{\"version\":3,\"state\":{\"map\":\"diner\",\"x\":350,\"y\":145,\"money\":4321,\"codingXp\":8,\"completedOrders\":6,\"shipped\":[\"total\"],\"upgrades\":[\"ssd\"]}}");
      var store = new life.ctrl.save.SaveStore(folder, Content.load());
      var state = store.load().state();
      assertEquals(240, state.x);
      assertEquals(248, state.y);
      assertEquals(4321, state.money);
      assertEquals(8, state.codingXp);
      assertEquals(6, state.completedOrders);
      assertTrue(state.shipped.contains("total"));
      assertTrue(state.upgrades.contains("ssd"));
      store.save(state);
      assertEquals(4321, store.load().state().money);
    } finally {
      try (var files = java.nio.file.Files.list(folder)) {
        for (var file : files.toList()) java.nio.file.Files.delete(file);
      }
      java.nio.file.Files.delete(folder);
    }
  }
}
