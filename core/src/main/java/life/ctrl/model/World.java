package life.ctrl.model;

import java.util.*;
import life.ctrl.content.Content;

public final class World {
  public static final float STEP = 1f / 60f, SPEED = 64f, RADIUS = 4f;
  public final Content content;
  public final GameState state;
  public boolean moving;
  private int cacheDay = -1, cachePlannedDay = -1;
  private String cacheMap = "", cacheGirl = "", cacheVenue = "";
  private final ArrayList<Content.Npc> activeNpcs = new ArrayList<>();

  public List<Content.Npc> npcs() {
    if (cacheDay != state.day()
        || cachePlannedDay != state.plannedDay
        || !cacheMap.equals(state.map)
        || !cacheGirl.equals(state.plannedGirl)
        || !cacheVenue.equals(state.plannedVenue)) {
      cacheDay = state.day();
      cachePlannedDay = state.plannedDay;
      cacheMap = state.map;
      cacheGirl = state.plannedGirl;
      cacheVenue = state.plannedVenue;
      activeNpcs.clear();
      for (var m : content.maps)
        for (var n : m.npcs) {
          boolean meeting = n.id.equals(state.plannedGirl) && state.plannedDay == state.day();
          if (meeting ? state.map.equals(state.plannedVenue) : m.id.equals(state.map))
            activeNpcs.add(n);
        }
    }
    return activeNpcs;
  }

  public World(Content content, GameState state) {
    this.content = content;
    this.state = state;
    validateState();
  }

  public Content.MapDef map() {
    return content.map(state.map);
  }

  public void validateState() {
    if (state.pinux == null) throw new IllegalArgumentException("Pinux session");
    state.pinux.validate();
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
    if (state.money < 0
        || state.rentDebt < 0
        || state.rentThroughDay < 3
        || !validNeed(state.health)
        || !validNeed(state.hunger)
        || !validNeed(state.energy)
        || !validNeed(state.mood)
        || !Double.isFinite(state.gameMinutes)
        || state.gameMinutes < 0
        || state.gameMinutes > 1e7
        || state.items == null
        || state.relationships == null
        || state.paidJobs == null
        || state.transactions == null
        || state.activeJob == null
        || state.activeLedger == null
        || state.codeSource == null
        || state.codeSource.length() > 4096
        || state.typedCount < 0
        || state.typedCount > state.codeSource.length()
        || state.partnerId == null
        || state.plannedGirl == null
        || state.plannedVenue == null
        || state.trackedGirl == null
        || state.jobStage < 0
        || state.jobStage > 5) throw new IllegalArgumentException("Некорректные показатели");
    if (state.codingXp < 0
        || state.completedOrders < 0
        || state.shipped == null
        || state.upgrades == null
        || state.customCases == null
        || state.revisions == null
        || state.revisions.size() > 8
        || state.customCases.size() > 20
        || state.rushPromises == null
        || state.lastClientMessage == null
        || state.workNotice == null) throw new IllegalArgumentException("Некорректная карьера");
    for (var rev : state.revisions)
      if (rev == null || rev.source == null || rev.source.length() > 4096 || rev.label == null)
        throw new IllegalArgumentException("Некорректная версия проекта");
    for (var item : state.items.entrySet())
      if (item.getKey() == null || item.getValue() == null || item.getValue() <= 0)
        throw new IllegalArgumentException("Некорректный инвентарь");
    for (var relation : state.relationships.values())
      if (relation == null
          || relation.affinity < 0
          || relation.affinity > 100
          || relation.trust < 0
          || relation.trust > 100
          || relation.dates < 0
          || relation.sharedNights < 0
          || relation.memory == null
          || relation.arcStep < 0
          || relation.arcStep > 3
          || relation.decisions == null
          || relation.decisions.size() != relation.arcStep
          || relation.activities == null
          || relation.activities.size() > 3
          || relation.memories == null
          || relation.memories.size() > 32
          || relation.promise == null
          || relation.brokenPromise == null)
        throw new IllegalArgumentException("Некорректные отношения");
    for (var relation : state.relationships.values()) {
      for (var m : relation.memories)
        if (m == null
            || m.day < 1
            || m.title == null
            || m.text == null
            || m.title.length() > 150
            || m.text.length() > 2000)
          throw new IllegalArgumentException("Некорректное воспоминание");
      for (var a : relation.activities.entrySet())
        if (a.getKey() == null || a.getValue() == null || a.getValue() < 1)
          throw new IllegalArgumentException("Некорректное занятие");
    }
    if (blocked(state.x, state.y))
      throw new IllegalArgumentException("Персонаж внутри препятствия");
    if (state.hasLetter && state.deliveredLetter)
      throw new IllegalArgumentException("Противоречивое состояние письма");
  }

  private static boolean validNeed(double value) {
    return Double.isFinite(value) && value >= 0 && value <= 100;
  }

  public void tick(float dx, float dy) {
    state.elapsed += STEP;
    Life.advance(state, STEP * .5);
    Social.expire(state);
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

  public static boolean intersects(float x, float y, float[] r) {
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
    if (n.id.equals(state.plannedGirl) && state.map.equals(state.plannedVenue)) {
      out.x = state.map.equals("park") ? 264 : 328;
      out.y = state.map.equals("park") ? 155 : 216;
      out.direction = 1;
      out.moving = false;
      return;
    }
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
    for (var n : npcs()) {
      position(n, a);
      double dist = Math.hypot(state.x - a.x, state.y - a.y);
      if (dist < best) {
        best = dist;
        found = n;
      }
    }
    return found;
  }

  /** Nearby fixtures win over a passing NPC, consistently in the prompt and input handler. */
  public Content.Npc interactionNpc() {
    var prop = nearestProp();
    if (prop != null
        && Math.hypot(state.x - prop.x, state.y - prop.y) < 21
        && Set.of("door", "shop", "computer", "rest", "date").contains(prop.action)) return null;
    return nearestNpc();
  }

  public void enter(Content.Prop p) {
    if (!p.action.equals("door")) throw new IllegalArgumentException("Это не дверь");
    String old = state.map;
    float x = state.x, y = state.y;
    state.map = p.target;
    state.x = p.targetX;
    state.y = p.targetY;
    for (var relation : state.relationships.values()) {
      for (var m : relation.memories)
        if (m == null
            || m.day < 1
            || m.title == null
            || m.text == null
            || m.title.length() > 150
            || m.text.length() > 2000)
          throw new IllegalArgumentException("Некорректное воспоминание");
      for (var a : relation.activities.entrySet())
        if (a.getKey() == null || a.getValue() == null || a.getValue() < 1)
          throw new IllegalArgumentException("Некорректное занятие");
    }
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
