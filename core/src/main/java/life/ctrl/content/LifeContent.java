package life.ctrl.content;

import com.badlogic.gdx.utils.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LifeContent {
  public Girl[] girls;
  public Item[] items;
  public Job[] jobs;

  public static final class Girl {
    public String id,
        name,
        about,
        values,
        likes,
        portrait,
        homeMap,
        introduction,
        commitLine,
        notReady,
        nightLine,
        story;
    public int age;
    public String[] favoriteGifts, venues;
    public Topic[] topics;
    public DateChoice[] dateChoices;
  }

  public static final class Topic {
    public String id, label, response;
    public int affinity, trust;
  }

  public static final class DateChoice {
    public String label, response;
    public int affinity, trust;
  }

  public static final class Item {
    public String id, name, kind, description;
    public int price, hunger, energy;
  }

  public static final class Job {
    public String id, title, brief, function, source;
    public String[] algorithms;
    public int pay, minutes;
    public Case[] tests;
  }

  public static final class Case {
    public double[] args;
    public double expected;
  }

  public static LifeContent load() {
    try (var in = LifeContent.class.getResourceAsStream("/data/life.json")) {
      if (in == null) throw new IOException("Нет life.json");
      var c =
          new Json()
              .fromJson(LifeContent.class, new String(in.readAllBytes(), StandardCharsets.UTF_8));
      Set<String> ids = new HashSet<>();
      for (var g : c.girls)
        if (g.age < 21 || !ids.add(g.id) || g.topics.length < 3 || g.dateChoices.length < 2)
          throw new IOException("Некорректная героиня");
      return c;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Girl girl(String id) {
    for (var g : girls) if (g.id.equals(id)) return g;
    throw new IllegalArgumentException("Героиня: " + id);
  }

  public Item item(String id) {
    for (var i : items) if (i.id.equals(id)) return i;
    throw new IllegalArgumentException("Предмет: " + id);
  }

  public Job job(String id) {
    for (var j : jobs) if (j.id.equals(id)) return j;
    throw new IllegalArgumentException("Заказ: " + id);
  }
}
