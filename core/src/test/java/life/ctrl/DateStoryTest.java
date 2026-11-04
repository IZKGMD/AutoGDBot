package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DateStoryTest {
  final LifeContent life = LifeContent.load();
  @TempDir Path directory;

  GameState invited(String id, String venue) {
    var s = new GameState();
    var g = life.girl(id);
    s.gameMinutes = 10 * 60;
    s.energy = 90;
    s.hunger = 80;
    s.map = venue;
    Social.meet(s, g);
    Social.talk(s, g, 0);
    assertTrue(Social.invite(s, g, venue).contains("согласилась"));
    return s;
  }

  @Test
  void allSeventyTwoAuthoredBranchesCompleteWithUniqueMemoryAndBoundedRewards() {
    for (var g : life.girls)
      for (int episode = 0; episode < 3; episode++)
        for (int branch = 0; branch < 8; branch++) {
          var s = invited(g.id, "park");
          var r = s.relation(g.id);
          r.dates = episode;
          int a = r.affinity, t = r.trust, money = s.money, minutes = s.minutes();
          assertEquals("", DateStory.start(s, g));
          String id = s.date.episode;
          for (int beat = 0; beat < 3; beat++) {
            assertFalse(DateStory.choose(s, (branch >> beat) & 1).isBlank());
            assertEquals(beat + (s.date.waiting ? 1 : 0), s.date.choices.size());
            DateStory.validate(s);
            DateStory.next(s);
          }
          assertFalse(s.date.active());
          assertEquals(episode + 1, r.dates);
          assertTrue(r.affinity - a >= 12 && r.affinity - a <= 15);
          assertTrue(r.trust - t >= 10 && r.trust - t <= 13);
          assertEquals(3, r.knownDetails.size());
          assertEquals(1, r.dateExperiences.get(id));
          assertEquals(90, s.minutes() - minutes);
          assertEquals(money, s.money);
          assertTrue(r.memory.contains(DateStory.DATA.episode(g.id, id).atmosphere));
          int after = r.affinity;
          DateStory.next(s);
          assertEquals(after, r.affinity);
        }
  }

  @Test
  void insufficientFundsAndEnergyDoNotStartOrSpendAnything() {
    var s = invited("asya", "cafe");
    s.money = 849;
    int time = s.minutes();
    assertTrue(DateStory.start(s, life.girl("asya")).contains("850"));
    assertEquals(849, s.money);
    assertEquals(time, s.minutes());
    assertFalse(s.date.active());
    s.money = 850;
    s.energy = 14;
    assertFalse(DateStory.start(s, life.girl("asya")).isBlank());
    assertEquals(850, s.money);
    s.energy = 80;
    assertEquals("", DateStory.start(s, life.girl("asya")));
    assertEquals(0, s.money);
    DateStory.start(s, life.girl("asya"));
    assertEquals(0, s.money);
    assertEquals(1, s.transactions.stream().filter(e -> e.reason.contains("Свидание")).count());
  }

  @Test
  void savesAndReloadsEveryDecisionAndResponseWithoutDuplicateChargeOrReward() throws Exception {
    var g = life.girl("asya");
    var s = invited(g.id, "cafe");
    s.x = 240;
    s.y = 190;
    var store = new SaveStore(directory, Content.load());
    DateStory.start(s, g);
    for (int i = 0; i < 3; i++) {
      DateStory.choose(s, i % 2);
      store.save(s);
      s = store.load().state();
      assertTrue(s.date.waiting);
      assertEquals(i, s.date.moment);
      assertEquals(9150, s.money);
      assertFalse(s.date.response.isBlank());
      assertEquals("", DateStory.start(s, g));
      DateStory.next(s);
      store.save(s);
      s = store.load().state();
    }
    assertEquals(1, s.relation(g.id).dates);
    assertFalse(s.date.active());
    assertEquals(9150, s.money);
    assertEquals(3, s.relation(g.id).knownDetails.size());
  }

  @Test
  void crossingMidnightDoesNotCreateMissedMeetingAndCanResumeAfterReload() throws Exception {
    var g = life.girl("mira");
    var s = invited(g.id, "park");
    s.gameMinutes = 23 * 60 + 45;
    s.x = 240;
    s.y = 190;
    DateStory.start(s, g);
    int trust = s.relation(g.id).trust;
    DateStory.choose(s, 0);
    assertEquals(2, s.day());
    assertEquals(g.id, s.plannedGirl);
    assertEquals(trust, s.relation(g.id).trust);
    assertTrue(Social.canStartDate(s, "park"));
    DateStory.next(s);
    DateStory.choose(s, 1);
    DateStory.next(s);
    DateStory.choose(s, 0);
    DateStory.next(s);
    assertEquals(2, s.relation(g.id).lastDateDay);
    assertTrue(s.plannedGirl.isEmpty());
  }

  @Test
  void leavingEarlyNeverAwardsDateAndCannotBypassCostOrSpendNegativeChoices() {
    var s = invited("asya", "cafe");
    var g = life.girl("asya");
    var r = s.relation(g.id);
    int a = r.affinity;
    DateStory.start(s, g);
    DateStory.choose(s, -1);
    assertTrue(s.date.choices.isEmpty());
    DateStory.choose(s, 0);
    int time = s.minutes();
    DateStory.choose(s, 1);
    assertEquals(time, s.minutes());
    assertTrue(Social.cancel(s, g).contains("началась"));
    assertTrue(Social.reschedule(s, g, "park", false).contains("началась"));
    DateStory.leave(s);
    assertEquals(0, r.dates);
    assertEquals(a, r.affinity);
    assertEquals(9150, s.money);
    assertTrue(Social.invite(s, g, "park").contains("завтра"));
  }

  @Test
  void remoteProgressAndForgedSaveStateAreRejected() {
    var s = invited("nika", "park");
    var g = life.girl("nika");
    s.map = "room";
    assertFalse(DateStory.start(s, g).isBlank());
    assertFalse(s.date.active());
    s.map = "park";
    DateStory.start(s, g);
    s.map = "room";
    DateStory.choose(s, 0);
    assertTrue(s.date.choices.isEmpty());
    s.map = "park";
    s.date.moment = 2;
    assertThrows(IllegalArgumentException.class, () -> DateStory.validate(s));
    s.date.moment = 0;
    s.date.affinity = 100;
    assertThrows(IllegalArgumentException.class, () -> DateStory.validate(s));
  }

  @Test
  void repeatedDatesHaveSmallerRewardsAndDoNotDuplicatePersonalDetails() {
    var s = invited("mira", "park");
    var g = life.girl("mira");
    var r = s.relation(g.id);
    for (int n = 0; n < 4; n++) {
      if (n > 0) {
        s.gameMinutes += 1440;
        s.energy = 90;
        Social.invite(s, g, "park");
      }
      DateStory.start(s, g);
      int a = r.affinity;
      for (int i = 0; i < 3; i++) {
        DateStory.choose(s, 0);
        DateStory.next(s);
      }
      if (n == 3) assertEquals(9, r.affinity - a);
    }
    assertEquals(9, r.knownDetails.size());
    assertEquals(3, r.dateExperiences.size());
  }

  @Test
  void couplePlansRequirePresenceAndFollowThroughAndCannotFarmPromises() {
    var g = life.girl("mira");
    var s = invited(g.id, "park");
    s.partnerId = g.id;
    var r = s.relation(g.id);
    r.dates = 4;
    r.arcStep = 3;
    r.decisions.addAll(java.util.List.of("a", "b", "c"));
    assertEquals("", RelationshipJourney.planReason(s, g));
    int trust = r.trust;
    RelationshipJourney.plan(s, g, 1);
    assertEquals(trust, r.trust);
    assertEquals("plants", r.promise);
    RelationshipJourney.plan(s, g, 2);
    assertEquals("plants", r.promise);
    RelationshipJourney.activity(s, g, 1);
    assertTrue(r.promise.isEmpty());
    assertEquals(trust + 11, r.trust);
    assertFalse(RelationshipJourney.planReason(s, g).isEmpty());
    Social.cancel(s, g);
    s.gameMinutes += 4 * 1440;
    s.map = "street";
    assertTrue(RelationshipJourney.planReason(s, g).contains("встрече"));
    s.map = g.homeMap;
    RelationshipJourney.plan(s, g, 2);
    int before = r.trust;
    Life.advance(s, 1440);
    Life.advance(s, 1440);
    Life.advance(s, 1440);
    Life.advance(s, 1440);
    assertEquals(before - 5, r.trust);
    assertEquals("walk", r.brokenPromise);
  }

  @Test
  void versionSixSavePreservesExistingRelationsAndGetsEmptyNewSystems() throws Exception {
    java.nio.file.Files.writeString(
        directory.resolve("save.json"),
        "{\"version\":6,\"state\":{\"money\":4321,\"relationships\":{\"mira\":{\"met\":true,\"trust\":42,\"dates\":3}}}}");
    var store = new SaveStore(directory, Content.load());
    var s = store.load().state();
    assertEquals(4321, s.money);
    assertEquals(42, s.relation("mira").trust);
    assertEquals(3, s.relation("mira").dates);
    assertFalse(s.date.active());
    assertTrue(s.relation("mira").knownDetails.isEmpty());
    store.save(s);
    assertEquals(4321, store.load().state().money);
  }
}
