# Everyday coverage diagnosis — run 37674241928

Report-derived inventory of all 330 pair/level combinations is in `COVERAGE.json`.
The gzip reports preserve the supplied report bytes exactly; hashes are in that inventory.
No complete selected-material file or raw pool was reprocessed locally.

## Measured causes

73 decks are below 1,000 entries, including two omitted decks. Of these, 53
already have fewer than 1,000 eligible exact targets; 20 fall below the threshold
during distinct-context allocation. None loses targets to the 8,000 budget.
48 of the 73 involve Korean. There are no missing direct pairs in the provided pool.

Eligible counts are post-sieve supply, not raw corpus vocabulary. Collision loss
means every retained evidence context for a target was already assigned to another
target in the same deck. The policy reserves primary and alternate contexts. It is
greedy and evidence is bounded; this report does not establish optimal allocation.

## Two omissions

Polish with Korean meanings has only 94 directed candidate rows in this snapshot.
21 fail source-sentence length; one has no usable target. Its remaining target
evidence provides 210 exact targets across frequency levels. The beginner level
keeps 51 of 117 targets; middle keeps 32 of 46; advanced keeps 30 of 47.
There is no budget rejection. The middle/advanced exclusions therefore combine
scarce direct sentence supply, context-allocation policy, and the minimum of 40.
They are not empty source pairs, Japanese guard failures, or an 8,000-cap problem.

## All below-threshold decks

Direction means language studied -> language of meanings. `Selected` includes
30/32 pre-omission entries for excluded decks; these 62 are absent from the full
included file. `Eligible` is before distinct-context assignment and the cap.

| Deck | Candidate rows (pair) | Eligible | Context loss | Selected | Decision |
| --- | ---: | ---: | ---: | ---: | --- |
| de-ko-everyday-advanced | 3312 | 1102 | 304 | 798 | publish-thin |
| de-ko-everyday-beginner | 3312 | 1195 | 275 | 920 | publish-thin |
| de-ko-everyday-middle | 3312 | 963 | 199 | 764 | publish-thin |
| es-ko-everyday-advanced | 1103 | 481 | 134 | 347 | publish-thin |
| es-ko-everyday-beginner | 1103 | 847 | 389 | 458 | publish-thin |
| es-ko-everyday-middle | 1103 | 475 | 119 | 356 | publish-thin |
| fr-ko-everyday-advanced | 2180 | 668 | 138 | 530 | publish-thin |
| fr-ko-everyday-beginner | 2180 | 1037 | 340 | 697 | publish-thin |
| fr-ko-everyday-middle | 2180 | 669 | 117 | 552 | publish-thin |
| it-ko-everyday-advanced | 214 | 70 | 17 | 53 | publish-thin |
| it-ko-everyday-beginner | 214 | 240 | 129 | 111 | publish-thin |
| it-ko-everyday-middle | 214 | 72 | 11 | 61 | publish-thin |
| it-pl-everyday-advanced | 2747 | 947 | 217 | 730 | publish-thin |
| it-pl-everyday-beginner | 2747 | 1145 | 291 | 854 | publish-thin |
| it-pl-everyday-middle | 2747 | 1003 | 188 | 815 | publish-thin |
| ja-ko-everyday-advanced | 2262 | 623 | 154 | 469 | publish-thin |
| ja-ko-everyday-beginner | 2262 | 1004 | 296 | 708 | publish-thin |
| ja-ko-everyday-middle | 2262 | 861 | 209 | 652 | publish-thin |
| ko-de-everyday-beginner | 3312 | 1209 | 326 | 883 | publish-thin |
| ko-es-everyday-advanced | 1103 | 1094 | 506 | 588 | publish-thin |
| ko-es-everyday-beginner | 1103 | 751 | 325 | 426 | publish-thin |
| ko-es-everyday-middle | 1103 | 615 | 169 | 446 | publish-thin |
| ko-fr-everyday-beginner | 2180 | 977 | 330 | 647 | publish-thin |
| ko-fr-everyday-middle | 2180 | 949 | 201 | 748 | publish-thin |
| ko-it-everyday-advanced | 214 | 169 | 61 | 108 | publish-thin |
| ko-it-everyday-beginner | 214 | 204 | 95 | 109 | publish-thin |
| ko-it-everyday-middle | 214 | 97 | 20 | 77 | publish-thin |
| ko-ja-everyday-beginner | 2262 | 1086 | 353 | 733 | publish-thin |
| ko-ja-everyday-middle | 2262 | 1189 | 312 | 877 | publish-thin |
| ko-pl-everyday-advanced | 94 | 74 | 29 | 45 | publish-thin |
| ko-pl-everyday-beginner | 94 | 127 | 72 | 55 | publish-thin |
| ko-pl-everyday-middle | 94 | 60 | 18 | 42 | publish-thin |
| ko-pt-everyday-advanced | 214 | 167 | 63 | 104 | publish-thin |
| ko-pt-everyday-beginner | 214 | 241 | 114 | 127 | publish-thin |
| ko-pt-everyday-middle | 214 | 117 | 24 | 93 | publish-thin |
| ko-ru-everyday-advanced | 248 | 218 | 95 | 123 | publish-thin |
| ko-ru-everyday-beginner | 248 | 257 | 117 | 140 | publish-thin |
| ko-ru-everyday-middle | 248 | 143 | 40 | 103 | publish-thin |
| ko-zh-everyday-beginner | 2842 | 1134 | 301 | 833 | publish-thin |
| pl-it-everyday-beginner | 2747 | 1159 | 334 | 825 | publish-thin |
| pl-it-everyday-middle | 2747 | 1147 | 256 | 891 | publish-thin |
| pl-ko-everyday-advanced | 94 | 47 | 17 | 30 | omit-below-minimum |
| pl-ko-everyday-beginner | 94 | 117 | 66 | 51 | publish-thin |
| pl-ko-everyday-middle | 94 | 46 | 14 | 32 | omit-below-minimum |
| pl-pt-everyday-advanced | 1582 | 1811 | 972 | 839 | publish-thin |
| pl-pt-everyday-beginner | 1582 | 988 | 363 | 625 | publish-thin |
| pl-pt-everyday-middle | 1582 | 864 | 235 | 629 | publish-thin |
| pl-zh-everyday-advanced | 1039 | 912 | 354 | 558 | publish-thin |
| pl-zh-everyday-beginner | 1039 | 847 | 375 | 472 | publish-thin |
| pl-zh-everyday-middle | 1039 | 637 | 153 | 484 | publish-thin |
| pt-ko-everyday-advanced | 214 | 54 | 12 | 42 | publish-thin |
| pt-ko-everyday-beginner | 214 | 288 | 163 | 125 | publish-thin |
| pt-ko-everyday-middle | 214 | 72 | 17 | 55 | publish-thin |
| pt-pl-everyday-advanced | 1582 | 981 | 374 | 607 | publish-thin |
| pt-pl-everyday-beginner | 1582 | 1039 | 405 | 634 | publish-thin |
| pt-pl-everyday-middle | 1582 | 850 | 267 | 583 | publish-thin |
| pt-zh-everyday-advanced | 1540 | 446 | 103 | 343 | publish-thin |
| pt-zh-everyday-beginner | 1540 | 915 | 341 | 574 | publish-thin |
| pt-zh-everyday-middle | 1540 | 579 | 119 | 460 | publish-thin |
| ru-ko-everyday-advanced | 248 | 137 | 58 | 79 | publish-thin |
| ru-ko-everyday-beginner | 248 | 286 | 162 | 124 | publish-thin |
| ru-ko-everyday-middle | 248 | 109 | 24 | 85 | publish-thin |
| zh-it-everyday-advanced | 4742 | 1300 | 363 | 937 | publish-thin |
| zh-it-everyday-beginner | 4742 | 1258 | 313 | 945 | publish-thin |
| zh-ko-everyday-advanced | 2842 | 1641 | 725 | 916 | publish-thin |
| zh-ko-everyday-beginner | 2842 | 1180 | 347 | 833 | publish-thin |
| zh-ko-everyday-middle | 2842 | 1362 | 444 | 918 | publish-thin |
| zh-pl-everyday-advanced | 1039 | 611 | 184 | 427 | publish-thin |
| zh-pl-everyday-beginner | 1039 | 932 | 453 | 479 | publish-thin |
| zh-pl-everyday-middle | 1039 | 738 | 228 | 510 | publish-thin |
| zh-pt-everyday-advanced | 1540 | 523 | 153 | 370 | publish-thin |
| zh-pt-everyday-beginner | 1540 | 964 | 407 | 557 | publish-thin |
| zh-pt-everyday-middle | 1540 | 674 | 164 | 510 | publish-thin |

## Recompute from saved reports

Decompress `EVERYDAY-SELECTION.json.gz` and `QUALITY.json.gz`. For each deck:

```text
eligibleTargets = selectedTargets + contextCollisionTargetsRejected + budgetRejected
below-threshold = selectedTargets < 1000
supply-below-threshold = below-threshold and eligibleTargets < 1000
allocation-crossed-threshold = below-threshold and eligibleTargets >= 1000
included = decision in {publish, publish-thin}
```

Pair statistics repeat in each of the three level rows: count them once per
directed pair, not three times. Near-duplicate counts concern alternate contexts,
not target losses. Boundary deferrals count choice entries, not bad cards.

## Checks executed

ZIP integrity; exact report hashes against QUALITY/admitted input; report/input
identity agreement; 3,280 valid preview rows; unique 330-deck/110-pair inventory;
per-deck balance formula; per-pair eligible totals; aggregate summary reconciliation;
928,224 pre-omission versus 928,162 included entries; zero below-threshold budget
loss. Source inspection: `selection_experiment.py` pair sieve/context reservation,
`build_catalogue_v2.py` target eligibility, `catalogue_core.py` frequency levels.
No new workflow, complete-material census, build, semantic certification or freeze.
