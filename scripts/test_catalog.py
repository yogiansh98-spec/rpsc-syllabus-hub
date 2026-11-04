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
failed=copy.deepcopy(original); failed['exams'][0]['status']='published';verify(failed,False)
failed=copy.deepcopy(original); failed['exams'][1]['id']=failed['exams'][0]['id'];verify(failed,False)
failed=copy.deepcopy(original);failed['schema_version']=1;verify(failed,False)
failed=copy.deepcopy(original);failed['exams'][0]['category']='not-a-category';verify(failed,False)
print('PASS: catalog sync, 3 verified Senior Teacher PDFs, 3/1/1 category grouping, invalid status, duplicates, schema, and unknown category blocked')
