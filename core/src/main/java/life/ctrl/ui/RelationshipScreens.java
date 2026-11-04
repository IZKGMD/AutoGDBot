package life.ctrl.ui;

import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.content.LifeContent;
import life.ctrl.model.*;
import life.ctrl.render.PixelCanvas;

/** Personal story hub and memory journal. Every available action is explained before selection. */
public final class RelationshipScreens {
  private final CtrlLife game;
  private final LifeScreens life;
  private final PixelCanvas c;
  private int memoryPage;

  public RelationshipScreens(CtrlLife game, LifeScreens life, PixelCanvas c) {
    this.game = game;
    this.life = life;
    this.c = c;
  }

  private GameState s() {
    return game.world().state;
  }

  private void button(String title, int x, int y, int width, boolean enabled, Runnable action) {
    game.uiButton(title, x, y, width, 22, enabled, action);
  }

  private void page(String id) {
    life.socialPage = id;
    game.resetSelection();
  }

  private void result(String title, String text) {
    life.scene(title, text, Mode.SOCIAL);
  }

  private void back() {
    button("Назад к общению", 28, 231, 424, true, () -> page("home"));
  }

  public boolean draw(LifeContent.Girl g) {
    String p = life.socialPage;
    if (!p.equals("journey")
        && !p.equals("chapter")
        && !p.equals("activities")
        && !p.equals("memories")
        && !p.equals("meeting")) return false;
    var r = s().relation(g.id);
    var arc = RelationshipJourney.DATA.arc(g.id);
    c.rect(0, 0, 480, 270, "15232eff");
    c.panel(12, 10, 456, 250);
    c.art("portrait_" + g.portrait, 28, 25);
    c.text(g.name + " · ваша история", 72, 24, "efe4c6");
    c.small(
        "Главы " + r.arcStep + " / 3 · общих занятий " + r.activities.size() + " / 3",
        72,
        44,
        "edbb71");
    c.fitted(
        "Симпатия " + r.affinity + " · доверие " + r.trust + " · " + s().money + " ₽",
        72,
        61,
        378,
        "a7b5b6",
        true);
    switch (p) {
      case "journey" -> {
        String reason = RelationshipJourney.nextReason(s(), g);
        c.wrap(
            r.arcStep >= 3
                ? "Вы завершили эту историю. Новые общие дни останутся в воспоминаниях."
                : "Следующая глава: " + arc.chapters[r.arcStep].title,
            28,
            76,
            424,
            34,
            "efe4c6");
        c.wrap(
            reason.isEmpty()
                ? "Личный разговор доступен. Выбор сохранится в вашей истории."
                : reason,
            28,
            111,
            424,
            47,
            "a7b5b6");
        button("Личный разговор", 28, 163, 206, reason.isEmpty(), () -> page("chapter"));
        button("Провести время вместе", 246, 163, 206, true, () -> page("activities"));
        button(
            "Воспоминания",
            28,
            191,
            206,
            true,
            () -> {
              memoryPage = 0;
              page("memories");
            });
        button(
            "Обсудить обещание",
            246,
            191,
            206,
            !r.brokenPromise.isEmpty(),
            () -> result(g.name, RelationshipJourney.repair(s(), g)));
        back();
      }
      case "chapter" -> {
        if (r.arcStep >= 3) {
          page("journey");
          return true;
        }
        var chapter = arc.chapters[r.arcStep];
        c.fitted(chapter.title, 28, 72, 424, "edbb71", false);
        c.wrap(RelationshipJourney.chapterText(s(), g.id), 28, 96, 424, 85, "efe4c6");
        for (int i = 0; i < 2; i++) {
          int choice = i;
          button(
              chapter.choices[i].label,
              28,
              183 + i * 26,
              424,
              true,
              () -> {
                String text = RelationshipJourney.choose(s(), g, choice);
                page("journey");
                result(g.name, text);
              });
        }
        button("Поговорить позже", 28, 235, 424, true, () -> page("journey"));
      }
      case "activities" -> {
        String reason = RelationshipJourney.activityReason(s(), g);
        c.wrap(
            reason.isEmpty()
                ? "Одно занятие в день. Новый опыт сближает сильнее повторений. Время и расходы"
                    + " указаны заранее."
                : reason,
            28,
            76,
            424,
            48,
            "a7b5b6");
        for (int i = 0; i < 3; i++) {
          var a = arc.activities[i];
          int option = i;
          int y = 128 + i * 31;
          button(
              a.label
                  + " · "
                  + a.minutes
                  + " мин · "
                  + a.cost
                  + " ₽"
                  + (r.promise.equals(a.id) ? " · обещано" : ""),
              28,
              y,
              424,
              reason.isEmpty() && s().money >= a.cost,
              () -> result(g.name, RelationshipJourney.activity(s(), g, option)));
        }
        back();
      }
      case "memories" -> {
        int count = r.memories.size();
        memoryPage = Math.max(0, Math.min(memoryPage, Math.max(0, count - 1)));
        if (count == 0) c.wrap(r.memory, 28, 90, 424, 120, "efe4c6");
        else {
          var m = r.memories.get(count - 1 - memoryPage);
          c.small("ДЕНЬ " + m.day + " · " + (memoryPage + 1) + " / " + count, 28, 76, "edbb71");
          c.fitted(m.title, 28, 95, 424, "efe4c6", false);
          c.wrap(m.text, 28, 119, 424, 82, "a7b5b6");
        }
        button("← Старше", 28, 205, 206, memoryPage + 1 < count, () -> memoryPage++);
        button("Новее →", 246, 205, 206, memoryPage > 0, () -> memoryPage--);
        button("К вашей истории", 28, 235, 424, true, () -> page("journey"));
      }
      case "meeting" -> {
        boolean planned = s().plannedGirl.equals(g.id);
        c.wrap(
            planned
                ? "Назначено: "
                    + Social.venueName(s().plannedVenue)
                    + ", день "
                    + s().plannedDay
                    + ". Можно предупредить об отмене или договориться на завтра."
                : "Сейчас встреча не назначена.",
            28,
            80,
            424,
            55,
            "efe4c6");
        button(
            "Перенести на завтра",
            28,
            147,
            424,
            planned,
            () -> result("Встреча", Social.reschedule(s(), g, s().plannedVenue, true)));
        button(
            "Сегодня в сквере · бесплатно",
            28,
            175,
            424,
            planned && java.util.Arrays.asList(g.venues).contains("park"),
            () -> result("Встреча", Social.reschedule(s(), g, "park", false)));
        button(
            "Предупредить об отмене",
            28,
            203,
            424,
            planned,
            () -> result("Встреча", Social.cancel(s(), g)));
        back();
      }
    }
    return true;
  }
}
