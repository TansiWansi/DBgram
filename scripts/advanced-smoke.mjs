import assert from 'node:assert/strict';
import { randomUUID, createHash, createHmac } from 'node:crypto';

// Requires a running server and PostgreSQL. Creates isolated test accounts; never touches existing users.
const base=(process.env.DBGRAM_TEST_URL||'http://127.0.0.1:8080').replace(/\/$/,'');
const tag=randomUUID().slice(0,8);let checks=0;
const pass=name=>{checks++;console.log(`PASS ${name}`);};
async function api(path,user,method='GET',body,expected=200){
 const response=await fetch(base+'/api'+path,{method,headers:{'Content-Type':'application/json','X-Client-Type':'native',...(user?{Authorization:`Bearer ${user.accessToken}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 const raw=await response.text();let result;try{result=JSON.parse(raw);}catch{throw new Error(`${method} ${path}: ${response.status} ${raw.slice(0,100)}`);}
 assert.ok([expected].flat().includes(response.status),`${method} ${path} got ${response.status}: ${result.message}`);return result.data;
}
async function account(letter){return api('/auth/register',null,'POST',{username:`advanced_${tag}_${letter}`,displayName:'Advanced QA',email:`advanced_${tag}_${letter}@example.test`,password:`Test-${randomUUID()}!`},201);}
const a=await account('a'),b=await account('b'),c=await account('c');
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jGxkAAAAASUVORK5CYII=','base64');
const auth={Authorization:`Bearer ${a.accessToken}`,'Tus-Resumable':'1.0.0'};
const created=await fetch(base+'/api/uploads',{method:'POST',headers:{...auth,'Upload-Length':String(png.length),'Upload-Metadata':`filename ${Buffer.from('../../proof.png').toString('base64')},filetype ${Buffer.from('image/png').toString('base64')}`}});
assert.equal(created.status,201);const location=new URL(created.headers.get('Location'),base).toString();const id=location.split('/').pop();
const chunk=await fetch(location,{method:'PATCH',headers:{...auth,'Content-Type':'application/offset+octet-stream','Upload-Offset':'0','Upload-Checksum':`sha256 ${createHash('sha256').update(png).digest('base64')}`},body:png});
assert.equal(chunk.status,204);
const meta=await api(`/media/${id}/metadata`,a);assert.equal(meta.status,'READY');assert.equal(meta.mimeType,'image/png');assert.ok(!meta.filename.includes('/'));
const original=await fetch(new URL(meta.url,base));assert.equal(original.status,200);assert.deepEqual(Buffer.from(await original.arrayBuffer()),png);
const corrupt=new URL(meta.url,base);corrupt.searchParams.set('signature','0'.repeat(64));assert.equal((await fetch(corrupt)).status,401);
assert.equal((await fetch(base+`/api/media/${id}`)).status,401);
const range=await fetch(new URL(meta.url,base),{headers:{Range:'bytes=0-7'}});assert.equal(range.status,206);assert.equal((await range.arrayBuffer()).byteLength,8);
pass('signed media integrity, path sanitization, signature rejection, HTTP byte ranges');
const proof=await api(`/media/${id}/provenance`,a);assert.equal(proof.anchored,false);assert.equal(proof.sha256,createHash('sha256').update(png).digest('hex'));
let root=proof.leafHash;for(const step of proof.siblings){root=createHash('sha256').update(Buffer.from(step.side==='left'?step.hash+root:root+step.hash,'hex')).digest('hex');}assert.equal(root,proof.merkleRoot);
await api(`/media/${id}/provenance?leaf=999`,a,'GET',undefined,400);pass('independently verified Merkle inclusion proof and truthful anchoring status');
assert.equal((await api('/media/provenance/verify',a,'POST',{leafHash:proof.leafHash,merkleRoot:proof.merkleRoot,siblings:proof.siblings})).valid,true);
assert.equal((await api('/media/provenance/verify',a,'POST',{leafHash:proof.leafHash,merkleRoot:'0'.repeat(64),siblings:proof.siblings})).valid,false);
assert.equal((await api(`/media/${id}/verify`,a,'POST',{})).valid,true);
assert.equal((await api('/media/provenance/batch',a,'POST',{ids:[id]})).anchored,false);
const post=await api('/posts',a,'POST',{blocks:[{type:'text',data:{content:'Advanced media visibility test'}},{type:'image',data:{url:`/api/media/${id}`,mediaId:id}}]},201);
const reader=await api(`/media/${id}/metadata`,b);assert.equal((await fetch(new URL(reader.url,base))).status,200);
await api(`/analytics/posts/${post.id}/view`,b,'POST',{});
await api(`/analytics/posts/${post.id}/view`,b,'POST',{});
await api(`/analytics/posts/${post.id}/view`,c,'POST',{});
const insights=await api('/analytics/me',a);assert.equal(Number(insights.views),2);assert.equal(Number(insights.uniqueViewers),2);assert.equal(insights.daily.length,30);
assert.equal(Number(insights.daily.at(-1).views),2);pass('distinct viewer metrics and repeat-safe daily time series');
const claim=await api('/copyright/claims',b,'POST',{targetPostId:post.id,targetMediaId:id,claimType:'COPYRIGHT',description:'This fixture tests the persisted reporting and evidence workflow.',evidenceUrls:['https://example.test/original'],claimantMediaHash:meta.sha256});
assert.equal(claim.status,'PENDING');assert.equal(claim.hashMatch,true);
assert.ok((await api('/copyright/claims',b)).some(x=>x.id===claim.id));
await api(`/copyright/claims/${claim.id}`,c,'GET',undefined,404);
await api(`/copyright/claims/${claim.id}`,a,'PATCH',{status:'UPHELD',notes:'An ordinary user cannot moderate this claim.'},403);
const respondent=await api(`/copyright/claims/${claim.id}`,a);assert.equal(respondent.claimantEmail,undefined);
const withdrawn=await api(`/copyright/claims/${claim.id}`,b,'PATCH',{status:'WITHDRAWN',notes:'Withdrawn after verifying the source fixture.'});assert.equal(withdrawn.status,'WITHDRAWN');assert.equal(withdrawn.events.length,2);
pass('copyright evidence/hash match, participant privacy, moderator gate, and audited withdrawal');
await api(`/posts/${post.id}`,a,'DELETE');
assert.equal((await fetch(new URL(reader.url,base))).status,404);
await api(`/analytics/posts/${post.id}/view`,b,'POST',{},404);pass('media access revoked immediately when the linked post is removed');
assert.equal((await fetch(location,{method:'DELETE',headers:auth})).status,204);
assert.equal((await fetch(location,{method:'HEAD',headers:auth})).status,410);assert.equal((await fetch(new URL(meta.url,base))).status,404);
pass('TUS termination revokes completed media');
const billing=await api('/billing/status',a);assert.equal(typeof billing.configured,'boolean');
if(!billing.configured){await api('/billing/checkout',a,'POST',{},503);pass('unconfigured billing fails closed');}
const secret=process.env.DBGRAM_TEST_STRIPE_WEBHOOK_SECRET;
if(secret){
 const subscription=`sub_qa_${tag}`,now=Math.floor(Date.now()/1000);
 async function event(eventId,status,created,type='customer.subscription.updated',signatureGood=true){
  const body=JSON.stringify({id:eventId,object:'event',api_version:'2024-06-20',created,type,data:{object:{id:subscription,object:'subscription',status,customer:`cus_qa_${tag}`,current_period_end:now+86400,metadata:{userId:a.userId}}}});
  const signature=createHmac('sha256',secret).update(`${now}.${body}`).digest('hex');
  const response=await fetch(base+'/api/webhooks/stripe',{method:'POST',headers:{'Content-Type':'application/json','Stripe-Signature':`t=${now},v1=${signatureGood?signature:'0'.repeat(64)}`},body});return response.status;
 }
 assert.equal(await event(`evt_bad_${tag}`,'active',now,'customer.subscription.created',false),400);
 assert.equal((await api('/billing/status',a)).active,false);
 assert.equal(await event(`evt_create_${tag}`,'active',now,'customer.subscription.created'),200);
 assert.equal(await event(`evt_create_${tag}`,'active',now,'customer.subscription.created'),200);
 assert.equal((await api('/billing/status',a)).subscriptions.length,1);assert.equal((await api('/billing/status',a)).active,true);
 assert.equal(await event(`evt_delete_${tag}`,'canceled',now+2,'customer.subscription.deleted'),200);
 assert.equal(await event(`evt_stale_${tag}`,'active',now+1),200);assert.equal((await api('/billing/status',a)).active,false);
 pass('verified Stripe webhook, durable deduplication, cancellation and stale-event rejection');
}else console.log('SKIP provider event lifecycle: set DBGRAM_TEST_STRIPE_WEBHOOK_SECRET to a local test webhook secret.');
console.log(`\n${checks} advanced end-to-end checks passed against ${base}. Test data prefix: advanced_${tag}`);
