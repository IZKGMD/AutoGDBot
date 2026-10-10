#pragma once
#include <Geode/Geode.hpp>

namespace agb {
// Generated alias unions make GJGameState's implicit assignment unsuitable
// on Android. Store canonical fields separately and use typed deep copies of
// containers; never memcpy the native game state or its owning pointers.
struct GameStateSnapshot {
#define AGB_STATE_MEMBER(name) decltype(GJGameState::name) name{};
#include "game_state_members.inc"
#undef AGB_STATE_MEMBER
    GameStateSnapshot() = default;
    explicit GameStateSnapshot(GJGameState const &state) { capture(state); }
    void capture(GJGameState const &state) {
#define AGB_STATE_MEMBER(name) name = state.name;
#include "game_state_members.inc"
#undef AGB_STATE_MEMBER
    }
    void restore(GJGameState &state) const {
#define AGB_STATE_MEMBER(name) state.name = name;
#include "game_state_members.inc"
#undef AGB_STATE_MEMBER
    }
};
}
