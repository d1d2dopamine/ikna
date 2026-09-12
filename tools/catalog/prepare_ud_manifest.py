#!/usr/bin/env python3
"""Create a hash-pinned Part 3 morphology manifest from audited UD checkouts."""
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_REGISTRY = HERE / "sources" / "catalogue-v2-ud-production.json"
LICENCE_NAME = "CC BY-SA 4.0"
LICENCE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"


def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024), b''): h.update(block)
    return h.hexdigest()


def main(argv=None):
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--root', required=True, help='folder containing UD_* checkouts')
    ap.add_argument('--langs', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--registry', default=str(DEFAULT_REGISTRY))
    args=ap.parse_args(argv)
    config=json.loads(Path(args.registry).read_text(encoding='utf-8'))
    if config.get('version') != 1: raise ValueError('unsupported production UD registry')
    release=config['udRelease']
    selected=[x.strip().lower() for x in args.langs.split(',') if x.strip()]
    datasets=[]
    out_path=Path(args.out).resolve(); out_path.parent.mkdir(parents=True, exist_ok=True)
    for lang in selected:
        row=config['datasets'].get(lang)
        if not row: continue
        if row['licence'] != 'CC-BY-SA-4.0': raise ValueError('%s is not in the audited permissive set' % lang)
        repo=row['repo']; folder=Path(args.root).resolve()/repo
        files=sorted(folder.glob('*.conllu'))
        if not files: raise ValueError('no CoNLL-U files in %s' % folder)
        datasets.append({
            'id':'ud-2.18-%s-%s' % (lang, repo.removeprefix('UD_').lower().replace('_','-')),
            'sourceFamily':'universal-dependencies','kind':'ud','lang':lang,
            'sourceVersion':release,
            'sourceUrl':'https://github.com/UniversalDependencies/%s/tree/%s' % (repo, release),
            'licence':{'id':'CC-BY-SA-4.0','name':LICENCE_NAME,'url':LICENCE_URL},
            'attribution':row['attribution'],
            'files':[{'path':str(path.relative_to(out_path.parent)), 'sha256':sha256(path)} for path in files],
        })
    if not datasets: raise ValueError('no audited UD datasets selected')
    out={'manifestVersion':1,'ruleVersion':1,'datasets':datasets}
    out_path.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('wrote %d datasets to %s' % (len(datasets), out_path))
    return 0
if __name__=='__main__': raise SystemExit(main())
