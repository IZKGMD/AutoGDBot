package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.*;
import life.ctrl.model.*;
import life.ctrl.save.SaveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LifeSystemsTest {
  final LifeContent data = LifeContent.load();
  final Content content = Content.load();
  @TempDir Path folder;

  @Test
  void startsWithTenThousandAndAllPricesAreAffordableButMeaningful() {
    var s = new GameState();
    assertEquals(10000, s.money);
    assertEquals(3000, Economy.WEEKLY_RENT);
    for (var j : data.jobs) if (j.tier == 0 && !j.once) assertTrue(j.pay >= 650 && j.pay <= 950);
    assertTrue(data.item("soup").price < 650);
  }

  @Test
  void purchaseAndFoodAreSingleInventoryTransactions() {
    var s = new GameState();
    var meal = data.item("soup");
    Life.buy(s, meal);
    assertEquals(10000 - meal.price, s.money);
    assertEquals(1, s.count("soup"));
    double h = s.hunger;
    Life.eat(s, meal);
    assertEquals(0, s.count("soup"));
    assertTrue(s.hunger > h);
    int money = s.money;
    Life.eat(s, meal);
    assertEquals(money, s.money);
  }

  @Test
  void purchaseRejectsOverspending() {
    var s = new GameState();
    s.money = 1;
    Life.buy(s, data.item("book"));
    assertEquals(1, s.money);
    assertEquals(0, s.count("book"));
  }

  @Test
  void weeklyRentOccursOnceAcrossSaveAndLoad() throws Exception {
    var s = new GameState();
    s.gameMinutes = 3 * 1440 - 1;
    Life.advance(s, 2);
    assertEquals(7000, s.money);
    assertEquals(10, s.rentThroughDay);
    var saves = new SaveStore(folder, content);
    saves.save(s);
    s = saves.load().state();
    Life.advance(s, 1);
    assertEquals(7000, s.money);
    assertEquals(1, s.transactions.size());
  }

  @Test
  void povertyHasFoodAndIncomeWithoutAPc() {
    var s = new GameState();
    s.money = 0;
    s.hunger = 1;
    Life.freeMeal(s);
    assertTrue(s.hunger >= 40);
    Life.courier(s);
    assertEquals(240, s.money);
    assertTrue(s.items.isEmpty());
    Life.courier(s);
    assertEquals(240, s.money);
  }

  @Test
  void everyTemplateReallyCompilesAndPassesItsClientCases() {
    for (var j : data.jobs) {
      var p = ByteScript.compile(j.source);
      assertEquals(j.function, p.name());
      for (var c : j.tests) assertEquals(c.expected, p.run(c.args), .001);
    }
  }

  @Test
  void syntaxAndSemanticAndTestFailuresAreDifferent() {
    assertEquals(
        ByteScript.Kind.SYNTAX,
        assertThrows(ByteScript.Error.class, () -> ByteScript.compile("fn f(x) { return ; }"))
            .kind);
    assertEquals(
        ByteScript.Kind.SEMANTIC,
        assertThrows(ByteScript.Error.class, () -> ByteScript.compile("fn f(x) { return y; }"))
            .kind);
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 1);
    Coding.completeTyping(s);
    var r = Coding.test(s, j);
    assertTrue(r.compiled());
    assertFalse(r.passed());
  }

  @Test
  void realRepairChangesProgramAndPaymentCannotBeDuplicatedEvenAcrossMidnight() {
    var s = new GameState();
    s.gameMinutes = 23 * 60 + 50;
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.completeTyping(s);
    assertFalse(Coding.test(s, j).compiled());
    String before = s.codeSource;
    Coding.repair(s, j, 0);
    assertNotEquals(before, s.codeSource);
    assertTrue(Coding.test(s, j).passed());
    assertEquals(90, Coding.preview(s, j, 3));
    Coding.deliver(s, j);
    int paid = s.money;
    assertEquals(10650, paid);
    assertEquals(2, s.day());
    Coding.deliver(s, j);
    assertEquals(paid, s.money);
  }

  @Test
  void projectNeedsPreviewAndSurvivesSavingInTheMiddle() throws Exception {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.type(s);
    var saves = new SaveStore(folder, content);
    saves.save(s);
    var copy = saves.load().state();
    assertEquals(s.typedCount, copy.typedCount);
    assertEquals(s.codeSource, copy.codeSource);
    Coding.deliver(copy, j);
    assertEquals(10000, copy.money);
  }

  @Test
  void favouriteGiftsDoNotBuyTrustAndCannotBeSpammed() {
    var s = new GameState();
    var g = data.girl("mira");
    Social.meet(s, g);
    s.items.put("seeds", 2);
    Social.gift(s, g, data.item("seeds"));
    var r = s.relation("mira");
    int a = r.affinity;
    Social.gift(s, g, data.item("seeds"));
    assertEquals(a, r.affinity);
    assertEquals(1, s.count("seeds"));
    Social.invite(s, g, "park");
    assertTrue(s.plannedGirl.isEmpty());
  }

  @Test
  void topicsAreUniqueAndRepeatedConversationsDoNotFarmAffection() {
    var s = new GameState();
    for (var g : data.girls) {
      Social.meet(s, g);
      Social.talk(s, g, 0);
      var r = s.relation(g.id);
      int a = r.affinity;
      Social.talk(s, g, 0);
      assertEquals(a, r.affinity);
      assertTrue(r.trust >= 8);
    }
    assertNotEquals(data.girl("mira").topics[0].response, data.girl("asya").topics[0].response);
  }

  @Test
  void datesRequireInvitationsAndMoneyAndFreeParkIsPossible() {
    var s = new GameState();
    var g = data.girl("asya");
    Social.meet(s, g);
    Social.talk(s, g, 0);
    Social.date(s, g, 0);
    assertEquals(0, s.relation(g.id).dates);
    Social.invite(s, g, "cafe");
    s.map = "cafe";
    s.money = 0;
    Social.date(s, g, 0);
    assertEquals(0, s.relation(g.id).dates);
    s.plannedVenue = "park";
    s.map = "park";
    Social.date(s, g, 0);
    assertEquals(1, s.relation(g.id).dates);
    assertEquals(0, s.money);
  }

  @Test
  void everyGirlHasAReachableRelationshipAndConsentPath() {
    for (var g : data.girls) {
      var s = new GameState();
      for (int day = 1; day <= 4; day++) {
        s.gameMinutes = (day - 1) * 1440 + 18 * 60;
        Social.meet(s, g);
        Social.talk(s, g, 0);
        Social.invite(s, g, "park");
        s.map = "park";
        Social.date(s, g, 0);
        if (day == 3) Social.commit(s, g);
      }
      assertEquals(g.id, s.partnerId);
      assertTrue(Social.canShareNight(s, g));
      s.map = "room";
      int nights = s.relation(g.id).sharedNights;
      Social.shareNight(s, g, false);
      assertEquals(nights, s.relation(g.id).sharedNights);
      Social.shareNight(s, g, true);
      assertEquals(nights + 1, s.relation(g.id).sharedNights);
      assertTrue(s.energy > 90);
    }
  }

  @Test
  void friendshipNeverImplicitlyConsentsToIntimacy() {
    var s = new GameState();
    var g = data.girl("mira");
    Social.meet(s, g);
    var r = s.relation(g.id);
    r.affinity = 100;
    r.trust = 100;
    r.dates = 10;
    assertFalse(Social.canShareNight(s, g));
    Social.shareNight(s, g, true);
    assertEquals(0, r.sharedNights);
  }

  @Test
  void socialStateAndLedgerRoundTrip() throws Exception {
    var s = new GameState();
    var g = data.girl("nika");
    Social.meet(s, g);
    Social.talk(s, g, 0);
    Life.buy(s, data.item("book"));
    Social.invite(s, g, "park");
    var store = new SaveStore(folder, content);
    store.save(s);
    var copy = store.load().state();
    assertEquals(s.relation(g.id).trust, copy.relation(g.id).trust);
    assertEquals(g.id, copy.plannedGirl);
    assertEquals(s.transactions.get(0).amount, copy.transactions.get(0).amount);
    assertEquals(1, copy.count("book"));
  }

  @Test
  void oneNpcExistsInOnePlaceDuringTheDate() {
    var s = new GameState();
    var g = data.girl("mira");
    Social.meet(s, g);
    Social.talk(s, g, 0);
    Social.invite(s, g, "park");
    var w = new World(content, s);
    int count = 0;
    for (var m : content.maps) {
      s.map = m.id;
      count += w.npcs().stream().filter(n -> n.id.equals("mira")).count();
    }
    assertEquals(1, count);
  }

  @Test
  void legacyRoomSaveMigratesToSafeSmallerRoom() throws Exception {
    String old =
        "{\"version\":1,\"state\":{\"name\":\"Саша\",\"map\":\"room\",\"x\":380,\"y\":210}}";
    Files.writeString(folder.resolve("save.json"), old);
    var state = new SaveStore(folder, content).load().state();
    assertEquals(240, state.x);
    assertEquals(190, state.y);
    assertEquals(10000, state.money);
  }

  @Test
  void allAlgorithmChoicesCanBeRepairedAndPaid() {
    for (var j : data.jobs)
      for (int choice = 0; choice < j.algorithms.length; choice++) {
        var s = new GameState();
        s.codingXp = 8;
        s.completedOrders = 1;
        if (j.id.equals("remind") || j.id.equals("support")) s.shipped.add("board");
        if (j.id.equals("support")) s.shipped.add("remind");
        Coding.accept(s, j, choice);
        Coding.completeTyping(s);
        Coding.repair(s, j, java.util.Arrays.asList(j.solutions).indexOf(j.source));
        assertTrue(Coding.test(s, j).passed(), j.id + ":" + choice);
        Coding.preview(s, j, 2);
        Coding.deliver(s, j);
        assertEquals(10000 + j.pay - j.cost, s.money);
      }
  }

  @Test
  void cannotCollectADifferentJobsFee() {
    var s = new GameState();
    var j = data.job("total");
    Coding.accept(s, j, 0);
    Coding.completeTyping(s);
    Coding.repair(s, j, 0);
    Coding.preview(s, j, 2);
    Coding.deliver(s, data.job("discount"));
    assertEquals(10000, s.money);
    assertFalse(Coding.test(s, data.job("discount")).passed());
  }

  @Test
  void datesCannotHappenRemotely() {
    var s = new GameState();
    var g = data.girl("mira");
    Social.meet(s, g);
    Social.talk(s, g, 0);
    Social.invite(s, g, "park");
    Social.date(s, g, 0);
    assertEquals(0, s.relation(g.id).dates);
    assertEquals("mira", s.plannedGirl);
    s.map = "park";
    Social.date(s, g, 0);
    assertEquals(1, s.relation(g.id).dates);
  }

  @Test
  void rejectsNonFiniteNeedsAndBrokenInventory() {
    var s = new GameState();
    s.health = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> new World(content, s));
    s.health = 85;
    s.items.put("soup", -1);
    assertThrows(IllegalArgumentException.class, () -> new World(content, s));
  }

  @Test
  void twoDailyOrdersCoverTwoMealsAndRentForTwoWeeks() {
    var s = new GameState();
    s.gameMinutes = 480;
    for (int day = 1; day <= 14; day++) {
      for (String id : new String[] {"total", "discount"}) {
        var j = data.job(id);
        Coding.accept(s, j, 0);
        Coding.completeTyping(s);
        Coding.repair(s, j, 0);
        Coding.preview(s, j, 2);
        Coding.deliver(s, j);
      }
      Life.buy(s, data.item("soup"));
      Life.eat(s, data.item("soup"));
      Life.advance(s, 360);
      Life.buy(s, data.item("soup"));
      Life.eat(s, data.item("soup"));
      Life.sleep(s);
      assertTrue(s.hunger > 20);
      assertEquals(0, s.rentDebt);
    }
    assertEquals(10000 + 14 * (650 + 950 - 440) - 6000, s.money);
    assertEquals("08:00", s.clock());
  }

  @Test
  void passingNpcCannotStealDoorInteraction() {
    var s = new GameState();
    s.map = "stair";
    s.x = 172.4f;
    s.y = 89.07f;
    var w = new World(content, s);
    assertNotNull(w.nearestNpc());
    assertEquals("door", w.nearestProp().action);
    assertNull(w.interactionNpc());
  }
}
