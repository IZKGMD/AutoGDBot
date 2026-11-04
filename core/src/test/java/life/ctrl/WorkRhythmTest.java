package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkRhythmTest {
  final LifeContent data = LifeContent.load();
  @TempDir Path directory;

  GameState ready(String pace) {
    var s = new GameState();
    s.workPace = pace;
    s.gameMinutes = 480;
    s.energy = 85;
    s.hunger = 85;
    prepare(s, "total");
    return s;
  }

  void prepare(GameState s, String id) {
    var j = data.job(id);
    Coding.accept(s, j, 0);
    Coding.repair(s, j, Arrays.asList(j.solutions).indexOf(j.source));
    Coding.preview(s, j, j.seed[j.seed.length - 1]);
  }

  @Test
  void paceTradesTimeForFatigueAndSurvivesReload() throws Exception {
    var j = data.job("total");
    for (var pace : WorkRhythm.Pace.values()) {
      var s = ready(pace.name());
      int minutes = Career.duration(s, j);
      double energy = s.energy;
      var store = new SaveStore(directory.resolve(pace.name()), Content.load());
      store.save(s);
      s = store.load().state();
      assertEquals(pace, WorkRhythm.pace(s));
      Coding.deliver(s, j);
      assertEquals(480 + minutes, s.minutes());
      assertEquals(pace.fatigue, s.workFatigue);
      assertEquals(energy - minutes * .045 - pace.energy, s.energy, .001);
      int money = s.money;
      Coding.deliver(s, j);
      assertEquals(money, s.money);
      assertEquals(pace.fatigue, s.workFatigue);
    }
    assertTrue(Career.duration(ready("SPRINT"), j) < Career.duration(ready("STEADY"), j));
    assertTrue(Career.duration(ready("REVIEW"), j) > Career.duration(ready("STEADY"), j));
  }

  @Test
  void exhaustedSprintKeepsCodeMoneyAndTimeUntilPaceChanged() {
    var s = ready("SPRINT");
    s.workFatigue = 51;
    var j = data.job("total");
    String source = s.codeSource;
    assertTrue(Coding.deliver(s, j).contains("Для рывка"));
    assertEquals(10000, s.money);
    assertEquals(480, s.minutes());
    assertEquals(source, s.codeSource);
    assertEquals(3, s.jobStage);
    s.workPace = "STEADY";
    Coding.deliver(s, j);
    assertEquals(10650, s.money);
  }

  @Test
  void needThresholdsAndHardwareChangeForecastWithoutRemovingJobs() {
    var s = ready("SPRINT");
    for (int need = 0; need < 3; need++) {
      s.energy = need == 0 ? 34 : 35;
      s.hunger = need == 1 ? 24 : 25;
      s.workFatigue = need == 2 ? 51 : 50;
      assertFalse(WorkRhythm.blocked(s).isEmpty());
    }
    s.energy = 35;
    s.hunger = 25;
    s.workFatigue = 50;
    assertEquals("", WorkRhythm.blocked(s));
    s.workPace = "STEADY";
    var j = data.job("total");
    int baseline = Career.duration(s, j);
    s.workFatigue = 60;
    s.hunger = 14;
    assertEquals((int) Math.ceil(baseline * 1.45), Career.duration(s, j));
    assertEquals("", Career.blocked(s, j));
    s.upgrades.addAll(List.of("ssd", "chair"));
    assertEquals((int) Math.ceil(j.minutes * .85 * 1.45), Career.duration(s, j));
    assertEquals(2, WorkRhythm.energyCost(s));
  }

  @Test
  void reviewPremiumNeedsPassingNovelTestsOnCurrentRevision() {
    var s = ready("REVIEW");
    var j = data.job("total");
    assertEquals(0, Career.bonus(s, j));
    Coding.probe(s, j, new double[] {12, 3});
    Coding.probe(s, j, new double[] {12, 4});
    assertEquals(200, Career.bonus(s, j));
    Coding.commit(s);
    Coding.repair(s, j, 1);
    assertEquals(0, Career.bonus(s, j));
    Coding.probe(s, j, new double[] {12, 3});
    Coding.probe(s, j, new double[] {12, 4});
    assertEquals(0, Career.bonus(s, j));
    Coding.restore(s, s.revisions.size() - 1);
    assertTrue(s.customCases.isEmpty());
    Coding.probe(s, j, new double[] {12, 3});
    Coding.probe(s, j, new double[] {12, 4});
    Coding.preview(s, j, 2);
    Coding.deliver(s, j);
    assertEquals(10850, s.money);
  }

  @Test
  void changingPaceCannotExtendRushDeadline() {
    var s = ready("STEADY");
    var j = data.job("total");
    Coding.abandon(s);
    Coding.accept(s, j, 0, true);
    int due = s.rushDeadline;
    s.workPace = "REVIEW";
    Coding.abandon(s);
    Coding.accept(s, j, 0, true);
    assertEquals(due, s.rushDeadline);
    Life.advance(s, 90);
    assertEquals(0, Career.bonus(s, j));
    assertTrue(WorkRhythm.forecast(s, j).contains("сгорит"));
  }

  @Test
  void breakCostsRealTimeAndExpiresMeetingsAndRent() {
    var s = new GameState();
    s.gameMinutes = 3 * 1440 - 10;
    s.workFatigue = 60;
    s.plannedGirl = "mira";
    s.plannedVenue = "park";
    s.plannedDay = 3;
    WorkRhythm.breakTime(s);
    assertEquals(4, s.day());
    assertEquals(40, s.workFatigue);
    assertEquals(7000, s.money);
    assertEquals("", s.plannedGirl);
  }

  @Test
  void tenMinuteSleepCannotFullyRestorePlayerAndNapHasExactCost() {
    var s = new GameState();
    s.gameMinutes = 470;
    s.energy = 0;
    s.workFatigue = 90;
    s.hunger = 80;
    Life.sleep(s);
    assertEquals(480, s.minutes());
    assertEquals(3.2, s.energy, .001);
    assertEquals(90 - 10.0 / 6, s.workFatigue, .001);
    assertEquals(79.75, s.hunger, .001);
    Life.nap(s);
    assertEquals(570, s.minutes());
    assertEquals(32, s.energy, .001);
    assertEquals(77.5, s.hunger, .001);
  }

  @Test
  void starvationDamageIsIndependentOfTimeChunkSize() {
    var a = new GameState();
    var b = new GameState();
    a.hunger = b.hunger = 20;
    Life.advance(a, 300);
    for (int i = 0; i < 30; i++) Life.advance(b, 10);
    assertEquals(a.health, b.health, .00001);
    assertEquals(a.hunger, b.hunger, .00001);
  }

  @Test
  void planAndReserveAreReadOnlyAndIncludeRelationshipPromises() {
    var s = new GameState();
    assertEquals(3440, WorkRhythm.reserve(s));
    int count = s.relationships.size();
    WorkRhythm.agenda(s, data);
    assertEquals(count, s.relationships.size());
    s.rentDebt = 100;
    s.plannedGirl = "mira";
    s.plannedVenue = "cafe";
    s.plannedDay = 2;
    assertEquals(4390, WorkRhythm.reserve(s));
    var r = s.relation("mira");
    r.promise = RelationshipJourney.DATA.arc("mira").activities[0].id;
    r.promiseDue = 3;
    String agenda = String.join(" ", WorkRhythm.agenda(s, data));
    assertTrue(agenda.contains("Мира"));
    assertTrue(agenda.contains("конца дня 3"));
    assertEquals(10000, s.money);
  }

  @Test
  void versionSevenDefaultsAndCorruptionValidation() throws Exception {
    Files.writeString(directory.resolve("save.json"), "{\"version\":7,\"state\":{\"money\":4321}}");
    var s = new SaveStore(directory, Content.load()).load().state();
    assertEquals(4321, s.money);
    assertEquals("STEADY", s.workPace);
    assertEquals(0, s.workFatigue);
    s.workFatigue = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> new World(Content.load(), s));
    s.workFatigue = 0;
    s.workPace = "FREE_MONEY";
    assertThrows(IllegalArgumentException.class, () -> new World(Content.load(), s));
  }

  @Test
  void thirtyDaysSupportMealsRentAndRegularDatesWithoutGrindingThreeJobs() {
    for (String pace : List.of("STEADY", "SPRINT", "REVIEW")) {
      var s = new GameState();
      s.gameMinutes = 480;
      s.workPace = pace;
      for (int day = 1; day <= 30; day++) {
        if (s.hunger < 60) {
          Life.buy(s, data.item("soup"));
          Life.eat(s, data.item("soup"));
        }
        for (String id : List.of("total", "discount")) {
          if (!WorkRhythm.blocked(s).isEmpty()) WorkRhythm.breakTime(s);
          if (s.hunger < 30) {
            Life.buy(s, data.item("soup"));
            Life.eat(s, data.item("soup"));
          }
          prepare(s, id);
          Coding.deliver(s, data.job(id));
          assertEquals(5, s.jobStage, pace + ": day " + day);
        }
        Life.buy(s, data.item("soup"));
        Life.eat(s, data.item("soup"));
        if (day % 3 == 0) {
          assertTrue(Economy.spend(s, 850, "Столик для двоих"));
          Life.advance(s, 90);
          WorkRhythm.recover(s, 15);
        }
        Life.advance(s, 240);
        Life.sleep(s);
        assertEquals(day + 1, s.day());
        assertEquals(0, s.rentDebt, pace);
        assertTrue(s.health >= 80, pace);
      }
      assertTrue(s.money > 10000, pace + " balance " + s.money);
      assertEquals(60, s.completedOrders);
    }
  }
}
