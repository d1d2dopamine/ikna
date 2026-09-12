#!/usr/bin/env python3
"""Print the bounded WikiMatrix acquisition plan used by Catalogue v2."""
from __future__ import annotations
import argparse

BASE='https://dl.fbaipublicfiles.com/laser/WikiMatrix/v1'

def main(argv=None):
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--langs', required=True)
    ap.add_argument('--hub', default='en')
    args=ap.parse_args(argv)
    langs=sorted({x.strip().lower() for x in args.langs.split(',') if x.strip()})
    hub=args.hub.strip().lower()
    if hub not in langs: raise SystemExit('hub language %s is not in --langs' % hub)
    for other in langs:
        if other == hub: continue
        first,second=sorted((hub,other))
        name='WikiMatrix.%s-%s.tsv.gz' % (first,second)
        print('%s\t%s\t%s/%s' % (first,second,BASE,name))
    return 0
if __name__=='__main__': raise SystemExit(main())
