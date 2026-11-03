package life.ctrl.content;

import com.badlogic.gdx.utils.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Content contains no rendering objects and can be validated in headless tests. */
public final class Content {
  public MapDef[] maps;
  public Dialogue[] dialogues;

  public static final class MapDef {
    public String id, name, subtitle;
    public int width, height;
    public float spawnX, spawnY;
    public float[][] solids;
    public Prop[] objects;
    public Npc[] npcs;
    public Sign[] signs;
  }

  public static final class Sign {
    public String text, color;
    public float x, y;
  }

  public static final class Prop {
    public String id, label, action, art, target;
    public float x, y, artX, artY, targetX, targetY;
    public float[] solid;
  }

  public static final class Npc {
    public String id, name, art, dialogue;
    public float[][] route;
  }

  public static final class Dialogue {
    public String id, speaker, text;
    public Choice[] choices;
  }

  public static final class Choice {
    public String label, next, effect;
  }

  public static Content load() {
    try (var in = Content.class.getResourceAsStream("/data/world.json")) {
      if (in == null) throw new IllegalStateException("Не найден world.json");
      Content c =
          new Json().fromJson(Content.class, new String(in.readAllBytes(), StandardCharsets.UTF_8));
      c.validate();
      return c;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public MapDef map(String id) {
    for (var m : maps) if (m.id.equals(id)) return m;
    throw new IllegalArgumentException("Карта: " + id);
  }

  public Dialogue dialogue(String id) {
    for (var d : dialogues) if (d.id.equals(id)) return d;
    throw new IllegalArgumentException("Диалог: " + id);
  }

  public void validate() {
    Set<String> mapIds = new HashSet<>(), dialogueIds = new HashSet<>(), npcIds = new HashSet<>();
    for (var d : dialogues)
      if (!dialogueIds.add(d.id)) throw new IllegalArgumentException("Повтор диалога: " + d.id);
    for (var m : maps)
      if (!mapIds.add(m.id)) throw new IllegalArgumentException("Повтор карты: " + m.id);
    for (var m : maps) {
      Set<String> props = new HashSet<>();
      for (var p : m.objects) {
        if (!props.add(p.id)) throw new IllegalArgumentException("Повтор объекта: " + p.id);
        if (p.target != null && !mapIds.contains(p.target))
          throw new IllegalArgumentException("Сломанный переход: " + p.id);
      }
      for (var n : m.npcs) {
        if (!npcIds.add(n.id) || !dialogueIds.contains(n.dialogue) || n.route.length < 2)
          throw new IllegalArgumentException("NPC: " + n.id);
      }
    }
    for (var d : dialogues)
      for (var c : d.choices)
        if (!c.next.isEmpty() && !dialogueIds.contains(c.next))
          throw new IllegalArgumentException("Ссылка диалога: " + c.next);
  }
}
