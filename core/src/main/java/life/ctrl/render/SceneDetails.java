package life.ctrl.render;

import life.ctrl.model.*;

/** World-space details are clipped by the same cutaway as furniture and characters. */
public final class SceneDetails {
  private SceneDetails() {}

  private static final float[][] STREET_PUDDLES = {
    {314, 306, 43, 7}, {420, 349, 30, 5}, {676, 316, 48, 8}, {820, 378, 39, 6}, {159, 353, 35, 5}
  };
  private static final float[][] PARK_PUDDLES = {
    {130, 226, 32, 5}, {267, 190, 43, 6}, {365, 241, 26, 4}
  };

  public static void ground(
      PixelCanvas c, GameState s, float ox, float oy, float time, boolean reduced) {
    if (s.map.equals("street") || s.map.equals("park")) {
      if (Atmosphere.rain(s)) {
        float[][] patches = s.map.equals("street") ? STREET_PUDDLES : PARK_PUDDLES;
        for (int i = 0; i < patches.length; i++) {
          var p = patches[i];
          float x = p[0] - ox, y = p[1] - oy;
          c.roundRect(x, y, p[2], p[3], 2, "b3c6cf19");
          c.rect(x + 4, y + 1, p[2] - 11, .45f, "c7d6ce48");
          if (!reduced) {
            float wave = (int) (((time * .7f + i * .27f) % 1) * 4) / 4f;
            c.roundRect(
                x + p[2] * .45f - wave * 7,
                y + p[3] * .5f - wave * 1.5f,
                1 + wave * 14,
                .5f + wave * 3,
                2,
                "c3d4d322");
          }
        }
      }
      if (!reduced) {
        for (int i = 0; i < 9; i++) {
          float wx = s.map.equals("street") ? 52 + i * 97 : 51 + i * 43;
          float wy = s.map.equals("street") ? 246 + i % 3 * 43 : 162 + i % 3 * 29;
          float sway = (float) Math.sin(time * .6f + i) * 2;
          c.rect(wx - ox + sway, wy - oy, 1.8f, .7f, i % 2 == 0 ? "be9c674b" : "b0ad7840");
        }
      }
    }
  }

  private static void keepsakes(PixelCanvas c, GameState s, float ox, float oy) {
    var mira = s.relationships.get("mira");
    var asya = s.relationships.get("asya");
    var nika = s.relationships.get("nika");
    if (mira != null && !mira.dateExperiences.isEmpty()) {
      c.rect(198 - ox, 131 - oy, 9, 1, "263139");
      c.rect(197 - ox, 125 - oy, 9, 6, "a18e6e");
      c.rect(198 - ox, 125.5f - oy, 7, 4, "dbcfaa");
      c.rect(202 - ox, 125 - oy, 1, 4, "a37958");
    }
    if (asya != null && !asya.dateExperiences.isEmpty()) {
      c.rect(208 - ox, 72 - oy, 12, 15, "14232b");
      c.rect(207 - ox, 71 - oy, 12, 15, "d8ceb6");
      c.rect(208 - ox, 72 - oy, 10, 10, "67828a");
      c.rect(208 - ox, 78 - oy, 10, 4, "334b5b");
      c.rect(210 - ox, 73 - oy, 2, 3, "d4b17b");
      c.rect(211 - ox, 69.5f - oy, 4, 2, "b8996dbb");
    }
    if (nika != null && !nika.dateExperiences.isEmpty()) {
      c.rect(294 - ox, 161 - oy, 10, 7, "222b3b");
      c.rect(295 - ox, 162 - oy, 8, 5, "887c91");
      c.rect(297 - ox, 164 - oy, 1, 1, "dccba8");
      c.rect(301 - ox, 164 - oy, 1, 1, "dccba8");
    }
  }

  public static void foreground(
      PixelCanvas c, GameState s, float ox, float oy, float time, boolean reduced) {
    if (s.map.equals("room")) keepsakes(c, s, ox, oy);
    if (s.map.equals("park")) {
      float light = .35f + Atmosphere.night(s) * .65f;
      c.glow("light_amber", 130 - ox, 53 - oy, light * .45f);
      if (!reduced)
        for (int i = 0; i < 6; i++) {
          float t = (time * .11f + i * .17f) % 1;
          c.rect(
              95 + i * 51 - ox + (float) Math.sin(time * .4f + i) * 7,
              103 + i % 3 * 37 - oy - t * 16,
              1,
              .7f,
              "d3c79e55");
        }
    }
    if (!reduced && (s.map.equals("cafe") || s.map.equals("diner"))) {
      for (int i = 0; i < 3; i++) {
        float phase = (time * .25f + i * .33f) % 1;
        c.rect(
            303 - ox + (float) Math.sin(phase * 5 + i) * 1.2f,
            183 - oy - phase * 10,
            .6f,
            1.5f,
            "d2dfce55");
      }
    }
  }
}
