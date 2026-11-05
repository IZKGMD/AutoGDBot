package life.ctrl.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.Comparator;
import life.ctrl.model.Atmosphere;
import life.ctrl.model.GameState;

/** Multiplicative material lighting. UI and the cutaway void keep their original colour. */
public final class WorldLighting implements Disposable {
  private static final int LIMIT = 6;
  private final ShaderProgram shader;
  private final float[] positions = new float[LIMIT * 4], energies = new float[LIMIT * 3];

  private record Light(float x, float y, float rx, float ry, float r, float g, float b) {}

  private final ArrayList<Light> lights = new ArrayList<>();

  public WorldLighting() {
    shader =
        new ShaderProgram(
            """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            varying vec2 v_position;
            void main() {
              v_color=a_color; v_color.a*=255.0/254.0;
              v_texCoords=a_texCoord0; v_position=a_position.xy;
              gl_Position=u_projTrans*a_position;
            }
            """,
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            varying vec2 v_position;
            uniform sampler2D u_texture;
            uniform vec3 u_ambient;
            uniform vec4 u_points[6];
            uniform vec3 u_energy[6];
            uniform int u_count;
            void main() {
              vec4 base=texture2D(u_texture,v_texCoords)*v_color;
              vec3 light=u_ambient;
              for(int i=0;i<6;i++) {
                if(i<u_count) {
                  vec2 delta=(v_position-u_points[i].xy)/u_points[i].zw;
                  float weight=max(0.0,1.0-dot(delta,delta));
                  light+=u_energy[i]*weight*weight;
                }
              }
              gl_FragColor=vec4(base.rgb*min(light,vec3(1.22)),base.a);
            }
            """);
    if (!shader.isCompiled())
      Gdx.app.error("WorldLighting", "Using ambient fallback: " + shader.getLog());
  }

  public boolean available() {
    return shader.isCompiled();
  }

  private void add(float x, float y, float rx, float ry, float r, float g, float b) {
    lights.add(new Light(x, y, rx, ry, r, g, b));
  }

  public void begin(PixelCanvas c, GameState s, float ox, float oy) {
    if (!available()) return;
    lights.clear();
    float n = Atmosphere.night(s), r = 1, g = 1, b = 1;
    switch (s.map) {
      case "room" -> {
        r = .96f - n * .32f;
        g = .97f - n * .29f;
        b = .98f - n * .22f;
        add(188, 119, 96, 76, .22f + n * .30f, .20f + n * .24f, .13f + n * .15f);
        add(187, 113, 44, 40, .05f, .16f, .15f);
        add(247, 89, 118, 112, .14f * (1 - n), .17f * (1 - n), .18f);
      }
      case "cafe" -> {
        r = .95f - n * .34f;
        g = .96f - n * .31f;
        b = .96f - n * .24f;
        add(155, 125, 160, 130, .15f + n * .44f, .12f + n * .28f, .05f + n * .10f);
        add(300, 185, 115, 105, .12f + n * .44f, .10f + n * .27f, .05f + n * .10f);
      }
      case "diner" -> {
        r = .96f - n * .21f;
        g = .97f - n * .21f;
        b = .96f - n * .19f;
        add(226, 150, 210, 170, .17f, .16f, .12f);
      }
      case "workshop" -> {
        r = .93f - n * .34f;
        g = .96f - n * .31f;
        b = 1 - n * .22f;
        add(174, 131, 110, 95, .10f, .28f, .24f);
        add(285, 200, 145, 140, .25f, .22f, .13f);
      }
      case "stair" -> {
        r = .94f - n * .36f;
        g = .96f - n * .31f;
        b = .98f - n * .22f;
        add(165, 69, 180, 165, .2f + n * .50f, .17f + n * .29f, .08f + n * .10f);
        add(300, 84, 100, 100, .17f, .12f, .06f);
      }
      case "street" -> {
        r = .96f - n * .43f;
        g = .98f - n * .38f;
        b = 1 - n * .27f;
        add(402, 282, 145, 105, n * .72f, n * .48f, n * .18f);
        add(874, 405, 145, 105, n * .78f, n * .50f, n * .18f);
        for (int x : new int[] {130, 241, 445, 556, 685, 796})
          add(x, 189, 78, 111, n * .36f, n * .25f, n * .11f);
      }
      case "park" -> {
        r = .96f - n * .48f;
        g = 1 - n * .42f;
        b = .98f - n * .27f;
        add(245, 178, 175, 145, n * .32f, n * .24f, n * .12f);
        add(140, 120, 100, 90, .03f, .11f, .12f);
      }
    }
    lights.sort(Comparator.comparingDouble(l -> Math.hypot(l.x - (ox + 240), l.y - (oy + 135))));
    int count = Math.min(LIMIT, lights.size());
    for (int i = 0; i < count; i++) {
      var l = lights.get(i);
      int p = i * 4, e = i * 3;
      positions[p] = l.x - ox;
      positions[p + 1] = PixelCanvas.H - l.y + oy;
      positions[p + 2] = l.rx;
      positions[p + 3] = l.ry;
      energies[e] = l.r;
      energies[e + 1] = l.g;
      energies[e + 2] = l.b;
    }
    c.batch.setShader(shader);
    shader.setUniformf("u_ambient", r, g, b);
    shader.setUniformi("u_count", count);
    if (count > 0) {
      shader.setUniform4fv("u_points[0]", positions, 0, count * 4);
      shader.setUniform3fv("u_energy[0]", energies, 0, count * 3);
    }
  }

  public void end(PixelCanvas c) {
    if (available()) c.batch.setShader(null);
  }

  @Override
  public void dispose() {
    shader.dispose();
  }
}
