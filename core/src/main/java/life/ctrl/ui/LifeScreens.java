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
  private final RelationshipScreens relationships;
  private final PinuxAstra pinux;
  public final LifeContent data;
  public int introPage, codePage, shopPage;
  public String girlId = "mira", socialPage = "home", socialMessage = "", mapId = "";
  public boolean remote;
  public Coding.Report report;
  public double previewInput = 2;
  private int agendaPage = -1, progressPage = -1, shopListPage, bagPage, receiptPage;
  private List<String> receiptPages = List.of("");
  private double cachedInput = Double.NaN, cachedResult;
  private String cachedSource = "";
  public String sceneTitle = "", sceneText = "";
  public Mode sceneReturn = Mode.WORLD;
  public boolean relationshipScene;
  private static final String TEXT = "efe4c6",
      SOFT = "a7b5b6",
      GOLD = "edbb71",
      GREEN = "a5c19c",
      RED = "e49a83";

  public LifeScreens(CtrlLife game, PixelCanvas canvas) {
    this.game = game;
    c = canvas;
    relationships = new RelationshipScreens(game, this, c);
    data = LifeContent.load();
    workbench = new Workbench(game, this, canvas);
    pinux = new PinuxAstra(game, this, workbench, canvas);
  }

  public void resetForSession() {
    introPage = codePage = shopPage = shopListPage = bagPage = receiptPage = 0;
    agendaPage = progressPage = -1;
    report = null;
    previewInput = 2;
    relationshipScene = false;
    girlId = "mira";
    socialPage = "home";
    socialMessage = mapId = "";
    remote = false;
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
    c.fitted(title, 26, 39, 426, TEXT, false);
  }

  public void scene(String title, String text, Mode back) {
    relationshipScene = false;
    sceneTitle = title;
    sceneText = text;
    sceneReturn = back;
    receiptPage = 0;
    receiptPages = c.pages(text, 420, 131, false);
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
    pinux.open();
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

  public boolean pcKeyDown(int key) {
    return pinux.keyDown(key);
  }

  public void pcKeyTyped(char ch) {
    pinux.keyTyped(ch);
  }

  public boolean pcPointerDown(int x, int y) {
    return pinux.pointerDown(x, y);
  }

  public void pcPointerMove(int x, int y) {
    pinux.pointerMove(x, y);
  }

  public boolean pcPointerDragged(int x, int y) {
    return pinux.pointerDragged(x, y);
  }

  public boolean pcPointerUp(int x, int y) {
    return pinux.pointerUp(x, y);
  }

  public void capturePcSession() {
    pinux.captureSession();
  }

  public void pcCancelPointer() {
    pinux.cancelPointer();
  }

  public com.badlogic.gdx.scenes.scene2d.Stage pcNativeStage() {
    return pinux.inputStage();
  }

  public boolean isLegacyEditorOpen() {
    return pinux.isLegacyEditorOpen();
  }

  public void renderPcOverlay(float delta) {
    pinux.renderNativeDesktop(delta);
  }

  public void resizePc(int width, int height) {
    pinux.resizeNative(width, height);
  }

  public void typeKey() {
    if (codePage != 2 || s().jobStage != 2) return;
    // Two tactile phases: after failed tests, the next real keystroke starts fixing.
    if (report != null
        && !report.passed()
        && s().typedCount >= s().codeSource.length()
        && !s().activeJob.isEmpty()) {
      var job = data.job(s().activeJob);
      for (int i = 0; i < job.solutions.length; i++)
        if (job.source.equals(job.solutions[i])) {
          Coding.repair(s(), job, i, true);
          report = null;
          break;
        }
    }
    Coding.type(s());
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
    if (relationshipScene) {
      relationships.drawResult();
      return;
    }
    frame("СОБЫТИЕ", sceneTitle);
    c.paragraph(receiptPages.get(receiptPage), 28, 72, 420, 131, TEXT, false);
    if (receiptPages.size() > 1)
      c.small("Страница " + (receiptPage + 1) + "/" + receiptPages.size(), 28, 210, SOFT);
    btn(
        receiptPage + 1 < receiptPages.size() ? "Читать дальше →" : "Продолжить",
        28,
        226,
        424,
        () -> {
          if (receiptPage + 1 < receiptPages.size()) {
            receiptPage++;
            game.resetSelection();
          } else mode(sceneReturn);
        });
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
    int pages = Math.max(1, (list.size() + 4) / 5);
    shopListPage = Math.floorMod(shopListPage, pages);
    int y = 82;
    for (var item : list.subList(shopListPage * 5, Math.min(list.size(), shopListPage * 5 + 5))) {
      String label = item.name + " · " + item.price + " ₽  [есть " + s().count(item.id) + "]";
      btn(label, 28, y, 424, () -> scene("Покупка", Life.buy(s(), item), Mode.SHOP));
      y += 22;
    }
    if (pages > 1)
      game.uiButton(
          "Ассортимент " + (shopListPage + 1) + "/" + pages + " →",
          280,
          58,
          172,
          18,
          true,
          () -> {
            shopListPage = (shopListPage + 1) % pages;
            game.resetSelection();
          });
    btn(
        shopPage == 0 ? "Подарки →" : "← Еда",
        28,
        196,
        206,
        () -> {
          shopPage = 1 - shopPage;
          shopListPage = 0;
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
    var owned = Arrays.stream(data.items).filter(i -> s().count(i.id) > 0).toList();
    int pages = Math.max(1, (owned.size() + 5) / 6);
    bagPage = Math.floorMod(bagPage, pages);
    int y = 66;
    for (var item : owned.subList(bagPage * 6, Math.min(owned.size(), bagPage * 6 + 6))) {
      game.uiButton(
          item.name
              + " ×"
              + s().count(item.id)
              + (item.kind.equals("food") ? " — съесть" : " — подарок"),
          28,
          y,
          424,
          18,
          true,
          () -> {
            if (item.kind.equals("food")) scene("Перекус", Life.eat(s(), item), Mode.BAG);
            else
              scene(
                  item.name,
                  item.description
                      + "\n\n"
                      + "Подарок остаётся в карманах. Передать его можно при личной встрече:"
                      + " «Подарить».",
                  Mode.BAG);
          });
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
    if (pages > 1)
      btn(
          "Предметы " + (bagPage + 1) + "/" + pages + " →",
          28,
          194,
          424,
          () -> {
            bagPage = (bagPage + 1) % pages;
            game.resetSelection();
          });
    btn("Закрыть", 28, 226, 424, () -> mode(Mode.WORLD));
  }

  private void rest() {
    frame("КОМНАТА В ОБЩЕЖИТИИ", "Время восстановиться");
    c.wrap(
        "Энергия "
            + (int) s().energy
            + " · сытость "
            + (int) s().hunger
            + " · рабочая усталость "
            + (int) s().workFatigue
            + "/100.\nДо 08:00: "
            + Life.sleepMinutes(s())
            + " мин. Короткий сон восстанавливает меньше сил. Встречи и сроки идут даже во сне.",
        28,
        64,
        420,
        SOFT);
    btn(
        "До 08:00 · " + Life.sleepMinutes(s()) + " мин",
        28,
        116,
        424,
        () -> {
          Life.sleep(s());
          scene(
              "После сна",
              "День "
                  + s().day()
                  + ", "
                  + s().clock()
                  + ". Энергия "
                  + (int) s().energy
                  + ", сытость "
                  + (int) s().hunger
                  + ". Проверь обязательства в журнале [J].",
              Mode.WORLD);
        });
    btn(
        "Вздремнуть · 90 минут",
        28,
        142,
        424,
        () -> scene("Короткий сон", Life.nap(s()), Mode.WORLD));
    btn(
        "Перерыв без экрана · 30 минут",
        28,
        168,
        424,
        () -> scene("Короткий отдых", WorkRhythm.breakTime(s()), Mode.WORLD));
    if (!s().partnerId.isEmpty())
      btn(
          "Поговорить о вечере вдвоём",
          28,
          194,
          424,
          () -> {
            girlId = s().partnerId;
            socialPage = "night";
            remote = false;
            mode(Mode.SOCIAL);
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
      c.fitted(
          "Д" + e.day + "  " + (e.amount >= 0 ? "+" : "") + e.amount + " ₽  " + e.reason,
          28,
          y,
          424,
          e.amount >= 0 ? GREEN : TEXT,
          true);
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
    if (progressPage >= 0) {
      LifeProgress.record(s());
      var steps = LifeProgress.steps(s());
      c.small(
          s().chapterOneFinished
              ? "ГЛАВА 1 ЗАВЕРШЕНА · СВОЙ АДРЕС"
              : "СВОЙ АДРЕС · " + LifeProgress.count(s()) + "/7",
          28,
          63,
          GOLD);
      int pages = (steps.size() + 1) / 2;
      progressPage = Math.floorMod(progressPage, pages);
      for (int i = progressPage * 2; i < Math.min(steps.size(), progressPage * 2 + 2); i++) {
        var step = steps.get(i);
        int y = 84 + (i % 2) * 48;
        c.fitted(
            (step.done() ? "[+] " : "[ ] ") + step.title(),
            28,
            y,
            424,
            step.done() ? GREEN : TEXT,
            false);
        c.paragraph(step.hint(), 28, y + 15, 424, 29, SOFT, true);
      }
      btn(
          "Далее · " + (progressPage + 1) + "/" + pages,
          28,
          183,
          206,
          () -> {
            progressPage = (progressPage + 1) % pages;
            game.resetSelection();
          });
      btn(
          "← Маршрут",
          246,
          183,
          206,
          () -> {
            progressPage = -1;
            game.resetSelection();
          });
      btn(
          s().chapterOneFinished ? "Итог главы →" : "К делу →",
          28,
          216,
          424,
          () -> {
            if (s().chapterOneFinished)
              scene(
                  "Свой адрес",
                  "Глава 1 завершена. Из временной комнаты получился дом: ты научился(ась)"
                      + " зарабатывать, запустил(а) полезный продукт и нашёл(ла) близких людей.\n\n"
                      + "Дальше можно продолжать жить в районе: брать заказы, развивать отношения и"
                      + " строить общие планы. Твоя история и все вещи остаются с тобой.",
                  Mode.WORLD);
            else mode(Mode.WORLD);
          });
      return;
    }
    if (agendaPage >= 0) {
      List<String> rows = new ArrayList<>(WorkRhythm.agenda(s(), data));
      int reserve = WorkRhythm.reserve(s());
      rows.add(
          "Деньги "
              + s().money
              + " ₽. Резерв: "
              + reserve
              + " ₽ (аренда, долг, два супа и встреча). "
              + (s().money >= reserve
                  ? "Свободно " + (s().money - reserve)
                  : "Не хватает " + (reserve - s().money))
              + " ₽.");
      rows.add(
          "Рабочая усталость "
              + (int) s().workFatigue
              + "/100. Перерыв 30 мин: −20. "
              + "Свидание: −15. Темп работы выбирается на ПК в разделе «Ритм дня».");
      int pages = (rows.size() + 2) / 3;
      agendaPage = Math.floorMod(agendaPage, pages);
      for (int i = agendaPage * 3; i < Math.min(rows.size(), agendaPage * 3 + 3); i++) {
        int y = 67 + (i % 3) * 43;
        c.rect(24, y - 3, 432, 40, "21313cee");
        c.paragraph(rows.get(i), 30, y + 2, 416, 35, TEXT, false);
      }
      btn(
          "Далее · " + (agendaPage + 1) + "/" + pages,
          28,
          203,
          206,
          () -> {
            agendaPage = (agendaPage + 1) % pages;
            game.resetSelection();
          });
      btn(
          "← Маршрут",
          246,
          203,
          206,
          () -> {
            agendaPage = -1;
            game.resetSelection();
          });
      btn("К делу →", 28, 230, 424, () -> mode(Mode.WORLD));
      return;
    }
    var goal = Navigation.goal(game.world(), data);
    c.fitted(goal.title(), 28, 68, 424, GOLD, false);
    c.wrap(goal.hint(), 28, 88, 420, TEXT);
    game.uiButton(
        "Моя история · " + LifeProgress.count(s()) + "/7 →",
        28,
        121,
        424,
        22,
        true,
        () -> {
          progressPage = 0;
          game.resetSelection();
        });
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
    btn(
        "Обязательства и бюджет",
        28,
        230,
        424,
        () -> {
          agendaPage = 0;
          game.resetSelection();
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
    var boundary = life.ctrl.render.SceneLayout.bounds(id);
    c.clip(
        ox + boundary.x() * ratio,
        oy + boundary.y() * ratio,
        boundary.width() * ratio,
        boundary.height() * ratio);
    Arrays.stream(m.objects)
        .filter(p -> p.art != null && game.world().visible(p))
        .sorted(
            Comparator.comparingDouble(
                p -> p.solid != null ? p.solid[1] + p.solid[3] : p.artY + c.artHeight(p.art)))
        .forEach(
            p -> {
              String art = p.art;
              if (p.id.equals("boxes") && s().unpacked) art = "boxes_open";
              if (p.id.equals("post") && s().signedMailbox) art = "mailbox_signed";
              if (p.art.equals("desk") && id.equals("room") && s().upgrades.contains("ssd"))
                art = "desk_ssd";
              c.scaledArt(
                  art,
                  ox + p.artX * ratio,
                  oy + p.artY * ratio,
                  c.artWidth(art) * ratio,
                  c.artHeight(art) * ratio);
            });
    c.unclip();
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
    relationships.contacts();
  }

  private void social() {
    relationships.draw(data.girl(girlId));
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

  public void dispose() {
    pinux.dispose();
  }

  private void coding() {
    pinux.draw();
  }
}
