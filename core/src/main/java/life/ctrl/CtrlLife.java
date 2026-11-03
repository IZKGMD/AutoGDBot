package life.ctrl;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import java.io.IOException;
import java.util.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;
import life.ctrl.render.*;
import life.ctrl.save.SaveStore;

/** Desktop exploration slice. Menus freeze simulation, input belongs to exactly one mode. */
public class CtrlLife extends ApplicationAdapter {
  public enum Mode {
    MENU,
    CREATE,
    CONFIRM_NEW,
    WORLD,
    DIALOGUE,
    PAUSE,
    SETTINGS,
    CONTROLS,
    JOURNAL,
    MAP,
    BAG
  }

  private PixelCanvas c;
  private AudioScene audio;
  private Content content;
  private SaveStore saves;
  private Settings settings;
  private World world;
  private Mode mode = Mode.MENU, settingsReturn = Mode.MENU;
  private Content.Dialogue dialogue;
  private final Set<Integer> held = new HashSet<>();
  private final ArrayList<Button> buttons = new ArrayList<>();
  private final ArrayList<DrawItem> drawItems = new ArrayList<>();
  private final World.Actor actor = new World.Actor();
  private final Comparator<DrawItem> depth = Comparator.comparingDouble(d -> d.y);
  private float accumulator,
      visualTime,
      footTimer,
      cameraX,
      cameraY,
      toastTimer,
      arrivalTimer,
      doorFade;
  private final com.badlogic.gdx.graphics.Color transitionColor =
      new com.badlogic.gdx.graphics.Color(.07f, .11f, .15f, 0);
  private String toast = "", name = "Саша";
  private int selection, bindIndex = -1, newOutfit = 0, windowScale = 3;
  private boolean confirm, clicked, mouseMoved, focused = true, showDetails, hasSession;
  private float clickX, clickY;
  private static final String CREAM = "ecdbb6", MUTED = "a1b2b0", GOLD = "ecc48a", MINT = "a8c3a4";

  private record Button(
      String label, int x, int y, int width, int height, boolean enabled, Runnable action) {}

  private static final class DrawItem {
    String art;
    float x, y, artX, artY;
    int dir, frame, outfit;
    boolean person;

    DrawItem(String art, float x, float y, float ax, float ay) {
      this.art = art;
      this.x = x;
      this.y = y;
      artX = ax;
      artY = ay;
    }
  }

  @Override
  public void create() {
    content = Content.load();
    saves = new SaveStore(SaveStore.defaultDirectory(), content);
    settings = Settings.load(saves.directory());
    c = new PixelCanvas();
    c.preload();
    audio = new AudioScene();
    world = new World(content, new GameState());
    rebuildDrawItems();
    snapCamera();
    windowScale = settings.scale;
    selection = saves.exists() ? 0 : 1;
    if (!Boolean.getBoolean("ctrl.test")) applyDisplay();
    Gdx.input.setInputProcessor(
        new InputAdapter() {
          @Override
          public boolean keyDown(int key) {
            return press(key);
          }

          @Override
          public boolean keyUp(int key) {
            held.remove(key);
            if (key == Keys.TAB) showDetails = false;
            return true;
          }

          @Override
          public boolean keyTyped(char ch) {
            if (mode == Mode.CREATE) {
              if (ch == '\b' && !name.isEmpty()) name = name.substring(0, name.length() - 1);
              else if (!Character.isISOControl(ch)
                  && (Character.isLetterOrDigit(ch) || ch == ' ' || ch == '-')
                  && name.length() < 18) name += ch;
            }
            return true;
          }

          @Override
          public boolean touchDown(int x, int y, int pointer, int button) {
            if (button == Input.Buttons.LEFT) {
              clicked = true;
              clickX = c.logicalX(x);
              clickY = c.logicalY(y);
            }
            return true;
          }

          @Override
          public boolean mouseMoved(int x, int y) {
            mouseMoved = true;
            return true;
          }
        });
  }

  public World world() {
    return world;
  }

  public boolean audioReady() {
    return audio != null && audio.ready();
  }

  public Mode mode() {
    return mode;
  }

  public PixelCanvas canvas() {
    return c;
  }

  private void change(Mode next) {
    mode = next;
    held.clear();
    accumulator = 0;
    selection = next == Mode.MENU && !saves.exists() ? 1 : 0;
    confirm = false;
    clicked = false;
    showDetails = false;
    buttons.clear();
    world.moving = false;
  }

  private boolean press(int key) {
    if (bindIndex >= 0) {
      if (key == Keys.ESCAPE) {
        bindIndex = -1;
        return true;
      }
      if (settings.bind(bindIndex, key)) {
        bindIndex = -1;
        persistSettings();
      } else notify("Эта клавиша занята. Выбери другую.");
      return true;
    }
    if (key == Keys.F11) {
      settings.fullscreen = !settings.fullscreen;
      applyDisplay();
      persistSettings();
      held.clear();
      return true;
    }
    if (mode == Mode.WORLD) {
      held.add(key);
      if (key == settings.interact || key == Keys.ENTER) {
        interact();
      } else if (key == Keys.ESCAPE) {
        change(Mode.PAUSE);
        saveQuiet();
      } else if (key == settings.journal) {
        change(Mode.JOURNAL);
      } else if (key == Keys.M) {
        change(Mode.MAP);
      } else if (key == Keys.I) {
        change(Mode.BAG);
      } else if (key == Keys.F5) {
        save();
      } else if (key == Keys.TAB) {
        showDetails = true;
      }
      return true;
    }
    if (key == Keys.ESCAPE) {
      back();
      return true;
    }
    if (key == Keys.UP || key == Keys.DOWN) {
      int direction = key == Keys.UP ? -1 : 1;
      int next = selection + direction;
      while (next >= 0 && next < buttons.size() && !buttons.get(next).enabled) next += direction;
      if (next >= 0 && next < buttons.size()) selection = next;
      return true;
    }
    if (key == Keys.ENTER) {
      confirm = true;
      return true;
    }
    if (mode == Mode.DIALOGUE && key >= Keys.NUM_1 && key <= Keys.NUM_3) {
      selection = key - Keys.NUM_1;
      confirm = true;
    }
    return true;
  }

  private void back() {
    switch (mode) {
      case MENU -> {
        /* Exit is an explicit menu action. */
      }
      case CREATE, CONFIRM_NEW -> change(Mode.MENU);
      case SETTINGS -> {
        persistSettings();
        change(settingsReturn);
      }
      case CONTROLS -> change(Mode.SETTINGS);
      case PAUSE, JOURNAL, MAP, BAG -> change(Mode.WORLD);
      case DIALOGUE -> {
        change(Mode.WORLD);
        saveQuiet();
      }
      default -> change(Mode.PAUSE);
    }
  }

  private void openSettings(Mode from) {
    settingsReturn = from;
    change(Mode.SETTINGS);
  }

  private void newGame() {
    GameState state = new GameState();
    state.name = name.strip().isEmpty() ? "Саша" : name.strip();
    state.outfit = newOutfit;
    world = new World(content, state);
    hasSession = true;
    rebuildDrawItems();
    snapCamera();
    change(Mode.WORLD);
    arrivalTimer = 4;
    saveQuiet();
    notify("WASD — идти · E — действие · J — журнал");
  }

  private void continueGame() {
    try {
      var loaded = saves.load();
      world = new World(content, loaded.state());
      hasSession = true;
      rebuildDrawItems();
      snapCamera();
      change(Mode.WORLD);
      arrivalTimer = 3;
      if (loaded.fromBackup()) notify("Восстановлена предыдущая резервная копия.");
    } catch (IOException e) {
      notify("Сохранение не прочитано. Можно начать новую игру.");
    }
  }

  private void save() {
    try {
      saves.save(world.state);
      notify("Игра сохранена.");
    } catch (IOException | RuntimeException e) {
      notify("Не удалось сохранить. Проверь доступ к папке данных.");
      Gdx.app.error("Save", "Не удалось сохранить игру", e);
    }
  }

  private void saveQuiet() {
    if (!hasSession) return;
    try {
      saves.save(world.state);
    } catch (IOException | RuntimeException e) {
      notify("Автосохранение не удалось. Нажми F5 для повтора.");
      Gdx.app.error("Save", "Автосохранение", e);
    }
  }

  private void persistSettings() {
    try {
      settings.save(saves.directory());
    } catch (IOException e) {
      notify("Не удалось сохранить настройки.");
    }
  }

  private void applyDisplay() {
    if (settings.fullscreen) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
    else {
      var display = Gdx.graphics.getDisplayMode();
      int max = Math.max(2, Math.min(display.width / 480, display.height / 270));
      windowScale = Math.min(settings.scale, max);
      Gdx.graphics.setWindowedMode(480 * windowScale, 270 * windowScale);
    }
  }

  public void focusChanged(boolean focused) {
    this.focused = focused;
    held.clear();
    accumulator = 0;
    showDetails = false;
    if (!focused && mode == Mode.WORLD) {
      change(Mode.PAUSE);
      saveQuiet();
    }
  }

  @Override
  public void pause() {
    focusChanged(false);
  }

  @Override
  public void resume() {
    focused = true;
    held.clear();
    accumulator = 0;
  }

  private void notify(String text) {
    toast = text;
    toastTimer = 4.5f;
  }

  private void interact() {
    var npc = world.nearestNpc();
    var prop = world.nearestProp();
    if (npc != null) {
      openDialogue(world.npcDialogue(npc));
      return;
    }
    if (prop == null) return;
    audio.click(settings);
    if (prop.action.equals("door")) {
      audio.door(settings);
      doorFade = settings.reducedEffects ? 0 : .18f;
      world.enter(prop);
      rebuildDrawItems();
      snapCamera();
      held.clear();
      accumulator = 0;
      arrivalTimer = 3;
      saveQuiet();
    } else if (prop.action.equals("wardrobe")) {
      world.state.outfit = (world.state.outfit + 1) % 3;
      notify("Куртка: " + new String[] {"медовая", "дождливая", "кирпичная"}[world.state.outfit]);
      saveQuiet();
    } else openDialogue(world.propDialogue(prop));
  }

  private void openDialogue(String id) {
    dialogue = content.dialogue(id);
    change(Mode.DIALOGUE);
    audio.click(settings);
  }

  private void choose(Content.Choice choice) {
    boolean delivered = world.state.deliveredLetter;
    world.effect(choice.effect);
    rebuildDrawItems();
    saveQuiet();
    if (!delivered && world.state.deliveredLetter) {
      audio.success(settings);
      notify("Письмо у Миры. В журнале новая запись.");
    }
    if (choice.next.isEmpty()) change(Mode.WORLD);
    else {
      dialogue = content.dialogue(choice.next);
      selection = 0;
    }
  }

  private float targetCameraX() {
    return Math.max(0, Math.min(world.map().width - 480, world.state.x - 240));
  }

  private float targetCameraY() {
    return Math.max(0, Math.min(world.map().height - 270, world.state.y - 148));
  }

  private void snapCamera() {
    cameraX = targetCameraX();
    cameraY = targetCameraY();
  }

  private void rebuildDrawItems() {
    drawItems.clear();
    for (var p : world.map().objects)
      if (p.art != null && world.visible(p)) {
        String art = p.art;
        if (p.id.equals("boxes") && world.state.unpacked) art = "boxes_open";
        if (p.id.equals("plant") && world.state.wateredPlant) art = "plant_watered";
        if (p.id.equals("post") && world.state.signedMailbox) art = "mailbox_signed";
        drawItems.add(new DrawItem(art, p.x, p.artY + c.texture(art).getHeight(), p.artX, p.artY));
      }
    for (var n : world.map().npcs) {
      DrawItem d = new DrawItem(n.art, 0, 0, 0, 0);
      d.person = true;
      drawItems.add(d);
    }
    DrawItem hero = new DrawItem("hero", world.state.x, world.state.y, 0, 0);
    hero.person = true;
    drawItems.add(hero);
  }

  @Override
  public void render() {
    float delta = Math.min(Gdx.graphics.getDeltaTime(), .1f);
    visualTime += delta;
    toastTimer = Math.max(0, toastTimer - delta);
    arrivalTimer = Math.max(0, arrivalTimer - delta);
    doorFade = Math.max(0, doorFade - delta);
    if (mode == Mode.WORLD && focused) {
      accumulator += delta;
      float dx =
          (held.contains(settings.right) || held.contains(Keys.RIGHT) ? 1 : 0)
              - (held.contains(settings.left) || held.contains(Keys.LEFT) ? 1 : 0);
      float dy =
          (held.contains(settings.down) || held.contains(Keys.DOWN) ? 1 : 0)
              - (held.contains(settings.up) || held.contains(Keys.UP) ? 1 : 0);
      while (accumulator >= World.STEP) {
        world.tick(dx, dy);
        accumulator -= World.STEP;
      }
      if (world.moving) {
        footTimer += delta;
        if (footTimer > .30f) {
          audio.step(settings);
          footTimer = 0;
        }
      }
      float blend = 1 - (float) Math.exp(-delta * 9);
      cameraX += (targetCameraX() - cameraX) * blend;
      cameraY += (targetCameraY() - cameraY) * blend;
    } else accumulator = 0;
    audio.update(settings, world.state.map.equals("street"), focused);
    buttons.clear();
    c.begin();
    drawWorld();
    if (doorFade > 0) {
      transitionColor.a = doorFade / .18f;
      c.rect(0, 0, 480, 270, transitionColor);
    }
    switch (mode) {
      case MENU -> drawMenu();
      case CREATE -> drawCreate();
      case CONFIRM_NEW -> drawConfirm();
      case WORLD -> drawHud();
      case DIALOGUE -> {
        drawHud();
        drawDialogue();
      }
      case PAUSE -> drawPause();
      case SETTINGS -> drawSettings();
      case CONTROLS -> drawControls();
      case JOURNAL -> drawJournal();
      case MAP -> drawMap();
      case BAG -> drawBag();
    }
    if (toastTimer > 0) {
      int w = Math.min(460, (int) c.width(toast) + 18);
      int y = (mode == Mode.WORLD || mode == Mode.DIALOGUE) ? 49 : 4;
      c.rect((480 - w) / 2, y, w, 20, "182a35f5");
      c.center(toast, y + 4, MINT);
    }
    c.end();
    activateButtons();
    mouseMoved = false;
  }

  private void drawWorld() {
    float ox = Math.round(cameraX), oy = Math.round(cameraY);
    c.art(world.state.map, -ox, -oy);
    if (world.state.map.equals("street")) {
      c.small("ДОМ № 7", 153 - ox, 160 - oy, CREAM);
      c.small("РЕМОНТ", 413 - ox, 179 - oy, "554947");
      c.small("У ДОМА", 723 - ox, 147 - oy, "554947");
    }
    for (var item : drawItems)
      if (item.person) {
        if (item.art.equals("hero")) {
          item.x = world.state.x;
          item.y = world.state.y;
          item.dir = world.state.direction;
          item.frame = world.moving ? (int) (world.state.elapsed * 8) % 4 : 0;
          item.outfit = world.state.outfit;
        } else
          for (var npc : world.map().npcs)
            if (npc.art.equals(item.art)) {
              world.position(npc, actor);
              item.x = actor.x;
              item.y = actor.y;
              item.dir = actor.direction;
              item.frame = actor.moving ? (int) (world.state.elapsed * 5) % 4 : 0;
            }
      }
    drawItems.sort(depth);
    for (var item : drawItems) {
      if (item.person) {
        c.rect(item.x - 7 - ox, item.y - 2 - oy, 14, 4, "172d3570");
        c.actor(item.art, item.dir, item.frame, item.x - ox, item.y - oy, item.outfit);
      } else {
        var texture = c.texture(item.art);
        if (item.artX + texture.getWidth() > ox
            && item.artX < ox + 480
            && item.artY + texture.getHeight() > oy
            && item.artY < oy + 270) c.art(item.art, item.artX - ox, item.artY - oy);
      }
    }
    if (world.state.map.equals("room")) {
      // CRT status light and a narrow glow cast by the window.
      c.art("glow_warm", 132 - ox, 34 - oy);
      c.art("glow_cool", 209 - ox, 39 - oy);
      c.rect(221 - ox, 65 - oy, 55, 2, "b3c7ad48");
      c.rect(153 - ox, 105 - oy, 2, 2, ((int) (visualTime * 2) % 2 == 0) ? "ecc48a" : "7d7770");
    }
    if (world.state.map.equals("stair")) c.art("glow_warm", 195 - ox, 20 - oy);
    if (world.state.map.equals("street")) {
      c.art("glow_warm", 359 - ox, 180 - oy);
      c.art("glow_warm", 359 - ox, 254 - oy);
      c.art("glow_warm", 830 - ox, 303 - oy);
      c.art("glow_warm", 830 - ox, 378 - oy);
      c.art("glow_warm", 617 - ox, 163 - oy);
      c.art("glow_warm", 681 - ox, 163 - oy);
      c.rect(0, 0, 480, 270, "18304622");
      if (!settings.reducedEffects)
        for (int i = 0; i < 65; i++) {
          float x = (i * 83 + visualTime * 21) % 500 - 10,
              y = (i * 47 + visualTime * 135) % 290 - 10;
          c.rect(x, y, 1, 5, "b8c9cc55");
          c.rect(x - 1, y + 4, 1, 3, "b8c9cc35");
        }
    }
  }

  private void drawHud() {
    c.panel(10, 10, 148, 32);
    c.small(world.map().name.toUpperCase(Locale.ROOT), 18, 15, CREAM);
    c.small(
        "ДЕНЬ " + (1 + world.state.minutes() / 1440) + "   /   " + world.state.clock(),
        18,
        28,
        MUTED);
    String help = Keys.toString(settings.journal) + " журнал   M карта   Esc пауза";
    c.small(help, 470 - c.smallWidth(help), 15, CREAM);
    if (arrivalTimer > 0 && toastTimer <= 0) {
      c.panel(133, 51, 214, 30);
      c.center(world.map().subtitle, 58, GOLD);
    }
    if (mode != Mode.WORLD) return;
    var n = world.nearestNpc();
    var p = world.nearestProp();
    String prompt = n != null ? "Поговорить: " + n.name.split(" · ")[0] : p != null ? p.label : "";
    if (n == null && p != null) {
      if (p.id.equals("plant") && world.state.wateredPlant) prompt = "Осмотреть растение";
      if (p.id.equals("boxes") && world.state.unpacked) prompt = "Осмотреть разобранную коробку";
    }
    if (!prompt.isEmpty()) {
      String text = Keys.toString(settings.interact) + "  —  " + prompt;
      int w = (int) c.width(text) + 24;
      c.panel((480 - w) / 2, 240, w, 23);
      c.center(text, 245, CREAM);
      if (n != null) world.position(n, actor);
      float x = n != null ? actor.x : p.x, y = n != null ? actor.y : p.y;
      c.text("·", x - cameraX - 2, y - cameraY - 34, GOLD);
    } else if (showDetails) {
      c.panel(16, 235, 448, 27);
      c.center(world.state.objective(), 241, CREAM);
    } else c.small("TAB — цель     F5 — сохранить", 16, 252, MUTED);
    if (world.state.deliveredLetter && world.state.map.equals("room")) {
      c.small("В новом городе появился знакомый свет.", 100, 224, GOLD);
    }
  }

  private void shade() {
    c.rect(0, 0, 480, 270, "142332cb");
  }

  private void drawMenu() {
    shade();
    c.small("ТИХИЙ ГОРОД. БОЛЬШОЕ НАЧАЛО.", 32, 31, MINT);
    c.title("CTRL+LIFE", 30, 57, CREAM);
    c.text("Н У Л Е В О Й   Б И Л Д", 33, 93, GOLD);
    c.wrap(
        "Коробки ещё не разобраны.\nЗа окном — дождь.\nА впереди — твоя жизнь.",
        33,
        122,
        236,
        MUTED);
    button("Продолжить", 32, 182, 176, 23, saves.exists(), this::continueGame);
    button(
        "Новая игра",
        32,
        209,
        176,
        23,
        true,
        () -> {
          name = "Саша";
          newOutfit = 0;
          change(saves.exists() ? Mode.CONFIRM_NEW : Mode.CREATE);
        });
    button("Настройки", 245, 182, 135, 23, true, () -> openSettings(Mode.MENU));
    button("Выйти", 245, 209, 135, 23, true, () -> Gdx.app.exit());
    c.small("GATE 01  /  ПЕРВЫЙ ВЕЧЕР", 32, 248, MUTED);
    c.small("0.1.0", 419, 248, MUTED);
    // An illuminated architectural vignette cut out of the room.
    c.panel(314, 44, 120, 112);
    c.art("desk", 342, 66);
    c.actor("hero", 0, (int) (visualTime * .7f) % 4, 372, 139, 0);
    c.small("КОМНАТА 04", 339, 145, GOLD);
  }

  private void drawCreate() {
    shade();
    c.panel(65, 24, 350, 222);
    c.small("НОВЫЙ АДРЕС", 84, 38, MINT);
    c.text("Кто зажигает свет в комнате 04?", 84, 59, CREAM);
    c.small("Имя · 21 год или старше", 84, 89, MUTED);
    c.rect(84, 105, 220, 25, "192b39");
    c.text(name + ((int) (visualTime * 2) % 2 == 0 ? "_" : ""), 91, 109, GOLD);
    c.small("Печатай имя. Backspace — стереть.", 84, 133, MUTED);
    c.actor("hero", 0, 0, 359, 125, newOutfit);
    button(
        "Куртка: " + new String[] {"медовая", "дождливая", "кирпичная"}[newOutfit],
        84,
        159,
        210,
        23,
        true,
        () -> newOutfit = (newOutfit + 1) % 3);
    button("Начать вечер", 84, 199, 180, 25, true, this::newGame);
    button("Назад", 292, 199, 103, 25, true, () -> change(Mode.MENU));
  }

  private void drawConfirm() {
    shade();
    c.panel(70, 65, 340, 141);
    c.text("Начать новую историю?", 89, 81, CREAM);
    c.wrap(
        "Текущее сохранение будет заменено. Предыдущая копия останется в папке данных.",
        89,
        108,
        300,
        MUTED);
    button("Новая история", 89, 163, 149, 25, true, () -> change(Mode.CREATE));
    button("Отмена", 253, 163, 137, 25, true, () -> change(Mode.MENU));
  }

  private void drawPause() {
    shade();
    c.panel(133, 37, 214, 200);
    c.center("МОЖНО НЕ ТОРОПИТЬСЯ", 52, CREAM);
    c.center("Время остановлено", 73, MUTED);
    button("Вернуться в игру", 151, 101, 178, 24, true, () -> change(Mode.WORLD));
    button("Сохранить", 151, 130, 178, 24, true, this::save);
    button("Настройки", 151, 159, 178, 24, true, () -> openSettings(Mode.PAUSE));
    button(
        "В главное меню",
        151,
        188,
        178,
        24,
        true,
        () -> {
          saveQuiet();
          change(Mode.MENU);
        });
  }

  private void drawDialogue() {
    c.panel(14, 142, 452, 121);
    c.small(dialogue.speaker.toUpperCase(Locale.ROOT), 27, 150, GOLD);
    String portrait =
        switch (dialogue.speaker) {
          case "Мира" -> "mira";
          case "Аркадий" -> "arkady";
          case "Лев" -> "lev";
          default -> "note";
        };
    c.rect(26, 165, 34, 35, "1c303e");
    c.art("portrait_" + portrait, 27, 166);
    c.wrap(dialogue.text, 72, 166, 378, CREAM);
    int count = dialogue.choices.length;
    for (int i = 0; i < count; i++) {
      var choice = dialogue.choices[i];
      button(
          (i + 1) + ". " + choice.label.replace("Саша", world.state.name),
          27,
          207 + i * 16,
          426,
          15,
          true,
          () -> choose(choice));
    }
    c.small("↑↓ / Enter · Esc — закончить разговор", 232, 133, MUTED);
  }

  private void drawSettings() {
    shade();
    c.panel(70, 16, 340, 239);
    c.text("Настройки вечера", 89, 30, CREAM);
    button(
        "Музыка    " + Math.round(settings.music * 100) + "%",
        89,
        57,
        302,
        23,
        true,
        () -> {
          settings.music = settings.music >= .99f ? 0 : Math.min(1, settings.music + .25f);
          persistSettings();
        });
    button(
        "Эффекты   " + Math.round(settings.sfx * 100) + "%",
        89,
        84,
        302,
        23,
        true,
        () -> {
          settings.sfx = settings.sfx >= .99f ? 0 : Math.min(1, settings.sfx + .25f);
          persistSettings();
        });
    button(
        "Окно "
            + settings.scale
            + "×  /  "
            + (settings.fullscreen ? "полный экран" : "оконный режим"),
        89,
        111,
        302,
        23,
        true,
        () -> {
          if (settings.fullscreen) {
            settings.fullscreen = false;
            settings.scale = 2;
          } else if (settings.scale == 4) settings.fullscreen = true;
          else settings.scale++;
          applyDisplay();
          persistSettings();
        });
    button(
        "Дождь и эффекты: " + (settings.reducedEffects ? "уменьшены" : "полные"),
        89,
        138,
        302,
        23,
        true,
        () -> {
          settings.reducedEffects = !settings.reducedEffects;
          persistSettings();
        });
    button("Переназначить клавиши", 89, 165, 302, 23, true, () -> change(Mode.CONTROLS));
    button("Готово", 89, 208, 302, 25, true, this::back);
    c.small("F11 — полный экран. Звук: нажми для смены уровня.", 89, 193, MUTED);
  }

  private void drawControls() {
    shade();
    c.panel(70, 12, 340, 247);
    c.text("Управление", 89, 25, CREAM);
    String[] labels = {"Вверх", "Вниз", "Влево", "Вправо", "Взаимодействие", "Журнал"};
    for (int i = 0; i < 6; i++) {
      int index = i;
      button(
          labels[i] + "    [ " + Keys.toString(settings.key(i)) + " ]",
          89,
          50 + i * 24,
          302,
          21,
          true,
          () -> {
            bindIndex = index;
            held.clear();
          });
    }
    c.small(
        bindIndex >= 0
            ? "Нажми новую клавишу. Esc — отмена."
            : "Стрелки, Enter, Esc, I, M, Tab, F5, F11 закреплены.",
        89,
        198,
        bindIndex >= 0 ? GOLD : MUTED);
    button("Готово", 89, 222, 302, 23, bindIndex < 0, () -> change(Mode.SETTINGS));
  }

  private void drawJournal() {
    shade();
    c.panel(34, 17, 412, 237);
    c.small("БЛОКНОТ  /  " + world.state.name.toUpperCase(Locale.ROOT), 51, 30, MINT);
    c.text("Первый вечер", 51, 49, CREAM);
    c.wrap(world.state.objective(), 51, 70, 374, GOLD);
    int y = 107;
    var s = world.state;
    journalLine(s.inspectedPc, "Старый ПК: три сигнала. Сначала диагностика.", y);
    y += 22;
    journalLine(s.metMira, "Познакомиться с Мирой в подъезде.", y);
    y += 22;
    journalLine(s.metArkady, "Найти Аркадия у мастерской, справа от дома.", y);
    y += 22;
    journalLine(
        s.deliveredLetter,
        s.deliveredLetter
            ? "Письмо возвращено. Весной посадим семена."
            : s.hasLetter
                ? "Письмо найдено. Мира ждёт в подъезде."
                : "Письмо Миры: под лавкой рядом с магазином.",
        y);
    y += 22;
    journalLine(s.metLev, "Поговорить со Львом на другой стороне улицы.", y);
    button("Закрыть блокнот", 51, 219, 378, 23, true, () -> change(Mode.WORLD));
  }

  private void journalLine(boolean done, String text, int y) {
    c.rect(52, y + 3, 8, 8, done ? MINT : "5c7279");
    if (done) c.rect(54, y + 5, 4, 4, "263847");
    c.small(text, 67, y, done ? MINT : MUTED);
  }

  private void drawBag() {
    shade();
    c.panel(60, 40, 360, 188);
    c.text("В карманах", 78, 54, CREAM);
    c.wrap(
        world.state.hasLetter
            ? "Плотный конверт для Миры. Семена внутри тихо шуршат. Нужно отнести его в подъезд."
            : "Ключ от комнаты 04, телефон и старый блокнот. Всё, с чего начинается новая жизнь.",
        78,
        83,
        322,
        MUTED);
    if (world.state.hasLetter) c.art("letter", 81, 142);
    c.small(
        world.state.deliveredLetter
            ? "Контакт добавлен: Мира, соседка."
            : "Ключи и блокнот всегда с собой.",
        78,
        165,
        GOLD);
    button("Убрать в карман", 78, 190, 324, 24, true, () -> change(Mode.WORLD));
  }

  private void drawMap() {
    shade();
    c.panel(28, 16, 424, 239);
    c.small("КАРТА ПРОГУЛКИ", 46, 30, MINT);
    c.text("Улица Тихая", 46, 49, CREAM);
    c.rect(51, 130, 376, 20, "657c81");
    c.rect(280, 150, 19, 45, "657c81");
    c.rect(51, 192, 376, 11, "657c81");
    c.panel(58, 83, 87, 44);
    c.small("ДОМ № 7", 70, 91, CREAM);
    c.small("Ты живёшь здесь", 64, 108, MUTED);
    c.panel(182, 83, 93, 44);
    c.small("МАСТЕРСКАЯ", 192, 91, CREAM);
    c.small("Аркадий рядом", 192, 108, MUTED);
    c.panel(317, 83, 99, 44);
    c.small("МАГАЗИН", 333, 91, CREAM);
    c.small("Лавка с письмом", 324, 108, MUTED);
    c.small("Мира — в подъезде", 53, 158, MUTED);
    c.small("Лев — у остановки", 307, 183, MUTED);
    float px = world.state.map.equals("street") ? 51 + world.state.x / 960 * 376 : 101;
    float py = world.state.map.equals("street") ? (world.state.y > 400 ? 195 : 138) : 120;
    c.rect(px - 3, py - 3, 7, 7, GOLD);
    c.small("ты", px + 8, py - 6, GOLD);
    button("Вернуться к прогулке", 46, 220, 388, 22, true, () -> change(Mode.WORLD));
  }

  private void button(
      String label, int x, int y, int width, int height, boolean enabled, Runnable action) {
    int index = buttons.size();
    Button b = new Button(label, x, y, width, height, enabled, action);
    buttons.add(b);
    if (mouseMoved && contains(b, c.mouseX(), c.mouseY()) && enabled) selection = index;
    boolean selected = selection == index && enabled;
    boolean pressed =
        enabled && ((clicked && contains(b, clickX, clickY)) || (confirm && selected));
    c.rect(x, y, width, height, selected ? "b69b74" : "465c65");
    c.rect(
        x + 1, y + 1, width - 2, height - 2, pressed ? "1a303b" : selected ? "40545b" : "263847");
    if (selected) c.rect(x + 3, y + 3, 2, height - 6, GOLD);
    if (height < 20) c.small(label, x + 9, y + 2, enabled ? (selected ? GOLD : CREAM) : "718387");
    else
      c.text(label, x + 10, y + (height - 15) / 2, enabled ? (selected ? GOLD : CREAM) : "718387");
  }

  private boolean contains(Button b, float x, float y) {
    return x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height;
  }

  private void activateButtons() {
    Button target = null;
    if (clicked) {
      for (var b : buttons)
        if (b.enabled && contains(b, clickX, clickY)) {
          target = b;
          break;
        }
    } else if (confirm
        && selection >= 0
        && selection < buttons.size()
        && buttons.get(selection).enabled) target = buttons.get(selection);
    clicked = false;
    confirm = false;
    if (target != null) {
      audio.click(settings);
      target.action.run();
    }
  }

  @Override
  public void resize(int width, int height) {
    held.clear();
  }

  @Override
  public void dispose() {
    if (mode != Mode.MENU && mode != Mode.CREATE && mode != Mode.CONFIRM_NEW && world != null)
      saveQuiet();
    if (settings != null) persistSettings();
    if (audio != null) audio.dispose();
    if (c != null) c.dispose();
  }
}
