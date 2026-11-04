package life.ctrl.content;

import com.badlogic.gdx.utils.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored dates with equal-length branches and no hidden monetary choices. */
public final class DateContent {
  public GirlDates[] girls;

  public static final class GirlDates {
    public String id;
    public Episode[] episodes;
  }

  public static final class Episode {
    public String id, title, atmosphere;
    public Beat[] beats;
  }

  public static final class Beat {
    public String text, detail;
    public Choice[] choices;
  }

  public static final class Choice {
    public String label, response;
    public int affinity, trust;
  }

  public GirlDates girl(String id) {
    for (var g : girls) if (g.id.equals(id)) return g;
    throw new IllegalArgumentException("Unknown date character: " + id);
  }

  public Episode episode(String girl, String id) {
    for (var e : girl(girl).episodes) if (e.id.equals(id)) return e;
    throw new IllegalArgumentException("Unknown date episode: " + id);
  }

  public static DateContent load() {
    try (var in = DateContent.class.getResourceAsStream("/data/dates.json")) {
      if (in == null) throw new IOException("Нет dates.json");
      var data =
          new Json()
              .fromJson(DateContent.class, new String(in.readAllBytes(), StandardCharsets.UTF_8));
      Set<String> girls = new HashSet<>(), episodes = new HashSet<>();
      if (data.girls.length != 3) throw new IOException("Неверное число героинь");
      for (var g : data.girls) {
        if (!girls.add(g.id) || g.episodes.length != 3) throw new IOException("Неверные свидания");
        for (var e : g.episodes) {
          if (!episodes.add(e.id)
              || e.beats.length != 3
              || e.title.isBlank()
              || e.atmosphere.isBlank()) throw new IOException("Неверный эпизод");
          for (var b : e.beats) {
            if (b.text.isBlank()
                || b.text.length() > 300
                || b.detail.isBlank()
                || b.choices.length != 2) throw new IOException("Неверный момент");
            for (var c : b.choices)
              if (c.label.isBlank()
                  || c.label.length() > 80
                  || c.response.isBlank()
                  || c.response.length() > 300
                  || c.affinity < 0
                  || c.affinity > 2
                  || c.trust < 0
                  || c.trust > 2) throw new IOException("Неверный ответ");
          }
        }
      }
      return data;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
