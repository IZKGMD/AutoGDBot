package life.ctrl.model;

import life.ctrl.content.*;

/** Individual story branches, shared routines, promises and a bounded durable journal. */
public final class RelationshipJourney {
  private RelationshipJourney() {}

  public static final RelationshipContent DATA = RelationshipContent.load();

  public static void remember(GameState s, Relationship r, String title, String text) {
    r.memory = text;
    r.memories.add(new Relationship.Memory(s.day(), title, text));
    if (r.memories.size() > 32) r.memories.remove(0);
  }

  public static boolean present(GameState s, LifeContent.Girl g) {
    return s.map.equals(g.homeMap)
        || (s.partnerId.equals(g.id) && s.map.equals("room"))
        || (s.plannedGirl.equals(g.id) && s.plannedDay == s.day() && s.map.equals(s.plannedVenue));
  }

  public static String nextReason(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    if (r.arcStep >= 3)
      return "Вы прошли эту историю. Общие занятия и воспоминания остаются доступны.";
    if (!r.met) return "Сначала познакомьтесь.";
    if (!present(s, g)) return "Личный разговор — при встрече. Найди её через контакты.";
    if (!r.promise.isEmpty())
      return "Сначала выполни обещание: "
          + promiseLabel(g.id, r.promise)
          + ". До конца дня "
          + r.promiseDue
          + ".";
    if (!r.brokenPromise.isEmpty()) return "Сначала обсудите невыполненное обещание.";
    if (r.lastArcDay == s.day())
      return "Сегодня вы уже обсудили важное. Продолжение — в другой день.";
    if (r.arcStep == 0 && (r.dates < 1 || r.trust < 16))
      return "Для первого личного разговора: одно свидание и 16 доверия.";
    if (r.arcStep == 1 && (r.activities.isEmpty() || r.trust < 30))
      return "Для продолжения: совместное занятие и 30 доверия.";
    if (r.arcStep == 2
        && (!s.partnerId.equals(g.id) || r.activities.size() < 2 || r.dates < 4 || r.trust < 50))
      return "Для финала: вы пара, четыре свидания, два разных занятия и 50 доверия.";
    return "";
  }

  public static String chapterText(GameState s, String id) {
    var r = s.relation(id);
    if (r.arcStep >= 3) return "История завершена.";
    String text = DATA.arc(id).chapters[r.arcStep].text;
    if (r.arcStep > 0 && !r.decisions.isEmpty()) {
      String flag = r.decisions.get(r.decisions.size() - 1);
      for (var c : DATA.arc(id).chapters[r.arcStep - 1].choices)
        if (c.flag.equals(flag)) text += "\n\nВы помните свой выбор: «" + c.label + "».";
    }
    return text;
  }

  public static String choose(GameState s, LifeContent.Girl g, int option) {
    String reason = nextReason(s, g);
    if (!reason.isEmpty()) return reason;
    if (option < 0 || option >= 2) return "Выбери ответ.";
    var r = s.relation(g.id);
    var chapter = DATA.arc(g.id).chapters[r.arcStep];
    var choice = chapter.choices[option];
    r.arcStep++;
    r.decisions.add(choice.flag);
    r.lastArcDay = s.day();
    r.affinity = Math.min(100, r.affinity + choice.affinity);
    r.trust = Math.min(100, r.trust + choice.trust);
    if (!choice.promise.isEmpty()) {
      r.promise = choice.promise;
      r.promiseDue = s.day() + 3;
    }
    remember(s, r, chapter.title, choice.text);
    Life.advance(s, 20);
    return choice.text
        + (r.promise.isEmpty()
            ? ""
            : "\n\nОбещание: "
                + promiseLabel(g.id, r.promise)
                + ". До конца дня "
                + r.promiseDue
                + ".");
  }

  public static String promiseLabel(String id, String activity) {
    for (var a : DATA.arc(id).activities) if (a.id.equals(activity)) return a.label;
    return activity;
  }

  public static String activityReason(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    if (!r.met || r.dates < 1) return "Сначала познакомьтесь и проведите первое свидание.";
    if (!present(s, g)) return "Совместное занятие доступно при личной встрече.";
    if (!s.partnerId.isEmpty() && !s.partnerId.equals(g.id))
      return "Сначала обсуди нынешние отношения.";
    if (r.lastActivityDay == s.day())
      return "Вы уже провели время вместе сегодня. Новое занятие — завтра.";
    if (s.energy < 15) return "Сначала отдохни: для встречи нужно хотя бы 15 энергии.";
    return "";
  }

  public static String activity(GameState s, LifeContent.Girl g, int option) {
    String reason = activityReason(s, g);
    if (!reason.isEmpty()) return reason;
    var activities = DATA.arc(g.id).activities;
    if (option < 0 || option >= activities.length) return "Выбери занятие.";
    var a = activities[option];
    if (s.money < a.cost) return "Нужно " + a.cost + " ₽. Есть и бесплатные занятия.";
    var r = s.relation(g.id);
    boolean fresh = !r.activities.containsKey(a.id);
    Economy.spend(s, a.cost, "Вместе: " + g.name + " · " + a.label);
    r.lastActivityDay = s.day();
    r.activities.merge(a.id, 1, Integer::sum);
    r.affinity = Math.min(100, r.affinity + (fresh ? 5 : 2));
    r.trust = Math.min(100, r.trust + (fresh ? 5 : 2));
    Life.advance(s, a.minutes);
    s.mood = Math.min(100, s.mood + 8);
    String text =
        a.text + (fresh ? "" : " Вы возвращаетесь к знакомому занятию — без прежней неловкости.");
    if (r.promise.equals(a.id)) {
      r.promise = "";
      r.promiseDue = -1;
      r.trust = Math.min(100, r.trust + 6);
      text += " Ты сдержал(а) обещание. Это оказалось важнее красивых слов.";
    }
    remember(s, r, a.label, text);
    return text;
  }

  public static String repair(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    if (r.brokenPromise.isEmpty()) return "Нет невыполненного обещания, которое нужно обсудить.";
    if (!present(s, g)) return "Это лучше обсудить лично.";
    String text =
        g.name
            + ": «Спасибо, что не делаешь вид, будто ничего не случилось. Мне нужно время, чтобы"
            + " снова рассчитывать на обещания».";
    r.brokenPromise = "";
    r.trust = Math.min(100, r.trust + 2);
    Life.advance(s, 15);
    remember(s, r, "Честный разговор", text);
    return text;
  }

  public static void expire(GameState s) {
    for (var e : s.relationships.entrySet()) {
      var r = e.getValue();
      if (!r.promise.isEmpty() && s.day() > r.promiseDue) {
        r.brokenPromise = r.promise;
        r.promise = "";
        r.promiseDue = -1;
        r.trust = Math.max(0, r.trust - 5);
        remember(
            s,
            r,
            "Несдержанное обещание",
            "Вы не нашли время: "
                + promiseLabel(e.getKey(), r.brokenPromise)
                + ". Можно обсудить это лично; разговор не отменяет случившегося.");
      }
    }
  }
}
