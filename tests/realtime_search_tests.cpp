#include "realtime_search.hpp"
#include "input_motion.hpp"
#include "flight_certificate.hpp"
#include "landing_schedule.hpp"
#include "verified_input_queue.hpp"
#include "trace_geometry.hpp"
#include <iostream>
#include <stdexcept>

using namespace agb;
void require(bool value, char const *message) {
    if (!value) throw std::runtime_error(message);
}

// Independent tick integration: this is an oracle fixture, not GD physics.
// Constants are fixture values and must never be promoted to game constants.
struct Body { float x = 0, y = 0, vy = 0; bool ground = true; };
struct Oracle {
    Body root;
    bool cube = false, dual = false, corruptCold = false, corruptInteractions = false;
    int boundary = -1, calls = 0, coldCalls = 0;
    float corridorWidth = 9;
    std::vector<int> requestedLengths;
    static float center(float x) {
        float phase = std::fmod(x, 64.f);
        if (phase < 16) return 0;
        if (phase < 32) return (phase - 16) * .4f;
        if (phase < 48) return (48 - phase) * .4f;
        return 0;
    }
    bool step(Body &b, bool down) const {
        if (!cube) {
            b.x += 1; b.y += down ? 1 : -1; b.vy = down ? 1 : -1;
            return std::abs(b.y - center(b.x)) < corridorWidth;
        }
        if (down && b.ground) { b.vy = 1.8f; b.ground = false; }
        auto previous = b;
        b.x += 1;
        if (!b.ground) { b.vy -= .15f; b.y += b.vy; }
        bool floor = b.x < 14 || b.x >= 22;
        if (!floor && b.ground) b.ground = false;
        if (floor && b.vy <= 0 && previous.y >= 0 && b.y <= 0) {
            b.y = 0; b.vy = 0; b.ground = true;
        }
        return b.y > -6;
    }
    Evaluation prefix(Plan const &p, Clock::time_point deadline, bool memo) {
        ++calls; coldCalls += !memo; requestedLengths.push_back(p.length);
        Evaluation e; e.hasRealized = true; e.clearance = e.nearClearance = 100;
        Body b = root;
        if (!cube) b.ground = false;
        CubePulse pulse; FlightCertificate flight(b.ground);
        ModeState mode; mode.mode = cube ? PlannerMode::Cube : PlannerMode::Wave;
        auto sample = [&] {
            for (int i = 0; i < (dual ? 2 : 1); ++i) {
                e.traces[i].push_back({b.x, i ? -b.y : b.y});
                e.modeTraces[i].push_back(mode);
                MotionSample m; m.velocity = i ? -b.vy : b.vy;
                m.grounded = m.canJump = b.ground;
                m.usedInteractions = corruptInteractions && !memo ? 7 : 0;
                e.motions[i].push_back(m);
            }
        };
        sample(); bool last = false; e.buttonChanges = 0;
        for (int t = 0; t < p.length; ++t) {
            if (Clock::now() >= deadline) { e.interrupted = true; break; }
            if (b.ground && !pulse.mustRelease()) e.groundedActions.push_back(t);
            if (!cube || b.ground) e.controlActions.push_back({t, mode.mode});
            bool wasGrounded = b.ground;
            bool down = cube ? pulse.command(p.at(t), true, b.ground) : p.at(t);
            e.realized.buttons[t] = down;
            e.buttonChanges += down != last; last = down;
            if (!step(b, down)) { e.died = e.nativeDeath = true; break; }
            if (wasGrounded && !b.ground && !down && e.supportLossTick < 0) e.supportLossTick = t;
            e.cubeTakeoffs += cube && down && wasGrounded && !b.ground;
            if (cube) flight.observe(wasGrounded, b.ground, down);
            if (!down && wasGrounded && b.ground && e.groundedPrefixTicks == t) ++e.groundedPrefixTicks;
            ++e.survived; e.realized.length = e.survived;
            e.endpointClearance = cube ? 100 : corridorWidth - std::abs(b.y - center(b.x));
            sample();
            if (boundary > 0 && e.survived >= boundary) { e.boundary = true; break; }
        }
        e.requiresLanding = cube && flight.required;
        e.landingVerified = cube && flight.required && flight.verified();
        e.firstActionVerified = !cube || flight.verified();
        if (!memo && corruptCold && e.traces[0].size() > 1) e.traces[0][1].y += 1;
        return e;
    }
};
Config config(bool cube = false) {
    Config c; c.horizon = cube ? 48 : 32; c.candidates = 96; c.budgetMs = 8;
    c.tickSeconds = 1. / 60; c.plannerMode = cube ? PlannerMode::Cube : PlannerMode::Wave;
    c.cubePulsePlanning = cube; return c;
}
// Independent obstacle fixture: an early jump must remain above the whole
// obstacle, whereas a very late legal jump has much less vertical clearance.
struct JumpOracle {
    int calls = 0;
    Evaluation prefix(Plan const &p, Clock::time_point end, bool) {
        ++calls;
        Evaluation e; e.hasRealized = true;
        e.clearance = e.nearClearance = e.landingMargin = 120;
        Body b; CubePulse pulse; LandingSchedule schedule(p.landingDelay, true);
        FlightCertificate flight;
        ModeState mode; mode.mode = PlannerMode::Cube;
        auto sample = [&] {
            e.traces[0].push_back({b.x, b.y}); e.modeTraces[0].push_back(mode);
            // Native on-ground and secondary contact flags can differ.
            MotionSample m; m.canJump = b.ground; m.grounded = false; m.velocity = b.vy;
            e.motions[0].push_back(m);
        };
        sample(); bool held = false; e.buttonChanges = 0;
        for (int t = 0; t < p.length; ++t) {
            if (Clock::now() >= end) { e.interrupted = true; break; }
            bool ground = b.ground;
            if (ground && !pulse.mustRelease()) {
                e.groundedActions.push_back(t); e.controlActions.push_back({t, mode.mode});
            }
            bool request = schedule.request(p.at(t), ground, pulse.mustRelease(), true);
            bool down = pulse.command(request, true, ground); schedule.sent(down, ground);
            e.realized.buttons[t] = down; e.buttonChanges += down != held; held = down;
            if (down && ground) { b.vy = 2.2f; b.ground = false; ++e.cubeTakeoffs; }
            b.x += 1;
            if (!b.ground) { b.vy -= .08f; b.y += b.vy; }
            if (b.y <= 0 && b.vy <= 0) { b.y = b.vy = 0; b.ground = true; }
            if (b.x >= 75 && b.x <= 90 && b.y < 12) { e.died = e.nativeDeath = true; break; }
            float dx = std::max({75 - b.x, b.x - 90, 0.f});
            float dy = std::max(b.y - 12, 0.f);
            float margin = std::hypot(dx, dy);
            e.endpointClearance = margin; e.clearance = std::min(e.clearance, margin);
            if (!b.ground) e.landingMargin = std::min(e.landingMargin, margin);
            if (!ground && b.ground) {
                ++e.landingCount; if (e.firstLandingTick < 0) e.firstLandingTick = t;
            }
            flight.observe(ground, b.ground, down);
            if (!down && ground && b.ground && e.groundedPrefixTicks == t) ++e.groundedPrefixTicks;
            ++e.survived; e.realized.length = e.survived; sample();
        }
        e.requiresLanding = flight.required;
        e.landingVerified = flight.required && flight.verified();
        e.firstActionVerified = flight.verified();
        return e;
    }
};
int main() {
    {
        RealtimeSearch search; Oracle oracle; oracle.corridorWidth = 10000;
        auto c = config(); c.horizon = 768; c.candidates = 16;
        auto d = search.decide(c, false, oracle);
        require(d.hasValidatedInput && d.forecastTicks == 768, "open route never reached far horizon");
        require(d.certificateTicks < 32 && d.selected.survived == d.certificateTicks,
                "short control replay was confused with the long forecast");
        require(oracle.calls <= 8, "open scene spends its budget expanding tiny branches");
        VerifiedInputQueue queue; std::array<ModeState, 2> states{d.selected.modeTraces[0][0], {}};
        require(queue.seed(d, states, false, c.tickSeconds), "far route could not seed checked controls");
        queue.consume(d.down); auto suffix = queue.continuation();
        require(suffix.forecastTicks == 767 && suffix.forecastPlan.length == 767 &&
                suffix.certificateTicks == d.certificateTicks - 1, "queue discarded the long forecast suffix");
        require(oracle.step(oracle.root, d.down), "open fixture step died"); search.advance();
        int before = oracle.calls;
        auto next = search.decide(c, d.down, oracle);
        require(next.forecastTicks == 768 && oracle.calls - before <= 8, "retained route loses far lookahead on the next command");
        std::cout << "PASS: 768-tick forecast, short independent control replay and retained queue suffix\n";
    }
    {
        RealtimeSearch search; JumpOracle oracle; auto c = config(true);
        c.tickSeconds = 1. / 240; c.horizon = 144;
        c.jumpEventCount = 6; c.jumpEvents = {68, 60, 52, 44, 36, 28};
        auto d = search.decide(c, false, oracle);
        require(d.hasValidatedInput && d.forecastTicks >= 144 && d.forecast.landingVerified,
                "coordinate search did not find a complete obstacle route");
        int first = -1;
        for (int t = 0; t < d.forecastPlan.length; ++t) if (d.forecastPlan.at(t)) { first = t; break; }
        require(first >= 35 && first <= 60, "selected takeoff is needlessly early or dangerously late");
        require(d.forecast.traces[0][first].x < 61 && d.forecast.landingMargin > 8,
                "takeoff coordinate lacks clearance before the obstacle");
        require(d.jumpWindowTicks >= 3, "takeoff did not survive neighbouring native timing samples");
        auto replay = oracle.prefix(d.forecastPlan, Clock::time_point::max(), false);
        require(!replay.died && replay.survived == d.forecastPlan.length,
                "coordinate-based obstacle route does not reproduce");
        std::cout << "PASS: obstacle takeoff coordinate selected well before contact with flight clearance\n";
    }
    {
        RealtimeSearch search; Oracle oracle; oracle.cube = true; oracle.boundary = 2;
        auto d = search.decide(config(true), false, oracle);
        require(!d.down && !d.hasValidatedInput, "one checked step authorized an unproved cube launch");
    }
    {
        std::vector<Point> shortPath{{0, 0}, {1, 1}};
        auto shortLine = traceSegments(shortPath, {});
        require(shortLine.size() == 1 && shortLine.back().b.x == 1, "short trace was invisible");
        std::vector<Point> corner{{0, 0}, {1, 1}, {2, 2}, {3, 1}, {4, 0}, {50, 20}, {51, 21}};
        auto segments = traceSegments(corner, {5});
        require(segments.size() == 3 && segments[0].b.x == 2 && segments[1].b.x == 4 &&
                segments[2].a.x == 50 && segments[2].b.x == 51,
                "trace removed a turn, bridged a teleport or lost the final segment");
        std::cout << "PASS: smooth native trace preserves corners, short paths, teleports and endpoint\n";
    }
    {
        RealtimeSearch search; Oracle oracle;
        auto d = search.decide(config(), false, oracle);
        require(d.hasValidatedInput || d.recoveryInput, "wave: no checked action");
        require(oracle.coldCalls > 0, "selected input was not independently replayed");
        require(oracle.requestedLengths.size() >= 2 && oracle.requestedLengths[0] == 1 && oracle.requestedLengths[1] == 1,
                "both immediate actions need equal one-tick root work");
        require(d.firstActionCandidates[0] && d.firstActionCandidates[1], "root diversity lost");
        require(d.candidates <= 96 && oracle.calls <= 98, "evaluation cap exceeded");
        require(d.certificateTicks <= d.selected.survived, "unrun future was certified");
    }
    {
        RealtimeSearch search; Oracle oracle; bool held = false; int changes = 0;
        auto c = config();
        for (int tick = 0; tick < 80; ++tick) {
            auto d = search.decide(c, held, oracle);
            require(d.hasValidatedInput || d.recoveryInput, "closed-loop wave lost both actions");
            changes += held != d.down; held = d.down;
            require(oracle.step(oracle.root, held), "closed-loop wave hit the changing corridor");
            search.advance();
        }
        require(changes >= 4, "wave needed several future changes, not a constant hold");
        std::cout << "PASS: 80 live commands in a changing wave corridor\n";
    }
    {
        RealtimeSearch search; Oracle oracle; oracle.cube = true;
        auto d = search.decide(config(true), false, oracle);
        require(d.hasValidatedInput && d.selected.landingVerified, "pit: no complete landing route");
        require(d.selected.requiresLanding && !d.selected.died, "pit coast was mislabeled as a crossing");
        Body replay = oracle.root;
        for (int t = 0; t < d.plan.length; ++t)
            require(oracle.step(replay, d.plan.at(t)), "chosen pit route did not reproduce");
        require(replay.x >= 22 && replay.ground, "pit route ended as a falling body");
        std::cout << "PASS: ballistic pit crossing and independent landing replay\n";
    }
    {
        struct SlowOracle {
            Oracle body; int calls = 0;
            Evaluation prefix(Plan const &p, Clock::time_point end, bool memo) {
                if (++calls <= 2) return body.prefix(p, end, memo);
                // Deliberately expire future work after a checked release.
                while (Clock::now() < end) {}
                Evaluation e; e.interrupted = true; return e;
            }
        } oracle;
        oracle.body.cube = true;
        RealtimeSearch search; auto c = config(true); c.forceRelease = true; c.budgetMs = 1;
        auto d = search.decide(c, true, oracle);
        require(d.recoveryInput && !d.down && d.selected.survived == 1 && !d.hasValidatedInput,
                "expired future work discarded the checked release command");
    }
    for (bool interaction : {false, true}) {
        RealtimeSearch search; Oracle oracle;
        oracle.corruptCold = !interaction; oracle.corruptInteractions = interaction;
        auto d = search.decide(config(), false, oracle);
        require(!d.hasValidatedInput && !d.recoveryInput && d.cacheMismatches > 0,
                "cold-replay divergence authorized input");
    }
    {
        RealtimeSearch search; Oracle oracle; oracle.dual = true;
        auto c = config(); c.dual = true; c.secondaryMode = PlannerMode::Wave;
        auto d = search.decide(c, false, oracle);
        require(d.hasValidatedInput || d.recoveryInput, "dual trajectory was discarded");
        require(d.selected.traces[1].size() > 1, "dual command checked only player one");
    }
    {
        RealtimeSearch search; Oracle oracle;
        auto c = config(); c.forceRelease = true;
        auto d = search.decide(c, true, oracle);
        require(!d.down && d.firstActionCandidates[1] == 0, "mandatory release was overridden");
        c.sharedDeadline = Clock::now(); int before = oracle.calls;
        auto expired = search.decide(c, true, oracle);
        require(expired.exhausted && !expired.hasValidatedInput && !expired.recoveryInput && oracle.calls == before,
                "expired deadline still simulated or certified input");
    }
    {
        RealtimeSearch search; Oracle oracle; oracle.boundary = 3;
        auto d = search.decide(config(), false, oracle);
        require(!d.hasValidatedInput && d.recoveryInput && d.plan.length == 1,
                "unsupported world boundary became a long route proof");
    }
    std::cout << "PASS: cold replay, consumed interactions, dual, releases, caps and boundaries\n";
}
