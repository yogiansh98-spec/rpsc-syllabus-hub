const crypto=require("node:crypto");
const {auth,gh,REPO,originOK,fail,method}=require("./_lib");
const official=u=>/^https:\/\/rpsc\.rajasthan\.gov\.in\/Static\/Syllabus\/[A-Za-z0-9-]+\.pdf$/i.test(u||"");
const translated=u=>/^https:\/\/github\.com\/yogiansh98-spec\/rpsc-syllabus-hub\/releases\/download\/[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+\.pdf$/.test(u||"");
function validate(d){
const errors=[];if(!d||d.schema_version!==2||!Array.isArray(d.exams)||!Array.isArray(d.exam_categories))return ["Unsupported catalog schema"];
if(!Number.isSafeInteger(d.catalog_version)||d.catalog_version<1)errors.push("catalog_version invalid");
if(d.exams.length>5000||d.exam_categories.length<1||d.exam_categories.length>60)errors.push("Invalid catalog size");
const categories=new Set,ids=new Set,docids=new Set,urls=new Set;
for(const c of d.exam_categories){if(!c||typeof c.id!=="string"||!/^([a-z0-9-]{2,40})$/.test(c.id)||["all","more"].includes(c.id)||categories.has(c.id)||typeof c.title!=="string"||!c.title.trim()||c.title.length>70)errors.push("Invalid/duplicate category");else categories.add(c.id);}
if(!categories.has("other"))errors.push("Missing other category");
for(const e of d.exams){
if(!e||!e.id||ids.has(e.id)||!categories.has(e.category)||!e.name_hi?.trim()||!e.name_en?.trim()) {errors.push("Invalid exam");continue}ids.add(e.id);
if(!["planned","published"].includes(e.status)||!Array.isArray(e.documents)) {errors.push(e.id+": invalid status/documents");continue}
for(const k of ["aliases_hi","aliases_en","post","department","subjects"])if(!Array.isArray(e[k]))errors.push(e.id+": "+k+" must be a list");
if(e.status==="planned"&&e.documents.length||e.status==="published"&&!e.documents.length)errors.push(e.id+": invalid status/documents");
if(!["unknown","partial_verified","complete_verified","not_applicable"].includes(e.coverage_status|| (e.status==="planned"?"not_applicable":"unknown")))errors.push(e.id+": invalid coverage");
for(const doc of e.documents){if(!doc?.id||docids.has(doc.id)||!doc.subject?.trim()||!doc.paper?.trim()||!doc.stage?.trim()||!doc.edition_label?.trim())errors.push(e.id+": invalid document");docids.add(doc.id);
if(doc.edition_status!=="current_verified"||!/^\d{4}-\d{2}-\d{2}$/.test(doc.verified_at||"")||!official(doc.official_url))errors.push(doc.id+": source not verified");
if(!["original","en","hi","mixed"].includes(doc.official_language||"original"))errors.push(doc.id+": invalid source language");
if(urls.has(doc.official_url))errors.push("Duplicate PDF");urls.add(doc.official_url);
if(doc.translation_hi_url){if(!translated(doc.translation_hi_url))errors.push(doc.id+": invalid translation link");if(urls.has(doc.translation_hi_url))errors.push("Duplicate translation");urls.add(doc.translation_hi_url);}
}
}
return errors.slice(0,40);
}
module.exports=async(req,res)=>{
if(!method(req,res,"POST"))return;const a=await auth(req,res);if(!a)return;if(!originOK(req,a.cfg.origin))return fail(res,403,"Invalid request origin");
if(Number(req.headers["content-length"]||0)>700000)return fail(res,413,"Catalog too large");
const body=req.body||{};if(body.confirm!==true)return fail(res,400,"Confirm PDF and translation review before requesting publication");
const d=body.catalog,errors=validate(d);if(errors.length)return fail(res,422,errors.join("; "));
try{
const path="/repos/"+REPO;
const current=await gh(a.token,path+"/contents/catalog/catalog.json?ref=main");
const original=JSON.parse(Buffer.from(current.content.replace(/\s/g,""),"base64").toString("utf8"));
if(body.baseVersion!==original.catalog_version||body.baseSha!==current.sha)return fail(res,409,"Main catalog has changed. Reload and reconcile before submitting.");
if(d.catalog_version!==original.catalog_version+1)return fail(res,422,"Catalog version must increase by one");
if(typeof d.published_at!=="string"||!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(d.published_at))return fail(res,422,"Invalid publication time");
const main=await gh(a.token,path+"/git/ref/heads/main");
const branch="admin/catalog-"+Date.now()+"-"+crypto.randomBytes(3).toString("hex");
await gh(a.token,path+"/git/refs",{method:"POST",body:JSON.stringify({ref:"refs/heads/"+branch,sha:main.object.sha})});
const json=Buffer.from(JSON.stringify(d,null,2)+"\n").toString("base64");
for(const file of ["catalog/catalog.json","app/src/main/assets/catalog.json"]){
const old=await gh(a.token,path+"/contents/"+file+"?ref="+encodeURIComponent(branch));
await gh(a.token,path+"/contents/"+file,{method:"PUT",body:JSON.stringify({message:"Admin: propose reviewed catalog update",content:json,sha:old.sha,branch})});
}
const pr=await gh(a.token,path+"/pulls",{method:"POST",body:JSON.stringify({title:"Admin catalog update v"+d.catalog_version,head:branch,base:"main",draft:true,body:"Drafted in RPSC Hub Admin. Manual review of official PDFs, translations, latest notices, Release asset links and catalog tests is required before merge. This PR intentionally does NOT auto-publish."})});
return res.status(201).json({pr:pr.html_url,number:pr.number});
}catch(e){return fail(res,e.status&&e.status<500?e.status:502,"Could not create review request: "+e.message)}
};
