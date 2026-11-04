package life.ctrl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import life.ctrl.content.LifeContent;
import life.ctrl.model.Career;
import life.ctrl.model.Coding;
import life.ctrl.model.GameState;

/**
 * Визуальный слой и дизайн-система Astra Code IDE для Pinux Astra (CTRL+LIFE).
 *
 * <p>Реализует эстетику Archcraft Linux + Tokyo Night + плотность JetBrains IDE / VS Code поверх
 * стандартного libGDX Scene2D и существующего {@link Skin}.
 *
 * <p>Ключевые архитектурные свойства:
 * <ul>
 *   <li>Никаких утечек GPU-памяти: единый 1x1 белый атлас {@link Texture} для всех процедурных
 *       плоских поверхностей, рамок и индикаторов с реализацией {@link Disposable}.</li>
 *   <li>Инкрементальное обновление редактора кода ({@link CodeEditorSession}): нажатие клавиш и
 *       рост {@code state.typedCount} обновляют только видимые строки, активную строку, курсор и
 *       статус-бар без пересоздания дерева Scene2D.</li>
 *   <li>Компактная плотная верстка: верхний тулбар 38px, узкая боковая навигация 186px с 3px
 *       индикатором активного экрана, табличные списки вместо гигантских карточек и нижняя
 *       док-панель с вкладками PROBLEMS / OUTPUT / TEST RESULTS.</li>
 * </ul>
 */
public final class AstraCodeVisuals implements Disposable {

  // ===============================================================================================
  // ПАЛИТРА TOKYO NIGHT / ARCHCRAFT DARK
  // ===============================================================================================

  /** Основной глубокий фон рабочей области и редактора (#16161e). */
  public static final Color BG_EDITOR = Color.valueOf("16161e");

  /** Фон боковой панели, тулбара и заголовков док-панелей (#1a1b26). */
  public static final Color BG_SURFACE = Color.valueOf("1a1b26");

  /** Вторичный приподнятый фон для табличных строк, инпутов и вкладок (#1f2335). */
  public static final Color BG_ELEVATED = Color.valueOf("1f2335");

  /** Чередующийся фон четных строк таблиц и списков (#191b28). */
  public static final Color BG_ROW_ALT = Color.valueOf("191b28");

  /** Подсветка активной строки в редакторе и выбранного ряда (#24283b). */
  public static final Color BG_ACTIVE_LINE = Color.valueOf("24283b");

  /** Состояние наведения для компактных элементов и кнопок (#292e42). */
  public static final Color BG_HOVER = Color.valueOf("292e42");

  /** Фон желоба нумерации строк (gutter) (#14141b). */
  public static final Color BG_GUTTER = Color.valueOf("14141b");

  /** Тонкие разделители окон и панелей 1px (#292e42). */
  public static final Color BORDER_SUBTLE = Color.valueOf("292e42");

  /** Акцентная рамка фокуса или важного блока (#3b4261). */
  public static final Color BORDER_STRONG = Color.valueOf("3b4261");

  /** Основной текст интерфейса и кода (#c0caf5). */
  public static final Color FG_PRIMARY = Color.valueOf("c0caf5");

  /** Вторичный текст параметров и описаний (#a9b1d6). */
  public static final Color FG_SECONDARY = Color.valueOf("a9b1d6");

  /** Приглушенный текст метаданных, неактивных вкладок и комментариев (#565f89). */
  public static final Color FG_MUTED = Color.valueOf("8994b3");

  /** Цвет номеров строк в неактивных рядах (#414868). */
  public static final Color FG_GUTTER = Color.valueOf("64718f");

  /** Фирменный синий акцент Tokyo Night / Astra (#7aa2f7). */
  public static final Color ACCENT_BLUE = Color.valueOf("7aa2f7");

  /** Голубой акцент типов, операторов и активных ссылок (#7dcfff). */
  public static final Color ACCENT_CYAN = Color.valueOf("7dcfff");

  /** Фиолетовый акцент ключевых слов ByteScript (fn, let, if, else, while) (#bb9af7). */
  public static final Color ACCENT_PURPLE = Color.valueOf("bb9af7");

  /** Зеленый цвет успешных тестов, литералов и коммитов (#9ece6a). */
  public static final Color STATUS_OK = Color.valueOf("9ece6a");

  /** Янтарный цвет предупреждений, return, денег и прогресса (#e0af68). */
  public static final Color STATUS_WARN = Color.valueOf("e0af68");

  /** Красный цвет ошибок компиляции, падений тестов и критических состояний (#f7768e). */
  public static final Color STATUS_ERR = Color.valueOf("f7768e");

  /** Приглушенный фон плашки успеха (#1c2a24). */
  public static final Color BADGE_OK_BG = Color.valueOf("1c2a24");

  /** Приглушенный фон плашки предупреждения (#2d261e). */
  public static final Color BADGE_WARN_BG = Color.valueOf("2d261e");

  /** Приглушенный фон плашки ошибки (#2d1b24). */
  public static final Color BADGE_ERR_BG = Color.valueOf("2d1b24");

  /** Приглушенный фон информационной плашки (#1d253a). */
  public static final Color BADGE_INFO_BG = Color.valueOf("1d253a");

  // ===============================================================================================
  // КОНСТАНТЫ КОМПОНОВКИ IDE
  // ===============================================================================================

  public static final float TOP_TOOLBAR_HEIGHT = 38f;
  public static final float SIDEBAR_WIDTH = 186f;
  public static final float NAV_ITEM_HEIGHT = 28f;
  public static final float TAB_BAR_HEIGHT = 28f;
  public static final float STATUS_BAR_HEIGHT = 24f;
  public static final float BOTTOM_DOCK_HEIGHT = 172f;
  public static final float COMPACT_BTN_HEIGHT = 26f;
  public static final float ACTION_BTN_HEIGHT = 30f;
  public static final float GUTTER_WIDTH = 46f;
  public static final float CODE_LINE_HEIGHT = 20f;

  // ===============================================================================================
  // ВНУТРЕННЕЕ СОСТОЯНИЕ И КЭШ РЕСУРСОВ
  // ===============================================================================================

  private final Skin skin;
  private final BitmapFont syntaxFont;
  private final Texture whitePixelTexture;
  private final TextureRegion whiteRegion;

  private final Map<Long, Drawable> solidDrawableCache = new HashMap<>();
  private final Map<String, Label.LabelStyle> labelStyleCache = new HashMap<>();
  private final Map<String, TextButton.TextButtonStyle> buttonStyleCache = new HashMap<>();
  private final ScrollPane.ScrollPaneStyle scrollStyle;

  /** Выбранная вкладка нижней консоли: 0 = PROBLEMS, 1 = OUTPUT, 2 = TEST RESULTS. */
  private int activeConsoleTab = 2;
  private boolean inspectorRequested = true;

  /** Текущая активная сессия редактора для быстрых инкрементальных обновлений без rebuild(). */
  private CodeEditorSession activeEditorSession;
  private Table activeJobProgressBadge, activeTypingBadge;
  private Label activeJobProgressLabel, activeTypingLabel;

  public AstraCodeVisuals(Skin skin) {
    this.skin = Objects.requireNonNull(skin, "skin cannot be null");

    // ByteScript owns a separate font so enabling markup cannot break UI labels
    // containing square brackets, such as TEST RESULTS [3/3].
    syntaxFont = new BitmapFont(Gdx.files.internal("fonts/hd/small.fnt"));
    syntaxFont.getData().setScale(.44f);
    syntaxFont.getData().markupEnabled = true;
    syntaxFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear,
        Texture.TextureFilter.Linear);

    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    this.whitePixelTexture = new Texture(pixmap);
    pixmap.dispose();

    this.whiteRegion = new TextureRegion(whitePixelTexture);
    this.scrollStyle = createScrollPaneStyle();
  }

  @Override
  public void dispose() {
    solidDrawableCache.clear();
    labelStyleCache.clear();
    buttonStyleCache.clear();
    activeEditorSession = null;
    whitePixelTexture.dispose();
    syntaxFont.dispose();
  }

  // ===============================================================================================
  // БАЗОВЫЕ РЕСУРСЫ: DRAWABLES, ШРИФТЫ, СТИЛИ
  // ===============================================================================================

  /**
   * Возвращает кэшированный плоский {@link Drawable} заданного цвета без создания новых текстур.
   */
  public Drawable solid(Color color) {
    long key = Color.rgba8888(color) & 0xFFFFFFFFL;
    return solidDrawableCache.computeIfAbsent(key, k -> {
      TextureRegionDrawable base = new TextureRegionDrawable(whiteRegion);
      return base.tint(color);
    });
  }

  /**
   * Возвращает плоский {@link Drawable} с внутренними отступами (padded drawable).
   */
  public Drawable paddedSolid(Color color, float padV, float padH) {
    TextureRegionDrawable base = new TextureRegionDrawable(whiteRegion);
    Drawable tinted = base.tint(color);
    tinted.setTopHeight(padV);
    tinted.setBottomHeight(padV);
    tinted.setLeftWidth(padH);
    tinted.setRightWidth(padH);
    return tinted;
  }

  /**
   * Выбирает моноширинный или компактный шрифт из существующего {@link Skin} с безопасным откатом.
   */
  public BitmapFont font(FontRole role) {
    return switch (role) {
      case MONO -> resolveFont("mono-font", "code-font", "small-font", "ui-font");
      case SMALL -> resolveFont("app-small-font", "small-font", "ui-font");
      case UI -> resolveFont("app-ui-font", "ui-font", "small-font");
    };
  }

  private BitmapFont resolveFont(String... candidates) {
    for (String name : candidates) {
      if (skin.has(name, BitmapFont.class)) {
        return skin.getFont(name);
      }
    }
    return skin.getFont("small-font");
  }

  public enum FontRole {
    UI,
    SMALL,
    MONO
  }

  public ScrollPane.ScrollPaneStyle scrollStyle() {
    return scrollStyle;
  }

  private ScrollPane.ScrollPaneStyle createScrollPaneStyle() {
    ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
    style.vScroll = solid(BG_SURFACE);
    style.vScrollKnob = paddedSolid(BORDER_STRONG, 2f, 2f);
    style.hScroll = solid(BG_SURFACE);
    style.hScrollKnob = paddedSolid(BORDER_STRONG, 2f, 2f);
    return style;
  }

  // ===============================================================================================
  // ТИПОГРАФИКА И ФАБРИКА БАЗОВЫХ ЭЛЕМЕНТОВ
  // ===============================================================================================

  public Label label(String text, Color color, FontRole role) {
    String cacheKey = role.name() + ":" + color.toString();
    Label.LabelStyle style = labelStyleCache.computeIfAbsent(
        cacheKey, k -> new Label.LabelStyle(font(role), new Color(color)));
    Label result = new Label(text == null ? "" : text, style);
    result.setAlignment(Align.left);
    return result;
  }

  public Label label(String text, Color color, boolean small) {
    return label(text, color, small ? FontRole.SMALL : FontRole.UI);
  }

  public Label monoLabel(String text, Color color) {
    return label(text, color, FontRole.MONO);
  }

  public Label wrapLabel(String text, Color color) {
    Label lbl = label(text, color, FontRole.SMALL);
    lbl.setWrap(true);
    lbl.setAlignment(Align.topLeft);
    return lbl;
  }

  /**
   * Создает компактную статусную пилюлю (badge) в стиле JetBrains / VS Code.
   */
  public Table badge(String text, Color fg, Color bg) {
    Table pill = new Table();
    pill.setBackground(paddedSolid(bg, 2f, 7f));
    Label lbl = label(text, fg, FontRole.SMALL);
    pill.add(lbl).center();
    return pill;
  }

  /**
   * Создает тонкую горизонтальную линию-разделитель 1px.
   */
  public void addHorizontalRule(Table parent, Color color, float padTop, float padBottom) {
    Table line = new Table();
    line.setBackground(solid(color));
    parent.add(line).height(1f).growX().padTop(padTop).padBottom(padBottom).row();
  }

  /**
   * Создает тонкую вертикальную линию-разделитель 1px.
   */
  public Cell<Table> addVerticalRule(Table parent, Color color, float padH) {
    Table line = new Table();
    line.setBackground(solid(color));
    return parent.add(line).width(1f).growY().padLeft(padH).padRight(padH);
  }

  // ===============================================================================================
  // ФАБРИКА КОМПАКТНЫХ КНОПОК И ИНСТРУМЕНТОВ
  // ===============================================================================================

  public enum ButtonVariant {
    /** Стандартная компактная кнопка панели инструментов. */
    SECONDARY,
    /** Акцентная кнопка основного действия (Запуск тестов, Сдать проект, Принять). */
    PRIMARY,
    /** Кнопка успешного завершения / подтверждения. */
    SUCCESS,
    /** Опасное действие (Отказ от контракта, Сброс). */
    DANGER,
    /** Прозрачная кнопка для вкладок и встроенных действий. */
    GHOST
  }

  public TextButton compactButton(
      String caption, ButtonVariant variant, boolean enabled, Runnable onClick) {

    String styleKey = variant.name() + ":" + enabled;
    TextButton.TextButtonStyle style = buttonStyleCache.computeIfAbsent(styleKey, k -> {
      TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
      s.font = font(FontRole.SMALL);

      if (!enabled) {
        s.up = paddedSolid(BG_SURFACE, 4f, 10f);
        s.over = s.up;
        s.down = s.up;
        s.disabled = s.up;
        s.fontColor = FG_MUTED;
        s.disabledFontColor = FG_MUTED;
        return s;
      }

      switch (variant) {
        case PRIMARY -> {
          s.up = paddedSolid(BADGE_INFO_BG, 4f, 10f);
          s.over = paddedSolid(Color.valueOf("263352"), 4f, 10f);
          s.down = paddedSolid(Color.valueOf("1b243b"), 4f, 10f);
          s.fontColor = ACCENT_BLUE;
          s.overFontColor = Color.WHITE;
        }
        case SUCCESS -> {
          s.up = paddedSolid(BADGE_OK_BG, 4f, 10f);
          s.over = paddedSolid(Color.valueOf("253b31"), 4f, 10f);
          s.down = paddedSolid(Color.valueOf("182620"), 4f, 10f);
          s.fontColor = STATUS_OK;
          s.overFontColor = Color.WHITE;
        }
        case DANGER -> {
          s.up = paddedSolid(BADGE_ERR_BG, 4f, 10f);
          s.over = paddedSolid(Color.valueOf("3d222e"), 4f, 10f);
          s.down = paddedSolid(Color.valueOf("26151d"), 4f, 10f);
          s.fontColor = STATUS_ERR;
          s.overFontColor = Color.WHITE;
        }
        case GHOST -> {
          s.up = paddedSolid(new Color(0, 0, 0, 0), 4f, 8f);
          s.over = paddedSolid(BG_HOVER, 4f, 8f);
          s.down = paddedSolid(BG_ACTIVE_LINE, 4f, 8f);
          s.fontColor = FG_SECONDARY;
          s.overFontColor = FG_PRIMARY;
        }
        case SECONDARY -> {
          s.up = paddedSolid(BG_ELEVATED, 4f, 10f);
          s.over = paddedSolid(BG_HOVER, 4f, 10f);
          s.down = paddedSolid(BG_ACTIVE_LINE, 4f, 10f);
          s.fontColor = FG_PRIMARY;
          s.overFontColor = Color.WHITE;
        }
      }
      return s;
    });

    TextButton btn = new TextButton(caption, style);
    btn.setDisabled(!enabled);
    btn.getLabel().setAlignment(Align.center);
    btn.getLabel().setWrap(false);
    btn.padLeft(10f).padRight(10f);

    if (enabled && onClick != null) {
      btn.addListener(new ChangeListener() {
        @Override
        public void changed(ChangeEvent event, Actor actor) {
          onClick.run();
        }
      });
    }
    return btn;
  }

  /**
   * Создает настраиваемый ScrollPane без лишних отступов и оверскролла.
   */
  public ScrollPane createScrollPane(Actor content, boolean allowHorizontal) {
    ScrollPane pane = new ScrollPane(content, scrollStyle);
    pane.setFadeScrollBars(false);
    pane.setScrollbarsOnTop(true);
    pane.setOverscroll(false, false);
    pane.setScrollingDisabled(!allowHorizontal, false);
    return pane;
  }

  // ===============================================================================================
  // 1. ВЕРХНИЙ КОМПАКТНЫЙ ТУЛБАР (38px)
  // ===============================================================================================

  /**
   * Строит верхний компактный тулбар IDE высотой 38px.
   *
   * <p>Слева: логотип Astra Code, активный файл и индикатор задания.
   * Справа: быстрый запуск тестов, индикатор ошибок/компиляции, состояние проекта, ранг и баланс.
   */
  public Table buildTopToolbar(
      GameState state,
      LifeContent.Job activeJob,
      Coding.Report report,
      Runnable onRunTests,
      IntConsumer onNavigate) {

    activeJobProgressBadge = null;
    activeTypingBadge = null;
    activeJobProgressLabel = null;
    activeTypingLabel = null;
    Table wrapper = new Table();
    wrapper.setBackground(solid(BG_SURFACE));

    Table bar = new Table();
    bar.left().padLeft(12f).padRight(12f);

    // 1. Логотип Astra Code
    Table brandPill = new Table();
    brandPill.setBackground(paddedSolid(BADGE_INFO_BG, 2f, 6f));
    brandPill.add(label("λ ASTRA", ACCENT_BLUE, FontRole.SMALL));
    bar.add(brandPill).padRight(8f);

    bar.add(label("CODE", FG_PRIMARY, FontRole.SMALL)).padRight(10f);
    addVerticalRule(bar, BORDER_SUBTLE, 8f);

    // 2. Активный файл и индикатор текущего контракта
    String activeFileName = activeJob != null ? "src/main.bs" : "workspace/contracts.bs";
    bar.add(monoLabel(activeFileName, ACCENT_CYAN)).padRight(10f);

    if (activeJob != null) {
      int totalLen = Math.max(1, state.codeSource.length());
      int typed = Math.max(0, Math.min(state.typedCount, state.codeSource.length()));
      int pct = (int) Math.round((typed * 100.0) / totalLen);

      String jobBadgeText = activeJob.id + "  [" + pct + "%]";
      Color badgeFg = typed >= state.codeSource.length() ? STATUS_OK : STATUS_WARN;
      Color badgeBg = typed >= state.codeSource.length() ? BADGE_OK_BG : BADGE_WARN_BG;

      Table jobIndicator = badge(jobBadgeText, badgeFg, badgeBg);
      activeJobProgressBadge = jobIndicator;
      activeJobProgressLabel = (Label) jobIndicator.getChildren().first();
      jobIndicator.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (onNavigate != null) {
            onNavigate.accept(2);
          }
        }
      });
      bar.add(jobIndicator).padRight(8f);
    } else {
      bar.add(badge("НЕТ АКТИВНОГО ТАСКА", FG_MUTED, BG_ELEVATED)).padRight(8f);
    }

    bar.add().expandX().fillX();

    // 3. Правая часть: запуск тестов, индикатор компиляции/ошибок, состояние проекта
    boolean hasJob = activeJob != null;
    TextButton runBtn = compactButton(
        "▶ Run / Test",
        ButtonVariant.PRIMARY,
        hasJob && onRunTests != null,
        onRunTests);
    bar.add(runBtn).height(COMPACT_BTN_HEIGHT).padRight(8f);

    // Индикатор компиляции и ошибок
    if (!hasJob) {
      bar.add(badge("IDLE", FG_MUTED, BG_ELEVATED)).padRight(8f);
    } else if (report == null) {
      boolean readyToCompile = state.typedCount >= state.codeSource.length()
          && !state.codeSource.isEmpty();
      Table typingBadge = badge(
          readyToCompile ? "● READY" : "✎ TYPING",
          readyToCompile ? ACCENT_CYAN : STATUS_WARN,
          readyToCompile ? BADGE_INFO_BG : BADGE_WARN_BG);
      activeTypingBadge = typingBadge;
      activeTypingLabel = (Label) typingBadge.getChildren().first();
      bar.add(typingBadge).padRight(8f);
    } else if (!report.compiled()) {
      bar.add(badge("✕ COMPILE ERR", STATUS_ERR, BADGE_ERR_BG)).padRight(8f);
    } else if (report.passed()) {
      bar.add(badge("✓ ALL TESTS PASS", STATUS_OK, BADGE_OK_BG)).padRight(8f);
    } else {
      int failedCount = countFailedCases(report);
      bar.add(badge("✕ " + failedCount + " TEST FAIL", STATUS_ERR, BADGE_ERR_BG)).padRight(8f);
    }

    // Состояние проекта / ревизий Git
    String revState = "rev:" + state.revisions.size() + " · ship:" + state.shipped.size();
    bar.add(monoLabel(revState, FG_MUTED)).padRight(6f);

    addVerticalRule(bar, BORDER_SUBTLE, 8f);

    // 4. Телеметрия игрока (Ранг, XP, Баланс)
    bar.add(label(state.codingXp + " XP", FG_SECONDARY, FontRole.SMALL))
        .padRight(10f);
    bar.add(monoLabel(state.money + " ₽", STATUS_WARN));

    wrapper.add(bar).height(TOP_TOOLBAR_HEIGHT).growX().row();
    addHorizontalRule(wrapper, BORDER_SUBTLE, 0f, 0f);
    return wrapper;
  }

  private int countFailedCases(Coding.Report report) {
    if (report == null || report.cases() == null) {
      return 0;
    }
    int fails = 0;
    for (String c : report.cases()) {
      if (c != null && !c.endsWith("OK")) {
        fails++;
      }
    }
    return Math.max(1, fails);
  }

  // ===============================================================================================
  // 2. ЛЕВАЯ УЗКАЯ НАВИГАЦИЯ (WORKSPACE / PROGRESSION) С ТОНКОЙ АКЦЕНТНОЙ ПОЛОСОЙ
  // ===============================================================================================

  /**
   * Строит левую боковую панель навигации IDE шириной 186px.
   *
   * <p>Активный пункт выделяется тонкой 3px акцентной полосой слева и легкой подсветкой строки,
   * без превращения пунктов в огромные кнопки.
   */
  public Table buildSidebar(
      GameState state,
      LifeContent.Job activeJob,
      int currentPage,
      Coding.Report report,
      IntConsumer onNavigate,
      Runnable onLeavePc) {

    Table sideContainer = new Table();
    sideContainer.setBackground(solid(BG_SURFACE));

    Table side = new Table();
    side.top().left().padTop(8f).padBottom(8f);

    boolean hasJob = activeJob != null;
    int errCount = (report != null && !report.passed()) ? countFailedCases(report) : 0;

    // Группа WORKSPACE
    addSidebarSectionHeader(side, "WORKSPACE");
    addSidebarItem(side, "Контракты", "◫", 0, currentPage, true,
        hasJob ? "1" : "", ACCENT_BLUE, onNavigate);
    addSidebarItem(side, "main.bs", "◇", 2, currentPage, hasJob,
        "", ACCENT_CYAN, onNavigate);
    addSidebarItem(side, "Исправления", "⚙", 3, currentPage, hasJob,
        errCount > 0 ? String.valueOf(errCount) : "", STATUS_ERR, onNavigate);
    addSidebarItem(side, "Предпросмотр", "▷", 4, currentPage, hasJob,
        (report != null && report.passed()) ? "OK" : "", STATUS_OK, onNavigate);
    addSidebarItem(side, "Лаборатория", "λ", 5, currentPage, hasJob,
        "", FG_MUTED, onNavigate);
    addSidebarItem(side, "Git / версии", "⎇", 6, currentPage, hasJob,
        !state.revisions.isEmpty() ? String.valueOf(state.revisions.size()) : "", FG_MUTED, onNavigate);

    addHorizontalRule(side, BORDER_SUBTLE, 8f, 6f);

    // Группа PROGRESSION
    addSidebarSectionHeader(side, "PROGRESSION");
    addSidebarItem(side, "Карьера", "◈", 7, currentPage, true,
        "T" + Career.tier(state), ACCENT_PURPLE, onNavigate);
    addSidebarItem(side, "Портфолио", "▤", 11, currentPage, true,
        !state.shipped.isEmpty() ? String.valueOf(state.shipped.size()) : "", STATUS_OK, onNavigate);
    addSidebarItem(side, "Оборудование", "▣", 8, currentPage, true,
        !state.upgrades.isEmpty() ? String.valueOf(state.upgrades.size()) : "", STATUS_WARN, onNavigate);
    addSidebarItem(side, "Свой бот", "⚡", 9, currentPage, true,
        "", ACCENT_CYAN, onNavigate);

    side.add().expandY().fillY().row();

    // Компактная нижняя панель состояния и выхода из ПК
    addHorizontalRule(side, BORDER_SUBTLE, 6f, 6f);

    Table footer = new Table();
    footer.padLeft(8f).padRight(8f);
    TextButton leaveBtn = compactButton("⏻ Выйти из ПК", ButtonVariant.GHOST, true, onLeavePc);
    leaveBtn.getLabel().setAlignment(Align.left);
    footer.add(leaveBtn).height(26f).growX();
    side.add(footer).growX().row();

    sideContainer.add(side).width(SIDEBAR_WIDTH - 1f).growY();
    addVerticalRule(sideContainer, BORDER_SUBTLE, 0f);
    return sideContainer;
  }

  private String formatTypingBadge(GameState state) {
    if (state.codeSource == null || state.codeSource.isEmpty()) {
      return "";
    }
    if (state.typedCount >= state.codeSource.length()) {
      return "100%";
    }
    int pct = (int) Math.round((state.typedCount * 100.0) / Math.max(1, state.codeSource.length()));
    return pct + "%";
  }

  private void addSidebarSectionHeader(Table side, String title) {
    Table header = new Table();
    header.left().padLeft(12f).padRight(10f).padTop(4f).padBottom(4f);
    header.add(label(title, FG_MUTED, FontRole.SMALL)).left();
    side.add(header).growX().row();
  }

  private void addSidebarItem(
      Table side,
      String title,
      String icon,
      int targetPage,
      int currentPage,
      boolean available,
      String rightTag,
      Color rightTagColor,
      IntConsumer onNavigate) {

    boolean selected = (currentPage == targetPage)
        || (targetPage == 0 && (currentPage == 1 || currentPage == 10));

    Table row = new Table();
    row.left();
    row.setBackground(solid(selected ? BG_ACTIVE_LINE : BG_SURFACE));

    // Тонкая 3px акцентная полоса слева для активного пункта
    Table accentBar = new Table();
    accentBar.setBackground(solid(selected ? ACCENT_BLUE : new Color(0, 0, 0, 0)));
    row.add(accentBar).width(3f).height(NAV_ITEM_HEIGHT);

    // Иконка + подпись
    Color itemColor = !available ? FG_GUTTER : (selected ? FG_PRIMARY : FG_SECONDARY);
    Color iconColor = !available ? FG_GUTTER : (selected ? ACCENT_BLUE : FG_MUTED);

    row.add(monoLabel(icon, iconColor)).padLeft(9f).padRight(7f);
    row.add(label(title, itemColor, FontRole.SMALL)).left().expandX();

    // Компактный правый счетчик/индикатор
    if (rightTag != null && !rightTag.isEmpty() && available) {
      row.add(monoLabel(rightTag, rightTagColor)).right().padRight(10f);
    } else {
      row.add().padRight(10f);
    }

    if (available && onNavigate != null) {
      row.addListener(new ClickListener() {
        @Override
        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
          if (!selected) {
            row.setBackground(solid(BG_HOVER));
          }
        }

        @Override
        public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
          if (!selected) {
            row.setBackground(solid(BG_SURFACE));
          }
        }

        @Override
        public void clicked(InputEvent event, float x, float y) {
          onNavigate.accept(targetPage);
        }
      });
    }

    side.add(row).height(NAV_ITEM_HEIGHT).growX().row();
  }

  // ===============================================================================================
  // 3. ЦЕНТРАЛЬНАЯ ОБЛАСТЬ: НАСТОЯЩИЙ ВИЗУАЛЬНЫЙ РЕДАКТОР КОДА + НИЖНЯЯ ДОК-КОНСОЛЬ
  // ===============================================================================================

  /**
   * Проверяет, можно ли обновить открытый экран редактора инкрементально (только изменился
   * {@code typedCount}), не вызывая полный {@code root.clearChildren()} при каждом нажатии клавиши.
   *
   * @return {@code true}, если экран редактора успешно обновлен на месте.
   */
  public boolean tryIncrementalEditorUpdate(
      int currentPage, GameState state, LifeContent.Job activeJob, Coding.Report report) {
    if (currentPage != 2 || activeEditorSession == null || activeJob == null) {
      return false;
    }
    boolean synced = activeEditorSession.syncIfCompatible(state, activeJob, report);
    if (synced) refreshTypingBadges(state, activeJob);
    return synced;
  }

  /** Responsive editor inspector layout is evaluated each frame because VisWindow can be resized. */
  public void tickLayout() {
    if (activeEditorSession != null) activeEditorSession.syncResponsive(false);
  }

  private void refreshTypingBadges(GameState state, LifeContent.Job job) {
    int length = Math.max(1, state.codeSource == null ? 0 : state.codeSource.length());
    int typed = Math.max(0, Math.min(state.typedCount, length));
    boolean ready = state.codeSource != null && typed >= state.codeSource.length();
    int percent = (int) Math.round(typed * 100.0 / length);
    if (activeJobProgressLabel != null && activeJobProgressBadge != null) {
      activeJobProgressLabel.setText(job.id + " [" + percent + "%]");
      activeJobProgressLabel.setColor(ready ? STATUS_OK : STATUS_WARN);
      activeJobProgressBadge.setBackground(solid(ready ? BADGE_OK_BG : BADGE_WARN_BG));
    }
    if (activeTypingLabel != null && activeTypingBadge != null) {
      activeTypingLabel.setText(ready ? "● READY" : "✎ TYPING");
      activeTypingLabel.setColor(ready ? ACCENT_CYAN : STATUS_WARN);
      activeTypingBadge.setBackground(solid(ready ? BADGE_INFO_BG : BADGE_WARN_BG));
    }
  }

  /**
   * Сбрасывает ссылку на активную сессию редактора при переключении на другой экран.
   */
  public void clearEditorSessionIfNotPage(int currentPage) {
    if (currentPage != 2) {
      activeEditorSession = null;
    }
  }

  /**
   * Строит полноценную рабочую область редактора кода (вкладка {@code main.bs}, тулбар редактора,
   * область кода с нумерацией строк, подсветкой ByteScript, курсором, горизонтальной и вертикальной
   * прокруткой, полосой прогресса набора и нижней панелью с вкладками PROBLEMS / OUTPUT / TEST RESULTS).
   */
  public void buildEditorWorkspace(
      Table container,
      GameState state,
      LifeContent.Job job,
      Coding.Report report,
      Runnable onRunTests,
      Runnable onCommit,
      IntConsumer onNavigate,
      Runnable onTabSwitchRebuild) {

    container.clearChildren();
    container.top().left();
    container.setBackground(solid(BG_EDITOR));

    if (job == null) {
      container.add(buildEmptyWorkspaceBanner(
          "Нет активного контракта",
          "Открой раздел «Контракты» в левой панели WORKSPACE и выбери задание для работы.",
          () -> {
            if (onNavigate != null) {
              onNavigate.accept(0);
            }
          })).grow().pad(24f);
      return;
    }

    CodeEditorSession session = new CodeEditorSession(
        state, job, report, onRunTests, onCommit, onNavigate, onTabSwitchRebuild);
    this.activeEditorSession = session;

    container.add(session.rootTable).grow();
  }

  /**
   * Управляет живыми виджетами редактора кода, позволяя обновлять {@code typedCount} за O(число_строк)
   * без пересоздания таблиц и сброса позиции скролла.
   */
  private final class CodeEditorSession {
    final Table rootTable = new Table();
    private final String boundJobId;
    private final String boundCodeSource;
    private final String boundReportSignature;

    private final Table codeLinesTable = new Table();
    private final ScrollPane codeScrollPane;
    private final Table editorArea = new Table();
    private final Table inspector;
    private final Cell<Table> inspectorCell;
    private Label inspectorProgress;
    private Label inspectorStatus;
    private Label inspectorNext;
    private final Coding.Report currentReport;
    private boolean inspectorVisible = true;
    private final Label progressLabel;
    private final Label cursorStatusLabel;
    private final Label stageHintLabel;
    private final Cell<Table> progressFillCell;
    private final Table progressTrack = new Table();

    private final List<LineRowWidgets> lineRows = new ArrayList<>();
    private int lastTypedCount = -1;

    CodeEditorSession(
        GameState state,
        LifeContent.Job job,
        Coding.Report report,
        Runnable onRunTests,
        Runnable onCommit,
        IntConsumer onNavigate,
        Runnable onTabSwitchRebuild) {

      this.boundJobId = state.activeJob;
      this.boundCodeSource = state.codeSource == null ? "" : state.codeSource;
      this.boundReportSignature = reportSignature(report);
      this.currentReport = report;

      rootTable.top().left();
      rootTable.setBackground(solid(BG_EDITOR));

      // 1. Вкладочная полоса редактора (main.bs + быстрые действия)
      Table tabStrip = new Table();
      tabStrip.setBackground(solid(BG_SURFACE));
      tabStrip.left().padLeft(0f).padRight(10f);

      // Активная вкладка main.bs с верхней акцентной линией
      Table activeTab = new Table();
      activeTab.setBackground(solid(BG_EDITOR));
      Table tabTopAccent = new Table();
      tabTopAccent.setBackground(solid(ACCENT_BLUE));
      activeTab.add(tabTopAccent).height(2f).growX().row();

      Table tabInner = new Table();
      tabInner.padLeft(12f).padRight(14f);
      tabInner.add(monoLabel("◇ ", ACCENT_BLUE));
      tabInner.add(monoLabel("main.bs", FG_PRIMARY)).padRight(8f);
      tabInner.add(monoLabel("ByteScript", FG_MUTED));
      activeTab.add(tabInner).height(TAB_BAR_HEIGHT - 2f);

      tabStrip.add(activeTab).height(TAB_BAR_HEIGHT);
      addVerticalRule(tabStrip, BORDER_SUBTLE, 0f);

      // Хлебные крошки задания
      tabStrip.add(monoLabel("  " + job.id, FG_SECONDARY)).padLeft(8f).padRight(8f);
      stageHintLabel = label("", FG_MUTED, FontRole.SMALL);
            tabStrip.add().expandX().fillX();

      // Компактные кнопки действий прямо в заголовке редактора
      tabStrip.add(compactButton("▤ Details", ButtonVariant.GHOST, true, () -> {
        inspectorRequested = !inspectorRequested;
        syncResponsive(true);
      })).height(22f).padRight(4f);
      tabStrip.add(compactButton("Commit", ButtonVariant.GHOST, onCommit != null, onCommit))
          .height(22f).padRight(4f);
      tabStrip.add(compactButton("Исправить", ButtonVariant.SECONDARY, true, () -> {
        if (onNavigate != null) {
          onNavigate.accept(3);
        }
      })).height(22f).padRight(4f);

      boolean canPreview = report != null && report.passed();
      tabStrip.add(compactButton("▷ Превью",
          canPreview ? ButtonVariant.SUCCESS : ButtonVariant.SECONDARY,
          canPreview,
          () -> {
            if (onNavigate != null) {
              onNavigate.accept(4);
            }
          })).height(22f).padRight(4f);

      tabStrip.add(compactButton("▶ Тесты", ButtonVariant.PRIMARY, true, onRunTests))
          .height(22f);

      rootTable.add(tabStrip).height(TAB_BAR_HEIGHT).growX().row();
      addHorizontalRule(rootTable, BORDER_SUBTLE, 0f, 0f);

      // 2. Тонкая полоса прогресса набора (2px) под вкладкой
      progressTrack.setBackground(solid(BG_SURFACE));
      progressTrack.left();
      Table fillBar = new Table();
      fillBar.setBackground(solid(ACCENT_BLUE));
      progressFillCell = progressTrack.add(fillBar).height(2f).width(0f).left();
      progressTrack.add().expandX().fillX();
      rootTable.add(progressTrack).height(2f).growX().row();

      // 3. Основное полотно кода со скроллом (вертикальным и горизонтальным)
      codeLinesTable.top().left();
      codeLinesTable.setBackground(solid(BG_EDITOR));
      buildLinePool(boundCodeSource);

      codeScrollPane = createScrollPane(codeLinesTable, true);

      // The inspector uses the otherwise empty space beside short ByteScript programs.
      // It is automatically removed from the layout when the IDE window is narrow.
      editorArea.top().left();
      editorArea.add(codeScrollPane).grow().minWidth(0f).minHeight(140f);
      inspector = new Table();
      Table inspectorBody = buildContractInspector(state, job, report, onRunTests, onCommit,
          onNavigate);
      // Long client briefs or smaller windows must never force the editor console off-screen.
      ScrollPane inspectorScroll = createScrollPane(inspectorBody, false);
      inspector.add(inspectorScroll).grow().minWidth(0f).minHeight(0f);
      inspectorCell = editorArea.add(inspector).width(224f).growY().padLeft(1f);
      rootTable.add(editorArea).grow().minHeight(140f).row();

      // 4. Компактная строка статуса редактора (Ln/Col, Прогресс символов, Кодировка)
      addHorizontalRule(rootTable, BORDER_SUBTLE, 0f, 0f);
      Table editorStatusStrip = new Table();
      editorStatusStrip.setBackground(solid(BG_SURFACE));
      editorStatusStrip.left().padLeft(10f).padRight(10f);

      cursorStatusLabel = monoLabel("Ln 1, Col 1", FG_SECONDARY);
      progressLabel = monoLabel("0 / 0 chars", STATUS_WARN);

      editorStatusStrip.add(cursorStatusLabel).padRight(16f);
      addVerticalRule(editorStatusStrip, BORDER_SUBTLE, 6f);
      editorStatusStrip.add(progressLabel).padLeft(6f).padRight(14f);
      editorStatusStrip.add(stageHintLabel).padRight(6f);
      editorStatusStrip.add().expandX().fillX();
      editorStatusStrip.add(monoLabel("ByteScript · UTF-8", FG_MUTED));

      rootTable.add(editorStatusStrip).height(STATUS_BAR_HEIGHT).growX().row();
      addHorizontalRule(rootTable, BORDER_SUBTLE, 0f, 0f);

      // 5. Нижняя док-панель (PROBLEMS / OUTPUT / TEST RESULTS)
      Table bottomDock = buildBottomConsoleDock(state, job, report, onRunTests, onTabSwitchRebuild);
      rootTable.add(bottomDock).height(BOTTOM_DOCK_HEIGHT).growX().row();

      // Initial state, then responsive sizing is maintained without rebuilding code widgets.
      updateTypedCodeView(state.typedCount);
    }

    private Table buildContractInspector(GameState state, LifeContent.Job job,
        Coding.Report report, Runnable runTests, Runnable commit, IntConsumer navigate) {
      Table panel = new Table();
      panel.top().left().pad(12f);
      panel.setBackground(solid(BG_SURFACE));

      panel.add(monoLabel("CONTRACT  /  " + job.id.toUpperCase(Locale.ROOT), ACCENT_BLUE))
          .left().padBottom(10f).row();
      Label jobName = wrapLabel(job.title, FG_PRIMARY);
      panel.add(jobName).width(197f).left().padBottom(9f).row();
      panel.add(label(job.client, FG_SECONDARY, FontRole.SMALL)).left().padBottom(8f).row();
      addHorizontalRule(panel, BORDER_SUBTLE, 6f, 10f);

      panel.add(monoLabel("REWARD", FG_MUTED)).left().padBottom(5f).row();
      panel.add(label(job.pay + " ₽", STATUS_OK, FontRole.UI)).left().padBottom(12f).row();
      panel.add(monoLabel("SOURCE  ·  main.bs", FG_MUTED)).left().padBottom(8f).row();
      inspectorProgress = monoLabel("", ACCENT_CYAN);
      panel.add(inspectorProgress).left().padBottom(6f).row();
      addHorizontalRule(panel, BORDER_SUBTLE, 6f, 12f);

      panel.add(monoLabel("WORKFLOW", FG_MUTED)).left().padBottom(11f).row();
      panel.add(label("01  ● Контракт принят", STATUS_OK, FontRole.SMALL)).left().padBottom(8f).row();
      inspectorStatus = label("", ACCENT_CYAN, FontRole.SMALL);
      panel.add(inspectorStatus).left().padBottom(8f).row();
      panel.add(label("03  ● Проверка ByteScript", report == null ? FG_MUTED :
          report.passed() ? STATUS_OK : STATUS_ERR, FontRole.SMALL)).left().padBottom(8f).row();
      panel.add(label("04  ● Отправка клиенту", report != null && report.passed() ?
          ACCENT_CYAN : FG_MUTED, FontRole.SMALL)).left().padBottom(12f).row();
      addHorizontalRule(panel, BORDER_SUBTLE, 6f, 12f);

      panel.add(monoLabel("NEXT ACTION", ACCENT_BLUE)).left().padBottom(7f).row();
      inspectorNext = wrapLabel("", FG_SECONDARY);
      panel.add(inspectorNext).width(197f).left().padBottom(13f).row();

      TextButton testButton = compactButton("▶ Run tests", ButtonVariant.PRIMARY, true, runTests);
      panel.add(testButton).height(28f).growX().padBottom(6f).row();
      if (report != null && report.passed()) {
        panel.add(compactButton("▷ Preview result", ButtonVariant.SUCCESS, true,
            () -> navigate.accept(4))).height(27f).growX().padBottom(6f).row();
      } else if (report != null) {
        panel.add(compactButton("⚙ Repair source", ButtonVariant.SECONDARY, true,
            () -> navigate.accept(3))).height(27f).growX().padBottom(6f).row();
      }
      panel.add(compactButton("⎇ Commit revision", ButtonVariant.GHOST, true, commit))
          .height(27f).growX().row();
      addHorizontalRule(panel, BORDER_SUBTLE, 10f, 8f);
      panel.add(monoLabel("KEYBOARD", FG_MUTED)).left().padBottom(5f).row();
      panel.add(wrapLabel("Нажимай буквенные клавиши — игровой код печатается автоматически.",
          FG_SECONDARY)).width(197f).left().row();
      return panel;
    }

    void syncResponsive(boolean forced) {
      float available = rootTable.getWidth();
      if (!forced && available <= 0f) return;
      boolean show = inspectorRequested && available >= 740f;
      if (!forced && inspectorVisible == show) return;
      inspectorVisible = show;
      inspector.setVisible(show);
      inspectorCell.width(show ? 224f : 0f).padLeft(show ? 1f : 0f);
      editorArea.invalidateHierarchy();
    }

    boolean syncIfCompatible(GameState state, LifeContent.Job job, Coding.Report report) {
      if (!Objects.equals(boundJobId, state.activeJob)) {
        return false;
      }
      String currentSrc = state.codeSource == null ? "" : state.codeSource;
      if (!Objects.equals(boundCodeSource, currentSrc)) {
        return false;
      }
      if (!Objects.equals(boundReportSignature, reportSignature(report))) {
        return false;
      }
      if (lastTypedCount != state.typedCount) {
        updateTypedCodeView(state.typedCount);
      }
      return true;
    }

    private void buildLinePool(String fullSource) {
      codeLinesTable.clearChildren();
      lineRows.clear();

      String[] allLines = fullSource.isEmpty() ? new String[] {""} : fullSource.split("\n", -1);
      int totalLines = Math.max(1, allLines.length);

      for (int i = 0; i < totalLines; i++) {
        Table row = new Table();
        row.left();

        // Желоб (Gutter) с номером строки и маркером активной строки
        Table gutter = new Table();
        gutter.setBackground(solid(BG_GUTTER));
        gutter.right().padRight(8f).padLeft(6f);

        Label lineNumLabel = monoLabel(String.format("%3d", i + 1), FG_GUTTER);
        lineNumLabel.setAlignment(Align.right);
        gutter.add(lineNumLabel).width(GUTTER_WIDTH - 14f).right();

        row.add(gutter).width(GUTTER_WIDTH).height(CODE_LINE_HEIGHT);

        // Тонкий разделитель желоба 1px
        Table gutterBorder = new Table();
        gutterBorder.setBackground(solid(BORDER_SUBTLE));
        row.add(gutterBorder).width(1f).height(CODE_LINE_HEIGHT);

        // Контейнер для токенов строки кода
        Table codeCell = new Table();
        codeCell.left().padLeft(12f).padRight(24f);
        Label codeTextLabel = new Label("", new Label.LabelStyle(syntaxFont, Color.WHITE));
        Label cursorLabel = new Label("", new Label.LabelStyle(syntaxFont, Color.WHITE));
        codeCell.add(codeTextLabel).left();
        codeCell.add(cursorLabel).left();
        codeCell.add().expandX().fillX();

        row.add(codeCell).height(CODE_LINE_HEIGHT).growX();

        codeLinesTable.add(row).height(CODE_LINE_HEIGHT).growX().row();
        lineRows.add(new LineRowWidgets(row, gutter, lineNumLabel, codeTextLabel, cursorLabel));
      }

      // Нижний отступ внутри редактора, чтобы последняя строка не прилипала к границе
      codeLinesTable.add().height(12f).growX().row();
    }

    private void updateTypedCodeView(int rawTypedCount) {
      this.lastTypedCount = rawTypedCount;

      int totalLen = boundCodeSource.length();
      int typed = Math.max(0, Math.min(rawTypedCount, totalLen));
      String visibleSub = boundCodeSource.substring(0, typed);
      String[] typedLines = visibleSub.split("\n", -1);

      int activeLineIndex = Math.max(0, typedLines.length - 1);
      int activeCol = typedLines.length == 0 ? 1 : typedLines[activeLineIndex].length() + 1;

      for (int i = 0; i < lineRows.size(); i++) {
        LineRowWidgets w = lineRows.get(i);
        boolean isTypedLine = i < typedLines.length;
        boolean isActive = (i == activeLineIndex);

        if (!isTypedLine) {
          w.row.setBackground(solid(BG_EDITOR));
          w.gutter.setBackground(solid(BG_GUTTER));
          w.lineNumLabel.setColor(FG_GUTTER);
          w.codeTextLabel.setText("");
          w.cursorLabel.setText("");
          continue;
        }

        String lineText = typedLines[i];
        w.row.setBackground(solid(isActive ? BG_ACTIVE_LINE : BG_EDITOR));
        w.gutter.setBackground(solid(isActive ? BG_ELEVATED : BG_GUTTER));
        w.lineNumLabel.setColor(isActive ? ACCENT_BLUE : FG_GUTTER);

        w.codeTextLabel.setColor(Color.WHITE);
        w.codeTextLabel.setText(highlightByteScript(lineText));

        if (isActive) {
          w.cursorLabel.setColor(typed < totalLen ? ACCENT_CYAN : STATUS_OK);
          w.cursorLabel.setText(typed < totalLen ? "▋" : " ✓");
        } else {
          w.cursorLabel.setText("");
        }
      }

      // Обновление статус-бара и прогресса набора
      cursorStatusLabel.setText("Ln " + (activeLineIndex + 1) + ", Col " + activeCol);
      int pct = totalLen == 0 ? 100 : (int) Math.round((typed * 100.0) / totalLen);
      progressLabel.setText(typed + "/" + totalLen + " · " + pct + "%");
      progressLabel.setColor(typed >= totalLen && totalLen > 0 ? STATUS_OK : STATUS_WARN);
      if (inspectorProgress != null) inspectorProgress.setText(typed + " / " + totalLen + " · " + pct + "%");
      if (inspectorStatus != null) {
        inspectorStatus.setText(typed >= totalLen ? "02  ✓ Исходник набран" : "02  ◌ Ввод кода: " + pct + "%");
        inspectorStatus.setColor(typed >= totalLen ? STATUS_OK : STATUS_WARN);
      }
      if (inspectorNext != null) {
        String guidance = currentReport != null && currentReport.passed()
            ? "Все тесты пройдены. Открой предпросмотр и отправь результат."
            : currentReport != null
                ? "Проверь диагностику ниже и открой раздел исправлений."
                : typed < totalLen
                    ? "Продолжай ввод ByteScript с клавиатуры."
                    : "Исходник готов. Запусти компиляцию и тесты.";
        inspectorNext.setText(guidance);
      }

      stageHintLabel.setText(typed < totalLen ? "TYPING" : "READY");
      stageHintLabel.setColor(typed < totalLen ? STATUS_WARN : STATUS_OK);

      float trackWidth = Math.max(640f, rootTable.getWidth());
      progressFillCell.width((trackWidth * pct) / 100f);
      progressTrack.invalidateHierarchy();

      // Автоматическая прокрутка к активной строке при наборе
      codeScrollPane.validate();
      float targetY = activeLineIndex * CODE_LINE_HEIGHT;
      float viewH = codeScrollPane.getScrollHeight();
      if (viewH > 0) {
        codeScrollPane.setScrollY(Math.max(0f, targetY - viewH * 0.65f));
      }
    }

    private String reportSignature(Coding.Report r) {
      if (r == null) {
        return "null";
      }
      return r.compiled() + "|" + r.passed() + "|" + r.message() + "|" + r.cases().size();
    }
  }

  private record LineRowWidgets(
      Table row,
      Table gutter,
      Label lineNumLabel,
      Label codeTextLabel,
      Label cursorLabel) {}

  /** Small lexical pass for real per-token ByteScript colors, not whole-line tinting. */
  private String highlightByteScript(String source) {
    StringBuilder markup = new StringBuilder(source.length() + 24);
    boolean functionNameNext = false;
    for (int i = 0; i < source.length();) {
      char ch = source.charAt(i);
      if (ch == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
        appendSyntax(markup, source.substring(i), FG_MUTED);
        break;
      }
      if (ch == '#') {
        appendSyntax(markup, source.substring(i), FG_MUTED);
        break;
      }
      if (ch == '"' || ch == '\'') {
        int end = i + 1;
        while (end < source.length()) {
          if (source.charAt(end) == '\\' && end + 1 < source.length()) {
            end += 2;
            continue;
          }
          if (source.charAt(end++) == ch) break;
        }
        appendSyntax(markup, source.substring(i, end), STATUS_OK);
        i = end;
        continue;
      }
      if (Character.isDigit(ch)) {
        int end = i + 1;
        while (end < source.length() &&
            (Character.isDigit(source.charAt(end)) || source.charAt(end) == '.')) end++;
        appendSyntax(markup, source.substring(i, end), STATUS_WARN);
        i = end;
        continue;
      }
      if (Character.isLetter(ch) || ch == '_') {
        int end = i + 1;
        while (end < source.length() &&
            (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) end++;
        String token = source.substring(i, end);
        Color syntax = switch (token) {
          case "fn", "let", "const", "if", "else", "while", "for", "return" -> ACCENT_PURPLE;
          case "true", "false" -> STATUS_WARN;
          default -> functionNameNext ? ACCENT_CYAN : FG_PRIMARY;
        };
        appendSyntax(markup, token, syntax);
        functionNameNext = token.equals("fn");
        i = end;
        continue;
      }
      appendSyntax(markup, String.valueOf(ch),
          "{}();,=".indexOf(ch) >= 0 ? FG_SECONDARY : FG_PRIMARY);
      i++;
    }
    return markup.toString();
  }

  private void appendSyntax(StringBuilder target, String text, Color color) {
    target.append("[#").append(color).append(']');
    // LibGDX BitmapFont markup escapes literal opening brackets as [[.
    target.append(text.replace("[", "[["));
    target.append("[]");
  }

  /**
   * Определяет семантический цвет строки ByteScript в палитре Tokyo Night.
   */
  public Color resolveLineSyntaxColor(String codeLine) {
    if (codeLine == null) {
      return FG_PRIMARY;
    }
    String trim = codeLine.stripLeading();
    if (trim.isEmpty()) {
      return FG_PRIMARY;
    }
    if (trim.startsWith("//") || trim.startsWith("#")) {
      return FG_MUTED;
    }
    if (trim.startsWith("fn ") || trim.startsWith("let ") || trim.startsWith("const ")) {
      return ACCENT_PURPLE;
    }
    if (trim.startsWith("if ") || trim.startsWith("else") || trim.startsWith("while ")
        || trim.startsWith("for ")) {
      return ACCENT_CYAN;
    }
    if (trim.startsWith("return ") || trim.equals("return")) {
      return STATUS_WARN;
    }
    if (trim.contains("\"") || trim.contains("'")) {
      return STATUS_OK;
    }
    if (trim.endsWith("{") || trim.equals("}")) {
      return FG_SECONDARY;
    }
    return FG_PRIMARY;
  }

  // ===============================================================================================
  // 4. НИЖНЯЯ ДОК-ПАНЕЛЬ: PROBLEMS / OUTPUT / TEST RESULTS
  // ===============================================================================================

  /**
   * Строит компактную нижнюю панель с вкладками PROBLEMS, OUTPUT и TEST RESULTS.
   */
  public Table buildBottomConsoleDock(
      GameState state,
      LifeContent.Job job,
      Coding.Report report,
      Runnable onRunTests,
      Runnable onTabSwitchRebuild) {

    Table dock = new Table();
    dock.top().left();
    dock.setBackground(solid(BG_SURFACE));

    // Счетчики для вкладок
    int problemsCount = 0;
    int totalCases = 0;
    int passedCases = 0;

    if (report != null) {
      if (!report.compiled()) {
        problemsCount = 1;
      } else if (!report.passed()) {
        problemsCount = countFailedCases(report);
      }
      if (report.cases() != null) {
        totalCases = report.cases().size();
        for (String c : report.cases()) {
          if (c != null && c.endsWith("OK")) {
            passedCases++;
          }
        }
      }
    }

    // Полоса вкладок нижней панели
    Table tabBar = new Table();
    tabBar.left().padLeft(8f).padRight(10f);
    tabBar.setBackground(solid(BG_SURFACE));

    addConsoleTabButton(tabBar, "PROBLEMS", 0,
        problemsCount > 0 ? String.valueOf(problemsCount) : "0",
        problemsCount > 0 ? STATUS_ERR : FG_MUTED,
        onTabSwitchRebuild);

    addConsoleTabButton(tabBar, "OUTPUT", 1,
        report != null ? (report.compiled() ? "BUILD OK" : "ERR") : "",
        report != null && !report.compiled() ? STATUS_ERR : ACCENT_CYAN,
        onTabSwitchRebuild);

    addConsoleTabButton(tabBar, "TEST RESULTS", 2,
        totalCases > 0 ? (passedCases + "/" + totalCases) : "",
        (report != null && report.passed()) ? STATUS_OK : (problemsCount > 0 ? STATUS_ERR : FG_MUTED),
        onTabSwitchRebuild);

    tabBar.add().expandX().fillX();

    if (onRunTests != null && job != null) {
      tabBar.add(compactButton("↻ Перезапустить тесты", ButtonVariant.GHOST, true, onRunTests))
          .height(22f);
    }

    dock.add(tabBar).height(26f).growX().row();
    addHorizontalRule(dock, BORDER_SUBTLE, 0f, 0f);

    // Тело выбранной вкладки со скроллом
    Table bodyContent = new Table();
    bodyContent.top().left().pad(8f);
    bodyContent.setBackground(solid(BG_EDITOR));

    switch (activeConsoleTab) {
      case 0 -> populateProblemsTab(bodyContent, state, report);
      case 1 -> populateOutputTab(bodyContent, state, job, report);
      default -> populateTestResultsTab(bodyContent, state, report);
    }

    ScrollPane consoleScroll = createScrollPane(bodyContent, false);
    dock.add(consoleScroll).grow().minHeight(0f).row();
    return dock;
  }

  private void addConsoleTabButton(
      Table tabBar,
      String title,
      int tabIndex,
      String badgeText,
      Color badgeColor,
      Runnable onTabSwitchRebuild) {

    boolean selected = (activeConsoleTab == tabIndex);

    Table tab = new Table();
    tab.setBackground(solid(selected ? BG_EDITOR : BG_SURFACE));

    Table inner = new Table();
    inner.padLeft(10f).padRight(10f);
    inner.add(label(title, selected ? FG_PRIMARY : FG_MUTED, FontRole.SMALL)).padRight(6f);

    if (badgeText != null && !badgeText.isEmpty()) {
      inner.add(monoLabel("[" + badgeText + "]", badgeColor));
    }

    tab.add(inner).height(24f).row();

    Table bottomIndicator = new Table();
    bottomIndicator.setBackground(solid(selected ? ACCENT_BLUE : new Color(0, 0, 0, 0)));
    tab.add(bottomIndicator).height(2f).growX();

    tab.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (activeConsoleTab != tabIndex) {
          activeConsoleTab = tabIndex;
          if (onTabSwitchRebuild != null) {
            onTabSwitchRebuild.run();
          }
        }
      }
    });

    tabBar.add(tab).height(26f).padRight(4f);
  }

  private void populateProblemsTab(Table body, GameState state, Coding.Report report) {
    int totalLen = state.codeSource == null ? 0 : state.codeSource.length();
    if (state.typedCount < totalLen) {
      addDiagnosticRow(body, "WARN", STATUS_WARN, BADGE_WARN_BG,
          "Исходный код набран не полностью (" + state.typedCount + "/" + totalLen + " симв.).",
          "src/main.bs");
    }

    if (report == null) {
      addDiagnosticRow(body, "INFO", ACCENT_BLUE, BADGE_INFO_BG,
          "Компиляция еще не запускалась. Нажми «▶ Run / Test» для проверки ByteScript.",
          "src/main.bs");
      return;
    }

    if (!report.compiled()) {
      addDiagnosticRow(body, "ERR", STATUS_ERR, BADGE_ERR_BG,
          "Ошибка компиляции ByteScript: " + report.message(),
          "src/main.bs:compile");
      return;
    }

    if (report.passed()) {
      addDiagnosticRow(body, "OK", STATUS_OK, BADGE_OK_BG,
          "Проблем не обнаружено. Компиляция и все проверочные кейсы пройдены успешно.",
          "src/main.bs");
      return;
    }

    addDiagnosticRow(body, "FAIL", STATUS_ERR, BADGE_ERR_BG,
        report.message(), "src/main.bs:runtime");

    for (String c : report.cases()) {
      if (c != null && !c.endsWith("OK")) {
        addDiagnosticRow(body, "CASE", STATUS_ERR, BADGE_ERR_BG, c, "test-suite");
      }
    }
  }

  private void populateOutputTab(
      Table body, GameState state, LifeContent.Job job, Coding.Report report) {

    addConsoleLogLine(body, "[Astra ByteScript toolchain — Pinux Astra]", FG_MUTED);
    if (job != null) {
      addConsoleLogLine(body, "> Target contract: " + job.title + " (stage " + state.jobStage + ")", FG_SECONDARY);
    }

    int totalLen = state.codeSource == null ? 0 : state.codeSource.length();
    if (state.typedCount < totalLen) {
      addConsoleLogLine(body,
          "> Ожидание завершения ввода исходника: осталось " + (totalLen - state.typedCount) + " симв.",
          STATUS_WARN);
    } else {
      addConsoleLogLine(body, "> Буфер исходного кода заполнен (" + totalLen + " chars).", STATUS_OK);
    }

    if (report != null) {
      addConsoleLogLine(body,
          "> Compiler status: " + (report.compiled() ? "COMPILED" : "FAILED"),
          report.compiled() ? STATUS_OK : STATUS_ERR);
      addConsoleLogLine(body,
          "> Verdict: " + report.message(),
          report.passed() ? STATUS_OK : STATUS_ERR);
    }
  }

  private void populateTestResultsTab(Table body, GameState state, Coding.Report report) {
    int totalLen = state.codeSource == null ? 0 : state.codeSource.length();
    if (report == null) {
      String msg = state.typedCount < totalLen
          ? "РЕЖИМ ВВОДА: нажимай клавиши на клавиатуре, чтобы допечатать main.bs."
          : "Код готов к проверке. Запусти «▶ Run / Test» для выполнения тестового набора.";
      addDiagnosticRow(body, "WAIT",
          state.typedCount < totalLen ? STATUS_WARN : ACCENT_CYAN,
          state.typedCount < totalLen ? BADGE_WARN_BG : BADGE_INFO_BG,
          msg, "test-runner");
      return;
    }

    addDiagnosticRow(
        body,
        report.passed() ? "PASS" : "FAIL",
        report.passed() ? STATUS_OK : STATUS_ERR,
        report.passed() ? BADGE_OK_BG : BADGE_ERR_BG,
        report.message(),
        report.compiled() ? "ByteScript VM" : "Compiler");

    List<String> cases = report.cases();
    if (cases != null && !cases.isEmpty()) {
      for (int i = 0; i < cases.size(); i++) {
        String c = cases.get(i);
        boolean ok = c != null && c.endsWith("OK");
        addDiagnosticRow(
            body,
            ok ? "✓ #" + (i + 1) : "✕ #" + (i + 1),
            ok ? STATUS_OK : STATUS_ERR,
            ok ? BADGE_OK_BG : BADGE_ERR_BG,
            c,
            ok ? "PASSED" : "FAILED");
      }
    }
  }

  private void addDiagnosticRow(
      Table parent, String tag, Color fg, Color badgeBg, String message, String sourceLoc) {

    Table row = new Table();
    row.left().padTop(3f).padBottom(3f).padLeft(6f).padRight(8f);
    row.setBackground(solid(BG_ELEVATED));

    row.add(badge(tag, fg, badgeBg)).width(54f).left().padRight(10f);

    Label msgLabel = wrapLabel(message, fg == STATUS_ERR ? STATUS_ERR : FG_PRIMARY);
    row.add(msgLabel).growX().minWidth(0f).padRight(10f);

    if (sourceLoc != null && !sourceLoc.isEmpty()) {
      row.add(monoLabel(sourceLoc, FG_MUTED)).right();
    }

    parent.add(row).growX().padBottom(3f).row();
  }

  private void addConsoleLogLine(Table parent, String line, Color color) {
    Label lbl = monoLabel(line, color);
    parent.add(lbl).left().growX().padBottom(3f).row();
  }

  // ===============================================================================================
  // 5. ЕДИНАЯ ДИЗАЙН-СИСТЕМА ДЛЯ ОСТАЛЬНЫХ ЭКРАНОВ IDE (ТАБЛИЦЫ, СТРОКИ, КОМПАКТНЫЕ ПАНЕЛИ)
  // ===============================================================================================

  /**
   * Создает компактный заголовок раздела для не-редакторных экранов (Контракты, Карьера, Git,
   * Оборудование, Лаборатория, Предпросмотр) без гигантских отступов.
   */
  public Table buildSectionHeader(String category, String title, String subtitle, Actor rightWidget) {
    Table header = new Table();
    header.left().pad(10f, 14f, 10f, 14f);
    header.setBackground(solid(BG_SURFACE));

    Table leftCol = new Table();
    leftCol.left();

    Table crumbRow = new Table();
    crumbRow.left();
    crumbRow.add(monoLabel(category.toUpperCase(Locale.ROOT), ACCENT_BLUE)).padRight(8f);
    crumbRow.add(label("·  " + title, FG_PRIMARY, FontRole.UI)).left();
    leftCol.add(crumbRow).left().padBottom(3f).row();

    if (subtitle != null && !subtitle.isEmpty()) {
      leftCol.add(wrapLabel(subtitle, FG_MUTED)).growX().minWidth(0f);
    }

    header.add(leftCol).growX().minWidth(0f).left();

    if (rightWidget != null) {
      header.add(rightWidget).right().padLeft(12f);
    }

    Table outer = new Table();
    outer.add(header).growX().row();
    addHorizontalRule(outer, BORDER_SUBTLE, 0f, 0f);
    return outer;
  }

  /**
   * Создает компактную панель-секцию с тонкой шапкой в стиле инструментальных окон JetBrains.
   */
  public Table buildToolPanel(String panelTitle, String rightMeta) {
    Table panel = new Table();
    panel.top().left();
    panel.setBackground(solid(BG_SURFACE));

    Table head = new Table();
    head.left().pad(5f, 10f, 5f, 10f);
    head.setBackground(solid(BG_ELEVATED));
    head.add(monoLabel("▸ " + panelTitle.toUpperCase(Locale.ROOT), ACCENT_CYAN)).left().expandX();
    if (rightMeta != null && !rightMeta.isEmpty()) {
      head.add(monoLabel(rightMeta, FG_MUTED)).right();
    }

    panel.add(head).growX().row();
    addHorizontalRule(panel, BORDER_SUBTLE, 0f, 0f);
    return panel;
  }

  /**
   * Создает компактную табличную строку (например, для биржи контрактов, списка ревизий Git,
   * оборудования, патчей или портфолио) вместо огромных карточек.
   */
  public Table buildDataRow(
      int rowIndex,
      String codeOrId,
      String primaryTitle,
      String secondaryDescription,
      String metricText,
      Color metricColor,
      Actor actionButton) {

    Table row = new Table();
    row.left().pad(6f, 10f, 6f, 10f);
    row.setBackground(solid(rowIndex % 2 == 0 ? BG_SURFACE : BG_ROW_ALT));

    if (codeOrId != null && !codeOrId.isEmpty()) {
      row.add(monoLabel(codeOrId, ACCENT_BLUE)).width(76f).left().padRight(8f);
    }

    Table textCol = new Table();
    textCol.left();
    textCol.add(label(primaryTitle, FG_PRIMARY, FontRole.SMALL)).left().row();
    if (secondaryDescription != null && !secondaryDescription.isEmpty()) {
      Label desc = wrapLabel(secondaryDescription, FG_MUTED);
      textCol.add(desc).growX().minWidth(0f).padTop(2f);
    }
    row.add(textCol).growX().minWidth(0f).padRight(10f);

    if (metricText != null && !metricText.isEmpty()) {
      row.add(monoLabel(metricText, metricColor)).right().padRight(10f);
    }

    if (actionButton != null) {
      // Scene2D Tables may otherwise shrink a wrapped label to a few pixels,
      // rendering the action vertically when the data columns expand.
      if (actionButton instanceof TextButton textButton) textButton.getLabel().setWrap(false);
      row.add(actionButton).width(136f).minWidth(136f).height(COMPACT_BTN_HEIGHT).right();
    }

    return row;
  }

  /**
   * Строит компактную строку «ключ — значение» для спецификаций контракта, статов карьеры и
   * профилировщика.
   */
  public Table buildKeyValueRow(String key, String value, Color valueColor) {
    Table row = new Table();
    row.left().pad(4f, 10f, 4f, 10f);
    row.setBackground(solid(BG_SURFACE));
    row.add(label(key, FG_MUTED, FontRole.SMALL)).width(160f).left();
    row.add(monoLabel(value, valueColor)).left().growX();
    return row;
  }

  /**
   * Строит компактное уведомление (toast/status strip) для показа {@code notice} без создания
   * огромного блока на пол-экрана.
   */
  public Table buildNoticeStrip(String noticeText, Runnable onDismiss) {
    Table strip = new Table();
    strip.left().pad(5f, 10f, 5f, 10f);

    boolean isError = noticeText.startsWith("✕") || noticeText.toLowerCase(Locale.ROOT).contains("ошиб");
    boolean isOk = noticeText.startsWith("✓");

    Color bg = isError ? BADGE_ERR_BG : (isOk ? BADGE_OK_BG : BADGE_WARN_BG);
    Color fg = isError ? STATUS_ERR : (isOk ? STATUS_OK : STATUS_WARN);

    strip.setBackground(solid(bg));
    strip.add(monoLabel(isError ? "ERR" : (isOk ? "OK " : "INF"), fg)).padRight(10f);
    strip.add(wrapLabel(noticeText, FG_PRIMARY)).growX().minWidth(0f).padRight(8f);

    if (onDismiss != null) {
      strip.add(compactButton("×", ButtonVariant.GHOST, true, onDismiss)).width(22f).height(20f);
    }
    return strip;
  }

  /**
   * Строит компактную заглушку, если пользователь открыл экран, требующий активного задания.
   */
  public Table buildEmptyWorkspaceBanner(String title, String hint, Runnable onOpenContracts) {
    Table box = new Table();
    box.setBackground(solid(BG_SURFACE));
    box.pad(18f);
    box.left();

    box.add(monoLabel("ASTRA IDE // NO ACTIVE TARGET", FG_MUTED)).left().padBottom(6f).row();
    box.add(label(title, FG_PRIMARY, FontRole.UI)).left().padBottom(6f).row();
    box.add(wrapLabel(hint, FG_SECONDARY)).width(460f).left().padBottom(12f).row();

    if (onOpenContracts != null) {
      box.add(compactButton("Перейти к бирже контрактов", ButtonVariant.PRIMARY, true, onOpenContracts))
          .height(ACTION_BTN_HEIGHT).left();
    }
    return box;
  }
}