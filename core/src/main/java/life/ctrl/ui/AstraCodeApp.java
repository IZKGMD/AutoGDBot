package life.ctrl.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.LifeContent;
import life.ctrl.model.Career;
import life.ctrl.model.Coding;
import life.ctrl.model.GameState;

/**
 * High-resolution Scene2D implementation of the complete Workbench gameplay.
 * The rendered 480x270 PixelCanvas is deliberately not used by this application.
 * GameState, Coding and ByteScript are the single source of truth for its results.
 */
final class AstraCodeApp {
  private static final Color FG = AstraCodeVisuals.FG_PRIMARY;
  private static final Color MUTED = AstraCodeVisuals.FG_MUTED;
  private static final Color BLUE = AstraCodeVisuals.ACCENT_BLUE;
  private static final Color GREEN = AstraCodeVisuals.STATUS_OK;
  private static final Color GOLD = AstraCodeVisuals.STATUS_WARN;
  private static final Color ERROR = AstraCodeVisuals.STATUS_ERR;

  private final CtrlLife game;
  private final LifeScreens life;
  private final Skin skin;
  private final AstraCodeVisuals visuals;
  private final Table root = new Table();
  private int displayedPage = -1, boardPage, portfolioPage;
  private String displayedStructuralKey = "", notice = "";
  private int displayedTypedCount = -1;
  private boolean dirty = true, rush;
  private double[] inputs = {0};
  private String labResult = "Измени аргументы и запусти собственный тест.";
  private int baseline = -1, operations = -1;

  AstraCodeApp(CtrlLife game, LifeScreens life, Skin skin) {
    this.game = game;
    this.life = life;
    this.skin = skin;
    this.visuals = new AstraCodeVisuals(skin);
    root.top().left();
    root.setBackground(visuals.solid(AstraCodeVisuals.BG_EDITOR));
  }

  Table view() { return root; }
  void invalidate() { dirty = true; }

  private GameState state() { return game.world().state; }
  private LifeContent.Job job() {
    return state().activeJob.isEmpty() ? null : life.data.job(state().activeJob);
  }
  private void persist() { game.refreshState(); }
  private void message(String text) { notice = text; dirty = true; }
  private void page(int n) { life.codePage = n; dirty = true; }
  private void leave() { persist(); game.switchMode(Mode.WORLD); }

  void tick() {
    visuals.tickLayout();
    if (job() == null && (life.codePage == 1 || life.codePage == 2 ||
        life.codePage == 3 || life.codePage == 4 || life.codePage == 5 ||
        life.codePage == 6 || life.codePage == 10)) life.codePage = 0;
    String report = life.report == null ? "" :
        life.report.compiled() + ":" + life.report.passed() + ":" +
        life.report.message() + ":" + life.report.cases().hashCode();
    // The structural key deliberately excludes typedCount: text entry is updated in place.
    String key = life.codePage + "|" + state().activeJob + "|" + state().jobStage +
        "|" + state().codeSource + "|" + report +
        "|" + state().money + "|" + state().codingXp + "|" +
        state().revisions.size() + "|" + state().shipped.size() + "|" +
        state().upgrades.size();
    boolean structuralChange = dirty || life.codePage != displayedPage ||
        !key.equals(displayedStructuralKey);
    if (!structuralChange) {
      if (state().typedCount == displayedTypedCount) return;
      if (visuals.tryIncrementalEditorUpdate(life.codePage, state(), job(), life.report)) {
        displayedTypedCount = state().typedCount;
        return;
      }
    }
    displayedPage = life.codePage;
    displayedStructuralKey = key;
    displayedTypedCount = state().typedCount;
    dirty = false;
    rebuild();
  }

  private Label label(String text, Color color, boolean small) {
    return visuals.label(text, color, small);
  }

  private Label wrap(String text, Color color) {
    return visuals.wrapLabel(text, color);
  }

  private TextButton button(String text, Runnable action, boolean enabled) {
    AstraCodeVisuals.ButtonVariant variant = AstraCodeVisuals.ButtonVariant.SECONDARY;
    if (text.startsWith("▶") || text.contains("Принять") || text.contains("Сдать"))
      variant = AstraCodeVisuals.ButtonVariant.PRIMARY;
    else if (text.contains("Отложить") || text.contains("Удалить"))
      variant = AstraCodeVisuals.ButtonVariant.DANGER;
    TextButton b = visuals.compactButton(text, variant, enabled, () -> {
      action.run();
      dirty = true;
    });
    // Some existing contracts have long names; allow content to wrap without clipping.
    b.getLabel().setWrap(true);
    return b;
  }

  private void action(Table table, String caption, Runnable run) {
    action(table, caption, run, true);
  }
  private void action(Table table, String caption, Runnable run, boolean enabled) {
    table.add(button(caption, run, enabled)).height(30).left().minWidth(160).padBottom(5);
    table.row();
  }

  private void note(Table table, String value) {
    table.add(wrap(value, MUTED)).growX().minWidth(0).padBottom(8);
    table.row();
  }

  private void title(Table table, String category, String name, String subtitle) {
    table.add(visuals.buildSectionHeader(category, name, subtitle, null))
        .growX().minWidth(0).padBottom(10);
    table.row();
  }

  private Table block() {
    Table t = new Table();
    t.top().left().pad(10, 12, 10, 12);
    t.setBackground(visuals.solid(AstraCodeVisuals.BG_SURFACE));
    return t;
  }

  private void addBlock(Table content, Table block) {
    content.add(block).growX().minWidth(0).padBottom(6);
    content.row();
    visuals.addHorizontalRule(content, AstraCodeVisuals.BORDER_SUBTLE, 0, 4);
  }

  private void header(Table bar) {
    bar.setBackground(skin.getDrawable("window-header"));
    bar.left().padLeft(17).padRight(18);
    Label logo = label("A  /  ASTRA CODE", BLUE, false);
    bar.add(logo).left().padRight(25);
    bar.add(label("ByteScript  •  project workspace", MUTED, true)).left();
    bar.add().expandX();
    bar.add(label(Career.rank(state()) + "   ·   " + state().codingXp + " XP", MUTED, true))
        .padRight(20);
    bar.add(label(state().money + " ₽", GOLD, true));
  }

  private void nav(Table side, String name, int page, boolean available) {
    boolean selected = life.codePage == page;
    TextButton b = button((selected ? "▎  " : "     ") + name, () -> navigate(page), available);
    // Desktop navigation uses a quiet flat surface, not the blue CTA capsules.
    // Only the active document has an accent, like Archcraft's compact menus.
    b.getStyle().up = skin.getDrawable(selected ? "term-tab" : "term-toolbar");
    b.getStyle().over = skin.getDrawable("term-tab");
    b.getStyle().down = skin.getDrawable("term-tab");
    b.getStyle().fontColor = selected ? BLUE : MUTED;
    b.getStyle().overFontColor = FG;
    side.add(b).height(36).growX().minWidth(0).padBottom(2);
    side.row();
  }

  private void navigate(int next) {
    if (next == 4) {
      if (job() == null) { page(0); return; }
      if (life.report == null || !life.report.passed()) {
        test();
        if (life.report == null || !life.report.passed()) {
          message("Сначала пройди тесты, затем открой предпросмотр.");
          page(2);
          return;
        }
      }
      life.previewInput = job().id.equals("discount") ? 500 :
          job().seed[job().seed.length - 1];
    }
    if (next == 5) prepareLab();
    page(next);
  }

  private void sidebar(Table side) {
    side.top().left().pad(12);
    side.setBackground(skin.getDrawable("window-bg"));
    Label work = label("WORKSPACE", BLUE, true);
    side.add(work).left().padLeft(11).padTop(8).padBottom(12);
    side.row();
    nav(side, "Контракты", 0, true);
    nav(side, "main.bs", 2, job() != null);
    nav(side, "Исправления", 3, job() != null);
    nav(side, "Предпросмотр", 4, job() != null);
    nav(side, "Лаборатория", 5, job() != null);
    nav(side, "Git / версии", 6, job() != null);
    side.add(label("PROGRESSION", BLUE, true)).padLeft(11).padTop(16).padBottom(10).left();
    side.row();
    nav(side, "Карьера", 7, true);
    nav(side, "Портфолио", 11, true);
    nav(side, "Оборудование", 8, true);
    nav(side, "Свой бот", 9, true);
    side.add().expandY();
    side.row();
    action(side, "Выйти из ПК", this::leave);
  }

  private void rebuild() {
    root.clearChildren();
    visuals.clearEditorSessionIfNotPage(life.codePage);

    Table bar = visuals.buildTopToolbar(state(), job(), life.report,
        this::test, this::navigate);
    root.add(bar).growX();
    root.row();

    Table body = new Table();
    body.top().left();
    Table side = visuals.buildSidebar(state(), job(), life.codePage, life.report,
        this::navigate, this::leave);
    body.add(side).width(AstraCodeVisuals.SIDEBAR_WIDTH).growY().top();

    Table workspace = new Table();
    workspace.top().left();
    workspace.setBackground(visuals.solid(AstraCodeVisuals.BG_EDITOR));
    if (!notice.isEmpty()) {
      workspace.add(visuals.buildNoticeStrip(notice, () -> {
        notice = "";
        dirty = true;
      })).growX();
      workspace.row();
    }
    if (life.codePage == 2) {
      Table editorHost = new Table();
      visuals.buildEditorWorkspace(editorHost, state(), job(), life.report,
          this::test, this::commitRevision, this::navigate, () -> dirty = true);
      workspace.add(editorHost).grow().minWidth(0).minHeight(0);
    } else {
      Table content = new Table();
      content.top().left().pad(12, 16, 16, 16);
      content.defaults().left();
      renderPage(content);
      ScrollPane pane = visuals.createScrollPane(content, false);
      workspace.add(pane).grow().minWidth(0).minHeight(0);
    }
    body.add(workspace).grow().minWidth(0).minHeight(0);
    root.add(body).grow().minHeight(0);
  }

  private void renderPage(Table content) {
    LifeContent.Job j = job();
    switch (life.codePage) {
      case 0 -> board(content);
      case 1 -> brief(content, j);
      case 2 -> editor(content, j);
      case 3 -> patches(content, j);
      case 4 -> preview(content, j);
      case 5 -> lab(content, j);
      case 6 -> history(content);
      case 7 -> career(content);
      case 8 -> gear(content);
      case 9 -> project(content);
      case 10 -> abandon(content);
      case 11 -> portfolio(content);
      default -> board(content);
    }
  }

  private void choose(LifeContent.Job j) {
    if (!state().activeJob.isEmpty() && state().jobStage >= 2 && state().jobStage < 5) {
      message("Текущий исходник сохранён. Закончи заказ или отложи его через Git.");
      page(2);
      return;
    }
    String reason = Career.blocked(state(), j);
    if (!reason.isEmpty()) { message(reason); return; }
    state().activeJob = j.id;
    rush = false;
    life.report = null;
    notice = "";
    page(1);
    persist();
  }

  private void board(Table t) {
    title(t, "TASKMAIL / JOB BOARD", "Биржа заказов",
        "День " + state().day() + "  ·  оплачено " + Career.paidCountToday(state()) +
        " из 3 клиентских заказов сегодня. Выбирай задачу, читай ТЗ, пиши код и проверяй тесты.");
    var offers = Career.offers(state(), life.data);
    int pages = Math.max(1, (offers.size() + 3) / 4);
    boardPage = Math.floorMod(boardPage, pages);
    // Compact, scannable job rows instead of large, repetitive cards.
    for (int i = boardPage * 4; i < Math.min(offers.size(), boardPage * 4 + 4); i++) {
      LifeContent.Job j = offers.get(i);
      String reason = Career.blocked(state(), j);
      String subtitle = j.client + "  ·  " + Career.duration(state(), j) + " мин" +
          (reason.isEmpty() ? "  ·  Доступно" : "  ·  " + reason);
      Table row = visuals.buildDataRow(i, String.format("%02d", i + 1), j.title,
          subtitle, j.pay + " ₽", reason.isEmpty() ? GREEN : MUTED,
          button(reason.isEmpty() ? "Открыть заказ" : "Недоступно",
              () -> choose(j), reason.isEmpty()));
      t.add(row).growX().minWidth(0).padBottom(5).row();
      visuals.addHorizontalRule(t, AstraCodeVisuals.BORDER_SUBTLE, 0, 3);
    }
    Table switcher = new Table();
    switcher.add(button("←", () -> { boardPage = Math.floorMod(boardPage - 1, pages); }, pages > 1))
        .width(45).height(29);
    switcher.add(label("   Страница " + (boardPage + 1) + " / " + pages + "   ", MUTED, true));
    switcher.add(button("→", () -> { boardPage = (boardPage + 1) % pages; }, pages > 1))
        .width(45).height(29);
    t.add(switcher).left();
    t.row();
  }

  private void brief(Table t, LifeContent.Job j) {
    title(t, "CONTRACT / " + j.id, j.title, j.client + "   •   " + j.pay + " ₽");
    Table card = block();
    card.add(wrap(j.brief, FG)).width(610).growX().minWidth(0).padBottom(16);
    card.row();
    card.add(label("Время: " + Career.duration(state(), j) + " мин   /   запуск: " +
        j.cost + " ₽", GOLD, true)).left();
    card.row();
    if (!j.once && state().completedOrders > 0) {
      action(card, rush ? "● Срочный заказ (+25%)" : "○ Срочный заказ (+25%)",
          () -> rush = !rush);
    }
    if (j.kind.equals("feature")) {
      card.add(label("ВЫБЕРИ РЕАЛИЗАЦИЮ", BLUE, true)).left().padTop(15);
      card.row();
      for (int i = 0; i < j.algorithms.length; i++) {
        int choice = i;
        action(card, j.algorithms[i], () -> start(j, choice));
      }
    } else {
      action(card, j.kind.equals("profile") ? "Открыть профилировщик и код" :
          "Открыть исходник", () -> start(j, 0));
      action(card, "Подсказка клиента", () -> message(j.hint));
    }
    addBlock(t, card);
    action(t, "← Вернуться на биржу", () -> page(0));
  }

  private void start(LifeContent.Job j, int choice) {
    String result = Coding.accept(state(), j, choice, rush, true);
    if (state().jobStage < 2 || !state().activeJob.equals(j.id)) {
      message(result);
      return;
    }
    life.report = null;
    notice = "";
    page(2);
    persist();
  }

  private void editor(Table t, LifeContent.Job j) {
    title(t, "EDITOR / SRC", j.title,
        "main.bs   ·   ByteScript   /   Нажимай буквенные клавиши: игровой код печатается автоматически.");
    Table code = block();
    code.add(label("◇  main.bs", BLUE, true)).left().padBottom(13);
    code.row();
    String source = state().codeSource;
    int typed = Math.max(0, Math.min(state().typedCount, source.length()));
    String[] lines = source.substring(0, typed).split("\n", -1);
    int first = Math.max(0, lines.length - 18);
    for (int i = first; i < lines.length; i++) {
      Table row = new Table();
      row.left();
      row.add(label(String.format("%02d   ", i + 1), MUTED, true)).width(55).left();
      String codeLine = lines[i];
      String trim = codeLine.stripLeading();
      Color syntax = trim.startsWith("fn ") ? BLUE :
          trim.startsWith("return ") ? GOLD :
          trim.startsWith("//") ? MUTED : GREEN;
      row.add(label(codeLine + (i == lines.length - 1 ? " ▏" : ""), syntax, true))
          .left().growX();
      code.add(row).left().growX().minWidth(0).padBottom(4);
      code.row();
    }
    code.add(label("Символов: " + typed + " / " + source.length() +
        "  •  " + (source.isEmpty() ? 100 :
            Math.round(typed * 100.0 / source.length())) + "%", GOLD, true))
        .padTop(17).left();
    addBlock(t, code);

    Table console = block();
    console.add(label("ПРОБЛЕМЫ   /   ВЫВОД   /   ТЕСТЫ", BLUE, true)).left().padBottom(11);
    console.row();
    if (life.report == null) {
      console.add(wrap(typed < source.length() ?
          "РЕЖИМ ВВОДА • нажимай клавиши, чтобы допечатать исходник." :
          "Код готов. Запусти компиляцию и тесты.", MUTED))
          .width(610).growX().minWidth(0);
    } else {
      console.add(wrap((life.report.passed() ? "PASS • " : "FAIL • ") +
          life.report.message(), life.report.passed() ? GREEN : ERROR))
          .width(610).growX().minWidth(0);
      console.row();
      for (String result : life.report.cases()) {
        console.add(wrap(result, result.endsWith("OK") ? GREEN : ERROR))
            .width(610).growX().minWidth(0).padTop(5);
        console.row();
      }
    }
    addBlock(t, console);
    Table commands = new Table();
    commands.defaults().padRight(7);
    commands.add(button("▶ Компиляция / тесты", this::test, true)).height(43).growX();
    commands.add(button("Исправления", () -> page(3), true)).height(43).growX();
    commands.add(button("Предпросмотр", () -> navigate(4),
        life.report != null && life.report.passed())).height(43).growX();
    t.add(commands).growX().minWidth(0);
    t.row();
    Table extras = new Table();
    extras.defaults().padRight(7);
    extras.add(button("Лаборатория", () -> navigate(5), true)).height(43).growX();
    extras.add(button("Git / версии", () -> page(6), true)).height(43).growX();
    t.add(extras).growX().minWidth(0).padTop(7);
    t.row();
  }

  private void commitRevision() {
    if (job() == null) return;
    message(Coding.commit(state()));
    persist();
  }

  private void test() {
    if (job() == null) return;
    life.report = Coding.test(state(), job());
    message((life.report.passed() ? "✓ " : "✕ ") + life.report.message());
    persist();
  }

  private void patches(Table t, LifeContent.Job j) {
    title(t, "DEBUGGER / CODE REVIEW", "Исправление ошибок",
        "Выбери изменение и заново напечатай код. Ошибки компиляции проверяются настоящим ByteScript.");
    note(t, state().codeSource.contains("prce") ?
        "Имя prce не объявлено. В аргументах есть price и count — что должно участвовать в произведении?"
        : j.hint);
    for (int i = 0; i < j.patches.length; i++) {
      int choice = i;
      action(t, j.patches[i], () -> {
        Coding.repair(state(), j, choice, true);
        life.report = null;
        notice = "Патч применён. Допечатай код и повтори тесты.";
        page(2);
        persist();
      });
    }
    action(t, "← Вернуться к исходнику", () -> page(2));
  }

  private void preview(Table t, LifeContent.Job j) {
    title(t, "RUN / LIVE PREVIEW", "Предпросмотр",
        "Результат выполняется игровым интерпретатором ByteScript. Перед отправкой обязательно запусти его.");
    if (life.report == null || !life.report.passed()) {
      note(t, "Чтобы открыть предпросмотр, сначала запусти тесты в редакторе.");
      action(t, "К тестам", () -> page(2));
      return;
    }
    double value;
    try {
      value = Coding.preview(state(), j, life.previewInput);
    } catch (RuntimeException e) {
      note(t, "Ошибка запуска: " + e.getMessage());
      action(t, "К исходнику", () -> page(2));
      return;
    }
    Table result = block();
    result.add(label("INPUT   →   OUTPUT", BLUE, true)).left().padBottom(16);
    result.row();
    result.add(label(Coding.format(life.previewInput) + "   →   " +
        Coding.format(value), GREEN, false)).left().padBottom(16);
    result.row();
    Table controls = new Table();
    controls.add(button("−10", () -> life.previewInput = Math.max(-10000, life.previewInput - 10), true))
        .width(76).height(40).padRight(5);
    controls.add(button("−1", () -> life.previewInput = Math.max(-10000, life.previewInput - 1), true))
        .width(76).height(40).padRight(5);
    controls.add(button("+1", () -> life.previewInput = Math.min(10000, life.previewInput + 1), true))
        .width(76).height(40).padRight(5);
    controls.add(button("+10", () -> life.previewInput = Math.min(10000, life.previewInput + 10), true))
        .width(76).height(40);
    result.add(controls).left();
    addBlock(t, result);
    note(t, "Время работы: " + Career.duration(state(), j) + " мин. " +
        "Премия: " + Career.bonus(state(), j) + " ₽.");
    action(t, j.once ? "Запустить этап • −" + j.cost + " ₽" :
        "Сдать клиенту • " + (j.pay + Career.bonus(state(), j)) + " ₽",
        () -> deliver(j), state().jobStage != 5);
    action(t, "← Вернуться к исходнику", () -> page(2));
  }

  private void deliver(LifeContent.Job j) {
    String result = Coding.deliver(state(), j);
    if (state().jobStage == 5) {
      persist();
      game.successCue();
      life.scene(j.once ? "Этап запущен" : "Работа принята", result, Mode.WORLD);
    } else message(result);
  }

  private int operations(String source, LifeContent.Job j) {
    try {
      var program = ByteScript.compile(source);
      int max = 0;
      for (var item : j.tests) max = Math.max(max, program.operations(item.args));
      return max;
    } catch (RuntimeException e) {
      return -1;
    }
  }

  private void measure(LifeContent.Job j) {
    baseline = operations(state().revisions.isEmpty() ? state().codeSource :
        state().revisions.get(0).source, j);
    operations = operations(state().codeSource, j);
  }

  private void prepareLab() {
    if (job() == null) return;
    inputs = job().seed.clone();
    labResult = "Проверь ноль, границы и отрицательные числа, затем запусти.";
    measure(job());
  }

  private void lab(Table t, LifeContent.Job j) {
    title(t, "TOOLS / PROFILER", "Лаборатория",
        "Свои тестовые случаи приносят бонус за качество. Ограничение — 10 000 по модулю.");
    var params = ByteScript.compile(j.source).parameters();
    for (int i = 0; i < inputs.length; i++) {
      int index = i;
      Table row = block();
      row.add(label(params.get(i) + " = " + Coding.format(inputs[i]), FG, false))
          .width(180).left().padRight(12);
      for (int step : new int[] {-10, -1, 1, 10}) {
        row.add(button((step > 0 ? "+" : "") + step, () -> {
          inputs[index] = Math.max(-10000, Math.min(10000, inputs[index] + step));
        }, true)).width(64).height(39).padRight(5);
      }
      addBlock(t, row);
    }
    note(t, "Операций: было " + (baseline < 0 ? "—" : baseline) +
        " → сейчас " + (operations < 0 ? "ошибка" : operations) +
        (j.operationBudget > 0 ? "  /  лимит " + j.operationBudget : "") +
        "     •     собственные тесты " + Math.min(2, state().customCases.size()) + " / 2");
    Table box = block();
    box.add(wrap(labResult, FG)).width(600).growX().minWidth(0);
    addBlock(t, box);
    action(t, "▶ Запустить свой тест", () -> {
      labResult = Coding.probe(state(), j, inputs.clone());
      measure(j);
      persist();
    });
    action(t, "Подсказка клиента", () -> message(j.hint));
    action(t, "← К исходнику", () -> page(2));
  }

  private void history(Table t) {
    title(t, "SOURCE CONTROL / GIT", "История версий",
        "Локальные коммиты и откат сохраняются вместе с игровым прогрессом.");
    action(t, "Создать коммит", () -> {
      message(Coding.commit(state()));
      persist();
    });
    for (int i = 0; i < state().revisions.size(); i++) {
      int index = i;
      Coding.Revision rev = state().revisions.get(i);
      action(t, rev.label + "    /    " + rev.source.replace('\n', ' ').replaceAll(".*return ", ""),
          () -> {
            message(Coding.restore(state(), index));
            life.report = null;
            persist();
          });
    }
    action(t, "Отложить заказ", () -> page(10));
    action(t, "← К исходнику", () -> page(2));
  }

  private void abandon(Table t) {
    title(t, "PROJECT / CANCEL", "Отложить заказ?",
        "Исходник и локальные коммиты будут удалены, но деньги и репутация останутся.");
    action(t, "Отложить без штрафа", () -> {
      message(Coding.abandon(state()));
      life.report = null;
      page(0);
      persist();
    });
    action(t, "Сохранить и вернуться", () -> page(6));
  }

  private void career(Table t) {
    title(t, "PROFILE / CAREER", "Карьера разработчика",
        Career.rank(state()) + "    •    " + state().codingXp + " XP    •    " +
        state().completedOrders + " выполненных заказов");
    note(t, Career.tier(state()) == 0 ?
        "При 3 XP откроются задачи по поиску ошибок." :
        Career.tier(state()) == 1 ?
        "При 8 XP откроются задачи на производительность." :
        "Открыты все категории. Лимит: 3 оплачиваемых заказа за день.");
    Table feedback = block();
    feedback.add(label("ПОСЛЕДНЕЕ СООБЩЕНИЕ КЛИЕНТА", BLUE, true)).left().padBottom(12);
    feedback.row();
    feedback.add(wrap(state().lastClientMessage.isEmpty() ?
        "Первый клиент ещё ждёт твою работу." : state().lastClientMessage, FG))
        .width(600).growX().minWidth(0);
    addBlock(t, feedback);
    action(t, "Готовые проекты: " + state().shipped.size(), () -> page(11));
    action(t, "← К заказам", () -> page(0));
  }

  private void portfolio(Table t) {
    title(t, "PROFILE / PORTFOLIO", "Портфолио", "Все выпущенные проекты и отзывы заказчиков.");
    var shipped = Arrays.stream(life.data.jobs)
        .filter(j -> state().shipped.contains(j.id)).toList();
    int pages = Math.max(1, (shipped.size() + 5) / 6);
    portfolioPage = Math.floorMod(portfolioPage, pages);
    if (shipped.isEmpty()) note(t, "Первый проект появится здесь после релиза.");
    for (int i = portfolioPage * 6; i < Math.min(shipped.size(), portfolioPage * 6 + 6); i++) {
      LifeContent.Job j = shipped.get(i);
      action(t, "✓  " + j.title + "  •  " + j.client, () -> message(j.feedback));
    }
    if (pages > 1) action(t, "Следующая страница " + (portfolioPage + 1) + " / " + pages,
        () -> portfolioPage = (portfolioPage + 1) % pages);
    action(t, "← К карьере", () -> page(7));
  }

  private void gear(Table t) {
    title(t, "HARDWARE / UPGRADES", "Рабочее место",
        "Покупки меняют реальные значения длительности и усталости в игровом мире.");
    for (var g : Career.GEAR) {
      Table row = block();
      row.add(label(g.title(), FG, false)).left().padBottom(10);
      row.row();
      row.add(wrap(g.description(), MUTED)).width(600).growX().minWidth(0);
      row.row();
      boolean installed = state().upgrades.contains(g.id());
      action(row, installed ? "✓ Установлено" : "Купить • " + g.price() + " ₽",
          () -> {
            message(Career.buy(state(), g.id()));
            persist();
          }, !installed);
      addBlock(t, row);
    }
    action(t, "← К заказам", () -> page(0));
  }

  private void project(Table t) {
    title(t, "SIDE PROJECT / ROADMAP", "Районный бот",
        "Три настоящих этапа: объявления → напоминания → запуск.");
    int stage = Career.projectStage(state());
    note(t, "Выполнено этапов: " + stage + " / 3");
    if (stage == 3) {
      note(t, "Бот работает и приносит 160 ₽ за игровой день. Все три этапа находятся в портфолио.");
    } else {
      LifeContent.Job j = life.data.job(List.of("board", "remind", "support").get(stage));
      Table card = block();
      card.add(label(j.title, FG, false)).left().padBottom(12);
      card.row();
      card.add(wrap("Нужны код, тесты и " + j.cost + " ₽ на запуск. " +
          Career.duration(state(), j) + " мин работы.", MUTED)).width(600).growX().minWidth(0);
      card.row();
      action(card, "Взяться за этап", () -> choose(j), Career.blocked(state(), j).isEmpty());
      if (!Career.blocked(state(), j).isEmpty()) {
        card.add(wrap(Career.blocked(state(), j), GOLD)).width(600).growX().minWidth(0);
      }
      addBlock(t, card);
    }
    action(t, "← К заказам", () -> page(0));
  }
  void dispose() { visuals.dispose(); }
}
