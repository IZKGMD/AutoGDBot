package life.ctrl.model;

/** Continuous dusk, dawn and deterministic weather; rendering never changes saved simulation. */
public final class Atmosphere {
  private Atmosphere() {}

  public static float night(GameState s) {
    double hour = (s.gameMinutes % 1440) / 60;
    if (hour < 5 || hour >= 22) return 1;
    if (hour < 8) return (float) ((8 - hour) / 3);
    if (hour < 17) return 0;
    return (float) ((hour - 17) / 5);
  }

  public static boolean rain(GameState s) {
    return Math.floorMod(s.day(), 3) != 2;
  }

  public static String period(GameState s) {
    double h = (s.gameMinutes % 1440) / 60;
    return h < 5 ? "ночь" : h < 11 ? "утро" : h < 17 ? "день" : h < 22 ? "вечер" : "ночь";
  }
}
