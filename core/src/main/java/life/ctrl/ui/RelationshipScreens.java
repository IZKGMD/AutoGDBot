package life.ctrl.ui;

import java.util.*;
import life.ctrl.CtrlLife;
import life.ctrl.CtrlLife.Mode;
import life.ctrl.content.LifeContent;
import life.ctrl.model.*;
import life.ctrl.render.PixelCanvas;

/** Illustrated conversations, saved dates and readable journals share one measured layout. */
public final class RelationshipScreens {
  private final CtrlLife game;
  private final LifeScreens life;
  private final PixelCanvas c;
  private int memoryPage, textPage, detailPage, resultPage, giftPage;
  private List<String> resultPages = List.of("");
  private String resultGirl = "mira";
  private static final int X = 176, W = 288;
  private static final String[] SHADE = new String[40];

  static {
    for (int i = 0; i < SHADE.length; i++)
      SHADE[i] =
          String.format(
              java.util.Locale.ROOT, "101822%02x", Math.round(220f * i / (SHADE.length - 1)));
  }

  private static final String INK = "e8e1d5", SOFT = "9caab5", GOLD = "dfb77e";

  public RelationshipScreens(CtrlLife game, LifeScreens life, PixelCanvas c) {
    this.game = game;
    this.life = life;
    this.c = c;
  }

  private GameState s() {
    return game.world().state;
  }

  private void button(String title, int x, int y, int width, boolean enabled, Runnable action) {
    game.narrativeButton(title, x, y, width, 23, enabled, action);
  }

  private void wide(String title, int y, boolean enabled, Runnable action) {
    button(title, X, y, W, enabled, action);
  }

  private void choice(String title, int y, boolean enabled, Runnable action) {
    game.narrativeButton(title, X, y, W, 34, enabled, action);
  }

  private void page(String id) {
    life.socialPage = id;
    textPage = 0;
    game.resetSelection();
  }

  private void back() {
    wide("← К общению", 236, true, () -> page("home"));
  }

  private void prose(String text, int y, int height) {
    c.paragraph(text, X, y, W, height, INK, true);
  }

  private void result(LifeContent.Girl g, String title, String text) {
    resultGirl = g.id;
    resultPage = 0;
    resultPages = c.pages(text, W, 139, false);
    life.scene(title, text, Mode.SOCIAL);
    life.relationshipScene = true;
  }

  private void header(LifeContent.Girl g, String eyebrow, String title) {
    var r = s().relation(g.id);
    c.rect(0, 0, 480, 270, "101822");
    c.roundRect(170, 10, 300, 250, 7, "16222e");
    c.clip(12, 12, 148, 246);
    c.portrait(g.id, 12, 12, 148, 246);
    c.unclip();
    for (int i = 0; i < SHADE.length; i++) c.rect(12, 178 + i * 2, 148, 2, SHADE[i]);
    c.text(g.name, 22, 222, INK);
    c.small(
        g.age
            + " лет · "
            + (s().partnerId.equals(g.id) ? "вы вместе" : r.met ? "знакомы" : "новое знакомство"),
        22,
        240,
        "dfc9a8");
    c.small(eyebrow, X, 19, GOLD);
    c.fitted(title, X, 34, W, INK, false);
    c.small("СИМПАТИЯ " + r.affinity, X, 57, SOFT);
    c.small("ДОВЕРИЕ " + r.trust, X + 150, 57, SOFT);
    c.roundRect(X, 70, 132, 2, 1, "2e3b46");
    c.roundRect(X, 70, Math.max(1, r.affinity * 1.32f), 2, 1, "b68e79");
    c.roundRect(X + 150, 70, 138, 2, 1, "2e3b46");
    c.roundRect(X + 150, 70, Math.max(1, r.trust * 1.38f), 2, 1, "87aaa3");
  }

  public void drawResult() {
    var g = life.data.girl(resultGirl);
    header(g, "ВАШ РАЗГОВОР", life.sceneTitle);
    c.paragraph(resultPages.get(resultPage), X, 88, W, 139, INK, false);
    wide(
        resultPage + 1 < resultPages.size() ? "Читать дальше →" : "Продолжить",
        236,
        true,
        () -> {
          if (resultPage + 1 < resultPages.size()) {
            resultPage++;
            game.resetSelection();
          } else {
            life.relationshipScene = false;
            game.switchMode(life.sceneReturn);
          }
        });
  }

  public void contacts() {
    c.rect(0, 0, 480, 270, "101822");
    c.small("ЛЮДИ БЕРЁЗОВСКА", 20, 16, GOLD);
    c.text("Есть кому написать", 20, 32, INK);
    for (int i = 0; i < life.data.girls.length; i++) {
      var g = life.data.girls[i];
      var r = s().relation(g.id);
      int y = 59 + i * 55;
      c.roundRect(16, y, 448, 49, 5, "1a2835");
      c.clip(20, y + 3, 45, 43);
      c.portrait(g.id, 20, y + 3, 45, 43);
      c.unclip();
      c.small(g.name + " · " + g.age + " лет", 76, y + 7, GOLD);
      c.fitted(
          s().partnerId.equals(g.id)
              ? "Вы вместе · свиданий " + r.dates
              : r.met ? "Знакомы · доверие " + r.trust : "Ещё не знакомы",
          76,
          y + 24,
          243,
          SOFT,
          true);
      button("Написать →", 335, y + 13, 117, true, () -> life.openSocial(g.id, true));
    }
    button("Вернуться в город", 16, 236, 448, true, () -> game.switchMode(Mode.WORLD));
  }

  public boolean draw(LifeContent.Girl g) {
    var r = s().relation(g.id);
    String p = life.socialPage;
    var arc = RelationshipJourney.DATA.arc(g.id);
    String title =
        switch (p) {
          case "journey" -> "Ваша история";
          case "chapter" -> "Личный разговор";
          case "activities" -> "Общие занятия";
          case "memories" -> "То, что осталось с вами";
          case "meeting" -> "Планы на встречу";
          case "talk" -> "О чём поговорим?";
          case "gift" -> "Маленький знак внимания";
          case "invite" -> "Выбрать место";
          case "date" -> "Время для вас двоих";
          case "details" -> "Что ты о ней знаешь";
          case "plans" -> "Беречь общее время";
          case "commit", "breakup" -> "Об отношениях";
          case "night" -> "Остаться вдвоём?";
          default -> life.remote ? "На связи" : "Хорошо, что ты здесь";
        };
    header(g, "ЛЮДИ БЕРЁЗОВСКА · " + s().clock(), title);
    switch (p) {
      case "home" -> {
        String body = life.socialMessage.isEmpty() ? g.about : life.socialMessage;
        prose(body, 84, 43);
        c.fitted("Ваша история: главы, занятия и памятные детали", X, 128, W, SOFT, true);
        if (life.remote) {
          wide(
              "Найти в городе →",
              143,
              true,
              () -> {
                s().trackedGirl = g.id;
                s().goalChoice = 2;
                game.switchMode(Mode.WORLD);
                game.refreshState();
              });
          wide(
              s().date.active() && s().date.girl.equals(g.id)
                  ? "Вернуться к свиданию"
                  : "Договориться о встрече",
              171,
              r.met,
              () -> {
                if (s().date.active() && s().date.girl.equals(g.id)) {
                  s().trackedGirl = g.id;
                  game.switchMode(Mode.WORLD);
                } else page("invite");
              });
        } else {
          button("Поговорить", X, 143, 138, true, () -> page("talk"));
          button(
              "Подарок",
              X + 150,
              143,
              138,
              true,
              () -> {
                giftPage = 0;
                page("gift");
              });
          button("Свидание", X, 171, 138, true, () -> page("invite"));
          button("Об отношениях", X + 150, 171, 138, true, () -> page("commit"));
        }
        button("Ваша история", X, 199, 138, r.met, () -> page("journey"));
        button(
            "Встреча / перенос",
            X + 150,
            199,
            138,
            s().plannedGirl.equals(g.id),
            () -> page("meeting"));
        wide(
            "До встречи",
            236,
            true,
            () -> game.switchMode(life.remote ? Mode.CONTACTS : Mode.WORLD));
      }
      case "talk" -> {
        prose(g.values, 84, 35);
        for (int i = 0; i < g.topics.length; i++) {
          int option = i;
          choice(
              g.topics[i].label,
              121 + i * 37,
              true,
              () -> {
                page("home");
                result(g, g.name, Social.talk(s(), g, option));
              });
        }
        back();
      }
      case "gift" -> {
        prose(g.likes, 84, 39);
        var gifts =
            java.util.Arrays.stream(life.data.items)
                .filter(item -> item.kind.equals("gift") && s().count(item.id) > 0)
                .toList();
        giftPage = Math.min(giftPage, Math.max(0, (gifts.size() - 1) / 3));
        for (int i = 0; i < 3 && giftPage * 3 + i < gifts.size(); i++) {
          var item = gifts.get(giftPage * 3 + i);
          int y = 129 + i * 28;
          wide(
              item.name + " ×" + s().count(item.id),
              y,
              true,
              () -> {
                page("home");
                result(g, g.name, Social.gift(s(), g, item));
              });
        }
        if (gifts.isEmpty())
          prose(
              "Подарков пока нет. Они продаются в кофейне. Внимательный разговор и прогулка"
                  + " доступны без покупок.",
              136,
              70);
        if (gifts.size() > 3)
          wide(
              "Другие подарки →",
              210,
              true,
              () -> giftPage = (giftPage + 1) % ((gifts.size() + 2) / 3));
        back();
      }
      case "invite" -> {
        prose(
            "За двоих: сквер — бесплатно; кофейня или «Ложка» — 850 ₽. Встреча длится 90 минут."
                + " Деньги списываются только при начале.",
            84,
            50);
        for (int i = 0; i < g.venues.length; i++) {
          String venue = g.venues[i];
          choice(
              Social.venueName(venue) + (venue.equals("park") ? " · 0 ₽" : " · 850 ₽"),
              145 + i * 39,
              true,
              () -> {
                String text = Social.invite(s(), g, venue);
                page("home");
                result(g, "Договорились?", text);
              });
        }
        back();
      }
      case "journey" -> {
        prose(
            r.arcStep >= 3
                ? "Три главы позади. Ваши отношения продолжаются в новых свиданиях и общих планах."
                : "Глава " + (r.arcStep + 1) + " / 3 · " + arc.chapters[r.arcStep].title,
            84,
            31);
        c.paragraph(
            RelationshipJourney.nextReason(s(), g).isEmpty()
                ? "Можно поговорить. Твой ответ останется в истории."
                : RelationshipJourney.nextReason(s(), g),
            X,
            119,
            W,
            38,
            SOFT,
            true);
        button(
            "Личный разговор",
            X,
            163,
            138,
            RelationshipJourney.nextReason(s(), g).isEmpty(),
            () -> page("chapter"));
        button("Общие занятия", X + 150, 163, 138, true, () -> page("activities"));
        button(
            "Воспоминания",
            X,
            191,
            138,
            true,
            () -> {
              memoryPage = 0;
              page("memories");
            });
        button(
            "Детали и планы",
            X + 150,
            191,
            138,
            true,
            () -> {
              detailPage = 0;
              page("details");
            });
        back();
      }
      case "chapter" -> {
        if (r.arcStep >= 3) {
          page("journey");
          return true;
        }
        var ch = arc.chapters[r.arcStep];
        c.fitted(ch.title, X, 83, W, GOLD, true);
        prose(RelationshipJourney.chapterText(s(), g.id), 101, 64);
        for (int i = 0; i < 2; i++) {
          int option = i;
          choice(
              ch.choices[i].label,
              163 + i * 35,
              true,
              () -> {
                String text = RelationshipJourney.choose(s(), g, option);
                page("journey");
                result(g, ch.title, text);
              });
        }
        wide("Поговорить позже", 236, true, () -> page("journey"));
      }
      case "activities" -> {
        String reason = RelationshipJourney.activityReason(s(), g);
        prose(
            reason.isEmpty()
                ? "Новое занятие сближает сильнее повторений. Одно в день. Время и расходы — перед"
                      + " выбором."
                : reason,
            84,
            43);
        for (int i = 0; i < 3; i++) {
          var a = arc.activities[i];
          int option = i;
          choice(
              a.label
                  + " · "
                  + a.minutes
                  + " мин · "
                  + a.cost
                  + " ₽"
                  + (r.promise.equals(a.id) ? " · обещано" : ""),
              126 + i * 36,
              reason.isEmpty() && s().money >= a.cost,
              () -> result(g, a.label, RelationshipJourney.activity(s(), g, option)));
        }
        back();
      }
      case "memories" -> {
        int count = r.memories.size();
        memoryPage = Math.max(0, Math.min(memoryPage, Math.max(0, count - 1)));
        if (count == 0) prose(r.memory, 90, 110);
        else {
          var m = r.memories.get(count - 1 - memoryPage);
          c.small("ДЕНЬ " + m.day + " · " + (memoryPage + 1) + " / " + count, X, 83, GOLD);
          c.fitted(m.title, X, 99, W, INK, true);
          var pages = c.pages(m.text, W, 87, true);
          textPage = Math.min(textPage, pages.size() - 1);
          prose(pages.get(textPage), 117, 87);
          if (pages.size() > 1)
            wide(
                "Страница " + (textPage + 1) + " / " + pages.size() + " →",
                205,
                true,
                () -> {
                  textPage = (textPage + 1) % pages.size();
                  game.resetSelection();
                });
        }
        button(
            "← Старше",
            X,
            236,
            90,
            memoryPage + 1 < count,
            () -> {
              memoryPage++;
              textPage = 0;
            });
        button(
            "Новее →",
            X + 98,
            236,
            90,
            memoryPage > 0,
            () -> {
              memoryPage--;
              textPage = 0;
            });
        button("К истории", X + 196, 236, 92, true, () -> page("journey"));
      }
      case "details" -> {
        if (r.knownDetails.isEmpty())
          prose(
              "Узнавайте друг друга на свиданиях. Здесь сохранятся небольшие привычки и вещи,"
                  + " которые ей важны.",
              85,
              80);
        else {
          detailPage = Math.min(detailPage, (r.knownDetails.size() - 1) / 3);
          for (int i = 0; i < 3; i++) {
            int n = detailPage * 3 + i;
            if (n >= r.knownDetails.size()) break;
            c.roundRect(X, 83 + i * 35, W, 31, 4, "1d2c37");
            c.paragraph(r.knownDetails.get(n), X + 9, 89 + i * 35, W - 18, 25, INK, true);
          }
          if (r.knownDetails.size() > 3)
            button(
                "Ещё детали →",
                X,
                195,
                138,
                true,
                () -> detailPage = (detailPage + 1) % ((r.knownDetails.size() + 2) / 3));
        }
        button("Общие планы", X + 150, 195, 138, s().partnerId.equals(g.id), () -> page("plans"));
        wide(
            !r.brokenPromise.isEmpty() ? "Обсудить обещание" : "← К вашей истории",
            236,
            true,
            () -> {
              if (!r.brokenPromise.isEmpty())
                result(g, "Сказать честно", RelationshipJourney.repair(s(), g));
              else page("journey");
            });
      }
      case "plans" -> {
        String reason = RelationshipJourney.planReason(s(), g);
        prose(
            reason.isEmpty()
                ? "Выберите занятие на ближайшие три дня. Это обещание: выполните его через «Общие"
                      + " занятия» при встрече."
                : reason,
            84,
            42);
        for (int i = 0; i < 3; i++) {
          int option = i;
          var a = arc.activities[i];
          choice(
              a.label + " · " + a.minutes + " мин · " + a.cost + " ₽",
              126 + i * 36,
              reason.isEmpty(),
              () -> result(g, "Ваш общий план", RelationshipJourney.plan(s(), g, option)));
        }
        back();
      }
      case "meeting" -> {
        boolean planned = s().plannedGirl.equals(g.id),
            active = s().date.active() && s().date.girl.equals(g.id);
        prose(
            active
                ? "Свидание уже началось. Решения сохранены. Вернись к отмеченному столику, чтобы"
                      + " продолжить."
                : planned
                    ? "День "
                        + s().plannedDay
                        + " · "
                        + Social.venueName(s().plannedVenue)
                        + ". Перенос и предупреждение об отмене бесплатны."
                    : "Сейчас встреча не назначена.",
            84,
            51);
        wide(
            "Перенести на завтра",
            145,
            planned && !active,
            () -> result(g, "Встреча", Social.reschedule(s(), g, s().plannedVenue, true)));
        wide(
            "Сегодня в сквере · бесплатно",
            174,
            planned && !active,
            () -> result(g, "Встреча", Social.reschedule(s(), g, "park", false)));
        wide(
            "Предупредить об отмене",
            203,
            planned && !active,
            () -> result(g, "Встреча", Social.cancel(s(), g)));
        back();
      }
      case "date" -> drawDate(g);
      case "leave-date" -> {
        prose(
            "Закончить встречу сейчас? Оплаченный заказ и прошедшее время не возвращаются. За"
                + " незавершённое свидание нет награды. Можно вернуться к разговору.",
            90,
            85);
        wide("Продолжить встречу", 180, true, () -> page("date"));
        wide(
            "Попрощаться и уйти",
            211,
            true,
            () -> {
              String text = DateStory.leave(s());
              page("home");
              result(g, "До другого вечера", text);
            });
      }
      case "commit" -> {
        prose(
            s().partnerId.equals(g.id)
                ? "Вы вместе. Личные истории, новые свидания и общие планы продолжаются после"
                      + " начала отношений."
                : "Для отношений нужно хотя бы три свидания, 40 доверия и 40 симпатии. Подарки не"
                      + " заменяют время вместе.",
            84,
            55);
        wide(
            "Предложить быть парой",
            148,
            !s().partnerId.equals(g.id),
            () -> result(g, "Важный разговор", Social.commit(s(), g)));
        wide("Общие планы", 176, s().partnerId.equals(g.id), () -> page("plans"));
        wide("Обсудить расставание", 204, s().partnerId.equals(g.id), () -> page("breakup"));
        back();
      }
      case "breakup" -> {
        prose(
            "Этот разговор завершит отношения. Симпатия и доверие уменьшатся. Это решение можно"
                + " обдумать; новая встреча не отменит его сама.",
            90,
            80);
        wide(
            "Да, сказать честно",
            182,
            true,
            () -> {
              page("home");
              result(g, "Честный разговор", Social.breakup(s(), g));
            });
        wide("Пока не говорить", 215, true, () -> page("home"));
      }
      case "night" -> {
        prose(g.nightLine, 85, 72);
        prose(
            Social.canShareNight(s(), g)
                ? "Вы оба этого хотите. Передумать можно в любой момент."
                : "Сейчас можно просто побыть рядом. Нужны устойчивые отношения, доверие и вечернее"
                      + " время.",
            159,
            43);
        wide(
            "Да, мы оба этого хотим",
            209,
            Social.canShareNight(s(), g),
            () -> game.switchMode(Mode.NIGHT));
        wide(
            "Давай не сегодня",
            236,
            true,
            () -> {
              page("home");
              result(g, g.name, Social.shareNight(s(), g, false));
            });
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  private void drawDate(LifeContent.Girl g) {
    var d = s().date;
    if (!d.active()) {
      prose(
          "Три момента, 90 минут. Выборы и ответы сохраняются. "
              + (s().plannedVenue.equals("park")
                  ? "Прогулка бесплатна."
                  : "За двоих — 850 ₽, включая еду. Оплата при начале."),
          85,
          55);
      wide(
          "Начать встречу",
          153,
          true,
          () -> {
            String reason = DateStory.start(s(), g);
            if (!reason.isEmpty()) result(g, "Перед встречей", reason);
            else game.refreshState();
            game.resetSelection();
          });
      wide("Изменить планы", 185, true, () -> page("meeting"));
      wide("Вернуться к городу", 236, true, () -> game.switchMode(Mode.WORLD));
      return;
    }
    if (!d.girl.equals(g.id)) {
      prose("Сначала закончи начатую встречу.", 90, 50);
      back();
      return;
    }
    var e = DateStory.episode(s());
    c.fitted(e.title, X, 83, W, GOLD, true);
    for (int i = 0; i < 3; i++)
      c.roundRect(X + i * 98, 99, 92, 2, 1, i <= d.moment ? "c5a16c" : "35414c");
    c.small("МОМЕНТ " + (d.moment + 1) + " / 3 · " + Social.venueName(d.venue), X, 108, SOFT);
    prose(d.waiting ? d.response : e.beats[d.moment].text, 128, 58);
    if (d.waiting) {
      wide(
          d.moment == 2 ? "Запомнить этот вечер" : "Продолжить разговор →",
          204,
          true,
          () -> {
            String text = DateStory.next(s());
            game.refreshState();
            game.resetSelection();
            if (!s().date.active()) {
              page("home");
              result(g, e.title, text);
            }
          });
    } else {
      for (int i = 0; i < 2; i++) {
        int option = i;
        choice(
            e.beats[d.moment].choices[i].label,
            190 + i * 35,
            true,
            () -> {
              DateStory.choose(s(), option);
              game.refreshState();
              game.resetSelection();
            });
      }
    }
    if (d.waiting) wide("Закончить встречу раньше…", 236, true, () -> page("leave-date"));
    else c.small("Решения сохраняются · Esc — пауза", X, 177, SOFT);
  }
}
