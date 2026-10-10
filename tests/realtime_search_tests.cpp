#include "realtime_search.hpp"
#include "input_motion.hpp"
#include "flight_certificate.hpp"
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
            return std::abs(b.y - center(b.x)) < 9;
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
            e.endpointClearance = cube ? 100 : 9 - std::abs(b.y - center(b.x));
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
int main() {
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
