#!/usr/bin/env python3
"""Fail-closed validation for the app-shell catalog. Not a substitute for manual latest-version verification."""
import json, re, sys
from pathlib import Path
from urllib.parse import urlsplit
path=Path(sys.argv[1]) if len(sys.argv)>1 else Path('catalog/catalog.json')
data=json.loads(path.read_text(encoding='utf-8'))
assert data.get('schema_version')==2, 'schema_version must be 2'
assert isinstance(data.get('catalog_version'),int) and data['catalog_version']>=1
assert isinstance(data.get('published_at'),str) and data['published_at'].endswith('Z')
assert isinstance(data.get('exams'),list) and len(data['exams'])<=5000
exids=set(); docids=set(); urls=set()
for e in data['exams']:
    assert isinstance(e.get('id'),str) and e['id'] and e['id'] not in exids
    exids.add(e['id'])
    assert e.get('status') in ('planned','published'), f'bad status: {e["id"]}'
    assert e.get('category') in {'teaching','administrative','police','engineering','agriculture','medical','legal','finance','science','other'}, f'unknown category for {e["id"]}'
    for key in ('name_hi','name_en','category'):
        assert isinstance(e.get(key),str) and e[key].strip(), f'{e["id"]}: {key} missing'
    for key in ('aliases_hi','aliases_en','post','department','subjects','documents'):
        assert isinstance(e.get(key),list), f'{e["id"]}: {key} must be list'
    docs=e['documents']
    assert not docs if e['status']=='planned' else bool(docs), f'{e["id"]}: status/docs conflict'
    for d in docs:
        assert d.get('id') and d['id'] not in docids, 'duplicate document ID'
        docids.add(d['id'])
        assert d.get('edition_status')=='current_verified', 'Unverified PDF cannot ship'
        assert re.fullmatch(r'\d{4}-\d{2}-\d{2}',d.get('verified_at',''))
        for k in ('stage','paper','subject','edition_label'):
            assert isinstance(d.get(k),str) and d[k].strip(), f'{d["id"]}: missing {k}'
        u=urlsplit(d.get('official_url',''))
        assert u.scheme=='https' and u.hostname=='rpsc.rajasthan.gov.in' and u.path.lower().startswith('/static/syllabus/') and u.path.lower().endswith('.pdf') and not u.username and not u.password and not u.fragment, 'Not official RPSC PDF URL'
        assert d['official_url'] not in urls, 'duplicate PDF URL'
        urls.add(d['official_url'])
print(f'PASS: {len(exids)} exam tracks, {len(docids)} CURRENT verified PDFs (structural checks only)')
