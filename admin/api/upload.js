const crypto=require("node:crypto");
const {auth,gh,REPO,originOK,fail,method}=require("./_lib");
module.exports=async(req,res)=>{
 if(!method(req,res,"POST"))return;
 const a=await auth(req,res);if(!a)return;
 if(!originOK(req,a.cfg.origin))return fail(res,403,"Invalid origin");
 if(Number(req.headers["content-length"]||0)>3200000)return fail(res,413,"PDF too large (max 2 MB)");
 const body=req.body||{},name=body.filename;
 if(typeof name!=="string"||!/^[A-Za-z0-9][A-Za-z0-9_-]{4,95}\.pdf$/.test(name))return fail(res,400,"Use a safe, unique PDF filename");
 if(typeof body.data!=="string"||!/^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(body.data))return fail(res,400,"Expected base64 PDF data");
 const bytes=Buffer.from(body.data,"base64");
 if(bytes.length<100||bytes.length>2000000||bytes.subarray(0,5).toString()!=="%PDF-"||!bytes.subarray(-2048).toString("latin1").includes("%%EOF"))return fail(res,422,"Invalid PDF or larger than 2 MB");
 const path="/repos/"+REPO, filename="syllabus/hindi/"+name;
 try{
  const main=await gh(a.token,path+"/git/ref/heads/main");
  const branch="admin/pdf-"+Date.now()+"-"+crypto.randomBytes(3).toString("hex");
  await gh(a.token,path+"/git/refs",{method:"POST",body:JSON.stringify({ref:"refs/heads/"+branch,sha:main.object.sha})});
  const content=bytes.toString("base64");
  await gh(a.token,path+"/contents/"+filename,{method:"PUT",body:JSON.stringify({message:"Stage independently translated PDF for manual review: "+name,content,branch})});
  const pr=await gh(a.token,path+"/pulls",{method:"POST",body:JSON.stringify({title:"Review Hindi syllabus PDF: "+name,head:branch,base:"main",draft:true,body:"This PDF is staged for source-by-source proofreading and review. Do not merge until the translation has been cross-verified against the official RPSC syllabus. Merging alone does not add it to the app catalog."})});
  res.status(201).json({pr:pr.html_url,number:pr.number,filename:name});
 }catch(e){fail(res,e.status&&e.status<500?e.status:502,"Unable to stage PDF: "+e.message)}
};
