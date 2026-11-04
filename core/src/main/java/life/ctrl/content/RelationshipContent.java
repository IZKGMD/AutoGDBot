package life.ctrl.content;

import com.badlogic.gdx.utils.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RelationshipContent {
  public Arc[] arcs;

  public static final class Arc {
    public String id;
    public Chapter[] chapters;
    public Activity[] activities;
  }

  public static final class Chapter {
    public String title, text;
    public Choice[] choices;
  }

  public static final class Choice {
    public String label, text, flag, promise;
    public int affinity, trust;
  }

  public static final class Activity {
    public String id, label, text;
    public int minutes, cost;
  }

  public Arc arc(String id) {
    for (var a : arcs) if (a.id.equals(id)) return a;
    throw new IllegalArgumentException(id);
  }

  public static RelationshipContent load() {
    try (var in = RelationshipContent.class.getResourceAsStream("/data/relationships.json")) {
      if (in == null) throw new IOException("Нет relationships.json");
      var c =
          new Json()
              .fromJson(
                  RelationshipContent.class, new String(in.readAllBytes(), StandardCharsets.UTF_8));
      Set<String> ids = new HashSet<>();
      for (var a : c.arcs) {
        if (!ids.add(a.id) || a.chapters.length != 3 || a.activities.length != 3)
          throw new IOException("Неверная история");
        Set<String> activities = new HashSet<>();
        for (var t : a.activities)
          if (!activities.add(t.id) || t.cost < 0 || t.minutes < 1 || t.minutes > 120)
            throw new IOException("Неверное занятие");
        for (var ch : a.chapters)
          if (ch.choices.length != 2) throw new IOException("Неверная развилка");
          else
            for (var v : ch.choices)
              if (!v.promise.isEmpty() && !activities.contains(v.promise))
                throw new IOException("Обещание без занятия");
      }
      return c;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
