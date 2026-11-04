package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CareerTest {
  final LifeContent data = LifeContent.load();
  @TempDir Path folder;

  void finish(GameState s, String id) {
    var j = data.job(id);
    Coding.accept(s, j, 0);
    Coding.completeTyping(s);
    Coding.repair(s, j, Arrays.asList(j.solutions).indexOf(j.source));
    assertTrue(Coding.test(s, j).passed(), id);
    Coding.preview(s, j, j.seed[j.seed.length - 1]);
    Coding.deliver(s, j);
    assertEquals(5, s.jobStage, id);
  }

  @Test
  void tactileDraftAndDebugRequireSeparateTypingPhases() {
    var state = new GameState();
    var job = data.job("total");
    Coding.accept(state, job, 0, false, true);
    assertEquals(0, state.typedCount);
    assertFalse(Coding.test(state, job).compiled());
    while (state.typedCount < state.codeSource.length()) Coding.type(state);
    assertFalse(Coding.test(state, job).passed());

    Coding.repair(state, job, 0, true);
    assertEquals(0, state.typedCount);
    assertFalse(Coding.test(state, job).compiled());
    while (state.typedCount < state.codeSource.length()) Coding.type(state);
    assertTrue(Coding.test(state, job).passed());
  }

  @Test
  void profileCountsExecutedOperationsAndRejectsSlowCorrectProgram() {
    var s = new GameState();
    s.codingXp = 8;
    var j = data.job("batch");
    Coding.accept(s, j, 0);
    var r = Coding.test(s, j);
    assertTrue(r.compiled());
    assertFalse(r.passed());
    assertTrue(r.cases().stream().allMatch(x -> x.endsWith("OK")));
    assertEquals(7, ByteScript.compile(s.codeSource).operations(3));
    Coding.repair(s, j, 1);
    assertTrue(Coding.test(s, j).passed());
    assertEquals(1, ByteScript.compile(s.codeSource).operations(3));
  }

  @Test
  void profilerOnlyVisitsTakenBranch() {
    var p = ByteScript.compile("fn f(x) { return x > 0 ? x + 1 : x * x + x; }");
    assertEquals(3, p.operations(2));
    assertEquals(4, p.operations(-2));
  }

  @Test
  void revisionsRestoreExactSourceAndInvalidateRelease() throws Exception {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.completeTyping(s);
    Coding.repair(s, j, 0);
    Coding.commit(s);
    Coding.preview(s, j, 2);
    var store = new SaveStore(folder, Content.load());
    store.save(s);
    s = store.load().state();
    assertEquals(2, s.revisions.size());
    Coding.restore(s, 0);
    assertFalse(s.programPreviewed);
    assertFalse(Coding.test(s, j).compiled());
    Coding.restore(s, 1);
    assertTrue(Coding.test(s, j).passed());
    assertFalse(s.programPreviewed);
  }

  @Test
  void repeatedCommitsAndStockTestsCannotFarmQualityBonus() {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.repair(s, j, 0);
    Coding.commit(s);
    Coding.commit(s);
    assertEquals(2, s.revisions.size());
    Coding.probe(s, j, new double[] {70, 2});
    assertTrue(s.customCases.isEmpty());
    Coding.probe(s, j, new double[] {12, 8});
    Coding.probe(s, j, new double[] {12, 8});
    assertEquals(1, s.customCases.size());
    assertEquals(0, Career.bonus(s, j));
    Coding.probe(s, j, new double[] {12, 0});
    assertEquals(100, Career.bonus(s, j));
  }

  @Test
  void probeErrorDoesNotGrantQualityCredit() {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.completeTyping(s);
    Coding.probe(s, j, new double[] {12, 8});
    assertTrue(s.customCases.isEmpty());
    Coding.repair(s, j, 0);
    Coding.probe(s, j, new double[] {Double.NaN, 8});
    assertTrue(s.customCases.isEmpty());
  }

  @Test
  void rushDeadlinePaysBonusOnlyWhenWorkFits() {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0, true);
    Coding.repair(s, j, 0);
    Coding.preview(s, j, 2);
    assertEquals(j.pay / 4, Career.bonus(s, j));
    Life.advance(s, 91);
    assertEquals(0, Career.bonus(s, j));
    Coding.deliver(s, j);
    assertEquals(10650, s.money);
    assertTrue(s.lastClientMessage.contains("Тамара"));
  }

  @Test
  void unfinishedProjectCannotBeSilentlyReplaced() {
    var s = new GameState();
    Coding.accept(s, data.job("total"), 0);
    String before = s.codeSource;
    Coding.accept(s, data.job("discount"), 0);
    assertEquals("total", s.activeJob);
    assertEquals(before, s.codeSource);
    Coding.abandon(s);
    Coding.accept(s, data.job("discount"), 0);
    assertEquals("discount", s.activeJob);
    assertEquals(10000, s.money);
  }

  @Test
  void progressionOpensNewWorkButCapsDailyIncome() {
    var s = new GameState();
    assertEquals(3, Career.offers(s, data).size());
    finish(s, "total");
    finish(s, "discount");
    finish(s, "delivery");
    assertEquals(1, Career.tier(s));
    assertEquals(6, Career.offers(s, data).size());
    Coding.accept(s, data.job("refund"), 0);
    assertNotEquals("refund", s.activeJob);
    Life.sleep(s);
    Coding.accept(s, data.job("refund"), 0);
    assertEquals("refund", s.activeJob);
    assertEquals(2, s.jobStage);
  }

  @Test
  void offerRotationIsStableAndChangesNextDay() {
    var s = new GameState();
    s.codingXp = 8;
    var first = Career.offers(s, data).stream().map(j -> j.id).toList();
    assertEquals(first, Career.offers(s, data).stream().map(j -> j.id).toList());
    Life.sleep(s);
    assertNotEquals(first, Career.offers(s, data).stream().map(j -> j.id).toList());
  }

  @Test
  void upgradesHaveRealEffectAndAreOneTimePurchases() {
    var s = new GameState();
    var j = data.job("total");
    int before = Career.duration(s, j);
    Career.buy(s, "ssd");
    assertEquals(5800, s.money);
    assertTrue(Career.duration(s, j) < before);
    Career.buy(s, "ssd");
    assertEquals(5800, s.money);
    Career.buy(s, "chair");
    assertEquals(3000, s.money);
    var poor = new GameState();
    poor.money = 0;
    Career.buy(poor, "ssd");
    assertTrue(poor.upgrades.isEmpty());
  }

  @Test
  void projectRequiresOrderedShippedCodeAndPaysOnlyAfterLaunch() throws Exception {
    var s = new GameState();
    s.completedOrders = 1;
    Coding.accept(s, data.job("support"), 0);
    assertTrue(s.activeJob.isEmpty());
    finish(s, "board");
    Life.sleep(s);
    assertEquals(9700, s.money);
    finish(s, "remind");
    finish(s, "support");
    assertEquals(9100, s.money);
    int day = s.day();
    Life.sleep(s);
    assertEquals(day + 1, s.day());
    assertEquals(9260, s.money);
    Career.settleProject(s);
    assertEquals(9260, s.money);
    var store = new SaveStore(folder, Content.load());
    store.save(s);
    s = store.load().state();
    Career.settleProject(s);
    assertEquals(9260, s.money);
    Coding.deliver(s, data.job("support"));
    assertEquals(9260, s.money);
  }

  @Test
  void unaffordableLaunchKeepsWorkingCodeUntilPlayerCanPay() {
    var s = new GameState();
    s.completedOrders = 1;
    s.money = 0;
    var j = data.job("board");
    Coding.accept(s, j, 0);
    Coding.repair(s, j, 0);
    Coding.preview(s, j, 2);
    Coding.deliver(s, j);
    assertEquals(0, Career.projectStage(s));
    assertEquals(3, s.jobStage);
    Economy.earn(s, 300, "Подработка");
    Coding.deliver(s, j);
    assertEquals(1, Career.projectStage(s));
    assertEquals(0, s.money);
  }

  @Test
  void versionTwoMigrationPreservesLifeAndRestoresCareer() throws Exception {
    Files.writeString(
        folder.resolve("save.json"),
        "{\"version\":2,\"state\":{\"name\":\"Саша\",\"money\":4321,\"paidJobs\":[\"1:total\",\"2:discount\"]}}");
    var s = new SaveStore(folder, Content.load()).load().state();
    assertEquals(4321, s.money);
    assertEquals(2, s.codingXp);
    assertEquals(2, s.completedOrders);
    assertTrue(s.shipped.contains("discount"));
  }

  @Test
  void duskAndWeatherStayDeterministicAndReadable() {
    var s = new GameState();
    s.gameMinutes = 12 * 60;
    assertEquals(0, Atmosphere.night(s));
    s.gameMinutes = 19 * 60;
    assertTrue(Atmosphere.night(s) > .3 && Atmosphere.night(s) < .5);
    s.gameMinutes = 23 * 60;
    assertEquals(1, Atmosphere.night(s));
    assertTrue(Atmosphere.rain(s));
    s.gameMinutes = 1440 + 12 * 60;
    assertFalse(Atmosphere.rain(s));
  }

  @Test
  void overnightLaunchOnlyPaysOnFollowingDay() {
    var s = new GameState();
    s.completedOrders = 1;
    s.shipped.addAll(List.of("board", "remind"));
    s.gameMinutes = 23 * 60;
    finish(s, "support");
    assertEquals(2, s.day());
    assertEquals(9700, s.money);
    assertEquals(2, s.projectIncomeDay);
    Life.sleep(s);
    assertEquals(9700, s.money);
    Life.sleep(s);
    assertEquals(9860, s.money);
  }

  @Test
  void cancellingDoesNotResetPromisedRushDeadline() {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0, true);
    int promised = s.rushDeadline;
    Life.advance(s, 100);
    Coding.abandon(s);
    Coding.accept(s, j, 0, true);
    assertEquals(promised, s.rushDeadline);
    assertEquals(0, Career.bonus(s, j));
  }
}
