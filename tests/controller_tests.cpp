#include "controller.hpp"
#include "input_motion.hpp"
#include "hazard_guard.hpp"
#include "planning_hints.hpp"
#include <iostream>
#include <stdexcept>
using namespace agb;
void require(bool b, char const *name) {
    if (!b)
        throw std::runtime_error(name);
}
// A deterministic corridor: the player must hold at t=0 to survive the first
// obstacle and release again before t=12. No graphics or GD installation.
Evaluation corridor(Plan const &p, Clock::time_point deadline) {
    Evaluation e;
    for (int t = 0; t < p.length; ++t) {
        if (Clock::now() >= deadline) {
            e.interrupted = true;
            break;
        }
        if ((t == 0 && !p.at(t)) || (t >= 12 && p.at(t))) {
            e.died = true;
            break;
        }
        ++e.survived;
        e.traces[0].push_back({float(t), p.at(t) ? 1.f : 0.f});
    }
    return e;
}
int main() {
    std::array<Bounds, 1> floorSpike{{{70, 0, 94, 30}}};
    require(shouldReflexJump({0, 0, 30, 30}, 1.3f, floorSpike),
            "ground reflex must detect a nearby forward spike");
    require(!shouldReflexJump({0, 0, 30, 30}, -1.3f, floorSpike),
            "ground reflex must respect reverse travel");
    std::array<Bounds, 1> ceilingHazard{{{70, 60, 94, 90}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, ceilingHazard),
            "ground reflex must ignore a high ceiling hazard");
    std::array<Bounds, 1> farSpike{{{130, 0, 154, 30}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, farSpike),
            "ground reflex must wait until a distant spike enters the takeoff window");
    std::array<Bounds, 1> saw{{{64, -4, 112, 50}}};
    require(!shouldReflexJump({0, 0, 30, 30}, 1.3f, saw),
            "large saws must be timed by native rollout instead of the early spike reflex");
    std::array<Bounds, 2> doubleSpike{{{112, 0, 132, 30}, {136, 0, 156, 30}}};
    require(shouldReflexJump({0, 0, 30, 30}, 1.3f, doubleSpike),
            "adjacent floor spikes must open an earlier committed takeoff window");
    require(!shouldUseReflexFallback(true, false, true, false, false, 216, 216),
            "reflex must never override a fully validated wait/route");
    require(shouldUseReflexFallback(true, false, false, false, false, 0, 216),
            "reflex may rescue the planner only when no route was validated");
    CubePulse pulse;
    CubePulse forecast = pulse;
    bool liveHeld = false;
    int liveTransitions = 0;
    for (int t = 0; t < 12; ++t) {
        bool grounded = t == 0;
        bool planned = forecast.command(true, true, grounded);
        bool executed = pulse.command(true, true, grounded);
        require(planned == executed, "forecast and live cube input must agree");
        applyJumpState(
            executed, [&] { return liveHeld; },
            [&] {
                liveHeld = true;
                ++liveTransitions;
            },
            [&] {
                liveHeld = false;
                ++liveTransitions;
            });
        require(
            liveHeld == (t == 0),
            "repeated DOWN requests must release on tick 1 and stay released during cube flight");
    }
    require(liveTransitions == 2, "one cube jump generates exactly one press and one release");
    require(pulse.command(true, true, true), "new grounded jump can press again");
    require(!pulse.command(true, true, true), "mandatory release prevents a persistent cube hold");
    require(pulse.command(true, true, true), "orb or grounded action can issue a subsequent pulse");
    require(pulse.command(true, false, false), "dash transition must preserve continuous holding");
    for (int t = 0; t < 12; ++t)
        require(pulse.command(true, false, false),
                "ship/wave/robot holding must remain continuous");
    pulse.reset();
    require(!pulse.command(true, true, false),
            "cube flight with no orb cannot start another pulse");
    require(pulse.command(true, true, true), "reset restores cube pulse readiness");
    PhysicsTiming timing;
    constexpr float seconds = 1.f / 240.f;
    require(std::abs(timing.nativeStep(seconds) - .25f) < .00001f,
            "240 TPS command seconds must become a quarter native frame");
    timing.beginCommand(seconds);
    timing.observeNative(.25f);
    require(timing.measured() && std::abs(timing.nativeStep(seconds) - .25f) < .00001f,
            "actual player update calibrates native physics units");
    timing.beginCommand(1.f / 120.f);
    require(std::abs(timing.nativeStep(1.f / 120.f) - .5f) < .00001f,
            "changed command duration preserves the measured unit scale");
    timing.observeNative(0);
    require(std::abs(timing.nativeStep(seconds) - .25f) < .00001f,
            "zero-delta callbacks must not corrupt physics timing");
    timing.reset();
    require(!timing.measured(), "reset clears timing calibration");
    auto corrected = completeTravelStep({10, 5}, {10, 7}, {1.3f, 0}, false);
    require(std::abs(corrected.x - 11.3f) < .0001f && corrected.y == 7,
            "detached clone must travel forward even when native update changes only Y");
    corrected = completeTravelStep({10, 5}, {11.3f, 7}, {1.3f, 0}, false);
    require(std::abs(corrected.x - 11.3f) < .0001f,
            "native travel must not be added a second time");
    corrected = completeTravelStep({10, 5}, {12, 5}, {0, -2}, true);
    require(corrected.x == 12 && corrected.y == 3, "sideways travel-axis correction");
    corrected = completeTravelStep({10, 5}, {10, 7}, {-1.3f, 0}, false);
    require(std::abs(corrected.x - 8.7f) < .0001f, "reverse travel-axis correction");
    bool actualHeld = false;
    int sent = 0;
    auto apply = [&](bool desired) {
        return applyJumpState(
            desired, [&] { return actualHeld; },
            [&] {
                actualHeld = true;
                ++sent;
            },
            [&] {
                actualHeld = false;
                ++sent;
            });
    };
    require(apply(true) && actualHeld && sent == 1, "direct press reaches the player");
    require(!apply(true) && sent == 1, "holding must not generate repeated presses");
    actualHeld = false; // native command processing/reset invalidates the old requested state
    require(apply(true) && actualHeld && sent == 2,
            "resynchronize after native command processing");
    require(apply(false) && !actualHeld && sent == 3, "direct release reaches the player");
    Controller c;
    Config cfg;
    cfg.horizon = 48;
    cfg.candidates = 48;
    cfg.budgetMs = 8;
    Config ledgeCfg;
    ledgeCfg.budgetMs = 8;
    ledgeCfg.candidates = 8;
    ledgeCfg.cubePulsePlanning = true;
    std::array<Bounds, 4> support = {
        {{0, 0, 30, 60}, {30, 0, 60, 60}, {60, 0, 90, 60}, {90, 0, 120, 60}}};
    seedCubeEvents(ledgeCfg, 55, 60, 1.3f, support, {});
    require(ledgeCfg.jumpEventCount > 0 && ledgeCfg.jumpEvents[0] == 48,
            "merge adjoing support tiles and prioritize takeoff near the actual platform edge");
    std::array<Bounds, 5> gapSupport = {
        {{0, 0, 30, 60}, {30, 0, 60, 60}, {60, 0, 90, 60}, {90, 0, 120, 60},
          {190, 0, 230, 60}}};
    auto gap = findForwardGap(55, 60, 1.3f, gapSupport);
    require(gap.found && gap.edgeX == 120 && gap.width > 60,
            "route lookahead must expose a platform gap before the cube falls off it");
    Config orbCfg = ledgeCfg;
    orbCfg.jumpEventCount = 0;
    orbCfg.orbEventCount = 0;
    std::array<Bounds, 1> orb{{{146, 80, 166, 100}}};
    seedCubeEvents(orbCfg, 55, 60, 1.3f, support, {}, orb);
    require(orbCfg.orbEventCount > 0 && orbCfg.jumpEventCount > 0,
            "orb routing must seed both takeoff and orb-activation events");
    bool foundTwoPulse = false;
    for (auto const &plan : c.propose(orbCfg, false)) {
        int pulses = 0;
        for (int t = 0; t < plan.length; ++t)
            pulses += plan.at(t);
        foundTwoPulse |= pulses >= 2;
    }
    require(foundTwoPulse, "cube planner must propose takeoff-plus-orb two-pulse routes");

    Config stairCfg = ledgeCfg;
    stairCfg.horizon = 216;
    stairCfg.candidates = 64;
    stairCfg.jumpEventCount = 0;
    stairCfg.orbEventCount = 0;
    stairCfg.landingEventCount = 0;
    std::array<Bounds, 7> stair = {{{0, 0, 120, 60}, {145, 70, 175, 90},
                                    {205, 100, 235, 120}, {265, 130, 295, 150},
                                    {325, 160, 355, 180}, {385, 190, 415, 210},
                                    {445, 220, 475, 240}}};
    seedCubeEvents(stairCfg, 70, 60, 1.3f, stair, {});
    require(stairCfg.landingEventCount >= 4,
            "staircase lookahead must expose several future landing platforms");
    bool foundStepChain = false;
    for (auto const &plan : c.propose(stairCfg, false)) {
        if (!plan.at(0))
            continue;
        int covered = 0;
        for (int i = 0; i < stairCfg.landingEventCount; ++i) {
            const int t = stairCfg.landingEvents[i];
            bool buffered = false;
            for (int k = std::max(0, t - 6); k <= std::min(plan.length - 1, t + 6); ++k)
                buffered |= plan.at(k);
            covered += buffered;
        }
        foundStepChain |= covered >= 4;
    }
    require(foundStepChain,
            "cube planner must propose a takeoff plus buffered re-jumps across a staircase");
    auto lateLedge = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        if (plan.at(0)) {
            e.survived = std::min(plan.length, 110);
            e.died = plan.length > 110;
            e.requiresLanding = true;
            e.firstActionVerified = false;
        } else if (plan.at(48)) {
            e.survived = plan.length;
        } else {
            e.survived = std::min(plan.length, 90);
            e.died = plan.length > 90;
        }
        return e;
    };
    auto ledge = c.decide(ledgeCfg, false, lateLedge);
    require(ledge.hasValidatedInput && !ledge.down && ledge.plan.at(48) &&
                ledge.predictedTicks == 144 && !ledge.selected.died,
            "find a delayed takeoff that reaches the platform rather than an early fatal jump");
    require(ledge.candidates <= 16,
            "dense geometry timing sweep must stay bounded while testing the safe takeoff neighborhood");
    for (size_t i = 0; i < ledge.proposals.size(); ++i)
        for (size_t j = i + 1; j < ledge.proposals.size(); ++j)
            require(ledge.proposals[i].buttons != ledge.proposals[j].buttons,
                    "GPU receives the same unique proposals that CPU search used");
    Config reverseCfg = ledgeCfg;
    reverseCfg.jumpEventCount = 0;
    seedCubeEvents(reverseCfg, 65, 60, -1.3f, support, {});
    require(reverseCfg.jumpEventCount > 0 && reverseCfg.jumpEvents[0] == 48,
            "reverse travel must schedule takeoff relative to the left support edge");
    c.reset();
    auto unverifiedLanding = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = plan.at(0) ? plan.length : std::min(plan.length, 8);
        e.died = !plan.at(0) && plan.length > 8;
        e.firstActionVerified = !plan.at(0);
        e.requiresLanding = plan.at(0);
        return e;
    };
    auto noLanding = c.decide(ledgeCfg, false, unverifiedLanding);
    require(
        !noLanding.down,
        "even a long surviving rollout cannot launch a cube without the required landing check");
    c.reset();
    Controller sticky;
    Config stickyCfg;
    stickyCfg.horizon = 48;
    stickyCfg.candidates = 16;
    stickyCfg.budgetMs = 8;
    stickyCfg.cubePulsePlanning = true;
    addJumpEvent(stickyCfg, 10);
    auto routeOnly = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        if (!plan.at(0) && plan.at(10)) {
            e.survived = plan.length;
            e.clearance = 50;
        } else {
            e.survived = std::min(plan.length, 18);
            e.died = plan.length > 18;
        }
        return e;
    };
    auto firstRoute = sticky.decide(stickyCfg, false, routeOnly);
    require(firstRoute.hasValidatedInput && firstRoute.plan.at(10),
            "geometry-timed route becomes the warm plan");
    auto equallySafe = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = plan.length;
        e.clearance = 50;
        return e;
    };
    auto heldRoute = sticky.decide(stickyCfg, false, equallySafe);
    require(heldRoute.hasValidatedInput && heldRoute.plan.at(10),
            "planner must keep the validated warm takeoff instead of jittering to a new timing");

    Controller robust;
    Config robustCfg = stickyCfg;
    addJumpEvent(robustCfg, 12);
    auto robustEval = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = plan.length;
        if (plan.at(10)) {
            e.clearance = 2;
            e.landingMargin = 1;
            e.robustness = .05f;
        } else if (plan.at(12)) {
            e.clearance = 32;
            e.landingMargin = 16;
            e.robustness = 1.f;
        } else {
            e.survived = std::min(plan.length, 14);
            e.died = plan.length > 14;
        }
        return e;
    };
    auto robustDecision = robust.decide(robustCfg, false, robustEval);
    require(robustDecision.hasValidatedInput && robustDecision.plan.at(12),
            "robust MPC must prefer a wide-margin takeoff over a spike-grazing route");

    c.reset();
    Config releaseCfg = cfg;
    releaseCfg.forceRelease = true;
    for (int count : {4, 48, 96}) {
        releaseCfg.candidates = count;
        for (auto const &plan : c.propose(releaseCfg, true))
            require(!plan.at(0),
                    "mandatory release constrains every candidate including warm/random plans");
    }
    auto releaseDecision = c.decide(releaseCfg, true, corridor);
    require(!releaseDecision.down && !releaseDecision.hasValidatedInput,
            "dead-end fallback must still honor mandatory release rather than latch the old hold");
    c.reset();
    auto openGround = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = plan.length;
        e.clearance = plan.at(0) ? 100 : 0;
        return e;
    };
    auto stable = c.decide(cfg, false, openGround);
    require(stable.hasValidatedInput && !stable.down,
            "endpoint clearance must not trigger an unnecessary jump on equally safe ground");
    require(stable.candidates == 3,
            "safe unchanged action stops after two short probes and one full-horizon rollout");
    require(stable.completeContinuation,
            "full-horizon result must be distinguished from a short probe");
    c.reset();
    Config prefixCfg = cfg;
    prefixCfg.candidates = 4;
    auto partialHold = [](Plan const &plan, Clock::time_point) {
        Evaluation e;
        e.survived = std::min(plan.length, plan.at(0) ? 10 : 3);
        e.interrupted = plan.at(0) && plan.length > 10;
        e.died = !plan.at(0) && plan.length > 3;
        return e;
    };
    auto partial = c.decide(prefixCfg, false, partialHold);
    require(partial.hasValidatedInput && partial.down && !partial.completeContinuation,
            "interrupted hold cannot be displayed as a completed horizon");
    c.advance(100);
    for (auto const &plan : c.propose(prefixCfg, false)) {
        bool expiredHold = true;
        for (int i = 0; i < plan.length; ++i)
            expiredHold &= plan.at(i) == (i < 10);
        require(!expiredHold, "expired checked prefix must not perpetuate its held tail");
    }
    c.reset();
    HazardGuard guard;
    guard.set({{10, 0, 14, 6}});
    require(guard.hit({7, 9, 9, 11}, {9, 1, 11, 3}), "landing into a spike must be rejected");
    require(!guard.hit({7, 19, 9, 21}, {9, 18, 11, 20}),
            "jump with vertical clearance must remain available");
    require(guard.hit({0, 1, 2, 3}, {20, 1, 22, 3}),
            "swept test must catch crossing a narrow spike between two positions");
    require(guard.hit({20, 1, 22, 3}, {0, 1, 2, 3}),
            "reverse motion must also check the swept path");
    guard.set({{4, 8, 8, 15}});
    int rejected = 0;
    auto missedNativeCollision = [&](Plan const &plan, Clock::time_point deadline) {
        Evaluation e;
        Bounds before{0, 0, 1, 2};
        for (int t = 0; t < plan.length; ++t) {
            if (Clock::now() >= deadline) {
                e.interrupted = true;
                break;
            }
            float y = plan.at(t) ? 10.f : 1.f;
            Bounds after{float(t + 1), y - 1, float(t + 2), y + 1};
            // Simulate a native clone collision pass that misses an overhead
            // hazard. The independent guard must still reject that jump.
            if (guard.hit(before, after)) {
                e.died = true;
                ++rejected;
                break;
            }
            before = after;
            ++e.survived;
        }
        return e;
    };
    auto guarded = c.decide(cfg, false, missedNativeCollision);
    require(guarded.hasValidatedInput && !guarded.down && guarded.predictedTicks == 48 &&
                rejected > 0,
            "guard must reject a jump into a hazard missed by native clone collisions");
    c.reset();
    // A grounded jump must clear a forward obstacle. With seconds mistakenly
    // supplied as native units its height is 60x smaller and every plan dies.
    // This checks unit fidelity rather than claiming to emulate GD physics.
    auto jumpCorridor = [&](Plan const &plan, Clock::time_point deadline) {
        Evaluation e;
        float x = 24, y = 0, vy = 0;
        bool grounded = true;
        for (int t = 0; t < plan.length; ++t) {
            if (Clock::now() >= deadline) {
                e.interrupted = true;
                break;
            }
            if (plan.at(t) && grounded) {
                vy = 11;
                grounded = false;
            }
            float nativeDt = timing.nativeStep(seconds);
            vy -= .8f * nativeDt;
            y += vy * nativeDt;
            if (y <= 0) {
                y = 0;
                vy = 0;
                grounded = true;
            }
            x += 1.3f;
            if (x >= 40 && x <= 70 && y < 30) {
                e.died = true;
                break;
            }
            ++e.survived;
        }
        return e;
    };
    auto jump = c.decide(cfg, false, jumpCorridor);
    require(jump.hasValidatedInput && jump.down && jump.predictedTicks == cfg.horizon,
            "correct native step must select a grounded jump over the forward obstacle");
    c.reset();
    auto d = c.decide(cfg, false, corridor);
    require(d.hasValidatedInput && d.down, "must press for immediate obstacle");
    require(d.predictedTicks == 48, "must discover a press then release route");
    auto detachedCorridor = [](Plan const &plan, Clock::time_point deadline) {
        Evaluation e;
        Point position{0, 0};
        for (int t = 0; t < plan.length; ++t) {
            if (Clock::now() >= deadline) {
                e.interrupted = true;
                break;
            }
            Point nativeAfter{position.x, plan.at(t) ? 10.f : 0.f};
            position = completeTravelStep(position, nativeAfter, {1, 0}, false);
            if (position.x >= 4 && position.x <= 6 && position.y < 5) {
                e.died = true;
                break;
            }
            ++e.survived;
        }
        return e;
    };
    c.reset();
    d = c.decide(cfg, false, detachedCorridor);
    require(d.hasValidatedInput && d.predictedTicks == 48,
            "detached forecast must encounter and cross the forward obstacle");
    require(d.plan.at(3), "forecast must request jump before crossing the obstacle");
    auto reject = [](Plan const &, Clock::time_point) {
        Evaluation e;
        e.died = true;
        return e;
    };
    d = c.decide(cfg, true, reject);
    require(!d.hasValidatedInput && d.down, "all candidates dead: do not invent safe input");
    auto future = std::vector<float>(48, 1000000);
    d = c.decide(cfg, false, reject, future);
    require(!d.hasValidatedInput, "GPU priority must never bypass verification");
    auto immediate = [](Plan const &, Clock::time_point) {
        Evaluation e;
        e.interrupted = true;
        return e;
    };
    d = c.decide(cfg, true, immediate);
    require(!d.hasValidatedInput && d.down, "zero evaluated steps cannot authorize an input");
    c.reset();
    auto a = c.propose(cfg, false);
    c.reset();
    auto b = c.propose(cfg, false);
    require(a.size() == b.size(), "reset candidate count");
    for (size_t i = 0; i < a.size(); ++i)
        require(a[i].buttons == b[i].buttons, "repeatable reset");
    c.advance(10000);
    c.reset();
    auto busy = [](Plan const &p, Clock::time_point deadline) {
        Evaluation e;
        while (Clock::now() < deadline) {
            ++e.survived;
            e.survived %= p.length + 1;
        }
        e.interrupted = true;
        return e;
    };
    cfg.budgetMs = .2;
    auto start = Clock::now();
    d = c.decide(cfg, false, busy);
    double ms = std::chrono::duration<double, std::milli>(Clock::now() - start).count();
    require(d.candidates <= 3, "deadline must stop candidate expansion");
    std::cout << "PASS: grounded hazard reflex, joined/reverse ledge events, delayed takeoff "
                 "priority, landing "
                 "verification gate, "
                 "unique CPU/GPU proposals, safe continuation early exit, cube pulse/live forecast "
                 "agreement, no flight hold, continuous-mode/dash hold, "
                 "mandatory release candidates/dead-end fallback, stable first-action tie, hazard "
                 "landing/clearance/swept/reverse guard, "
                 "missed native hazard rejection, command/native time units, live timing "
                 "calibration, grounded jump, "
                 "detached travel, native travel preservation, sideways/reverse motion, "
                 "direct input/resynchronization, forward obstacle, first-action coverage, "
                 "multi-switch route, dead-end fallback, "
                 "GPU validation gate, interrupted rollout, deterministic reset, deadline ("
              << ms << " ms)\n";
}
