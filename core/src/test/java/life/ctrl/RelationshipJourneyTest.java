package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationshipJourneyTest {
  final LifeContent data = LifeContent.load();
  @TempDir Path folder;

  GameState ready(String id) {
    var s = new GameState();
    s.map = data.girl(id).homeMap;
    s.x = 240;
    s.y = 190;
    s.gameMinutes = 480;
    s.energy = 90;
    Social.meet(s, data.girl(id));
    var r = s.relation(id);
    r.dates = 1;
    r.trust = 20;
    r.affinity = 20;
    return s;
  }

  void nextDay(GameState s) {
    Life.sleep(s);
    s.energy = 90;
  }

  @Test
  void eachGirlHasDistinctChoicesAndActivities() {
    Set<String> titles = new HashSet<>(), activityIds = new HashSet<>();
    for (var g : data.girls) {
      var a = RelationshipJourney.DATA.arc(g.id);
      for (var c : a.chapters) assertTrue(titles.add(c.title));
      for (var t : a.activities) assertTrue(activityIds.add(t.id));
    }
    assertEquals(9, titles.size());
    assertEquals(9, activityIds.size());
  }

  @Test
  void everyBranchOfEveryArcCanBeCompletedWithoutBuyingGifts() {
    for (var g : data.girls)
      for (int mask = 0; mask < 8; mask++) {
        var s = ready(g.id);
        var r = s.relation(g.id);
        var arc = RelationshipJourney.DATA.arc(g.id);
        for (int chapter = 0; chapter < 3; chapter++) {
          if (chapter > 0) {
            nextDay(s);
            r.trust = Math.max(r.trust, chapter == 2 ? 50 : 30);
          }
          if (chapter == 2) {
            s.partnerId = g.id;
            r.dates = 4;
            r.affinity = 60;
          }
          assertEquals("", RelationshipJourney.nextReason(s, g), g.id + " chapter " + chapter);
          int option = (mask >> chapter) & 1;
          RelationshipJourney.choose(s, g, option);
          assertEquals(chapter + 1, r.arcStep);
          if (!r.promise.isEmpty()) {
            int index = 0;
            while (!arc.activities[index].id.equals(r.promise)) index++;
            if (r.lastActivityDay == s.day()) nextDay(s);
            RelationshipJourney.activity(s, g, index);
            assertTrue(r.promise.isEmpty());
          }
          if (chapter < 2) {
            if (r.lastActivityDay == s.day()) nextDay(s);
            RelationshipJourney.activity(s, g, chapter);
          }
        }
        assertEquals(3, r.decisions.size());
        assertTrue(r.memories.size() >= 4);
      }
  }

  @Test
  void promiseIsFulfilledOnceAndReloadCannotDuplicateReward() throws Exception {
    var s = ready("mira");
    var g = data.girl("mira");
    RelationshipJourney.choose(s, g, 0);
    assertEquals("binding", s.relation("mira").promise);
    int before = s.money;
    RelationshipJourney.activity(s, g, 0);
    assertEquals(before - 120, s.money);
    int trust = s.relation("mira").trust;
    var store = new SaveStore(folder, Content.load());
    store.save(s);
    s = store.load().state();
    RelationshipJourney.activity(s, g, 0);
    assertEquals(trust, s.relation("mira").trust);
    assertTrue(s.relation("mira").promise.isEmpty());
  }

  @Test
  void missingPromiseAppliesPenaltyOnceAndHonestRepairDoesNotFarmTrust() {
    var s = ready("mira");
    var g = data.girl("mira");
    RelationshipJourney.choose(s, g, 0);
    int trust = s.relation("mira").trust;
    for (int i = 0; i < 4; i++) Life.advance(s, 1440);
    var r = s.relation("mira");
    assertEquals(trust - 5, r.trust);
    assertEquals("binding", r.brokenPromise);
    Social.expire(s);
    assertEquals(trust - 5, r.trust);
    RelationshipJourney.repair(s, g);
    assertEquals(trust - 3, r.trust);
    RelationshipJourney.repair(s, g);
    assertEquals(trust - 3, r.trust);
  }

  @Test
  void lateActivityDoesNotRescueAnAlreadyExpiredPromise() {
    var s = ready("mira");
    var g = data.girl("mira");
    RelationshipJourney.choose(s, g, 0);
    var r = s.relation("mira");
    s.gameMinutes = (r.promiseDue - 1) * 1440 + 23 * 60 + 30;
    s.energy = 90;
    RelationshipJourney.activity(s, g, 0);
    assertEquals("binding", r.brokenPromise);
    assertTrue(r.promise.isEmpty());
  }

  @Test
  void costsAndTimeAreAtomicWhenUnaffordableAndActivitiesNeedPresence() {
    var s = ready("asya");
    var g = data.girl("asya");
    s.money = 0;
    double time = s.gameMinutes;
    RelationshipJourney.activity(s, g, 1);
    assertEquals(time, s.gameMinutes);
    assertTrue(s.relation("asya").activities.isEmpty());
    s.map = "room";
    RelationshipJourney.activity(s, g, 0);
    assertEquals(time, s.gameMinutes);
    s.map = g.homeMap;
    RelationshipJourney.activity(s, g, 0);
    assertEquals(time + 70, s.gameMinutes);
    assertEquals(0, s.money);
  }

  @Test
  void storyRequiresTrustAndCannotBeRepeatedAfterSelection() {
    var s = ready("nika");
    var g = data.girl("nika");
    s.relation("nika").trust = 0;
    RelationshipJourney.choose(s, g, 0);
    assertEquals(0, s.relation("nika").arcStep);
    s.relation("nika").trust = 20;
    RelationshipJourney.choose(s, g, 0);
    int trust = s.relation("nika").trust;
    RelationshipJourney.choose(s, g, 1);
    assertEquals(trust, s.relation("nika").trust);
    assertEquals(1, s.relation("nika").decisions.size());
  }

  @Test
  void futureMeetingDoesNotTeleportNpcUntilTomorrowAndCanBeCancelled() {
    var s = ready("mira");
    var g = data.girl("mira");
    Social.invite(s, g, "park");
    Social.reschedule(s, g, "park", true);
    var w = new World(Content.load(), s);
    assertTrue(w.npcs().stream().anyMatch(n -> n.id.equals("mira")));
    assertFalse(Social.canStartDate(s, "park"));
    Life.sleep(s);
    assertFalse(w.npcs().stream().anyMatch(n -> n.id.equals("mira")));
    s.map = "park";
    assertTrue(w.npcs().stream().anyMatch(n -> n.id.equals("mira")));
    int trust = s.relation("mira").trust;
    Social.cancel(s, g);
    assertTrue(s.plannedGirl.isEmpty());
    assertEquals(trust, s.relation("mira").trust);
  }

  @Test
  void reschedulingKeepsNpcInOnePlaceAndSkippedMeetingPenaltyIsOnce() {
    var s = ready("asya");
    var g = data.girl("asya");
    Social.invite(s, g, "cafe");
    Social.reschedule(s, g, "park", false);
    assertEquals("park", s.plannedVenue);
    int trust = s.relation("asya").trust;
    Life.advance(s, 1440);
    assertEquals(trust - 2, s.relation("asya").trust);
    Social.expire(s);
    assertEquals(trust - 2, s.relation("asya").trust);
  }

  @Test
  void journalRemainsBoundedAndMigratesOldRelationshipWithoutLosingIt() throws Exception {
    Files.writeString(
        folder.resolve("save.json"),
        "{\"version\":5,\"state\":{\"relationships\":{\"mira\":{\"met\":true,\"trust\":42,\"dates\":3,\"memory\":\"Старое"
            + " воспоминание\"}}}}");
    var store = new SaveStore(folder, Content.load());
    var s = store.load().state();
    var r = s.relation("mira");
    assertEquals(42, r.trust);
    assertEquals("Старое воспоминание", r.memory);
    assertEquals("Старое воспоминание", r.memories.get(0).text);
    assertEquals(0, r.arcStep);
    for (int i = 0; i < 90; i++)
      RelationshipJourney.remember(s, r, "День " + i, "Общий момент " + i);
    assertEquals(32, r.memories.size());
    store.save(s);
    assertEquals(32, store.load().state().relation("mira").memories.size());
  }

  @Test
  void familiarTopicGivesLessTrustThanFreshTopic() {
    var s = ready("mira");
    var g = data.girl("mira");
    Social.talk(s, g, 0);
    int trust = s.relation("mira").trust;
    nextDay(s);
    Social.talk(s, g, 0);
    assertTrue(s.relation("mira").trust - trust < g.topics[0].trust);
  }

  @Test
  void allThreeStoriesAreReachableThroughNormalActionsAndDays() {
    for (var g : data.girls) {
      var s = new GameState();
      s.gameMinutes = 480;
      s.map = g.homeMap;
      Social.meet(s, g);
      for (int day = 0; day < 8 && s.relation(g.id).arcStep < 3; day++) {
        s.map = g.homeMap;
        Life.buy(s, data.item("soup"));
        Life.eat(s, data.item("soup"));
        Life.buy(s, data.item("soup"));
        Life.eat(s, data.item("soup"));
        Social.talk(s, g, 0);
        Social.invite(s, g, "park");
        s.map = "park";
        Social.date(s, g, 0);
        Social.commit(s, g);
        s.map = g.homeMap;
        var r = s.relation(g.id);
        if (RelationshipJourney.nextReason(s, g).isEmpty()) RelationshipJourney.choose(s, g, 0);
        int activity = day % 3;
        if (!r.promise.isEmpty())
          for (int i = 0; i < 3; i++)
            if (RelationshipJourney.DATA.arc(g.id).activities[i].id.equals(r.promise)) activity = i;
        RelationshipJourney.activity(s, g, activity);
        s.map = "room";
        Life.sleep(s);
      }
      assertEquals(3, s.relation(g.id).arcStep, g.id + " needs a reachable story path");
      assertEquals(g.id, s.partnerId);
      assertTrue(s.money >= 0);
    }
  }
}
