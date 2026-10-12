#!/usr/bin/env python3
"""Bounded pl<->ko metadata discovery; no corpus-file/row requests or text generation.
Metadata can contain embedded examples; raw responses are not deliverables."""
import argparse
import collections
import csv
import datetime
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request

HERE = Path(__file__).resolve().parent
QUERIES = (
 ('opus', 'https://opus.nlpl.eu/opusapi?source=pl&target=ko&preprocessing=moses&version=latest'),
 ('hf-search', 'https://huggingface.co/api/datasets?search=polish-korean&limit=30&full=true'),
 ('hf-tags', 'https://huggingface.co/api/datasets?filter=language%3Apl&filter=language%3Ako&filter=task_categories%3Atranslation&limit=30&full=true'),
)
MAX_BYTES = 2 * 1024 * 1024
MAX_RECORDS = 500
POLICY = {
 'flores': ('reference-only', ['flores', 'fleurs']),
 'pud': ('reference-only', ['pud']),
 'massive': ('known-quality-nonadmission', ['massive']),
 'wikimatrix': ('known-quality-nonadmission', ['wikimatrix']),
 'wmt': ('rights-unresolved', ['wmt24', 'wmt24pp']),
 'mkqa': ('conditional-question-subset', ['mkqa']),
 'ntrex': ('rights-unresolved', ['ntrex']),
 'opensubtitles': ('rights-unresolved', ['opensubtitles']),
 'ted': ('rights-unresolved', ['ted2020', 'ted', 'tedtalks', 'qed']),
 'nikl': ('rights-unresolved', ['nikl', 'modu']),
 'paracrawl': ('rights-unresolved', ['paracrawl', 'multiparacrawl']),
 'ccmatrix': ('rights-unresolved', ['ccmatrix']),
 'nllb': ('rights-unresolved', ['nllb']),
 'tatoeba': ('accepted-family-needs-version-audit', ['tatoeba']),
 'globalvoices': ('article-provenance-gate', ['globalvoices']),
 'opus100': ('repackage-needs-component-lineage', ['opus100', 'opus-100']),
}

OBSERVATIONS = {
 'apple/hat': ('c12d1149d2cc2b5b1d5138cd16b9f0e855051e93', 'confirmed-machine-translated-output'),
 'vpermilp/nllb-200-distilled-600M-rust': ('8e52fee18a694d4351608ea0b51406c380b08153', 'model-card-not-verified-corpus'),
 'Nicolas-BZRD/English_French_Songs_Lyrics_Translation_Original': ('f2b64a358132305095e602ce11819d834344d90f', 'description-scoped-to-french-lyrics-not-verified-pl-ko'),
}
# Dated manual metadata/primary review; a changed revision is not auto-excluded.
# Evidence: docs/evidence/discovery-round42/PRIMARY-REVIEW.json.
def sha(b): return hashlib.sha256(b).hexdigest()
def write(path, obj): path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')
def clean(value):
    if value is None: return None
    if not isinstance(value, (str, int, float, bool)): return None
    s = str(value)
    return s[:500].replace('\r', ' ').replace('\n', ' ')

def family(name):
    # Name hints do not prove row ancestry. Unknown subsets remain visible.
    leaf = name.rsplit('/', 1)[-1].lower()
    tokens = re.split(r'[^a-z0-9]+', leaf)
    compact = re.sub(r'[^a-z0-9]', '', leaf)
    for key, (status, aliases) in POLICY.items():
        if any(a in tokens or compact == a.replace('-', '') or
               any(''.join(tokens[start:end]) == a.replace('-', '') for start in range(len(tokens)) for end in range(start+1,len(tokens)+1)) or
               any(t.startswith(a) and t[len(a):].isdigit() for t in tokens)
               for a in aliases):
            return key, status
    return None, 'needs-investigation'

def normalize(provider, payload):
    rows = payload.get('corpora') if provider == 'opus' and isinstance(payload, dict) else payload
    if not isinstance(rows, list): raise ValueError('metadata-list-missing')
    if len(rows) > MAX_RECORDS: raise ValueError('metadata-record-limit')
    out = []
    for r in rows:
        if not isinstance(r, dict): raise ValueError('metadata-record-not-object')
        if provider == 'opus':
            name = clean(r.get('corpus'))
            source, target = clean(r.get('source')), clean(r.get('target'))
            coverage = 'explicit-pair' if {source, target} == {'pl', 'ko'} else 'unverified'
            version = clean(r.get('version'))
            claims = [clean(r.get('license') or r.get('licence'))]
            url = clean(r.get('url'))
            tags = []
            counts = {k: clean(r.get(k)) for k in ('alignment_pairs', 'source_tokens', 'target_tokens', 'size') if k in r}
        else:
            name = clean(r.get('id') or r.get('_id'))
            raw_tags = r.get('tags', [])
            if not isinstance(raw_tags, list): raise ValueError('tags-not-list')
            tags = [t for t in raw_tags if isinstance(t, str) and len(t) <= 150]
            coverage = 'both-language-tags' if all('language:' + k in tags for k in ('pl', 'ko')) else 'unverified'
            version = clean(r.get('sha'))
            claims = [t[8:] for t in tags if t.startswith('license:')]
            url = 'https://huggingface.co/datasets/' + urllib.parse.quote(name or '', safe='/')
            counts = {}
        if not name: raise ValueError('dataset-identity-missing')
        lineage, status = family(name)
        observed = OBSERVATIONS.get(name)
        inspection = observed[1] if observed and observed[0] == version else None
        out.append({'provider': provider, 'dataset': name, 'version': version,
                    'pairEvidence': coverage, 'languageTags': sorted(t for t in tags if t.startswith('language:')),
                    'declaredLicenceClaims': [x for x in claims if x], 'metadataUrl': url,
                    'upstreamCountsNotCards': counts, 'familyHint': lineage, 'knownDecision': status,
                    'lineageEvidence': 'name-hint-only' if lineage else 'unknown',
                    'humanTranslationEvidence': 'confirmed-MT-output' if inspection == 'confirmed-machine-translated-output' else 'not-established-by-metadata',
                    'revisionBoundManualObservation': inspection,
                    'originalTextRights': 'requires-primary-evidence',
                    'newVersionOrSubsetMayChangeEvidence': True,
                    'sampleAuthorizedByMetadataAlone': False, 'productionAdmission': False})
    return out

class RestrictedRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('redirect-disabled-for-bounded-discovery')

def fetch(url):
    if url not in dict(QUERIES).values(): raise ValueError('non-allowlisted-query')
    req = urllib.request.Request(url, headers={'User-Agent': 'ikna-bounded-corpus-discovery/1', 'Accept': 'application/json'})
    with urllib.request.build_opener(RestrictedRedirect).open(req, timeout=30) as response:
        data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES: raise ValueError('metadata-byte-limit')
        json.loads(data)
        return data

def gaps(path):
    with path.open(encoding='utf-8-sig', newline='') as f:
        rows = [r for r in csv.DictReader(f) if {r.get('lang'), r.get('meaningLang')} == {'pl', 'ko'}]
    if len(rows) != 6 or len({r['deckId'] for r in rows}) != 6: raise ValueError('expected-six-gap-cells')
    return sorted(rows, key=lambda r: r['deckId'])

def rank(records):
    distinct = {}; duplicates = 0
    for r in records:
        # Only exact metadata identities collapse. Family hints never delete a lead.
        key = (r['provider'].split('-')[0], r['dataset'], r['version'], r['metadataUrl'])
        if key in distinct:
            duplicates += 1
            prior = distinct[key]
            prior['discoveredBy'] = sorted(set(prior['discoveredBy'] + [r['provider']]))
            prior['declaredLicenceClaims'] = sorted(set(prior['declaredLicenceClaims'] + r['declaredLicenceClaims']))
            prior['languageTags'] = sorted(set(prior['languageTags'] + r['languageTags']))
            if r['pairEvidence'] != 'unverified': prior['pairEvidence'] = r['pairEvidence']
        else:
            distinct[key] = dict(r, discoveredBy=[r['provider']])
    result = list(distinct.values())
    for r in result:
        # Investigation priority, never acceptance probability or a legal score.
        r['priorityComponents'] = {'pairEvidence': 30 if r['pairEvidence'] == 'explicit-pair' else (10 if r['pairEvidence'] == 'both-language-tags' else 0),
                                   'unreviewedFamily': 10 if r['familyHint'] is None else 0,
                                   'observedScopeMismatch': -40 if r.get('revisionBoundManualObservation') else 0}
        r['investigationPriority'] = sum(r['priorityComponents'].values())
    return sorted(result, key=lambda r: (-r['investigationPriority'], r['dataset'], r['provider'])), duplicates

def run(cache, out, gap_csv, replay=False):
    if out.exists(): raise ValueError('output-must-be-new')
    if cache.resolve() == out.resolve() or cache.resolve() in out.resolve().parents or out.resolve() in cache.resolve().parents:
        raise ValueError('cache-output-overlap')
    gap_rows = gaps(gap_csv)
    if not replay and cache.exists(): raise ValueError('live-cache-must-be-new')
    if replay and not cache.is_dir(): raise ValueError('replay-cache-missing')
    cache.mkdir(parents=True, exist_ok=replay); out.mkdir(parents=True)
    requests = []; records = []; start = time.monotonic()
    for provider, url in QUERIES:
        entry = {'provider': provider, 'url': url, 'mode': 'replay' if replay else 'live', 'utc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
        try:
            path = cache / (provider + '.json')
            data = path.read_bytes() if replay else fetch(url)
            if len(data) > MAX_BYTES: raise ValueError('metadata-byte-limit')
            parsed = normalize(provider, json.loads(data))
            if not replay: path.write_bytes(data)
            entry.update(status='ok', sizeBytes=len(data), sha256=sha(data), rows=len(parsed))
            records.extend(parsed)
        except Exception as exc:
            # No remote body or exception URL is leaked into reports.
            entry.update(status='failed', errorType=type(exc).__name__, httpStatus=getattr(exc, 'code', None))
        requests.append(entry)
    ranked, duplicates = rank(records)
    write(out / 'GAPS.json', {'csvSha256': sha(gap_csv.read_bytes()), 'cells': gap_rows,
                              'historicalCap': 8000, 'currentDefaultCap': 12000, 'thinBoundaryIsNotReleaseQuota': True})
    write(out / 'LEADS.json', ranked)
    write(out / 'REQUESTS.json', requests)
    summary = {'status': 'complete-metadata-discovery' if all(x['status']=='ok' for x in requests) else 'partial-metadata-discovery',
               'networkRequests': 0 if replay else len(QUERIES), 'automaticRedirectsEnabled': False, 'successfulQueries': sum(x['status']=='ok' for x in requests),
               'metadataBytes': sum(x.get('sizeBytes',0) for x in requests), 'rawRows': len(records), 'distinctLeads': len(ranked),
               'exactDuplicateRecords': duplicates, 'unknownFamilyLeads': sum(r['familyHint'] is None for r in ranked),
               'familyHints': dict(collections.Counter(r['familyHint'] or 'unknown' for r in ranked)),
               'corpusDownloads': 0, 'metadataMayContainEmbeddedExamples': True, 'sampledRows': 0, 'admittedCards': 0, 'generatedCardTexts': 0,
               'paidApiCalls': 0, 'elapsedSeconds': time.monotonic()-start,
               'limits': {'requests': 3, 'bytesPerResponse': MAX_BYTES, 'recordsPerResponse': MAX_RECORDS, 'socketTimeoutSeconds': 30, 'totalWallDeadlineEnforcedByTool': False},
               'limitations': ['Metadata badges do not establish original rights, human authorship, alignment or useful yield.',
                  'Unknown evidence remains visible for bounded primary-source investigation.',
                  'Name-based family hints are not content deduplication and do not reject genuinely new subsets.',
                  'Discovery covers only these queries and first HF page (limit30); no pagination, exhaustive-search or absence claim.',
                  'Repository language tags are not proof of direct pl-ko pairs; API counts are alignments, not cards.',
                  'No corpus archives or row endpoints are requested; full metadata may embed examples. Raw responses stay outside deliverables.',
                  'No card text is generated, translated, rewritten or published by this tool.'],
               'toolSha256': sha(Path(__file__).read_bytes())}
    write(out / 'SUMMARY.json', summary)
    write(out / 'MANIFEST.json', {p.name: {'sizeBytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in sorted(out.glob('*.json'))})
    return summary

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--gap-csv',type=Path,default=HERE.parents[1]/'docs/evidence/catalogue-round35/everyday-weak-73.csv');p.add_argument('--replay',action='store_true')
    a=p.parse_args();print(json.dumps(run(a.cache,a.out,a.gap_csv,a.replay),indent=2))
if __name__ == '__main__': main()
