package life.ctrl.ui;

import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.LifeContent;
import life.ctrl.model.*;
import life.ctrl.render.PixelCanvas;

/** An in-world workbench: contracts, experiments, revisions, portfolio and owned tools. */
public final class Workbench {
  private final CtrlLife game;
  private final LifeScreens life;
  private final PixelCanvas c;
  private int boardPage;
  private int portfolioPage;
  private boolean rush;
  private double[] inputs = {0};
  private String labResult = "Нажми «Запустить»: увидишь ответ, эталон и число операций.";
  private int baseline = -1, current = -1;
  private double cachedInput = Double.NaN, previewValue;
  private String cachedSource = "";
  private static final String TEXT = "efe4c6",
      SOFT = "a7b5b6",
      GOLD = "edbb71",
      GREEN = "a5c19c",
      RED = "e49a83";

  public Workbench(CtrlLife game, LifeScreens life, PixelCanvas c) {
    this.game = game;
    this.life = life;
    this.c = c;
  }

  private GameState s() {
    return game.world().state;
  }

  private void button(String label, int x, int y, int w, Runnable f) {
    button(label, x, y, w, true, f);
  }

  private void button(String label, int x, int y, int w, boolean enabled, Runnable f) {
    game.uiButton(label, x, y, w, 22, enabled, f);
  }

  private void small(String label, int x, int y, int w, Runnable f) {
    game.uiButton(label, x, y, w, 17, true, f);
  }

  private void page(int p) {
    life.codePage = p;
    game.resetSelection();
  }

  private void save() {
    game.refreshState();
  }

  private void scene(String title, String text) {
    life.scene(title, text, Mode.IDE);
  }

  private void frame(String title) {
    c.rect(0, 0, 480, 270, "111e2bf2");
    c.panel(12, 10, 456, 250);
    c.rect(16, 14, 448, 38, "203441");
    c.small("KEYCODE  /  " + Career.rank(s()).toUpperCase(Locale.ROOT), 26, 22, GREEN);
    c.text(title, 26, 39, TEXT);
    c.small(s().money + " ₽", 379, 23, GOLD);
    c.rect(16, 254, 448, 2, "527b78");
  }

  private void home() {
    button("К заказам", 28, 231, 206, () -> page(0));
    button(
        "Встать из-за ПК",
        246,
        231,
        206,
        () -> {
          game.switchMode(Mode.WORLD);
          save();
        });
  }

  private void choose(LifeContent.Job j) {
    if (!s().activeJob.isEmpty() && s().jobStage >= 2 && s().jobStage < 5) {
      page(2);
      scene(
          "Текущий проект",
          "Исходник сохранён. Закончи проект или отложи его через Git → «Отложить заказ». Денежного"
              + " штрафа нет.");
      return;
    }
    String reason = Career.blocked(s(), j);
    if (!reason.isEmpty()) {
      scene("Пока недоступно", reason);
      return;
    }
    s().activeJob = j.id;
    page(1);
    rush = false;
    life.report = null;
  }

  public void draw() {
    if (life.codePage == 0) {
      board();
      return;
    }
    if (life.codePage == 7) {
      career();
      return;
    }
    if (life.codePage == 8) {
      gear();
      return;
    }
    if (life.codePage == 9) {
      project();
      return;
    }
    if (life.codePage == 11) {
      portfolio();
      return;
    }
    if (s().activeJob.isEmpty()) {
      page(0);
      board();
      return;
    }
    var j = life.data.job(s().activeJob);
    frame(j.title);
    if (life.codePage == 1) {
      brief(j);
      return;
    }
    if (life.codePage == 3) {
      patches(j);
      return;
    }
    if (life.codePage == 4) {
      preview(j);
      return;
    }
    if (life.codePage == 5) {
      lab(j);
      return;
    }
    if (life.codePage == 6) {
      history();
      return;
    }
    if (life.codePage == 10) {
      c.wrap(
          "Отложить текущий заказ? Его исходник и локальные коммиты будут удалены. Денег и"
              + " репутации ты не потеряешь.",
          28,
          83,
          420,
          TEXT);
      button(
          "Отложить",
          28,
          199,
          206,
          () -> {
            Coding.abandon(s());
            page(0);
            save();
          });
      button("Сохранить проект", 246, 199, 206, () -> page(6));
      return;
    }
    editor(j);
  }

  private void board() {
    frame("Биржа заказов · день " + s().day());
    c.small("СДАНО " + Career.paidCountToday(s()) + " / 3  ·  ОПЫТ " + s().codingXp, 28, 65, GOLD);
    c.wrap(
        s().completedOrders == 0
            ? "Начни с кассы для «Ложки». Читай правило → код → тесты → оплата."
            : "Баги, новые функции и быстрый код. Состав предложений меняется каждый день.",
        28,
        82,
        420,
        SOFT);
    var offers = Career.offers(s(), life.data);
    int pages = Math.max(1, (offers.size() + 2) / 3);
    boardPage = Math.floorMod(boardPage, pages);
    for (int row = 0; row < 3; row++) {
      int index = boardPage * 3 + row;
      if (index >= offers.size()) break;
      var j = offers.get(index);
      String lock = Career.blocked(s(), j);
      button(
          j.title + " · " + j.pay + " ₽", 28, 112 + row * 29, 424, lock.isEmpty(), () -> choose(j));
    }
    small(
        "Лист " + (boardPage + 1) + "/" + pages,
        28,
        201,
        90,
        () -> {
          boardPage++;
          game.resetSelection();
        });
    small("Карьера", 125, 201, 100, () -> page(7));
    small("Свой бот", 232, 201, 100, () -> page(9));
    small("Железо", 339, 201, 113, () -> page(8));
    button(
        "Встать из-за ПК",
        28,
        226,
        424,
        () -> {
          game.switchMode(Mode.WORLD);
          save();
        });
  }

  private void brief(LifeContent.Job j) {
    c.small(j.client, 28, 66, GOLD);
    if (!j.once && s().completedOrders > 0)
      small(rush ? "Срочно: +25%" : "Обычный срок", 302, 61, 150, () -> rush = !rush);
    c.wrap(j.brief, 28, 88, 420, TEXT);
    c.small(
        j.cost > 0
            ? "Запуск: " + j.cost + " ₽ · работа " + Career.duration(s(), j) + " мин"
            : rush
                ? "Бонус, если сдашь за "
                    + (Career.duration(s(), j) + 90)
                    + " мин. Оплата: "
                    + j.pay
                    + " ₽ +25%."
                : "Работа: " + Career.duration(s(), j) + " мин · оплата " + j.pay + " ₽",
        28,
        132,
        SOFT);
    if (j.kind.equals("feature"))
      for (int i = 0; i < j.algorithms.length; i++) {
        int choice = i;
        button(j.algorithms[i], 28, 150 + i * 28, 424, () -> start(j, choice));
      }
    else {
      button(
          j.kind.equals("profile") ? "Открыть код и измерить нагрузку" : "Открыть исходник",
          28,
          150,
          424,
          () -> start(j, 0));
      button("Прочитать сообщение клиента", 28, 180, 424, () -> scene(j.client, j.hint));
      button("К заказам", 28, 226, 424, () -> page(0));
    }
  }

  private void start(LifeContent.Job j, int choice) {
    String result = Coding.accept(s(), j, choice, rush);
    if (s().jobStage < 2 || !s().activeJob.equals(j.id)) {
      scene("Проект", result);
      return;
    }
    life.report = null;
    page(2);
    save();
  }

  /**
   * The game-native code editor borrows the familiar VS Code layout while remaining
   * a fictional application in Pinux Astra. The current ByteScript engine stays authoritative.
   */
  private void editor(LifeContent.Job j) {
    c.rect(16, 53, 448, 197, "0a1725");
    c.rect(16, 53, 28, 197, "182c3e");
    c.rect(45, 53, 100, 197, "142538");
    c.rect(145, 53, 319, 118, "0c1928");
    c.rect(145, 172, 319, 55, "0b1723");
    c.rect(145, 226, 319, 24, "20384b");
    c.rect(144, 53, 1, 197, "38516a");
    c.rect(145, 171, 319, 1, "38516a");
    c.small("{}", 22, 70, GREEN);
    c.small("[]", 22, 103, SOFT);
    c.small(">", 25, 136, GOLD);
    c.small("EXPLORER", 52, 63, SOFT);
    c.small("▼  PROJECT", 51, 82, TEXT);
    c.small("   ▼ src", 52, 104, SOFT);
    c.small("     main.bs", 52, 121, GREEN);
    c.small("   README.md", 52, 142, SOFT);
    c.rect(51, 161, 88, 1, "355167");
    c.small("ACTIVE JOB", 52, 171, SOFT);
    c.fitted(j.id, 52, 189, 84, GOLD, true);
    c.small("XP " + s().codingXp, 52, 210, SOFT);
    c.rect(146, 54, 138, 23, "152b40");
    c.rect(146, 76, 138, 1, CYAN);
    c.small("◇ main.bs   ●", 156, 59, TEXT);
    c.small("ByteScript  /  UTF-8", 334, 59, SOFT);
    String visible = s().codeSource.substring(0, Math.min(s().typedCount, s().codeSource.length()));
    c.clip(151, 80, 307, 80);
    int row = 0;
    for (String line : visible.split("\n", -1)) {
      if (row >= 5) break;
      int y = 83 + row * 15;
      if (row == visible.split("\n", -1).length - 1) c.rect(153, y - 2, 301, 14, "172d4055");
      c.small(String.valueOf(row + 1), 155, y, "58758b");
      String codeColor = line.stripLeading().startsWith("fn ") ? "86cfe0" :
          line.stripLeading().startsWith("return ") ? "c8a1e6" : GREEN;
      c.fitted(line, 177, y, 275, codeColor, true);
      row++;
    }
    c.unclip();
    float progress = s().codeSource.isEmpty() ? 0f :
        s().typedCount / (float) s().codeSource.length();
    c.rect(153, 158, 299, 2, "263e53");
    c.rect(153, 158, 299 * Math.min(1f, progress), 2, GREEN);
    c.small("ПРОБЛЕМЫ   ВЫВОД   ТЕСТЫ", 153, 177, SOFT);
    if (life.report == null) {
      c.fitted(s().typedCount < s().codeSource.length()
          ? "ВВОД: нажимай любые клавиши — исходник печатается."
          : "Черновик готов. Запусти тесты.", 154, 191, 299, SOFT, true);
      c.fitted("Символов: " + s().typedCount + "/" + s().codeSource.length()
          + "   |   " + (s().rushJob ? "СРОЧНЫЙ ЗАКАЗ" : "main"), 154, 208, 299, GOLD, true);
    } else {
      c.fitted((life.report.passed() ? "PASS: " : "FAIL: ") + life.report.message(),
          154, 191, 299, life.report.passed() ? GREEN : RED, true);
      if (!life.report.cases().isEmpty())
        c.fitted(life.report.cases().get(0), 154, 208, 299, SOFT, true);
    }
    small("Тесты", 151, 229, 65, () -> {
      life.report = Coding.test(s(), j);
      save();
    });
    small(life.report != null && life.report.passed() ? "Превью" : "Отладка", 221, 229, 84, () -> {
      if (s().typedCount < s().codeSource.length()) {
        life.report = Coding.test(s(), j);
      } else if (life.report != null && life.report.passed()) {
        page(4);
        life.previewInput = j.id.equals("discount") ? 500 : j.seed[j.seed.length - 1];
      } else page(3);
    });
    small("Lab", 310, 229, 67, () -> openLab(j));
    small("Git", 382, 229, 75, () -> page(6));
  }

  private void patches(LifeContent.Job j) {
    c.small("CODE REVIEW · ВЫБЕРИ ИЗМЕНЕНИЕ", 28, 66, GOLD);
    c.wrap(
        s().codeSource.contains("prce")
            ? "Имя prce не объявлено. В аргументах есть price и count. Что должно участвовать в"
                + " произведении?"
            : j.hint,
        28,
        88,
        420,
        TEXT);
    for (int i = 0; i < j.patches.length; i++) {
      int choice = i;
      button(
          j.patches[i],
          28,
          150 + i * 28,
          424,
          () -> {
            Coding.repair(s(), j, choice);
            life.report = null;
            page(2);
            save();
          });
    }
  }

  private void preview(LifeContent.Job j) {
    c.small("ПРЕДПРОСМОТР · РЕЗУЛЬТАТ ТВОЕГО КОДА", 28, 67, GOLD);
    c.wrap(
        j.id.equals("total")
            ? "Цена блюда 30 ₽. Меняй количество: касса считает твоей функцией."
            : "Меняй "
                + j.function
                + " / "
                + ByteScript.compile(j.source).parameters().get(j.seed.length - 1)
                + ". Остальные аргументы — из первого теста клиента.",
        28,
        91,
        420,
        TEXT);
    if (!s().programPreviewed
        || cachedInput != life.previewInput
        || !cachedSource.equals(s().codeSource)) {
      previewValue = Coding.preview(s(), j, life.previewInput);
      cachedSource = s().codeSource;
      cachedInput = life.previewInput;
    }
    double value = previewValue;
    c.text(
        "Ввод " + Coding.format(life.previewInput) + " → " + Coding.format(value), 28, 134, GREEN);
    button("−", 28, 164, 70, () -> life.previewInput = Math.max(-10000, life.previewInput - 1));
    button("+", 110, 164, 70, () -> life.previewInput = Math.min(10000, life.previewInput + 1));
    c.small(
        "Работа " + Career.duration(s(), j) + " мин · бонус " + Career.bonus(s(), j) + " ₽",
        195,
        170,
        SOFT);
    button(
        j.once
            ? "Запустить этап · −" + j.cost + " ₽"
            : "Сдать клиенту · " + (j.pay + Career.bonus(s(), j)) + " ₽",
        28,
        199,
        424,
        s().jobStage != 5,
        () -> {
          String result = Coding.deliver(s(), j);
          game.successCue();
          life.scene(s().jobStage == 5 ? "Работа принята" : "Проект сохранён", result, Mode.WORLD);
        });
    button("Вернуться к коду", 28, 229, 424, () -> page(2));
  }

  private static String clock(int minute) {
    int v = Math.floorMod(minute, 1440);
    return "%02d:%02d".formatted(v / 60, v % 60);
  }

  private void openLab(LifeContent.Job j) {
    inputs = j.seed.clone();
    labResult = "Меняй входы: ноль, граница, отрицательное число. Потом запускай.";
    measure(j);
    page(5);
  }

  private int operations(String source, LifeContent.Job j) {
    try {
      var p = ByteScript.compile(source);
      int max = 0;
      for (var t : j.tests) max = Math.max(max, p.operations(t.args));
      return max;
    } catch (RuntimeException e) {
      return -1;
    }
  }

  private void measure(LifeContent.Job j) {
    baseline =
        operations(s().revisions.isEmpty() ? s().codeSource : s().revisions.get(0).source, j);
    current = operations(s().codeSource, j);
  }

  private void lab(LifeContent.Job j) {
    c.small("ЛАБОРАТОРИЯ / ПРОФИЛИРОВЩИК", 28, 64, GOLD);
    var params = ByteScript.compile(j.source).parameters();
    for (int i = 0; i < inputs.length; i++) {
      int at = i, y = 86 + i * 27;
      c.text(params.get(i) + " = " + Coding.format(inputs[i]), 28, y, TEXT);
      small("−10", 228, y - 2, 50, () -> inputs[at] = Math.max(-10000, inputs[at] - 10));
      small("−1", 284, y - 2, 50, () -> inputs[at] = Math.max(-10000, inputs[at] - 1));
      small("+1", 340, y - 2, 50, () -> inputs[at] = Math.min(10000, inputs[at] + 1));
      small("+10", 396, y - 2, 56, () -> inputs[at] = Math.min(10000, inputs[at] + 10));
    }
    c.small(
        "Операции на тестах: было "
            + (baseline < 0 ? "—" : baseline)
            + " → сейчас "
            + (current < 0 ? "ошибка кода" : current)
            + (j.operationBudget > 0 ? " / лимит " + j.operationBudget : ""),
        28,
        145,
        SOFT);
    c.rect(28, 160, 180, 3, "344652");
    c.rect(
        28,
        160,
        Math.max(2, 180f * Math.max(0, current) / Math.max(1, Math.max(baseline, current))),
        3,
        current >= 0 && (j.operationBudget == 0 || current <= j.operationBudget) ? GREEN : RED);
    c.small(
        j.once
            ? "Свой проект · без премии за тесты"
            : "Свои случаи: " + Math.min(2, s().customCases.size()) + " / 2 · бонус 100 ₽",
        225,
        156,
        GOLD);
    c.wrap(labResult, 28, 176, 424, TEXT);
    button(
        "Запустить",
        28,
        226,
        132,
        () -> {
          labResult = Coding.probe(s(), j, inputs.clone());
          measure(j);
          save();
        });
    button("Подсказка", 168, 226, 132, () -> scene("Заметка по расследованию", j.hint));
    button("К исходнику", 308, 226, 144, () -> page(2));
  }

  private void history() {
    c.small("GIT · ВЫБОР ВЕРСИИ = ОТКАТ", 28, 64, GOLD);
    small(
        "Коммит",
        260,
        59,
        90,
        () -> {
          String msg = Coding.commit(s());
          save();
          scene("Git", msg);
        });
    small("Отложить", 358, 59, 94, () -> page(10));
    for (int i = 0; i < s().revisions.size(); i++) {
      int at = i;
      var rev = s().revisions.get(i);
      String expr = rev.source.replace('\n', ' ').replaceAll(".*return ", "");
      if (expr.length() > 27) expr = expr.substring(0, 27) + "…";
      small(
          rev.label + " · " + expr,
          28,
          83 + i * 18,
          424,
          () -> {
            String msg = Coding.restore(s(), at);
            life.report = null;
            save();
            scene("Git / checkout", msg);
          });
    }
    button("Назад к исходнику", 28, 231, 424, () -> page(2));
  }

  private void career() {
    frame("Портфолио / карьера");
    c.text(Career.rank(s()) + " · " + s().codingXp + " опыта", 28, 69, GOLD);
    c.wrap(
        Career.tier(s()) == 0
            ? "При 3 опыте откроется расследование багов. Выполнено заказов: " + s().completedOrders
            : Career.tier(s()) == 1
                ? "При 8 опыте откроются задачи на производительность."
                : "Доступны все виды задач. Клиентские заказы: максимум три в день.",
        28,
        94,
        420,
        SOFT);
    c.small("ПОСЛЕДНЕЕ СООБЩЕНИЕ", 28, 140, GREEN);
    c.wrap(
        s().lastClientMessage.isEmpty()
            ? "Первый клиент ждёт кассу. После релиза здесь останется его отзыв."
            : s().lastClientMessage,
        28,
        159,
        420,
        TEXT);
    small("Готовые проекты: " + s().shipped.size(), 28, 205, 424, () -> page(11));
    home();
  }

  private void portfolio() {
    frame("Портфолио / выпущенные проекты");
    var jobs = Arrays.stream(life.data.jobs).filter(j -> s().shipped.contains(j.id)).toList();
    int pages = Math.max(1, (jobs.size() + 4) / 5);
    portfolioPage = Math.floorMod(portfolioPage, pages);
    c.small("Выбери проект: прочитаешь отзыв после запуска.", 28, 65, SOFT);
    if (jobs.isEmpty())
      c.wrap("Здесь появится твоя первая работа. Начни с кассы для «Ложки».", 28, 100, 420, TEXT);
    for (int row = 0; row < 5; row++) {
      int at = portfolioPage * 5 + row;
      if (at >= jobs.size()) break;
      var j = jobs.get(at);
      button(j.title, 28, 88 + row * 25, 424, () -> scene(j.client, j.feedback));
    }
    small(
        "Лист " + (portfolioPage + 1) + "/" + pages,
        28,
        222,
        140,
        () -> {
          portfolioPage++;
          game.resetSelection();
        });
    small("К карьере", 182, 222, 270, () -> page(7));
  }

  private void gear() {
    frame("Мастерская / рабочее место");
    int y = 68;
    for (var g : Career.GEAR) {
      c.text(g.title(), 28, y, GOLD);
      c.wrap(g.description(), 28, y + 21, 285, SOFT);
      button(
          s().upgrades.contains(g.id()) ? "Установлено" : g.price() + " ₽",
          335,
          y + 20,
          117,
          !s().upgrades.contains(g.id()),
          () -> scene("Рабочее место", Career.buy(s(), g.id())));
      y += 74;
    }
    home();
  }

  private void project() {
    frame("Свой проект / районный бот");
    int stage = Career.projectStage(s());
    c.small("ОБЪЯВЛЕНИЯ → НАПОМИНАНИЯ → ЗАПУСК", 28, 65, GOLD);
    for (int i = 0; i < 3; i++) c.rect(28 + i * 142, 85, 132, 4, i < stage ? GREEN : "425663");
    if (stage == 3) {
      c.wrap(
          "Бот работает. Жители поддерживают его: +160 ₽ за новый игровой день. Поступления видны в"
              + " бюджете. Все три этапа сохранены в портфолио.",
          28,
          111,
          420,
          TEXT);
      c.small("Твой первый продукт живёт за пределами редактора.", 28, 190, GREEN);
    } else {
      var j = life.data.job(List.of("board", "remind", "support").get(stage));
      c.text(j.title, 28, 108, TEXT);
      c.wrap(
          "Нужны код, тесты и "
              + j.cost
              + " ₽ на запуск этапа. Всего три этапа; после запуска — 160 ₽ в день. Можно делать"
              + " между клиентскими заказами.",
          28,
          134,
          420,
          SOFT);
      button(
          "Взяться за этап · " + Career.duration(s(), j) + " мин", 28, 191, 424, () -> choose(j));
    }
    home();
  }
}
