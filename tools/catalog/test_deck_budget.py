#!/usr/bin/env python3
"""Budget boundary, historical replay and saved-evidence bounds."""
import contextlib
import io

import build_catalogue_v2
import catalogue_core as core
import everyday_selection
import selection_experiment
import supply_census
import supply_census_shards
from deck_budget_impact import impact


def main():
    common = ["--candidates", "in.gz", "--json", "r.json", "--markdown", "r.md",
              "--preview", "p.gz", "--staging", "s.db"]
    cases = [(selection_experiment.parser(), common),
             (build_catalogue_v2.parser(), ["--candidates", "in.gz", "--out", "out"]),
             (everyday_selection.parser(), ["--pool", "in.gz", "--pool-report", "pool.json",
                                            "--json", "r.json", "--markdown", "r.md", "--preview", "p.gz",
                                            "--samples", "s.md", "--staging", "s.db"])]
    # All other parser defaults use the same type and ceiling, including shards.
    for parser in (supply_census.parser(), supply_census_shards.parser()):
        actions = list(parser._actions)
        for action in parser._actions:
            if hasattr(action, "choices") and isinstance(action.choices, dict):
                for child in action.choices.values():
                    if hasattr(child, "_actions"):
                        actions.extend(child._actions)
        budgets = [a for a in actions if a.dest == "max_deck"]
        assert budgets and all(a.default == 12000 and a.type is core.v2_deck_budget for a in budgets)
    for parser, required in cases:
        assert parser.parse_args(required).max_deck == 12000
        assert parser.parse_args(required + ["--max-deck", "8000"]).max_deck == 8000
        for value in ("0", "12001", "no", "1.5"):
            with contextlib.redirect_stderr(io.StringIO()):
                try:
                    parser.parse_args(required + ["--max-deck", value])
                except SystemExit as exc:
                    assert exc.code == 2
                else:
                    raise AssertionError("invalid budget accepted")
    report = {"publicationSafe": False, "policy": {"maxTargetsPerLevel": 8000}, "decks": [
        {"deckId": "capped", "selectedTargets": 8000, "budgetRejected": 123, "decision": "publish"},
        {"deckId": "thin", "selectedTargets": 51, "budgetRejected": 0, "decision": "publish-thin"},
        {"deckId": "omit", "selectedTargets": 32, "budgetRejected": 0, "decision": "omit-below-minimum"}]}
    result = impact(report, 12000)
    assert result["includedMembershipsRange"] == [8051, 8174]
    assert result["below1000UnaffectedByBudget"] == 2
    assert impact(report, 8000)["additionalMembershipsRange"] == [0, 0]
    report["decks"][1]["budgetRejected"] = 1
    try:
        impact(report, 12000)
    except ValueError:
        pass
    else:
        raise AssertionError("invalid evidence accepted")
    print("Catalogue v2 deck budget contracts: OK")


if __name__ == "__main__":
    main()
