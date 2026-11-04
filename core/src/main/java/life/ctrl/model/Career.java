package life.ctrl.model;

import java.util.*;
import life.ctrl.content.LifeContent;

/** Career rules are deterministic, inspectable, and saved independently of UI. */
public final class Career {
  private Career() {}

  public record Upgrade(String id, String title, int price, String description) {}

  public static final List<Upgrade> GEAR =
      List.of(
          new Upgrade(
              "ssd",
              "SSD из мастерской",
              4200,
              "Работа занимает на 15% меньше времени. Установка включена."),
          new Upgrade(
              "chair",
              "Кресло с поддержкой спины",
              2800,
              "Заказ расходует на 4 энергии меньше при любом темпе. В комнате появится кресло."));

  public static int tier(GameState s) {
    return s.codingXp >= 8 ? 2 : s.codingXp >= 3 ? 1 : 0;
  }

  public static String rank(GameState s) {
    return switch (tier(s)) {
      case 2 -> "Разработчик";
      case 1 -> "Фрилансер";
      default -> "Первый коммит";
    };
  }

  public static int paidCountToday(GameState s) {
    return (int) s.paidJobs.stream().filter(x -> x.startsWith(s.day() + ":")).count();
  }

  public static int projectStage(GameState s) {
    int n = 0;
    for (String id : List.of("board", "remind", "support")) {
      if (!s.shipped.contains(id)) break;
      n++;
    }
    return n;
  }

  public static String blocked(GameState s, LifeContent.Job j) {
    if (j.once) {
      int stage = projectStage(s);
      int required = List.of("board", "remind", "support").indexOf(j.id);
      if (s.shipped.contains(j.id)) return "Этот этап уже запущен.";
      if (required != stage) return "Сначала заверши предыдущий этап своего бота.";
      if (s.completedOrders < 1) return "Сначала выполни первый заказ для клиента.";
    } else {
      if (paidCountToday(s) >= 3)
        return "Три клиентских заказа на сегодня выполнены. Время для своего проекта или прогулки.";
      if (Coding.paidToday(s, j.id)) return "Этот заказ уже оплачен сегодня.";
    }
    if (tier(s) < j.tier)
      return "Нужно опыта: " + (j.tier == 1 ? 3 : 8) + ". Сейчас " + s.codingXp + ".";
    return "";
  }

  public static List<LifeContent.Job> offers(GameState s, LifeContent data) {
    List<LifeContent.Job> result = new ArrayList<>();
    for (var j : data.jobs) if (!j.once && j.tier == 0) result.add(j);
    List<LifeContent.Job> advanced =
        Arrays.stream(data.jobs).filter(j -> !j.once && j.tier > 0 && j.tier <= tier(s)).toList();
    if (!advanced.isEmpty())
      for (int i = 0; i < Math.min(3, advanced.size()); i++)
        result.add(advanced.get(Math.floorMod(s.day() + i - 1, advanced.size())));
    return result;
  }

  public static int duration(GameState s, LifeContent.Job j) {
    return WorkRhythm.duration(s, j);
  }

  public static int bonus(GameState s, LifeContent.Job j) {
    if (j.once) return 0;
    return (s.customCases.size() >= 2 ? (WorkRhythm.pace(s) == WorkRhythm.Pace.REVIEW ? 200 : 100) : 0)
        + (s.rushJob && s.minutes() + duration(s, j) <= s.rushDeadline ? j.pay / 4 : 0);
  }

  public static String buy(GameState s, String id) {
    var gear = GEAR.stream().filter(g -> g.id.equals(id)).findFirst().orElseThrow();
    if (s.upgrades.contains(id)) return "Уже установлено.";
    if (!Economy.spend(s, gear.price, gear.title))
      return "Не хватает денег. Улучшение подождёт; на работу можно брать заказы и со старым ПК.";
    s.upgrades.add(id);
    return gear.title + " установлено. " + gear.description;
  }

  public static void settleProject(GameState s) {
    if (projectStage(s) < 3) return;
    if (s.projectIncomeDay < 0) {
      s.projectIncomeDay = s.day();
      return;
    }
    int days = s.day() - s.projectIncomeDay;
    if (days <= 0) return;
    Economy.earn(s, Math.min(days, 365) * 160, "Поддержка районного бота: " + days + " дн.");
    s.projectIncomeDay = s.day();
  }

  public static String community(GameState s, String map) {
    if (map.equals("cafe") && s.shipped.contains("queue"))
      return "Ася: «С твоей очередью я теперь успеваю выйти поснимать».";
    if (map.equals("diner") && s.shipped.contains("refund"))
      return "Тамара: «Возвраты больше не съедают выручку. Спасибо за патч».";
    if (map.equals("diner") && s.shipped.contains("total"))
      return "Тамара узнаёт тебя: «Наш программист! Касса работает».";
    if (map.equals("park") && s.shipped.contains("booking"))
      return "Ника: «В репетиционной теперь порядок с бронированиями».";
    if (projectStage(s) == 3)
      return "В районном чате благодарят за бота. Кто-то уже нашёл потерянные ключи.";
    return "";
  }
}
