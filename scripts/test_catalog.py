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
assert len(next(e for e in original['exams'] if e['id']=='senior-teacher')['documents'])==3
assert [e['category'] for e in original['exams']].count('teaching')==3
assert [e['category'] for e in original['exams']].count('administrative')==1
assert [e['category'] for e in original['exams']].count('engineering')==1
assert len(original['exam_categories'])==10
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
print('PASS: dynamic exam categories, new category/exam acceptance, rejected missing/duplicate/invalid categories, catalog sync and syllabus integrity')
