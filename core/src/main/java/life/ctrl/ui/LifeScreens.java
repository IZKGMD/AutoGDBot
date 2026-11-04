package life.ctrl.ui;

import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.render.PixelCanvas;

/** Narrative, work and social UI. Mutations always go through the domain systems. */
public final class LifeScreens {
  private final CtrlLife game;
  private final PixelCanvas c;
  private final Workbench workbench;
  public final LifeContent data;
  public int introPage, codePage, shopPage;
  public String girlId = "mira", socialPage = "home", socialMessage = "", mapId = "";
  public boolean remote;
  public Coding.Report report;
  public double previewInput = 2;
  private double cachedInput = Double.NaN, cachedResult;
  private String cachedSource = "";
  public String sceneTitle = "", sceneText = "";
  public Mode sceneReturn = Mode.WORLD;
  private static final String TEXT = "efe4c6",
      SOFT = "a7b5b6",
      GOLD = "edbb71",
      GREEN = "a5c19c",
      RED = "e49a83";

  public LifeScreens(CtrlLife game, PixelCanvas canvas) {
    this.game = game;
    c = canvas;
    data = LifeContent.load();
    workbench = new Workbench(game, this, canvas);
  }

  private GameState s() {
    return game.world().state;
  }

  private void btn(String text, int x, int y, int w, Runnable run) {
    game.uiButton(text, x, y, w, 22, true, run);
  }

  private void btn(String text, int x, int y, int w, boolean enabled, Runnable run) {
    game.uiButton(text, x, y, w, 22, enabled, run);
  }

  private void mode(Mode mode) {
    game.switchMode(mode);
  }

  private void persist() {
    game.refreshState();
  }

  private void frame(String eyebrow, String title) {
    c.rect(0, 0, 480, 270, "15232eee");
    c.panel(12, 10, 456, 250);
    c.small(eyebrow, 26, 22, GREEN);
    c.text(title, 26, 39, TEXT);
  }

  public void scene(String title, String text, Mode back) {
    sceneTitle = title;
    sceneText = text;
    sceneReturn = back;
    mode(Mode.SCENE);
    persist();
  }

  public void draw(Mode mode) {
    switch (mode) {
      case INTRO -> intro();
      case SHOP -> shop();
      case IDE -> coding();
      case SOCIAL -> social();
      case CONTACTS -> contacts();
      case REST -> rest();
      case SCENE -> scene();
      case NIGHT -> night();
      case BUDGET -> budget();
      case JOURNAL -> journal();
      case MAP -> map();
      case BAG -> bag();
      default -> {}
    }
  }

  public void openSocial(String id, boolean phone) {
    girlId = id;
    remote = phone;
    socialPage = "home";
    socialMessage = "";
    var g = data.girl(id);
    if (!phone && !s().relation(id).met) socialMessage = Social.meet(s(), g);
    if (!phone && s().relation(id).met && socialMessage.isEmpty())
      socialMessage = Career.community(s(), s().map);
    mode(Mode.SOCIAL);
    persist();
  }

  public void openCode() {
    s().inspectedPc = true;
    codePage = s().activeJob.isEmpty() || s().jobStage < 2 || s().jobStage == 5 ? 0 : 2;
    report = null;
    mode(Mode.IDE);
    persist();
  }

  public void openDate() {
    if (!Social.canStartDate(s(), s().map)) {
      scene(
          "Столик для встреч",
          "Здесь можно провести свидание. Сначала познакомься с девушкой и пригласи её — лично или"
              + " через контакты [R].",
          Mode.WORLD);
      return;
    }
    girlId = s().plannedGirl;
    remote = false;
    socialPage = "date";
    mode(Mode.SOCIAL);
  }

  public void typeKey() {
    if (codePage == 2 && s().jobStage == 2) {
      Coding.type(s());
    }
  }

  public void intro() {
    String[] titles = {
      "Последняя маршрутка", "Комната за тонкой стеной", "Первый шаг — заработать"
    };
    String[] texts = {
      "Тебе 23 года. В родном городе закрылась фирма, где ты работал(а) в поддержке. Несколько"
          + " недель поиска — и первый удалённый заказ от маленькой забегаловки.\n\n"
          + "С сумкой и старым компьютером ты приехал(а) в Берёзовск на маршрутке. Здесь можно"
          + " начать заново.",
      "Восемь квадратных метров в общежитии: узкая кровать, облупленная стена и стол у розетки."
          + " Кухня и душ общие, в коридоре.\n\n"
          + "У тебя 10000 ₽. Комната оплачена на три ночи. Потом — 3000 ₽ в неделю. Впереди еда,"
          + " работа, знакомства и свои решения.",
      "Сегодня у тебя конкретный план:\n\n"
          + "1. Подойти к столу и нажать E.\n"
          + "2. Сделать заказ «Касса для Ложки» за 650 ₽.\n"
          + "3. Поесть в «Ложке» и познакомиться с соседкой.\n\n"
          + "WASD / стрелки — ходить. E — действие. Золотые точки ведут к цели. J — план, M —"
          + " карта, R — контакты, B — деньги."
    };
    frame("ПЕРВЫЙ ДЕНЬ  /  " + (introPage + 1) + " ИЗ 3", titles[introPage]);
    c.wrap(texts[introPage], 28, 69, 422, TEXT);
    btn(
        introPage == 2 ? "Понятно. К компьютеру →" : "Дальше →",
        28,
        226,
        424,
        () -> {
          if (introPage < 2) {
            introPage++;
            game.resetSelection();
          } else {
            s().introSeen = true;
            mode(Mode.WORLD);
            persist();
          }
        });
  }

  private void scene() {
    frame("СОБЫТИЕ", sceneTitle);
    c.wrap(sceneText, 28, 72, 420, TEXT);
    btn("Продолжить", 28, 226, 424, () -> mode(sceneReturn));
  }

  private void shop() {
    boolean cafe = s().map.equals("cafe");
    frame(
        cafe ? "КОФЕЙНЯ «ПЛЁНКА»" : "ЗАБЕГАЛОВКА «ЛОЖКА»", "У стойки · у тебя " + s().money + " ₽");
    c.small(
        shopPage == 0
            ? "Купленная еда лежит в карманах [I]."
            : "Подарок полезен, когда ты знаешь её интересы.",
        28,
        63,
        SOFT);
    List<LifeContent.Item> list =
        Arrays.stream(data.items)
            .filter(i -> shopPage == 0 ? i.kind.equals("food") : i.kind.equals("gift"))
            .toList();
    int y = 82;
    for (var item : list) {
      if (y > 181) break;
      String label = item.name + " · " + item.price + " ₽  [есть " + s().count(item.id) + "]";
      btn(label, 28, y, 424, () -> scene("Покупка", Life.buy(s(), item), Mode.SHOP));
      y += 22;
    }
    btn(
        shopPage == 0 ? "Подарки →" : "← Еда",
        28,
        196,
        206,
        () -> {
          shopPage = 1 - shopPage;
          game.resetSelection();
        });
    btn(
        "Бесплатный суп",
        246,
        196,
        206,
        () -> scene("Помощь на трудный день", Life.freeMeal(s()), Mode.SHOP));
    btn("Отойти от стойки", 28, 226, 424, () -> mode(Mode.WORLD));
  }

  private void bag() {
    frame("КАРМАНЫ  [I]", "Еда и подарки");
    int y = 66;
    for (var item : data.items) {
      if (s().count(item.id) == 0) continue;
      if (y > 202) break;
      game.uiButton(
          item.name
              + " ×"
              + s().count(item.id)
              + (item.kind.equals("food") ? " — съесть" : " — подарок"),
          28,
          y,
          424,
          18,
          item.kind.equals("food"),
          () -> scene("Перекус", Life.eat(s(), item), Mode.BAG));
      y += 19;
    }
    if (s().items.isEmpty())
      c.wrap(
          "Сейчас пусто. Еду можно купить в «Ложке» или в кофейне. Подарки передаются при личной"
              + " встрече.",
          28,
          78,
          420,
          SOFT);
    btn("Закрыть", 28, 226, 424, () -> mode(Mode.WORLD));
  }

  private void rest() {
    frame("КОМНАТА В ОБЩЕЖИТИИ", "Передохнуть");
    c.wrap(
        "Энергия: "
            + (int) s().energy
            + " / 100. Сон восстанавливает силы и здоровье. Сытость за ночь уменьшится. Следующая"
            + " аренда — день "
            + (s().rentThroughDay + 1)
            + ", 3000 ₽.",
        28,
        74,
        420,
        SOFT);
    btn(
        "Завершить день · проснуться в 08:00",
        28,
        140,
        424,
        () -> {
          Life.sleep(s());
          Social.expire(s());
          scene(
              "После сна",
              "Ты выспался(ась). День "
                  + s().day()
                  + ", "
                  + s().clock()
                  + ". Проверь сытость и выбери цель в журнале [J].",
              Mode.WORLD);
        });
    if (!s().partnerId.isEmpty())
      btn(
          "Поговорить о вечере вдвоём",
          28,
          168,
          424,
          () -> {
            girlId = s().partnerId;
            socialPage = "night";
            remote = false;
            mode(Mode.SOCIAL);
          });
    btn(
        "Посидеть 30 минут · энергия +12",
        28,
        196,
        424,
        () -> {
          Life.advance(s(), 30);
          s().energy = Math.min(100, s().energy + 12);
          scene(
              "Короткий отдых",
              "Стало немного легче. Но полноценный сон это не заменяет.",
              Mode.WORLD);
        });
    btn("Встать", 28, 226, 424, () -> mode(Mode.WORLD));
  }

  private void budget() {
    frame("БЮДЖЕТ  [B]", s().money + " ₽ · долг " + s().rentDebt + " ₽");
    c.wrap(
        "Аренда: 3000 ₽ раз в неделю. Следующая — день "
            + (s().rentThroughDay + 1)
            + ". Еда: примерно 400–650 ₽ в день.\n"
            + "Кодинг: 650–1500 ₽, до 3 заказов/день. Бонусы отдельно.\n"
            + "Сквер бесплатно, столик 850 ₽. Запущенный бот: +160 ₽/день.",
        28,
        66,
        420,
        SOFT);
    int y = 137;
    var entries = s().transactions;
    for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 4); i--) {
      var e = entries.get(i);
      c.small(
          "Д" + e.day + "  " + (e.amount >= 0 ? "+" : "") + e.amount + " ₽  " + e.reason,
          28,
          y,
          e.amount >= 0 ? GREEN : TEXT);
      y += 15;
    }
    btn(
        "Погасить долг",
        28,
        205,
        206,
        s().rentDebt > 0 && s().money > 0,
        () -> scene("Аренда", Economy.repay(s()), Mode.BUDGET));
    btn("Закрыть", 246, 205, 206, () -> mode(Mode.WORLD));
  }

  private void journal() {
    frame("ПЛАН НА ДЕНЬ  [J]", "День " + s().day() + " · " + s().clock());
    var goal = Navigation.goal(game.world(), data);
    c.text(goal.title(), 28, 68, GOLD);
    c.wrap(goal.hint(), 28, 88, 420, TEXT);
    c.small("Золотой маршрут на земле ведёт к нужной двери и объекту.", 28, 130, SOFT);
    btn(
        "Цель: заработок кодингом",
        28,
        151,
        424,
        () -> {
          s().goalChoice = 1;
          mode(Mode.WORLD);
          persist();
        });
    btn(
        "Цель: знакомства и отношения",
        28,
        177,
        424,
        () -> {
          s().goalChoice = 2;
          mode(Mode.CONTACTS);
          persist();
        });
    btn("Показать карту", 28, 203, 206, () -> mode(Mode.MAP));
    btn("К делу →", 246, 203, 206, () -> mode(Mode.WORLD));
  }

  private void map() {
    frame("КАРТА  [M]", "Ты здесь: " + game.world().map().name);
    String id = mapId.isEmpty() ? s().map : mapId;
    var m = game.world().content.map(id);
    float ratio = Math.min(300f / m.width, 151f / m.height),
        w = m.width * ratio,
        h = m.height * ratio,
        ox = 25 + (300 - w) / 2,
        oy = 65 + (151 - h) / 2;
    c.scaledArt(id, ox, oy, w, h);
    for (var p : m.objects)
      if (p.action.equals("door")) {
        float x = ox + p.x * ratio, y = oy + p.y * ratio;
        c.rect(x - 2, y - 2, 5, 5, GOLD);
      }
    if (s().map.equals(id)) c.rect(ox + s().x * ratio - 3, oy + s().y * ratio - 3, 6, 6, "fff2c0");
    var target = Navigation.goal(game.world(), data);
    var local = Navigation.localGoal(game.world(), target);
    if (local.map().equals(id)) {
      float x = ox + local.x() * ratio, y = oy + local.y() * ratio;
      c.text("!", x - 2, y - 10, GOLD);
    }
    c.small("БЕЛЫЙ — ТЫ", 333, 72, TEXT);
    c.small("ЗОЛОТОЙ — ДВЕРИ", 333, 89, GOLD);
    c.small("! — БЛИЖАЙШАЯ ЦЕЛЬ", 333, 106, GOLD);
    c.wrap(target.title(), 333, 133, 116, SOFT);
    btn(
        id.equals("street") ? "Моя локация" : "Улица целиком",
        28,
        226,
        206,
        () -> {
          mapId = id.equals("street") ? s().map : "street";
          game.resetSelection();
        });
    btn("Продолжить путь", 246, 226, 206, () -> mode(Mode.WORLD));
  }

  private void contacts() {
    frame("КОНТАКТЫ  [R]", "Люди, ради которых хочется выйти из дома");
    int y = 76;
    for (var g : data.girls) {
      var r = s().relation(g.id);
      c.art("portrait_" + g.portrait, 28, y - 4);
      String status =
          s().partnerId.equals(g.id)
              ? "вместе"
              : r.met ? "знакомы · свиданий " + r.dates : "ещё не знакомы";
      btn(g.name + ", " + g.age + " · " + status, 69, y, 383, () -> openSocial(g.id, true));
      y += 46;
    }
    btn("Вернуться", 28, 226, 424, () -> mode(Mode.WORLD));
  }

  private void social() {
    var g = data.girl(girlId);
    var r = s().relation(g.id);
    frame(
        g.name.toUpperCase() + " · " + g.age + " ЛЕТ",
        switch (socialPage) {
          case "date" -> "Свидание · " + Social.venueName(s().plannedVenue);
          case "night" -> "Остаться вдвоём?";
          default ->
              s().partnerId.equals(g.id) ? "Вы вместе" : "Знакомство, которое может стать важным";
        });
    c.art("portrait_" + g.portrait, 28, 67);
    c.small(
        "Симпатия " + r.affinity + "  Доверие " + r.trust + "  Свидания " + r.dates, 72, 68, GOLD);
    String body =
        socialMessage.isEmpty()
            ? (socialPage.equals("home")
                ? g.about
                : socialPage.equals("night")
                    ? g.nightLine
                    : socialPage.equals("date")
                        ? "Это время для вас двоих. Выбери, как провести разговор."
                        : g.likes)
            : socialMessage;
    c.wrap(body, 72, 85, 378, TEXT);
    switch (socialPage) {
      case "home" -> {
        if (remote) {
          btn(
              "Найти в городе →",
              28,
              150,
              424,
              () -> {
                s().trackedGirl = g.id;
                s().goalChoice = 2;
                mode(Mode.WORLD);
                persist();
              });
          btn(
              "Договориться о встрече",
              28,
              176,
              424,
              r.met,
              () -> {
                socialPage = "invite";
                game.resetSelection();
              });
        } else {
          btn(
              "Поговорить по душам",
              28,
              150,
              206,
              () -> {
                socialPage = "talk";
                socialMessage = "";
                game.resetSelection();
              });
          btn(
              "Передать подарок",
              246,
              150,
              206,
              () -> {
                socialPage = "gift";
                socialMessage = "";
                game.resetSelection();
              });
          btn(
              "Пригласить на свидание",
              28,
              176,
              206,
              () -> {
                socialPage = "invite";
                socialMessage = "";
                game.resetSelection();
              });
          btn(
              "Об отношениях",
              246,
              176,
              206,
              () -> {
                socialPage = "commit";
                socialMessage = "";
                game.resetSelection();
              });
        }
        c.small("Интересы: " + g.likes.substring(0, Math.min(68, g.likes.length())), 28, 206, SOFT);
        btn("До встречи", 28, 231, 424, () -> mode(remote ? Mode.CONTACTS : Mode.WORLD));
      }
      case "talk" -> {
        int y = 151;
        for (int i = 0; i < g.topics.length; i++) {
          int index = i;
          btn(
              g.topics[i].label,
              28,
              y,
              424,
              () -> scene(g.name, Social.talk(s(), g, index), Mode.WORLD));
          y += 26;
        }
      }
      case "gift" -> {
        int y = 139;
        for (var item : data.items) {
          if (!item.kind.equals("gift") || s().count(item.id) < 1) continue;
          if (y > 218) break;
          game.uiButton(
              item.name + " ×" + s().count(item.id),
              28,
              y,
              424,
              18,
              true,
              () -> scene(g.name, Social.gift(s(), g, item), Mode.WORLD));
          y += 18;
        }
        if (y == 139) c.small("Подарков нет. Купить можно в кофейне.", 28, 164, SOFT);
        btn(
            "Назад",
            28,
            231,
            424,
            () -> {
              socialPage = "home";
              socialMessage = "";
              game.resetSelection();
            });
      }
      case "invite" -> {
        int y = 151;
        for (String venue : g.venues) {
          btn(
              Social.venueName(venue) + (venue.equals("park") ? " · бесплатно" : " · 850 ₽"),
              28,
              y,
              424,
              () -> {
                String result = Social.invite(s(), g, venue);
                scene(g.name, result, Mode.WORLD);
              });
          y += 26;
        }
        btn(
            "Назад",
            28,
            231,
            424,
            () -> {
              socialPage = "home";
              game.resetSelection();
            });
      }
      case "date" -> {
        int y = 151;
        for (int i = 0; i < g.dateChoices.length; i++) {
          int index = i;
          btn(
              g.dateChoices[i].label,
              28,
              y,
              424,
              () -> scene(g.name, Social.date(s(), g, index), Mode.WORLD));
          y += 28;
        }
        if (!s().plannedVenue.equals("park"))
          btn(
              "Перенести в сквер · бесплатно",
              28,
              211,
              424,
              () -> {
                if (Arrays.asList(g.venues).contains("park")) {
                  s().plannedVenue = "park";
                  scene(
                      "Перенос встречи",
                      "Вы договорились встретиться в сквере. Золотой маршрут покажет путь.",
                      Mode.WORLD);
                }
              });
      }
      case "commit" -> {
        btn(
            "Предложить быть парой",
            28,
            151,
            424,
            () -> scene(g.name, Social.commit(s(), g), Mode.WORLD));
        btn(
            "Честно обсудить расставание",
            28,
            179,
            424,
            s().partnerId.equals(g.id),
            () -> {
              socialPage = "breakup";
              game.resetSelection();
            });
        btn(
            "Не торопиться",
            28,
            231,
            424,
            () -> {
              socialPage = "home";
              game.resetSelection();
            });
      }
      case "breakup" -> {
        c.wrap(
            "Это завершит отношения. Доверие и симпатия уменьшатся; новая встреча не отменит"
                + " разговор автоматически.",
            28,
            150,
            424,
            SOFT);
        btn(
            "Да, сказать честно",
            28,
            205,
            206,
            () -> scene(g.name, Social.breakup(s(), g), Mode.WORLD));
        btn(
            "Пока не говорить",
            246,
            205,
            206,
            () -> {
              socialPage = "home";
              game.resetSelection();
            });
      }
      case "night" -> {
        c.wrap(
            Social.canShareNight(s(), g)
                ? "Вы оба этого хотите. Если передумаешь, можно отказаться — без обид и объяснений."
                : "Для этого нужны устойчивые отношения, доверие и вечернее время. Сейчас можно"
                    + " просто побыть рядом.",
            28,
            144,
            424,
            SOFT);
        btn(
            "Да, мы оба этого хотим",
            28,
            205,
            206,
            Social.canShareNight(s(), g),
            () -> mode(Mode.NIGHT));
        btn(
            "Давай не сегодня",
            246,
            205,
            206,
            () -> scene(g.name, Social.shareNight(s(), g, false), Mode.WORLD));
      }
    }
  }

  private void night() {
    c.rect(0, 0, 480, 270, "10151e");
    c.center("ВЕЧЕР ВДВОЁМ", 66, GOLD);
    c.wrap(
        "Вы убираете телефоны и закрываете дверь. За окном ещё шумит город.\n\n"
            + "Свет в комнате гаснет. Эту ночь вы решаете провести вместе.",
        66,
        106,
        348,
        TEXT);
    btn(
        "Следующее утро",
        92,
        214,
        296,
        () -> scene("Утро", Social.shareNight(s(), data.girl(girlId), true), Mode.WORLD));
  }

  private void coding() {
    workbench.draw();
  }
}
