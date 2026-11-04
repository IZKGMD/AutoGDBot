package life.ctrl.model;

import java.util.ArrayList;
import java.util.List;
import life.ctrl.content.LifeContent;

/** Explicit trade-offs: speed, verification and recovery. No real-time clicking advantage. */
public final class WorkRhythm {
  private WorkRhythm() {}

  public enum Pace {
    STEADY("Обычный", 1, 18, 6),
    SPRINT("Рывок", .75, 34, 14),
    REVIEW("С проверкой", 1.25, 10, 4);

    public final String title;
    public final double time;
    public final int fatigue, energy;

    Pace(String title, double time, int fatigue, int energy) {
      this.title = title;
      this.time = time;
      this.fatigue = fatigue;
      this.energy = energy;
    }
  }

  public static Pace pace(GameState s) {
    return Pace.valueOf(s.workPace);
  }

  public static String blocked(GameState s) {
    if (pace(s) != Pace.SPRINT) return "";
    if (s.energy < 35 || s.hunger < 25 || s.workFatigue > 50)
      return "Для рывка: энергия ≥35, сытость ≥25, усталость ≤50. Поешь, отдохни или выбери обычный"
                 + " темп.";
    return "";
  }

  public static int duration(GameState s, LifeContent.Job j) {
    double penalty = (s.workFatigue >= 60 ? .25 : 0) + (s.energy < 15 || s.hunger < 15 ? .20 : 0);
    return (int)
        Math.ceil(
            j.minutes * (s.upgrades.contains("ssd") ? .85 : 1) * pace(s).time * (1 + penalty));
  }

  public static int energyCost(GameState s) {
    return Math.max(0, pace(s).energy - (s.upgrades.contains("chair") ? 4 : 0));
  }

  public static void finish(GameState s) {
    s.workFatigue = Math.min(100, s.workFatigue + pace(s).fatigue);
    s.energy = Math.max(0, s.energy - energyCost(s));
  }

  public static void recover(GameState s, double amount) {
    s.workFatigue = Math.max(0, s.workFatigue - amount);
  }

  public static String breakTime(GameState s) {
    Life.advance(s, 30);
    s.energy = Math.min(100, s.energy + 12);
    recover(s, 20);
    return "30 минут без экрана. Энергия +12 после расхода времени, рабочая усталость −20. "
        + "Сытость и сроки продолжают идти; сон этот перерыв не заменяет.";
  }

  public static String forecast(GameState s, LifeContent.Job j) {
    int time = duration(s, j);
    int end = s.minutes() + time;
    String text =
        "Финиш: день "
            + (1 + end / 1440)
            + ", "
            + clock(end)
            + ". Работа "
            + time
            + " мин; энергия −"
            + (int) Math.ceil(time * .045 + energyCost(s))
            + ", сытость −"
            + (int) Math.ceil(time * .065)
            + ", усталость +"
            + pace(s).fatigue
            + ".";
    if (s.workFatigue >= 60) text += " Усталость замедляет работу на 25%.";
    if (s.energy < 15 || s.hunger < 15) text += " Недостаток сил или еды: ещё +20% времени.";
    if (s.rushJob && s.activeJob.equals(j.id) && s.jobStage >= 2 && s.jobStage < 5)
      text +=
          " Срочный срок: день "
              + (1 + s.rushDeadline / 1440)
              + ", "
              + clock(s.rushDeadline)
              + (end <= s.rushDeadline ? ". Успеваешь." : ". Премия за срок сгорит.");
    if (!s.plannedGirl.isEmpty() && s.plannedDay == s.day() && end / 1440 > s.minutes() / 1440)
      text += " Работа закончится завтра: сегодняшняя встреча будет пропущена.";
    if (1 + end / 1440 > s.rentThroughDay) text += " Во время работы наступит срок аренды: 3000 ₽.";
    if (s.relationships.values().stream()
        .anyMatch(r -> !r.promise.isEmpty() && 1 + end / 1440 > r.promiseDue))
      text += " До окончания работы истечёт обещание. Проверь обязательства [J].";
    return text;
  }

  public static String clock(int minute) {
    int m = Math.floorMod(minute, 1440);
    return "%02d:%02d".formatted(m / 60, m % 60);
  }

  /** A planning reserve, never an automatic charge or locked balance. */
  public static int reserve(GameState s) {
    int date =
        !s.plannedGirl.isEmpty() && !s.date.active() && !s.plannedVenue.equals("park") ? 850 : 0;
    return Economy.WEEKLY_RENT + s.rentDebt + 440 + date;
  }

  public static List<String> agenda(GameState s, LifeContent data) {
    List<String> rows = new ArrayList<>();
    rows.add(
        "Аренда: день "
            + (s.rentThroughDay + 1)
            + " · 3000 ₽"
            + (s.rentDebt > 0 ? "; долг " + s.rentDebt + " ₽" : "."));
    if (!s.plannedGirl.isEmpty())
      rows.add(
          data.girl(s.plannedGirl).name
              + ": "
              + (s.date.active() ? "продолжить встречу" : "встреча, день " + s.plannedDay)
              + " · "
              + (s.plannedVenue.equals("park") ? "сквер" : "кафе")
              + ". [R] — контакты.");
    for (var g : data.girls) {
      var r = s.relationships.get(g.id);
      if (r == null) continue;
      if (!r.promise.isEmpty())
        rows.add(
            g.name
                + ": "
                + RelationshipJourney.promiseLabel(g.id, r.promise)
                + ". До конца дня "
                + r.promiseDue
                + ".");
      if (!r.brokenPromise.isEmpty()) rows.add(g.name + ": нужно обсудить невыполненное обещание.");
    }
    if (rows.size() == 1) rows.add("Встреч пока нет. [R] — познакомиться и договориться о вечере.");
    return List.copyOf(rows);
  }
}
