package life.ctrl.model;

import java.util.*;
import life.ctrl.content.Content;

public final class World {
  public static final float STEP = 1f / 60f, SPEED = 64f, RADIUS = 4f;
  public final Content content;
  public final GameState state;
  public boolean moving;

  public World(Content content, GameState state) {
    this.content = content;
    this.state = state;
    validateState();
  }

  public Content.MapDef map() {
    return content.map(state.map);
  }

  public void validateState() {
    if (state.name == null
        || state.name.isBlank()
        || state.name.length() > 18
        || !Float.isFinite(state.x)
        || !Float.isFinite(state.y)
        || !Double.isFinite(state.elapsed)
        || state.elapsed < 0
        || state.elapsed > 1e9
        || state.direction < 0
        || state.direction > 3
        || state.outfit < 0
        || state.outfit > 2
        || state.visited == null) throw new IllegalArgumentException("Некорректное сохранение");
    if (blocked(state.x, state.y))
      throw new IllegalArgumentException("Персонаж внутри препятствия");
    if (state.hasLetter && state.deliveredLetter)
      throw new IllegalArgumentException("Противоречивое состояние письма");
  }

  public void tick(float dx, float dy) {
    state.elapsed += STEP;
    float length = (float) Math.hypot(dx, dy);
    moving = length > 0;
    if (!moving) return;
    dx = dx / length * SPEED * STEP;
    dy = dy / length * SPEED * STEP;
    state.direction = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 1 : 2) : (dy < 0 ? 3 : 0);
    if (!blocked(state.x + dx, state.y)) state.x += dx;
    if (!blocked(state.x, state.y + dy)) state.y += dy;
  }

  public boolean blocked(float x, float y) {
    var m = map();
    if (x < RADIUS || y < RADIUS || x > m.width - RADIUS || y > m.height - RADIUS) return true;
    for (var r : m.solids) if (intersects(x, y, r)) return true;
    for (var p : m.objects) if (p.solid != null && intersects(x, y, p.solid)) return true;
    return false;
  }

  private boolean intersects(float x, float y, float[] r) {
    return x + RADIUS > r[0] && x - RADIUS < r[0] + r[2] && y + 2 > r[1] && y - 4 < r[1] + r[3];
  }

  public boolean visible(Content.Prop p) {
    return !p.id.equals("letter") || (!state.hasLetter && !state.deliveredLetter);
  }

  public Content.Prop nearestProp() {
    Content.Prop found = null;
    double best = 29;
    for (var p : map().objects) {
      if (p.action.equals("decoration") || !visible(p)) continue;
      double dist = Math.hypot(state.x - p.x, state.y - p.y);
      if (dist < best) {
        best = dist;
        found = p;
      }
    }
    return found;
  }

  public static final class Actor {
    public float x, y;
    public int direction;
    public boolean moving;
  }

  public void position(Content.Npc n, Actor out) {
    // A loop with a four-second pause at every waypoint; derived from saved world time.
    double duration = 0;
    int count = n.route.length;
    for (int i = 0; i < count; i++)
      duration +=
          4
              + Math.hypot(
                      n.route[(i + 1) % count][0] - n.route[i][0],
                      n.route[(i + 1) % count][1] - n.route[i][1])
                  / 18;
    double time = state.elapsed % duration;
    for (int i = 0; i < count; i++) {
      float[] a = n.route[i], b = n.route[(i + 1) % count];
      double travel = Math.hypot(b[0] - a[0], b[1] - a[1]) / 18;
      if (time < 4 + travel) {
        float t = (float) Math.max(0, (time - 4) / travel);
        out.x = a[0] + (b[0] - a[0]) * t;
        out.y = a[1] + (b[1] - a[1]) * t;
        out.direction =
            time < 4
                ? 0
                : Math.abs(b[0] - a[0]) > Math.abs(b[1] - a[1])
                    ? (b[0] < a[0] ? 1 : 2)
                    : (b[1] < a[1] ? 3 : 0);
        out.moving = time >= 4;
        return;
      }
      time -= 4 + travel;
    }
  }

  public Content.Npc nearestNpc() {
    Content.Npc found = null;
    double best = 28;
    Actor a = new Actor();
    for (var n : map().npcs) {
      position(n, a);
      double dist = Math.hypot(state.x - a.x, state.y - a.y);
      if (dist < best) {
        best = dist;
        found = n;
      }
    }
    return found;
  }

  public void enter(Content.Prop p) {
    if (!p.action.equals("door")) throw new IllegalArgumentException("Это не дверь");
    String old = state.map;
    float x = state.x, y = state.y;
    state.map = p.target;
    state.x = p.targetX;
    state.y = p.targetY;
    if (blocked(state.x, state.y)) {
      state.map = old;
      state.x = x;
      state.y = y;
      throw new IllegalStateException("Выход двери заблокирован");
    }
    if (!state.visited.contains(state.map)) state.visited.add(state.map);
    state.direction = p.target.equals("room") ? 3 : 0;
    moving = false;
  }

  public String npcDialogue(Content.Npc npc) {
    return switch (npc.id) {
      case "mira" -> state.hasLetter ? "miraFound" : state.deliveredLetter ? "miraAgain" : "mira";
      case "arkady" -> state.metArkady ? "arkadyAgain" : "arkady";
      default -> npc.dialogue;
    };
  }

  public String propDialogue(Content.Prop p) {
    return switch (p.action) {
      case "computer" -> "pc";
      case "plant" -> state.wateredPlant ? "plantAgain" : "plant";
      case "boxes" -> state.unpacked ? "boxesAgain" : "boxes";
      case "post" -> state.signedMailbox ? "postAgain" : "post";
      default -> p.action;
    };
  }

  public void effect(String effect) {
    switch (effect) {
      case "meetMira" -> state.metMira = true;
      case "meetArkady" -> state.metArkady = true;
      case "meetLev" -> state.metLev = true;
      case "inspectPc" -> state.inspectedPc = true;
      case "askLetter" -> {
        state.metMira = true;
        state.askedLetter = true;
      }
      case "takeLetter" -> {
        if (!state.deliveredLetter) state.hasLetter = true;
      }
      case "deliverLetter" -> {
        if (state.hasLetter) {
          state.hasLetter = false;
          state.deliveredLetter = true;
          state.metMira = true;
        }
      }
      case "waterPlant" -> state.wateredPlant = true;
      case "unpack" -> state.unpacked = true;
      case "signMailbox" -> state.signedMailbox = true;
      case "readBoard" -> state.readBoard = true;
      case "rest" -> state.elapsed += 30;
      case "water", "" -> {}
      default -> throw new IllegalArgumentException("Неизвестный эффект: " + effect);
    }
  }
}
