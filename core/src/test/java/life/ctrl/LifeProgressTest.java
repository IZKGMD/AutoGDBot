package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LifeProgressTest {
  final LifeContent data = LifeContent.load();
  @TempDir Path folder;

  void finish(GameState s, String id) {
    var j = data.job(id);
    Coding.accept(s, j, 0);
    Coding.repair(s, j, Arrays.asList(j.solutions).indexOf(j.source));
    Coding.preview(s, j, j.seed[j.seed.length - 1]);
    Coding.deliver(s, j);
    assertEquals(5, s.jobStage, id);
  }

  @Test
  void firstChapterIsReachableThroughNormalActionsForEachGirl() throws Exception {
    for (var g : data.girls) {
      var s = new GameState();
      for (int day = 1; day <= 8; day++) {
        finish(s, "total");
        Life.buy(s, data.item("soup"));
        Life.eat(s, data.item("soup"));
        if (day == 1) {
          s.map = g.homeMap;
          Social.meet(s, g);
          Social.talk(s, g, 0);
          assertTrue(Social.invite(s, g, "park").contains("согласилась"));
          s.map = "park";
          assertEquals("", DateStory.start(s, g));
          for (int i = 0; i < 3; i++) {
            DateStory.choose(s, 0);
            DateStory.next(s);
          }
        } else finish(s, "discount");
        if (day >= 2 && day <= 4) finish(s, List.of("board", "remind", "support").get(day - 2));
        if (day == 5) {
          Career.buy(s, "ssd");
          Career.buy(s, "chair");
        }
        Life.buy(s, data.item("soup"));
        Life.eat(s, data.item("soup"));
        if (day < 8) Life.sleep(s);
      }
      assertEquals(8, s.day());
      assertEquals(7, LifeProgress.count(s), g.id + " money " + s.money);
      assertTrue(LifeProgress.ready(s));
      var store = new SaveStore(folder.resolve(g.id), Content.load());
      s.map = "room";
      s.x = 240;
      s.y = 190;
      store.save(s);
      assertTrue(s.chapterOneFinished);
      s.money = 0;
      s.rentDebt = 3000;
      assertEquals(7, LifeProgress.count(s));
      store.save(s);
      assertTrue(store.load().state().chapterOneFinished);
    }
  }

  @Test
  void newGameAndIncompleteCareerDoNotAwardFinale() {
    var s = new GameState();
    assertEquals(0, LifeProgress.count(s));
    assertEquals("Первый заработок", LifeProgress.next(s).title());
    assertTrue(s.relationships.isEmpty());
    LifeProgress.record(s);
    assertFalse(s.chapterOneFinished);
    s.codingXp = 8;
    assertFalse(LifeProgress.steps(s).get(6).done());
    s.upgrades.add("ssd");
    assertFalse(LifeProgress.steps(s).get(6).done());
    s.relation("asya").arcStep = 3;
    assertTrue(LifeProgress.steps(s).get(6).done());
    assertFalse(LifeProgress.ready(s));
  }

  @Test
  void firstWeekRequiresActualReserveAndNoDebt() {
    var s = new GameState();
    s.gameMinutes = 7 * 1440 + 480;
    s.money = 3440;
    s.rentDebt = 1;
    assertFalse(LifeProgress.steps(s).get(5).done());
    s.rentDebt = 0;
    s.money = 3439;
    assertFalse(LifeProgress.steps(s).get(5).done());
    s.money = 3440;
    assertTrue(LifeProgress.steps(s).get(5).done());
  }

  @Test
  void urgentNeedsTakePriorityButActiveDateCanContinue() {
    var s = new GameState();
    s.plannedGirl = "mira";
    s.plannedVenue = "park";
    s.plannedDay = 1;
    s.hunger = 0;
    var w = new World(Content.load(), s);
    assertEquals("diner", Navigation.goal(w, data).map());
    s.items.put("soup", 1);
    assertTrue(Navigation.goal(w, data).title().contains("карманы"));
    s.hunger = 80;
    s.energy = 0;
    assertEquals("room", Navigation.goal(w, data).map());
    s.energy = 80;
    s.map = "park";
    var r = s.relation("mira");
    r.met = true;
    r.affinity = r.trust = 30;
    assertEquals("", DateStory.start(s, data.girl("mira")));
    s.energy = 0;
    s.hunger = 0;
    assertEquals("park", Navigation.goal(w, data).map());
  }

  @Test
  void versionEightPreservesExistingProgress() throws Exception {
    Files.writeString(
        folder.resolve("save.json"),
        "{\"version\":8,\"state\":{\"money\":7654,\"workPace\":\"REVIEW\",\"workFatigue\":35}}");
    var s = new SaveStore(folder, Content.load()).load().state();
    assertEquals(7654, s.money);
    assertEquals(35, s.workFatigue);
    assertEquals("REVIEW", s.workPace);
    assertFalse(s.chapterOneFinished);
  }
}
