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
    if (!s.date.active() && s.hunger < 15) {
      if (Arrays.stream(life.items).anyMatch(i -> i.kind.equals("food") && s.count(i.id) > 0))
        return new Goal(
            s.map,
            s.x,
            s.y,
            "Сначала поешь · карманы [I]",
            "Еда уже куплена. Нажми I и съешь её перед работой или встречей.");
      return prop(
          c,
          "diner",
          "counter",
          "Сначала поесть в «Ложке»",
          "[E] у стойки. Если денег меньше 250 ₽, доступен бесплатный суп.");
    }
    if (!s.date.active() && s.energy < 15)
      return prop(
          c,
          "room",
          "bed",
          "Сначала восстановить силы",
          "У кровати: сон на 90 минут или до 08:00. Текущий заказ сохранён.");
    if (!s.plannedGirl.isEmpty() && s.plannedDay > s.day())
      return prop(
          c,
          "room",
          "bed",
          "Встреча завтра: " + life.girl(s.plannedGirl).name,
          "День " + s.plannedDay + ". Можно заняться делами или поспать. [R] — перенести встречу.");
    if (!s.plannedGirl.isEmpty())
      return prop(
          c,
          s.plannedVenue,
          "date",
          (s.date.active() ? "Продолжить встречу: " : "Встреча: ") + life.girl(s.plannedGirl).name,
          s.date.active()
              ? "[E] у столика. Решения сохранены, повторной оплаты нет."
              : "Иди к золотой метке. [E] у столика начнёт свидание.");
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
    if (!s.firstDateFinished
        || s.goalChoice == 2
        || (s.goalChoice == 0
            && Career.projectStage(s) == 3
            && !s.chapterOneFinished
            && s.relationships.values().stream().noneMatch(r -> r.arcStep == 3))) {
      String id =
          !s.partnerId.isEmpty() ? s.partnerId : s.trackedGirl.isEmpty() ? "mira" : s.trackedGirl;
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
                    ? socialHint(s, g)
                    : "Подойди к девушке и нажми [E]. Никто не ждёт идеальной реплики.");
          }
    }
    if (s.goalChoice != 2 && s.firstDateFinished && Career.projectStage(s) < 3 && s.money >= 300)
      return prop(
          c,
          "room",
          "pc",
          "Свой бот · этап " + (Career.projectStage(s) + 1) + "/3",
          "[E] у ПК → Свой бот. Тесты → предпросмотр → запуск за 300 ₽.");
    if (s.chapterOneFinished && s.goalChoice == 0)
      return new Goal(
          s.map,
          s.x,
          s.y,
          "Глава завершена · это твой район",
          "[J] → Моя история → Итог главы. Можно продолжать работу и отношения.");
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

  private static String socialHint(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    if (!r.brokenPromise.isEmpty())
      return "При встрече → Ваша история → обсудить невыполненное обещание.";
    if (!r.promise.isEmpty())
      return "Общие занятия → "
          + RelationshipJourney.promiseLabel(g.id, r.promise)
          + ". До конца дня "
          + r.promiseDue
          + ".";
    if (r.arcStep < 3 && r.dates >= 1 && r.trust >= 16)
      return "Ваша история → личный разговор. Требования продолжения указаны внутри; общие занятия"
                 + " укрепляют доверие.";
    return "Разговор по душам → приглашение. Сквер бесплатный. [J] → Моя история: следующий шаг.";
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
