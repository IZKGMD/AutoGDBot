package life.ctrl.model;

import life.ctrl.content.LifeContent;

/** Every scene is initiated by the player and subject to the other adult's own preferences. */
public final class Social {
  private Social() {}

  private static void change(Relationship r, int affection, int trust) {
    r.affinity = Math.max(0, Math.min(100, r.affinity + affection));
    r.trust = Math.max(0, Math.min(100, r.trust + trust));
  }

  public static String meet(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    r.met = true;
    if (g.id.equals("mira")) s.metMira = true;
    if (r.met && !r.memories.isEmpty()) return g.introduction;
    RelationshipJourney.remember(s, r, "Знакомство", g.introduction);
    return g.introduction;
  }

  public static String talk(GameState s, LifeContent.Girl g, int topic) {
    var r = s.relation(g.id);
    if (!r.met) meet(s, g);
    if (r.lastTalkDay == s.day())
      return "На сегодня вы уже поговорили по душам. Новая беседа — завтра; сейчас можно погулять"
          + " или выбрать подарок.";
    var t = g.topics[topic];
    r.lastTalkDay = s.day();
    r.lastTopic = t.id;
    boolean repeated = r.memories.stream().anyMatch(m -> m.title.equals("Разговор: " + t.label));
    change(
        r,
        repeated ? Math.max(1, t.affinity / 2) : t.affinity,
        repeated ? Math.max(0, t.trust / 2) : t.trust);
    Life.advance(s, 15);
    RelationshipJourney.remember(
        s,
        r,
        "Разговор: " + t.label,
        t.response
            + (repeated
                ? " Вы уже обсуждали это. Новый опыт даст больше, чем повторение знакомых слов."
                : ""));
    return r.memory;
  }

  public static String gift(GameState s, LifeContent.Girl g, LifeContent.Item item) {
    var r = s.relation(g.id);
    if (!r.met) return "Сначала познакомьтесь.";
    if (r.lastGiftDay == s.day())
      return "Она благодарит, но просит оставить следующий подарок на другой день.";
    if (s.count(item.id) < 1) return "У тебя нет этого подарка. Купить можно в кофейне.";
    if (!item.kind.equals("gift"))
      return "Это еда для тебя. Для подарка выбери предмет с пометкой «подарок».";
    Life.consume(s, item.id);
    r.lastGiftDay = s.day();
    boolean likes = java.util.Arrays.asList(g.favoriteGifts).contains(item.id);
    change(r, likes ? 7 : 1, likes ? 3 : 0);
    r.memory =
        likes
            ? "Ты запомнил(а), что мне нравится. Спасибо — это очень приятно."
            : "Спасибо. Я не очень увлекаюсь такими вещами, но ценю внимание.";
    RelationshipJourney.remember(s, r, "Подарок: " + item.name, r.memory);
    return r.memory;
  }

  public static String invite(GameState s, LifeContent.Girl g, String venue) {
    var r = s.relation(g.id);
    if (!s.plannedGirl.isEmpty()) return "У тебя уже назначена встреча. Посмотри цель на экране.";
    if (!r.met || r.affinity < 12 || r.trust < 8)
      return g.notReady + " Поговорите по душам и узнайте её интересы.";
    if (r.lastDateDay == s.day())
      return "Сегодня вы уже виделись. Она предлагает встретиться завтра.";
    if (!java.util.Arrays.asList(g.venues).contains(venue))
      return "Это место ей не подходит. Выбери другой вариант.";
    if (!s.partnerId.isEmpty() && !s.partnerId.equals(g.id))
      return "Сначала обсуди завершение нынешних отношений. Здесь знакомства не превращаются в"
          + " тайные параллельные романы.";
    s.plannedGirl = g.id;
    s.plannedVenue = venue;
    s.plannedDay = s.day();
    return "Она согласилась. Место: "
        + venueName(venue)
        + ". Иди к отмеченному столику; она дождётся до конца дня.";
  }

  public static String cancel(GameState s, LifeContent.Girl g) {
    if (s.date.active()) return "Встреча уже началась. Продолжи её или попрощайтесь лично.";
    if (!s.plannedGirl.equals(g.id)) return "С этой девушкой встреча не назначена.";
    s.plannedGirl = "";
    s.plannedVenue = "";
    s.plannedDay = -1;
    String text =
        "Ты предупредил(а), что не придёшь. "
            + g.name
            + " благодарит за сообщение. Когда появится время, можно договориться заново.";
    RelationshipJourney.remember(s, s.relation(g.id), "Встреча отменена заранее", text);
    return text;
  }

  public static String reschedule(GameState s, LifeContent.Girl g, String venue, boolean tomorrow) {
    if (s.date.active())
      return "Встреча уже началась. Сменить место можно перед следующим свиданием.";
    if (!s.plannedGirl.equals(g.id)) return "Сначала договоритесь о встрече.";
    if (!java.util.Arrays.asList(g.venues).contains(venue)) return "Это место ей не подходит.";
    s.plannedVenue = venue;
    s.plannedDay = s.day() + (tomorrow ? 1 : 0);
    return "Вы договорились: "
        + venueName(venue)
        + ", день "
        + s.plannedDay
        + ". Перенос не требует доплаты.";
  }

  public static String venueName(String id) {
    return switch (id) {
      case "park" -> "сквер, столик у дерева";
      case "cafe" -> "кофейня «Плёнка»";
      default -> "забегаловка «Ложка»";
    };
  }

  public static boolean canStartDate(GameState s, String venue) {
    return !s.plannedGirl.isEmpty()
        && s.plannedVenue.equals(venue)
        && (s.plannedDay == s.day() || s.date.active());
  }

  public static String date(GameState s, LifeContent.Girl g, int choice) {
    var r = s.relation(g.id);
    if (s.date.active()) return "Продолжи начатую встречу: решения уже сохраняются.";
    if (choice < 0 || choice >= g.dateChoices.length) return "Выбери ответ.";
    if (!s.plannedGirl.equals(g.id) || s.plannedDay != s.day())
      return "Сначала договоритесь о встрече.";
    if (!s.map.equals(s.plannedVenue))
      return "Сначала доберись до места встречи. Маршрут отмечен золотыми точками.";
    int cost = s.plannedVenue.equals("park") ? 0 : 850;
    if (s.money < cost)
      return "Для этой встречи нужно 850 ₽. Можно перенести её в сквер — прогулка бесплатна.";
    var c = g.dateChoices[choice];
    Economy.spend(s, cost, "Свидание: " + g.name);
    boolean food = !s.plannedVenue.equals("park");
    s.plannedGirl = "";
    s.plannedVenue = "";
    r.lastDateDay = s.day();
    r.dates++;
    change(r, c.affinity, c.trust);
    Life.advance(s, 90);
    if (food) {
      s.hunger = Math.min(100, s.hunger + 35);
      s.ateFirstMeal = true;
    }
    s.firstDateFinished = true;
    s.mood = Math.min(100, s.mood + 8);
    if (r.dates >= 2 && r.trust >= 28 && r.storyChapter == 0) {
      r.storyChapter = 1;
      RelationshipJourney.remember(s, r, "Свидание №" + r.dates + " · личная история", g.story);
      return c.response + "\n\n" + g.story;
    }
    RelationshipJourney.remember(s, r, "Свидание №" + r.dates, c.response);
    return c.response;
  }

  public static String commit(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    if (!s.partnerId.isEmpty() && !s.partnerId.equals(g.id))
      return "Сначала заверши нынешние отношения.";
    if (r.dates < 3 || r.trust < 40 || r.affinity < 40)
      return "Ей нужно больше времени: хотя бы три свидания и достаточно доверия. Подарки не"
          + " заменяют общения.";
    if (s.partnerId.equals(g.id)) return "Вы уже вместе.";
    s.partnerId = g.id;
    r.committedDay = s.day();
    RelationshipJourney.remember(s, r, "Вы стали парой", g.commitLine);
    return g.commitLine;
  }

  public static String breakup(GameState s, LifeContent.Girl g) {
    if (!s.partnerId.equals(g.id)) return "Вы не состоите в отношениях.";
    s.partnerId = "";
    var r = s.relation(g.id);
    change(r, -12, -8);
    r.committedDay = -1;
    RelationshipJourney.remember(
        s, r, "Расставание", "Вы честно обсудили расставание. Ей нужно время.");
    return r.memory;
  }

  public static boolean canShareNight(GameState s, LifeContent.Girl g) {
    var r = s.relation(g.id);
    return s.partnerId.equals(g.id)
        && r.dates >= 4
        && r.trust >= 60
        && r.affinity >= 60
        && s.day() > r.committedDay
        && r.lastNightDay != s.day()
        && (s.minutes() % 1440 >= 18 * 60 || s.minutes() % 1440 < 2 * 60);
  }

  public static String shareNight(GameState s, LifeContent.Girl g, boolean mutualYes) {
    var r = s.relation(g.id);
    if (!mutualYes) return "Вы решаете не спешить. Можно просто побыть рядом.";
    if (!s.map.equals("room")) return "Приглашение можно обсудить дома, у кровати.";
    if (!canShareNight(s, g)) return g.notReady;
    r.lastNightDay = s.day();
    r.sharedNights++;
    change(r, 3, 3);
    Life.sleep(s);
    s.mood = Math.min(100, s.mood + 10);
    RelationshipJourney.remember(
        s, r, "Утро вдвоём", "Утром вы проснулись рядом. Это осталось вашим личным воспоминанием.");
    return r.memory;
  }

  public static void expire(GameState s) {
    RelationshipJourney.expire(s);
    if (!s.date.active() && !s.plannedGirl.isEmpty() && s.day() > s.plannedDay) {
      var r = s.relation(s.plannedGirl);
      change(r, 0, -2);
      RelationshipJourney.remember(
          s,
          r,
          "Пропущенная встреча",
          "Встреча не состоялась. Она предлагает договориться в другой день.");
      s.plannedGirl = "";
      s.plannedVenue = "";
    }
  }
}
