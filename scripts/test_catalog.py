#!/usr/bin/env python3
import copy,json,subprocess,sys,tempfile
from pathlib import Path
BASE=Path('catalog/catalog.json')
original=json.loads(BASE.read_text(encoding='utf-8'))
def verify(data,expected):
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',suffix='.json') as f:
        json.dump(data,f,ensure_ascii=False);f.flush()
        res=subprocess.run([sys.executable,'scripts/validate_catalog.py',f.name],capture_output=True,text=True)
        if (res.returncode==0)!=expected: raise AssertionError(res.stdout+res.stderr)
verify(original,True)
assert original==json.loads(Path('app/src/main/assets/catalog.json').read_text(encoding='utf-8')), 'offline assets and public catalog differ'
assert len(original['exams'])==5
assert sum(e['status']=='published' for e in original['exams'])==1
assert sum(e['status']=='planned' for e in original['exams'])==4
assert len(next(e for e in original['exams'] if e['id']=='senior-teacher')['documents'])==11
assert [e['category'] for e in original['exams']].count('teaching')==3
assert [e['category'] for e in original['exams']].count('administrative')==1
assert [e['category'] for e in original['exams']].count('engineering')==1
assert len(original['exam_categories'])==10
senior=next(e for e in original['exams'] if e['id']=='senior-teacher')
assert senior['coverage_status']=='complete_verified'
assert senior['documents'][0]['official_language']=='en'
bilingual=copy.deepcopy(original)
bilingual['exams'][1]['documents'][0]['translation_hi_url']='https://github.com/yogiansh98-spec/rpsc-syllabus-hub/releases/download/v0.7.0-multilingual-test/SeniorTeacher_GK_Hindi.pdf'
verify(bilingual,True)
invalid=copy.deepcopy(bilingual);invalid['exams'][1]['documents'][0]['translation_hi_url']='https://example.com/a.pdf';verify(invalid,False)
invalid=copy.deepcopy(original);invalid['exams'][1]['coverage_status']='not_applicable';verify(invalid,False)
invalid=copy.deepcopy(original);invalid['exams'][1]['documents'][0]['official_language']='wrong';verify(invalid,False)
assert {e['category'] for e in original['exams']} <= {c['id'] for c in original['exam_categories']}
# A newly introduced category and exam can ship in JSON, without hardcoding Kotlin IDs.
dynamic=copy.deepcopy(original)
dynamic['exam_categories'].append({'id':'transport','title':'Transport & Licensing'})
dynamic['exams'].append({**copy.deepcopy(original['exams'][0]), 'id':'transport-officer','name_en':'Transport Officer','name_hi':'परिवहन अधिकारी','category':'transport','status':'planned','documents':[]})
verify(dynamic,True)
failed=copy.deepcopy(dynamic); failed['exam_categories'].pop(); verify(failed,False)
failed=copy.deepcopy(original); failed['exam_categories'][1]['id']='teaching'; verify(failed,False)
failed=copy.deepcopy(original); failed['exam_categories'][0]['id']='more'; verify(failed,False)
failed=copy.deepcopy(original); failed['exam_categories'][0]['title']=' '; verify(failed,False)
failed=copy.deepcopy(original); failed.pop('exam_categories'); verify(failed,False)
failed=copy.deepcopy(original); failed['exams'][0]['status']='published';verify(failed,False)
failed=copy.deepcopy(original); failed['exams'][1]['id']=failed['exams'][0]['id'];verify(failed,False)
failed=copy.deepcopy(original);failed['schema_version']=1;verify(failed,False)
failed=copy.deepcopy(original);failed['exams'][0]['category']='not-a-category';verify(failed,False)
print('PASS: categories, bilingual URL validation, coverage status, catalog and document integrity')
