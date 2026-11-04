package life.ctrl;

import com.badlogic.gdx.*;
import com.badlogic.gdx.Input.Keys;
import java.io.IOException;
import java.util.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;
import life.ctrl.render.*;
import life.ctrl.save.SaveStore;
import life.ctrl.ui.LifeScreens;

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
    BAG,
    INTRO,
    SHOP,
    IDE,
    SOCIAL,
    CONTACTS,
    REST,
    SCENE,
    NIGHT,
    BUDGET
  }

  private PixelCanvas c;
  private LifeScreens life;
  private List<float[]> guidePath = List.of();
  private float guideTimer;
  private AudioScene audio;
  private final com.badlogic.gdx.graphics.Color dusk =
      new com.badlogic.gdx.graphics.Color(.10f, .17f, .27f, 0);
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
    life = new LifeScreens(this, c);
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
            } else if (mode == Mode.IDE) {
              life.pcKeyTyped(ch);
            }
            return true;
          }

          @Override
          public boolean touchDown(int x, int y, int pointer, int button) {
            if (button == Input.Buttons.LEFT) {
              int px = Math.round(c.logicalX(x)), py = Math.round(c.logicalY(y));
              if (mode == Mode.IDE && life.pcPointerDown(px, py)) {
                clicked = false;
                return true;
              }
              clicked = true;
              clickX = c.logicalX(x);
              clickY = c.logicalY(y);
            }
            return true;
          }

          @Override
          public boolean touchDragged(int x, int y, int pointer) {
            if (mode == Mode.IDE)
              return life.pcPointerDragged(Math.round(c.logicalX(x)), Math.round(c.logicalY(y)));
            return false;
          }

          @Override
          public boolean touchUp(int x, int y, int pointer, int button) {
            if (mode == Mode.IDE && button == Input.Buttons.LEFT)
              return life.pcPointerUp(Math.round(c.logicalX(x)), Math.round(c.logicalY(y)));
            return false;
          }

          @Override
          public boolean mouseMoved(int x, int y) {
            mouseMoved = true;
            return true;
          }
        });
  }

  public void switchMode(Mode next) {
    change(next);
  }

  public void resetSelection() {
    selection = 0;
    confirm = false;
    clicked = false;
  }

  public void uiButton(String text, int x, int y, int w, int h, boolean enabled, Runnable run) {
    button(text, x, y, w, h, enabled, run);
  }

  public void refreshState() {
    Social.expire(world.state);
    rebuildDrawItems();
    saveQuiet();
    guideTimer = 0;
  }

  public void successCue() {
    audio.success(settings);
  }

  public LifeScreens lifeScreens() {
    return life;
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
    if (mode == Mode.IDE && next != Mode.IDE && life != null) life.pcCancelPointer();
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
    if (mode == Mode.IDE && life.pcKeyDown(key)) return true;
    if (mode == Mode.WORLD) {
      held.add(key);
      if (key == settings.interact || key == Keys.ENTER) {
        interact();
      } else if (key == Keys.ESCAPE) {
        change(Mode.PAUSE);
        saveQuiet();
      } else if (key == settings.journal) {
        change(Mode.JOURNAL);
      } else if (key == Keys.R) {
        change(Mode.CONTACTS);
      } else if (key == Keys.B) {
        change(Mode.BUDGET);
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
      case INTRO -> {
        world.state.introSeen = true;
        change(Mode.WORLD);
        saveQuiet();
      }
      case IDE, SHOP, SOCIAL, CONTACTS, REST, SCENE, NIGHT, BUDGET, PAUSE, JOURNAL, MAP, BAG -> {
        change(Mode.WORLD);
        saveQuiet();
      }
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
    life.introPage = 0;
    change(Mode.INTRO);
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
      life.introPage = 0;
      change(world.state.introSeen ? Mode.WORLD : Mode.INTRO);
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
    var npc = world.interactionNpc();
    var prop = world.nearestProp();
    if (npc != null) {
      if (npc.id.equals("mira") || npc.id.equals("asya") || npc.id.equals("nika")) {
        if (npc.id.equals(world.state.plannedGirl)
            && world.state.map.equals(world.state.plannedVenue)) life.openDate();
        else life.openSocial(npc.id, false);
        return;
      }
      String reaction = Career.community(world.state, world.state.map);
      if (npc.id.equals("tamara") && !reaction.isEmpty()) {
        life.scene(
            "Тамара", reaction + "\n\nЕда — у стойки. Рада, что ты заглянул(а).", Mode.WORLD);
        return;
      }
      openDialogue(world.npcDialogue(npc));
      return;
    }
    if (prop == null) return;
    audio.click(settings);
    switch (prop.action) {
      case "mentor" -> {
        life.openCode();
        life.codePage = 8;
        return;
      }
      case "computer" -> {
        life.openCode();
        return;
      }
      case "shop" -> {
        life.shopPage = 0;
        change(Mode.SHOP);
        return;
      }
      case "rest" -> {
        change(Mode.REST);
        return;
      }
      case "date" -> {
        life.openDate();
        return;
      }
      case "courier" -> {
        life.scene("Подработка без ПК", Life.courier(world.state), Mode.WORLD);
        return;
      }
      case "wait" -> {
        Life.advance(world.state, 30);
        world.state.energy = Math.min(100, world.state.energy + 8);
        life.scene("Пауза в сквере", "Прошло 30 минут. Ты немного отдохнул(а).", Mode.WORLD);
        return;
      }
    }
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
    return Math.max(0, Math.min(world.map().height - 270, world.state.y - 172));
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
        drawItems.add(
            new DrawItem(
                art,
                p.x,
                p.solid != null ? p.solid[1] + p.solid[3] : p.artY + c.artHeight(art),
                p.artX,
                p.artY));
      }
    for (var n : world.npcs()) {
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
    audio.update(
        settings,
        (world.state.map.equals("street") || world.state.map.equals("park"))
            && Atmosphere.rain(world.state),
        focused);
    buttons.clear();
    c.begin();
    drawWorld();
    if (mode == Mode.WORLD) drawGuide();
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
      case JOURNAL, MAP, BAG, INTRO, SHOP, IDE, SOCIAL, CONTACTS, REST, SCENE, NIGHT, BUDGET ->
          life.draw(mode);
    }
    if (toastTimer > 0 && mode == Mode.WORLD) {
      int w = Math.min(460, (int) Math.ceil(c.width(toast)) + 18);
      int y = 57;
      c.rect((480 - w) / 2, y, w, 20, "182a35f5");
      c.fitted(toast, (480 - w) / 2f + 9, y + 4, w - 18, MINT, false);
    }
    c.end();
    activateButtons();
    mouseMoved = false;
  }

  private void drawWorld() {
    float ox = Math.round(cameraX), oy = Math.round(cameraY);
    c.art(world.state.map, -ox, -oy);
    var boundary = SceneLayout.bounds(world.state.map);
    c.clip(boundary.x() - ox, boundary.y() - oy, boundary.width(), boundary.height());
    if (world.state.map.equals("street")) {
      String[] names = {"ОБЩЕЖИТИЕ № 7", "ПЛЁНКА", "МАСТЕРСКАЯ", "ЛОЖКА"};
      int[] left = {76, 300, 411, 628}, widths = {200, 86, 185, 217};
      for (int i = 0; i < names.length; i++) {
        c.rect(left[i] - ox, 145 - oy, widths[i], 12, "203c46");
        c.rect(left[i] - ox, 145 - oy, widths[i], .5f, "b7b397");
        c.fitted(
            names[i],
            left[i] + (widths[i] - c.smallWidth(names[i])) / 2 - ox,
            146 - oy,
            widths[i] - 4,
            "ead7ae",
            true);
      }
      c.rect(850 - ox, 415 - oy, 85, 14, "203c46");
      c.fitted("СКВЕР →", 862 - ox, 417 - oy, 69, "d6d9b5", true);
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
          for (var npc : world.npcs())
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
        c.scaledArt("shadow", item.x - 9 - ox, item.y - 4 - oy, 18, 7);
        c.actor(item.art, item.dir, item.frame, item.x - ox, item.y - oy, item.outfit);
      } else {
        String drawnArt =
            item.art.equals("desk")
                    && world.state.map.equals("room")
                    && world.state.upgrades.contains("ssd")
                ? "desk_ssd"
                : item.art;
        if (item.art.equals("desk")
            && world.state.map.equals("room")
            && world.state.upgrades.contains("chair")) c.art("chair_upgrade", 204 - ox, 121 - oy);
        c.scaledArt("shadow", item.artX - 2 - ox, item.y - 7 - oy, c.artWidth(drawnArt) + 4, 13);
        if (item.artX + c.artWidth(drawnArt) > ox
            && item.artX < ox + 480
            && item.artY + c.artHeight(drawnArt) > oy
            && item.artY < oy + 270) c.art(drawnArt, item.artX - ox, item.artY - oy);
      }
    }
    float night = Atmosphere.night(world.state);
    boolean outside = world.state.map.equals("street") || world.state.map.equals("park");
    dusk.a = night * (outside ? .32f : .18f);
    c.rect(0, 0, 480, 270, dusk);
    if (world.state.map.equals("room")) {
      c.art("light_monitor", 107 - ox, 64 - oy);
      c.glow("light_window", 170 - ox, 55 - oy, .3f + night * .7f);
      c.rect(211 - ox, 121 - oy, 2, 2, ((int) (visualTime * 2) % 2 == 0) ? "c4c992" : "68756e");
      if (!settings.reducedEffects) {
        int lift = (int) (visualTime * 4) % 4;
        c.rect(163 - ox, 110 - oy - lift, 1, 2, "d8d3b14a");
      }
    }
    if (world.state.map.equals("stair"))
      c.glow("light_amber", 152 - ox, 40 - oy, .4f + night * .6f);
    if (world.state.map.equals("cafe") || world.state.map.equals("diner"))
      c.art("light_amber", 90 - ox, 68 - oy);
    if (world.state.map.equals("street")) {
      for (float[] light : new float[][] {{391, 282}, {862, 405}, {649, 192}, {713, 192}})
        c.glow("light_amber", light[0] - 80 - ox, light[1] - 56 - oy, night);
      for (int x : new int[] {130, 241, 445, 556, 685, 796})
        c.glow("light_window", x - 80 - ox, 118 - oy, night);
    }
    if (outside && Atmosphere.rain(world.state) && !settings.reducedEffects) {
      for (int i = 0; i < 42; i++) {
        float x = (i * 83 + visualTime * 19) % 500 - 10, y = (i * 47 + visualTime * 125) % 290 - 10;
        c.rect(x, y, 1, 4, "b8c9cc42");
        if (i < 10) {
          c.clip(
              -ox,
              (world.state.map.equals("street") ? 221 : 56) - oy,
              world.map().width,
              world.state.map.equals("street") ? 244 : 208);
          float t = (visualTime * 1.7f + i * .31f) % 1;
          if (t < .45f) {
            float px = (i * 67 + 43 - cameraX * .2f) % 480, py = 155 + i % 4 * 12;
            c.rect(px - 2 - t * 3, py, 4 + t * 6, 1, "bcc8bb45");
          }
          c.unclip();
        }
      }
    }
    c.unclip();
  }

  private void meter(String label, double value, int x, String color) {
    c.small(label + " " + (int) value, x, 8, CREAM);
    c.rect(x, 21, 70, 3, "344652");
    c.rect(x, 21, Math.max(1, (float) value * .7f), 3, value < 25 ? "df8d72" : color);
  }

  private void drawHud() {
    c.rect(0, 0, 480, 41, "15232ef8");
    c.rect(12, 40, 456, .5f, "607a7560");
    meter("ЗДОРОВЬЕ", world.state.health, 12, "b99d89");
    meter("СЫТОСТЬ", world.state.hunger, 100, "b8bd82");
    meter("ЭНЕРГИЯ", world.state.energy, 188, "8caaa5");
    c.small(world.state.money + " ₽", 285, 8, GOLD);
    c.small("День " + world.state.day() + " · " + world.state.clock(), 365, 8, CREAM);
    c.fitted(world.map().name + " · " + Atmosphere.period(world.state), 12, 28, 223, MUTED, true);
    c.small("J план · M карта · R люди · B деньги", 242, 28, MUTED);
    if (mode != Mode.WORLD) return;
    var target = Navigation.goal(world, life.data);
    c.rect(0, 231, 480, 39, "15232efb");
    c.rect(12, 231, 456, .5f, "607a7560");
    c.small("ТЕКУЩАЯ ЦЕЛЬ", 12, 233, GOLD);
    c.fitted(target.title(), 12, 244, 456, CREAM, false);
    c.fitted(target.hint(), 12, 259, 456, MUTED, true);
    var n = world.interactionNpc();
    var p = world.nearestProp();
    String prompt = n != null ? "Поговорить: " + n.name.split(" · ")[0] : p != null ? p.label : "";
    if (!prompt.isEmpty()) {
      String text = Keys.toString(settings.interact) + " — " + prompt;
      int w = Math.min(464, (int) Math.ceil(c.width(text)) + 20);
      c.panel((480 - w) / 2, 201, w, 23);
      c.fitted(text, (480 - w) / 2f + 10, 206, w - 20, CREAM, false);
    }
  }

  private void drawGuide() {
    var goal = Navigation.localGoal(world, Navigation.goal(world, life.data));
    guideTimer -= Gdx.graphics.getDeltaTime();
    if (guideTimer <= 0) {
      guidePath = Navigation.route(world, goal.x(), goal.y());
      guideTimer = .45f;
    }
    int phase = (int) (visualTime * 3) % 3;
    for (int i = phase; i < guidePath.size(); i += 5) {
      var p = guidePath.get(i);
      float x = p[0] - cameraX, y = p[1] - cameraY;
      if (x > 5 && x < 475 && y > 45 && y < 200) c.rect(x - 1, y - 1, 3, 2, "d8b875a0");
    }
    float x = goal.x() - cameraX, y = goal.y() - cameraY - 29;
    if (x > 12 && x < 468 && y > 48 && y < 198) {
      c.rect(x - 7, y - 3, 15, 17, "192f3dde");
      c.text("!", x - 2, y - 2, GOLD);
    } else {
      float px = Math.max(18, Math.min(462, x)), py = Math.max(55, Math.min(198, y));
      c.rect(px - 8, py - 6, 17, 17, "192f3dde");
      c.text(x < 18 ? "←" : x > 462 ? "→" : y < 55 ? "↑" : "↓", px - 5, py - 5, GOLD);
    }
  }

  private void shade() {
    c.rect(0, 0, 480, 270, "142332cb");
  }

  private void drawMenu() {
    shade();
    c.small("ТИХИЙ ГОРОД. БОЛЬШОЕ НАЧАЛО.", 32, 31, MINT);
    c.title("CTRL+LIFE", 30, 57, CREAM);
    c.text("РАЙОН, В КОТОРОМ ГОРИТ СВЕТ", 33, 93, GOLD);
    c.wrap(
        "Последняя маршрутка.\nКомната в общежитии.\nПервый заказ — и новые люди.",
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
    c.small("ПЕРВЫЕ ДЕНЬГИ. ПЕРВЫЕ ЧУВСТВА.", 32, 248, MUTED);
    c.small("0.4.0", 419, 248, MUTED);
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

  private void button(
      String label, int x, int y, int width, int height, boolean enabled, Runnable action) {
    int index = buttons.size();
    Button b = new Button(label, x, y, width, height, enabled, action);
    buttons.add(b);
    if (mouseMoved && contains(b, c.mouseX(), c.mouseY()) && enabled) selection = index;
    boolean selected = selection == index && enabled;
    boolean pressed =
        enabled && ((clicked && contains(b, clickX, clickY)) || (confirm && selected));
    c.rect(x, y, width, height, selected ? "c8b184" : "48616b");
    c.rect(
        x + .5f,
        y + .5f,
        width - 1,
        height - 1,
        pressed ? "112c38" : selected ? "354f58" : "203946");
    if (selected) c.rect(x + 3, y + 3, 2, height - 6, GOLD);
    c.fitted(
        label,
        x + 10,
        y + (height < 20 ? 2 : (height - 15) / 2),
        width - 19,
        enabled ? (selected ? GOLD : CREAM) : "809597",
        height < 20);
  }

  private boolean contains(Button b, float x, float y) {
    return x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height;
  }

  private void activateButtons() {
    Button target = null;
    if (clicked) {
      // Late-drawn window/dock buttons appear on top; give them click priority.
      for (int i = buttons.size() - 1; i >= 0; i--) {
        var b = buttons.get(i);
        if (b.enabled && contains(b, clickX, clickY)) {
          target = b;
          break;
        }
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
