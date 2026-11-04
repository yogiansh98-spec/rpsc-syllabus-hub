#!/usr/bin/env python3
"""Conservative read-only weekly link health check: report only, never edit/publish."""
import json, sys, time, urllib.request, urllib.error
from pathlib import Path
from urllib.parse import urlsplit
path=Path(sys.argv[1]) if len(sys.argv)>1 else Path('catalog/catalog.json')
data=json.loads(path.read_text(encoding='utf-8'))
results=[]
class RedirectChecker(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, hdrs, newurl):
        u=urlsplit(newurl)
        if u.scheme!='https' or u.hostname!='rpsc.rajasthan.gov.in':
            raise urllib.error.HTTPError(newurl,code,'Redirect outside RPSC domain',hdrs,fp)
        return super().redirect_request(req,fp,code,msg,hdrs,newurl)
opener=urllib.request.build_opener(RedirectChecker)
for exam in data['exams']:
    for doc in exam['documents']:
        url=doc['official_url']; status='REVIEW'; detail=''
        # GET Range requests are generally better supported by legacy official sites than HEAD.
        req=urllib.request.Request(url,headers={'User-Agent':'RPSCSyllabusHubLinkReview/0.1 (manual verification)','Range':'bytes=0-1023','Accept':'application/pdf'})
        try:
            with opener.open(req,timeout=18) as resp:
                head=resp.read(1024)
                content_type=resp.headers.get('Content-Type','')
                final=resp.geturl()
                if resp.status in (200,206) and head.startswith(b'%PDF'):
                    status='OK'; detail=f'HTTP {resp.status}; PDF signature detected; final {final}'
                else:
                    detail=f'HTTP {resp.status}; Content-Type {content_type}; PDF signature not detected; final {final}'
        except Exception as e:
            detail=str(e)[:220]
        results.append({'id':doc['id'],'status':status,'url':url,'detail':detail})
        print(f'[{status}] {doc["id"]}: {detail}',flush=True)
        time.sleep(1.5)  # be considerate with public site
Path('link_health_report.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if any(x['status']!='OK' for x in results):
    print('One or more links need HUMAN review. No catalog changes made.')
    sys.exit(1)
print('All checked URLs returned PDF-like content. Manual identity/latest checks still required.')
