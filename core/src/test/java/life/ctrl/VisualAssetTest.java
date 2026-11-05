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
  void illustratedPropsAreCompleteIsolatedCutoutsInsideAtlasCells() throws Exception {
    for (String name : List.of("furniture", "outdoors", "utility", "states", "pond")) {
      var p = image("art/illustrations/" + name);
      assertTrue(p.getColorModel().hasAlpha(), name + " needs genuine transparency");
      assertTrue(
          p.getWidth() <= 2048 && p.getHeight() <= 2048,
          name + " exceeds baseline GPU texture size");
      int columns = name.equals("pond") ? 1 : 2,
          rows = name.equals("pond") ? 1 : name.equals("utility") ? 3 : 2;
      for (int row = 0; row < rows; row++)
        for (int col = 0; col < columns; col++) {
          int x0 = col * p.getWidth() / columns, x1 = (col + 1) * p.getWidth() / columns;
          int y0 = row * p.getHeight() / rows, y1 = (row + 1) * p.getHeight() / rows;
          int opaque = 0;
          for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
              int alpha = p.getRGB(x, y) >>> 24;
              if (alpha > 128) opaque++;
              if (x < x0 + 8 || x >= x1 - 8 || y < y0 + 8 || y >= y1 - 8)
                assertTrue(
                    alpha <= 16,
                    name
                        + " cell "
                        + col
                        + ","
                        + row
                        + " cuts a silhouette or contains backdrop at "
                        + x
                        + ","
                        + y);
            }
          assertTrue(opaque > (x1 - x0) * (y1 - y0) / 50, name + " cell has no usable prop");
        }
    }
  }

  @Test
  void everyMaterialCellIsOpaqueAndHasEnoughDetailForNativeResolution() throws Exception {
    var p = image("art/illustrations/materials");
    assertTrue(p.getWidth() >= 1024 && p.getHeight() >= 1024);
    assertTrue(p.getWidth() <= 2048 && p.getHeight() <= 2048);
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 3; col++) {
        Set<Integer> colors = new HashSet<>();
        for (int y = row * p.getHeight() / 3 + 2; y < (row + 1) * p.getHeight() / 3 - 2; y += 3)
          for (int x = col * p.getWidth() / 3 + 2; x < (col + 1) * p.getWidth() / 3 - 2; x += 3) {
            int rgb = p.getRGB(x, y);
            assertEquals(255, rgb >>> 24, "Transparent hole in walkable material");
            colors.add(rgb);
          }
        assertTrue(colors.size() > 256, "Material cell lost its painted detail");
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
