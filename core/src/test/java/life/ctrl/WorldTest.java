package life.ctrl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;
import org.junit.jupiter.api.Test;

class WorldTest {
  final Content content = Content.load();

  World fresh() {
    return new World(content, new GameState());
  }

  @Test
  void contentReferencesAndEffectsAreValid() {
    content.validate();
    World world = fresh();
    for (var d : content.dialogues)
      for (var c : d.choices) assertDoesNotThrow(() -> world.effect(c.effect));
    for (var m : content.maps)
      for (var p : m.objects)
        if (!Set.of("door", "decoration", "wardrobe").contains(p.action))
          assertDoesNotThrow(() -> content.dialogue(world.propDialogue(p)));
  }

  @Test
  void diagonalMovementIsNotFaster() {
    var straight = fresh();
    var diagonal = fresh();
    for (int i = 0; i < 15; i++) {
      straight.tick(1, 0);
      diagonal.tick(1, 1);
    }
    assertEquals(
        Math.hypot(straight.state.x - 240, straight.state.y - 176),
        Math.hypot(diagonal.state.x - 240, diagonal.state.y - 176),
        .002);
  }

  @Test
  void walkingIntoWallStopsWithoutTunnelling() {
    var w = fresh();
    for (int i = 0; i < 10000; i++) w.tick(1, 0);
    assertFalse(w.blocked(w.state.x, w.state.y));
    assertTrue(w.state.x < 416);
    assertTrue(w.state.x > 300);
  }

  @Test
  void equalTickCountsGiveEqualTimeAndDistance() {
    var a = fresh();
    var b = fresh();
    for (int frame = 0; frame < 30; frame++) {
      a.tick(-1, 0);
      a.tick(-1, 0);
    }
    for (int frame = 0; frame < 60; frame++) b.tick(-1, 0);
    assertEquals(a.state.x, b.state.x);
    assertEquals(a.state.elapsed, b.state.elapsed);
  }

  @Test
  void doorsHaveValidSpawnPositionsAndConnectEveryMap() {
    Set<String> visited = new HashSet<>();
    ArrayDeque<String> todo = new ArrayDeque<>();
    todo.add("room");
    while (!todo.isEmpty()) {
      String id = todo.remove();
      if (!visited.add(id)) continue;
      for (var p : content.map(id).objects)
        if (p.action.equals("door")) {
          GameState s = new GameState();
          s.map = id;
          s.x = content.map(id).spawnX;
          s.y = content.map(id).spawnY;
          World w = new World(content, s);
          w.enter(p);
          assertFalse(w.blocked(s.x, s.y), id + " -> " + p.target);
          todo.add(p.target);
          assertTrue(
              Arrays.stream(content.map(p.target).objects).anyMatch(q -> id.equals(q.target)),
              "Return door absent: " + id);
        }
    }
    assertEquals(content.maps.length, visited.size());
  }

  @Test
  void everyInteractionCanBeReachedFromMapEntry() {
    for (var map : content.maps) {
      GameState s = new GameState();
      s.map = map.id;
      s.x = map.spawnX;
      s.y = map.spawnY;
      World w = new World(content, s);
      int step = 4, cols = map.width / step, rows = map.height / step;
      boolean[] reachable = new boolean[cols * rows];
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      int start = Math.round(s.y / step) * cols + Math.round(s.x / step);
      reachable[start] = true;
      queue.add(start);
      while (!queue.isEmpty()) {
        int at = queue.remove(), x = at % cols, y = at / cols;
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
          int nx = x + d[0], ny = y + d[1];
          if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
          int next = ny * cols + nx;
          if (!reachable[next] && !w.blocked(nx * step, ny * step)) {
            reachable[next] = true;
            queue.add(next);
          }
        }
      }
      for (var p : map.objects) {
        if (p.action.equals("decoration")) continue;
        boolean found = false;
        for (int i = 0; i < reachable.length; i++)
          if (reachable[i] && Math.hypot((i % cols) * step - p.x, (i / cols) * step - p.y) < 28) {
            found = true;
            break;
          }
        assertTrue(found, map.id + " unreachable: " + p.id);
      }
    }
  }

  @Test
  void npcRoutesStayWalkableAndDeterministic() {
    for (var map : content.maps) {
      GameState s = new GameState();
      s.map = map.id;
      s.x = map.spawnX;
      s.y = map.spawnY;
      World w = new World(content, s);
      var a = new World.Actor();
      var b = new World.Actor();
      for (int t = 0; t < 500; t++) {
        s.elapsed = t * .25;
        for (var npc : map.npcs) {
          w.position(npc, a);
          w.position(npc, b);
          assertEquals(a.x, b.x);
          assertEquals(a.y, b.y);
          assertFalse(w.blocked(a.x, a.y), npc.id + " intersects wall at " + a.x + "," + a.y);
        }
      }
    }
  }

  @Test
  void letterCanBeFoundBeforeQuestAndCannotBeDeliveredTwice() {
    var w = fresh();
    w.effect("deliverLetter");
    assertFalse(w.state.deliveredLetter);
    w.effect("takeLetter");
    assertTrue(w.state.hasLetter);
    w.effect("deliverLetter");
    w.effect("deliverLetter");
    w.effect("takeLetter");
    assertTrue(w.state.deliveredLetter);
    assertFalse(w.state.hasLetter);
    assertEquals("miraAgain", w.npcDialogue(content.map("stair").npcs[0]));
  }

  @Test
  void letterDisappearsFromWorldOnPickup() {
    var w = fresh();
    var p =
        Arrays.stream(content.map("street").objects)
            .filter(o -> o.id.equals("letter"))
            .findFirst()
            .orElseThrow();
    assertTrue(w.visible(p));
    w.effect("takeLetter");
    assertFalse(w.visible(p));
  }

  @Test
  void nearbyNpcHasContextualDialogue() {
    var w = fresh();
    var npc = content.map("stair").npcs[0];
    assertEquals("mira", w.npcDialogue(npc));
    w.effect("takeLetter");
    assertEquals("miraFound", w.npcDialogue(npc));
  }

  @Test
  void malformedStatesAreRejected() {
    var s = new GameState();
    s.x = Float.NaN;
    assertThrows(IllegalArgumentException.class, () -> new World(content, s));
    s.x = 10;
    s.y = 10;
    assertThrows(IllegalArgumentException.class, () -> new World(content, s));
    s.map = "absent";
    assertThrows(IllegalArgumentException.class, () -> new World(content, s));
  }
}
