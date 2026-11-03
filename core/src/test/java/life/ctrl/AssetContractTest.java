package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import life.ctrl.content.Content;
import org.junit.jupiter.api.Test;

/** Asset and text contracts catch failures that Java compilation cannot see. */
class AssetContractTest {
  private java.awt.image.BufferedImage image(String id) throws IOException {
    try (var in = getClass().getResourceAsStream("/art/" + id + ".png")) {
      assertNotNull(in, "Missing PNG: " + id);
      var png = ImageIO.read(in);
      assertNotNull(png, "Invalid PNG: " + id);
      return png;
    }
  }

  @Test
  void everyMapAndObjectHasARealReadableTexture() throws Exception {
    var content = Content.load();
    for (var m : content.maps) {
      var png = image(m.id);
      assertEquals(m.width, png.getWidth());
      assertEquals(m.height, png.getHeight());
      for (var p : m.objects) if (p.art != null) image(p.art);
      for (var n : m.npcs) image(n.art);
    }
  }

  @Test
  void animationAndStateVariantsMatchRuntimeGeometry() throws Exception {
    for (String id : List.of("hero", "hero_1", "hero_2", "mira", "arkady", "lev")) {
      var png = image(id);
      assertEquals(64, png.getWidth(), id);
      assertEquals(96, png.getHeight(), id);
      assertTrue(png.getColorModel().hasAlpha(), id);
    }
    for (String[] pair :
        new String[][] {
          {"boxes", "boxes_open"}, {"plant", "plant_watered"}, {"mailbox", "mailbox_signed"}
        }) {
      var base = image(pair[0]);
      var variant = image(pair[1]);
      assertEquals(base.getWidth(), variant.getWidth());
      assertEquals(base.getHeight(), variant.getHeight());
    }
    for (String id :
        List.of(
            "portrait_hero", "portrait_mira", "portrait_arkady", "portrait_lev", "portrait_note")) {
      var png = image(id);
      assertEquals(32, png.getWidth());
      assertEquals(32, png.getHeight());
    }
  }

  private Map<Integer, Integer> advances() throws IOException {
    try (var in = getClass().getResourceAsStream("/fonts/ui.fnt")) {
      assertNotNull(in);
      String fnt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(fnt.contains("padding=0,0,0,0"));
      Map<Integer, Integer> a = new HashMap<>();
      var matcher = Pattern.compile("char id=(\\d+).*?xadvance=(\\d+)").matcher(fnt);
      while (matcher.find())
        a.put(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
      return a;
    }
  }

  @Test
  void cyrillicDialogueFitsBesidePortraitWithoutMissingGlyphs() throws Exception {
    var widths = advances();
    for (char c : "ЁёЙй×·№→←↑↓".toCharArray()) assertTrue(widths.containsKey((int) c));
    for (var node : Content.load().dialogues) {
      int lines = 1, used = 0;
      for (String word : node.text.split(" ")) {
        int width = widths.get(32);
        for (char c : word.toCharArray()) {
          assertTrue(widths.containsKey((int) c), "Missing glyph: " + c);
          width += widths.get((int) c);
        }
        if (used + width > 378) {
          lines++;
          used = width;
        } else used += width;
      }
      assertTrue(lines <= 3, node.id + " overflows into response buttons: " + lines + " lines");
    }
  }
}
