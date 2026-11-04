package life.ctrl.model;

import java.util.ArrayList;
import life.ctrl.content.*;

/**
 * A saved three-moment date. Pay once on arrival, spend time per moment, reward once at the end.
 */
public final class DateStory {
  private DateStory() {}

  public static final DateContent DATA = DateContent.load();

  public static final class Session {
    public String girl = "", venue = "", episode = "", response = "";
    public int moment, startedDay, affinity, trust;
    public boolean waiting;
    public ArrayList<Integer> choices = new ArrayList<>();

    public boolean active() {
      return !girl.isEmpty();
    }
  }

  public static String start(GameState s, LifeContent.Girl g) {
    if (s.date.active()) return s.date.girl.equals(g.id) ? "" : "Сначала закончи начатую встречу.";
    if (!s.plannedGirl.equals(g.id) || s.plannedDay != s.day())
      return "Сначала договоритесь о встрече.";
    if (!s.map.equals(s.plannedVenue)) return "Сначала доберись до места встречи.";
    if (!java.util.Arrays.asList(g.venues).contains(s.plannedVenue))
      return "Это место не подходит для встречи.";
    if (s.energy < 15) return "Сначала отдохни: для встречи нужно хотя бы 15 энергии.";
    int cost = s.plannedVenue.equals("park") ? 0 : 850;
    if (s.money < cost)
      return "На встречу нужно 850 ₽. Через «Встреча / перенос» можно выбрать бесплатный сквер.";
    var r = s.relation(g.id);
    var d = new Session();
    d.girl = g.id;
    d.venue = s.plannedVenue;
    d.startedDay = s.day();
    d.episode = DATA.girl(g.id).episodes[r.dates % 3].id;
    Economy.spend(s, cost, "Свидание: " + g.name);
    s.date = d;
    r.lastDateDay = s.day();
    return "";
  }

  public static DateContent.Episode episode(GameState s) {
    return DATA.episode(s.date.girl, s.date.episode);
  }

  public static String choose(GameState s, int option) {
    var d = s.date;
    if (!d.active() || d.waiting) return "Сначала прочитай ответ.";
    if (!s.map.equals(d.venue)) return "Вернись к месту встречи, чтобы продолжить.";
    if (option < 0 || option > 1) return "Выбери ответ.";
    var b = episode(s).beats[d.moment];
    var c = b.choices[option];
    d.choices.add(option);
    d.affinity += c.affinity;
    d.trust += c.trust;
    d.response = c.response;
    d.waiting = true;
    var r = s.relation(d.girl);
    if (!r.knownDetails.contains(b.detail)) r.knownDetails.add(b.detail);
    Life.advance(s, 30);
    return d.response;
  }

  public static String next(GameState s) {
    var d = s.date;
    if (!d.active() || !d.waiting) return "Сначала выбери ответ.";
    if (!s.map.equals(d.venue)) return "Вернись к месту встречи, чтобы продолжить.";
    if (d.moment < 2) {
      d.moment++;
      d.waiting = false;
      d.response = "";
      return "";
    }
    var e = episode(s);
    var r = s.relation(d.girl);
    boolean first = r.dateExperiences.isEmpty();
    boolean fresh = !r.dateExperiences.containsKey(e.id);
    r.dateExperiences.merge(e.id, 1, Integer::sum);
    int a = (fresh ? 9 : 5) + d.affinity, t = (fresh ? 7 : 3) + d.trust;
    r.affinity = Math.min(100, r.affinity + a);
    r.trust = Math.min(100, r.trust + t);
    r.lastDateDay = s.day();
    r.dates++;
    s.firstDateFinished = true;
    WorkRhythm.recover(s, 15);
    s.mood = Math.min(100, s.mood + 8);
    if (!d.venue.equals("park")) {
      s.hunger = Math.min(100, s.hunger + 35);
      s.ateFirstMeal = true;
    }
    StringBuilder memory = new StringBuilder(e.atmosphere);
    for (int i = 0; i < 3; i++)
      memory.append("\n\n").append(e.beats[i].choices[d.choices.get(i)].response);
    if (first)
      memory
          .append("\n\n")
          .append(
              switch (d.girl) {
                case "mira" ->
                    "Мира одалживает тебе сборник с листом-закладкой. Он останется на столе у"
                        + " домашнего компьютера.";
                case "asya" ->
                    "Ася отдаёт маленький отпечаток городского снимка. Ты прикрепляешь его над"
                        + " столом в своей комнате.";
                default ->
                    "Ника дарит кассету с ранними записями группы: «Только не суди слишком строго»."
                        + " Ты оставляешь её у кровати.";
              });
    String text =
        memory
            + "\n\n"
            + (fresh ? "Новая общая история" : "Знакомый вечер, новые детали")
            + " · симпатия +"
            + a
            + ", доверие +"
            + t
            + ".";
    RelationshipJourney.remember(s, r, e.title, text);
    s.date = new Session();
    s.plannedGirl = "";
    s.plannedVenue = "";
    s.plannedDay = -1;
    return text;
  }

  public static String leave(GameState s) {
    if (!s.date.active()) return "Сейчас встреча не идёт.";
    var r = s.relation(s.date.girl);
    r.lastDateDay = s.day();
    String text =
        "Вы закончили встречу раньше и предупредили друг друга. Потраченное время и оплачанный"
            + " заказ не возвращаются. Завершённое свидание не засчитано; договориться о новом"
            + " можно завтра.";
    RelationshipJourney.remember(s, r, "Встреча закончилась раньше", text);
    s.date = new Session();
    s.plannedGirl = "";
    s.plannedVenue = "";
    s.plannedDay = -1;
    return text;
  }

  public static void validate(GameState s) {
    var d = s.date;
    if (d == null
        || d.girl == null
        || d.venue == null
        || d.episode == null
        || d.response == null
        || d.choices == null) throw new IllegalArgumentException("Свидание");
    if (!d.active()) {
      if (!d.choices.isEmpty()
          || d.moment != 0
          || d.waiting
          || !d.venue.isEmpty()
          || !d.episode.isEmpty()
          || !d.response.isEmpty()
          || d.affinity != 0
          || d.trust != 0) throw new IllegalArgumentException("Пустое свидание");
      return;
    }
    var e = episode(s);
    if (!d.girl.equals(s.plannedGirl)
        || !d.venue.equals(s.plannedVenue)
        || !java.util.Set.of("park", "cafe", "diner").contains(d.venue)
        || d.startedDay < 1
        || d.startedDay > s.day()
        || d.moment < 0
        || d.moment > 2
        || d.choices.size() != d.moment + (d.waiting ? 1 : 0))
      throw new IllegalArgumentException("Прогресс свидания");
    int a = 0, t = 0;
    for (int i = 0; i < d.choices.size(); i++) {
      Integer choice = d.choices.get(i);
      if (choice == null || choice < 0 || choice > 1)
        throw new IllegalArgumentException("Ответ на свидании");
      var c = e.beats[i].choices[choice];
      a += c.affinity;
      t += c.trust;
    }
    if (d.affinity != a
        || d.trust != t
        || !d.response.equals(
            d.waiting ? e.beats[d.moment].choices[d.choices.get(d.moment)].response : ""))
      throw new IllegalArgumentException("Результат свидания");
  }
}
