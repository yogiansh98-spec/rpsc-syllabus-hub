const crypto = require("node:crypto");
const OWNER_ID = 340794886;
const REPO = "yogiansh98-spec/rpsc-syllabus-hub";
const RELEASE_TAG = "v0.7.0-multilingual-test";
function config(){const {ADMIN_BASE_URL, GITHUB_OAUTH_CLIENT_ID, GITHUB_OAUTH_CLIENT_SECRET, ADMIN_SESSION_SECRET}=process.env;if(!ADMIN_BASE_URL||!GITHUB_OAUTH_CLIENT_ID||!GITHUB_OAUTH_CLIENT_SECRET||!ADMIN_SESSION_SECRET||ADMIN_SESSION_SECRET.length<32)throw Error("Admin auth is not configured");const origin=new URL(ADMIN_BASE_URL).origin;if(!origin.startsWith("https://"))throw Error("Admin URL must be HTTPS");return {origin,clientId:GITHUB_OAUTH_CLIENT_ID,clientSecret:GITHUB_OAUTH_CLIENT_SECRET,secret:ADMIN_SESSION_SECRET};}
const cookie=(name,value,age)=>name+"="+encodeURIComponent(value)+"; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age="+age;
const clear=name=>cookie(name,"",0);
function cookies(req){return Object.fromEntries(String(req.headers.cookie||"").split(";").map(x=>x.trim()).filter(x=>x.includes("=")).map(x=>{const i=x.indexOf("=");return [x.slice(0,i),decodeURIComponent(x.slice(i+1))]}));}
function key(secret){return crypto.createHash("sha256").update(secret).digest();}
function seal(value,secret){const iv=crypto.randomBytes(12),c=crypto.createCipheriv("aes-256-gcm",key(secret),iv);const out=Buffer.concat([c.update(JSON.stringify(value)),c.final()]);return Buffer.concat([iv,c.getAuthTag(),out]).toString("base64url");}
function unseal(raw,secret){try{const b=Buffer.from(raw,"base64url");if(b.length<30)return null;const c=crypto.createDecipheriv("aes-256-gcm",key(secret),b.subarray(0,12));c.setAuthTag(b.subarray(12,28));const obj=JSON.parse(Buffer.concat([c.update(b.subarray(28)),c.final()]).toString("utf8"));return obj.expires>Date.now()?obj:null;}catch{return null;}}
function fail(res,code,message){return res.status(code).json({error:message});}
function originOK(req,origin){return req.headers.origin===origin;}
async function gh(token,path,opts={}){const r=await fetch("https://api.github.com"+path,{...opts,headers:{"Accept":"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28","Authorization":"Bearer "+token,"User-Agent":"RPSC-Hub-Admin",...(opts.headers||{})}});const raw=await r.text();let result;try{result=JSON.parse(raw)}catch{result={message:raw.slice(0,300)}};if(!r.ok){const err=new Error("GitHub: "+(result.message||r.status));err.status=r.status;throw err;}return result;}
async function auth(req,res){let cfg;try{cfg=config()}catch{fail(res,503,"Secure login is not configured yet");return null;}const session=unseal(cookies(req).rpsc_admin,cfg.secret);if(!session||session.id!==OWNER_ID)return fail(res,401,"Sign in with authorized GitHub account"),null;
try{const me=await gh(session.token,"/user");if(me.id!==OWNER_ID)return fail(res,403,"Not authorized"),null;return {token:session.token,user:me,cfg};}catch{return fail(res,401,"Session no longer valid"),null;}}
function method(req,res,allowed){if(req.method!==allowed){res.setHeader("Allow",allowed);fail(res,405,"Method not allowed");return false;}return true;}
module.exports={OWNER_ID,REPO,RELEASE_TAG,config,cookie,clear,cookies,seal,unseal,fail,originOK,gh,auth,method};
