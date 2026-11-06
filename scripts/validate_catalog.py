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
assert isinstance(data.get('exam_categories'),list), 'exam_categories required for v0.6+'
categories=data['exam_categories']
assert 1<=len(categories)<=60, 'category count must be 1-60'
category_ids=set()
for cat in categories:
    assert isinstance(cat,dict), 'category must be object'
    cid=cat.get('id')
    assert isinstance(cid,str) and re.fullmatch(r'[a-z0-9-]{2,40}',cid) and cid not in {'all','more'} and cid not in category_ids, 'invalid/duplicate category ID'
    title=cat.get('title')
    assert isinstance(title,str) and title.strip() and len(title.strip())<=70, 'invalid category title'
    category_ids.add(cid)
assert 'other' in category_ids, 'fallback category required'
exids=set(); docids=set(); urls=set()
for e in data['exams']:
    assert isinstance(e.get('id'),str) and e['id'] and e['id'] not in exids
    exids.add(e['id'])
    assert e.get('status') in ('planned','published'), f'bad status: {e["id"]}'
    assert e.get('category') in category_ids, f'unknown category for {e["id"]}'
    for key in ('name_hi','name_en','category'):
        assert isinstance(e.get(key),str) and e[key].strip(), f'{e["id"]}: {key} missing'
    for key in ('aliases_hi','aliases_en','post','department','subjects','documents'):
        assert isinstance(e.get(key),list), f'{e["id"]}: {key} must be list'
    coverage=e.get('coverage_status','unknown' if e['status']=='published' else 'not_applicable')
    assert coverage in ('unknown','partial_verified','complete_verified','not_applicable'), 'bad coverage'
    assert e['status']!='published' or coverage!='not_applicable', 'published status requires coverage'
    assert isinstance(e.get('coverage_note',''),str) and len(e.get('coverage_note',''))<=350, 'invalid coverage note'
    docs=e['documents']
    assert not docs if e['status']=='planned' else bool(docs), f'{e["id"]}: status/docs conflict'
    for d in docs:
        assert d.get('id') and d['id'] not in docids, 'duplicate document ID'
        docids.add(d['id'])
        assert d.get('edition_status')=='current_verified', 'Unverified PDF cannot ship'
        assert re.fullmatch(r'\d{4}-\d{2}-\d{2}',d.get('verified_at',''))
        for k in ('stage','paper','subject','edition_label'):
            assert isinstance(d.get(k),str) and d[k].strip(), f'{d["id"]}: missing {k}'
        assert d.get('official_language','original') in ('original','en','hi','mixed'), 'invalid original language'
        translated=d.get('translation_hi_url','')
        if translated:
            tu=urlsplit(translated)
            assert tu.scheme=='https' and tu.netloc=='github.com' and not tu.query and not tu.fragment and re.fullmatch(r'/yogiansh98-spec/rpsc-syllabus-hub/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\.pdf',tu.path), 'Invalid Hindi PDF host'
            assert translated not in urls, 'duplicate translated URL'
            urls.add(translated)
        u=urlsplit(d.get('official_url',''))
        assert u.scheme=='https' and u.hostname=='rpsc.rajasthan.gov.in' and u.path.lower().startswith('/static/syllabus/') and u.path.lower().endswith('.pdf') and not u.username and not u.password and not u.fragment, 'Not official RPSC PDF URL'
        assert d['official_url'] not in urls, 'duplicate PDF URL'
        urls.add(d['official_url'])
print(f'PASS: {len(exids)} exam tracks, {len(category_ids)} online categories, {len(docids)} CURRENT verified PDFs (structural checks only)')
