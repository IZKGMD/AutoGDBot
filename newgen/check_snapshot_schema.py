#!/usr/bin/env python3
"""Reject snapshot member lists that omit, duplicate or reorder binding storage."""
import argparse
import re
from pathlib import Path


def check(bindings):
    text = (Path(bindings) / 'bindings/2.2081/GeometryDash.bro').read_text()
    section = text.split('class GJGameState {', 1)[1].split('\n}', 1)[0]
    expected = re.findall(r'^\s+[^\n;]+\b(m_\w+)\s*;[^\n]*$', section, re.M)
    actual = re.findall(r'^AGB_STATE_MEMBER\((m_\w+)\)$',
                        Path(__file__).with_name('game_state_members.inc').read_text(), re.M)
    if not expected or len(actual) != len(set(actual)) or actual != expected:
        raise RuntimeError('Snapshot storage does not exactly match the pinned GD bindings')
    print('Snapshot schema: all ' + str(len(actual)) + ' canonical members covered')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bindings', type=Path)
    check(parser.parse_args().bindings)
