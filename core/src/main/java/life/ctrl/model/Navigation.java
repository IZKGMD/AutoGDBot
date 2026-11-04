package life.ctrl.model;

import java.util.*;
import life.ctrl.content.*;

/** One explicit destination, connected by real doors and a walkable path. */
public final class Navigation {
  public record Goal(String map, float x, float y, String title, String hint) {}

  private Navigation() {}

  private static Goal prop(Content c, String map, String id, String title, String hint) {
    for (var p : c.map(map).objects)
      if (p.id.equals(id)) return new Goal(map, p.x, p.y, title, hint);
    throw new IllegalArgumentException(id);
  }

  public static Goal goal(World w, LifeContent life) {
    var s = w.state;
    var c = w.content;
    if (!s.plannedGirl.isEmpty())
      return prop(
          c,
          s.plannedVenue,
          "date",
          "Встреча: " + life.girl(s.plannedGirl).name,
          "Иди к золотой метке. [E] у столика начнёт свидание.");
    if (s.paidJobs.isEmpty())
      return prop(
          c,
          "room",
          "pc",
          "Первый заработок: касса для «Ложки»",
          "[E] у ПК → выбрать правило → набрать код → тесты → оплата.");
    if ((s.hunger < 25 || !s.ateFirstMeal)
        && Arrays.stream(life.items).anyMatch(i -> i.kind.equals("food") && s.count(i.id) > 0))
      return new Goal(
          s.map,
          s.x,
          s.y,
          "Еда куплена — открой карманы [I]",
          "Нажми I и выбери «съесть». Купленная еда пока лежит в сумке.");
    if (s.hunger < 25 || !s.ateFirstMeal)
      return prop(
          c,
          "diner",
          "counter",
          "Поесть в «Ложке»",
          "[E] у стойки. Суп: 220 ₽. Съесть можно в карманах [I].");
    if (s.energy < 25 || (s.firstDateFinished && s.day() == 1 && s.goalChoice == 0))
      return prop(
          c,
          "room",
          "bed",
          "Вернуться в комнату и поспать",
          "[E] у кровати. Сон восстановит силы и начнёт новый день.");
    if (!s.firstDateFinished || s.goalChoice == 2) {
      String id = s.trackedGirl.isEmpty() ? "mira" : s.trackedGirl;
      var g = life.girl(id);
      var r = s.relation(id);
      if (r.lastTalkDay == s.day() && (r.affinity < 12 || r.trust < 8))
        return prop(
            c,
            "room",
            "bed",
            "Дайте друг другу время. Встретьтесь завтра.",
            "[E] у кровати: начать новый день. Неудачная реплика не закрывает отношения.");
      for (var map : c.maps)
        for (var n : map.npcs)
          if (n.id.equals(id)) {
            var a = new World.Actor();
            w.position(n, a);
            return new Goal(
                map.id,
                a.x,
                a.y,
                r.met ? "Пообщаться: " + g.name : "Познакомиться: " + g.name,
                r.met
                    ? "Разговор по душам → приглашение. Сквер бесплатный."
                    : "Подойди к девушке и нажми [E]. Никто не ждёт идеальной реплики.");
          }
    }
    if (Career.paidCountToday(s) >= 3)
      return prop(
          c,
          "room",
          "bed",
          "Работа на сегодня закончена",
          "Три заказа сданы. Можно сделать свой проект, встретиться с девушкой или поспать.");
    return prop(
        c,
        "room",
        "pc",
        "Новые заказы: заработай на свою жизнь",
        "На ПК три заказа в день. [B] — деньги и ближайшая аренда.");
  }

  public static Goal localGoal(World w, Goal finalGoal) {
    if (w.state.map.equals(finalGoal.map)) return finalGoal;
    record Hop(String map, Content.Prop first) {}
    ArrayDeque<Hop> q = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    q.add(new Hop(w.state.map, null));
    while (!q.isEmpty()) {
      var h = q.remove();
      if (!seen.add(h.map)) continue;
      for (var p : w.content.map(h.map).objects)
        if (p.action.equals("door")) {
          var first = h.first == null ? p : h.first;
          if (p.target.equals(finalGoal.map))
            return new Goal(
                w.state.map,
                first.x,
                first.y,
                finalGoal.title,
                "Следующий проход: " + w.content.map(first.target).name);
          q.add(new Hop(p.target, first));
        }
    }
    return finalGoal;
  }

  public static List<float[]> route(World w, float targetX, float targetY) {
    int grid = 4, cols = w.map().width / grid, rows = w.map().height / grid;
    int start = Math.round(w.state.y / grid) * cols + Math.round(w.state.x / grid);
    int[] previous = new int[cols * rows];
    Arrays.fill(previous, -1);
    if (start < 0 || start >= previous.length) return List.of();
    previous[start] = start;
    ArrayDeque<Integer> todo = new ArrayDeque<>();
    todo.add(start);
    int goal = -1;
    while (!todo.isEmpty()) {
      int at = todo.remove(), x = at % cols, y = at / cols;
      if (Math.hypot(x * grid - targetX, y * grid - targetY) < 17) {
        goal = at;
        break;
      }
      for (int[] d : DIRS) {
        int nx = x + d[0], ny = y + d[1];
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
        int next = ny * cols + nx;
        if (previous[next] < 0
            && !w.blocked(nx * grid - 2, ny * grid - 2)
            && !w.blocked(nx * grid + 2, ny * grid - 2)
            && !w.blocked(nx * grid - 2, ny * grid + 2)
            && !w.blocked(nx * grid + 2, ny * grid + 2)) {
          previous[next] = at;
          todo.add(next);
        }
      }
    }
    if (goal < 0) return List.of();
    ArrayList<float[]> path = new ArrayList<>();
    for (int p = goal; p != start; p = previous[p])
      path.add(new float[] {p % cols * grid, p / cols * grid});
    Collections.reverse(path);
    return path;
  }

  private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
