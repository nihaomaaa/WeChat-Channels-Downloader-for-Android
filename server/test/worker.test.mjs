import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker,{handleRequest,resolveShare} from '../worker.mjs';
import {createNodeServer} from '../node-server.mjs';
const env={RESOLVER_TOKEN:'a'.repeat(64),YUANBAO_COOKIE:'hy_token=TEST_ONLY_SESSION'};
const source='https://weixin.qq.com/sph/ABGuGHqZkl';
const media='https://finder.video.qq.com/stodownload?sign=retained%2Bvalue';
const request=(path='/v1/resolve', body={url:source}, token=env.RESOLVER_TOKEN)=>new Request('https://own.invalid'+path,{
  method:body===null?'GET':'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},
  ...(body===null?{}:{body:typeof body==='string'?body:JSON.stringify(body)})});
function fakeTencent(calls){
  return async(url,options)=>{
    calls.push({url:String(url),...options});
    return Response.json(calls.length%2===1?{data:{playable_url:'https://channels.weixin.qq.com/finder-preview/pages/feed?token=OWN_TOKEN&eid=OWN_EID'}}:
      {data:{feedInfo:{description:'Fixture only',h264VideoInfo:{videoUrl:media},coverUrl:'https://finder.video.qq.com/c.jpg'},authorInfo:{nickname:'test'}}});
  };
}
test('unauthorized calls never touch Tencent',async()=>{
  let called=false;const response=await handleRequest(request(undefined,undefined,'wrong'),env,async()=>{called=true;});
  assert.equal(response.status,401);assert.equal(called,false);
});
test('health separates connection from upstream authentication',async()=>{
  const response=await worker.fetch(request('/v1/health',null),{...env,YUANBAO_COOKIE:''},{waitUntil(){}});
  assert.deepEqual(await response.json(),{api:'wx-video-resolver',version:1,sessionConfigured:false});
});
test('template placeholders cannot become a public default credential',async()=>{
  const value='REPLACE_WITH_YOUR_RANDOM_64_HEX_TOKEN';
  const response=await handleRequest(request('/v1/health',null,value),{RESOLVER_TOKEN:value});
  assert.equal(response.status,503);
});
test('rejects unsupported links before any outbound fetch',async()=>{
  let called=false;
  const response=await handleRequest(request('/v1/resolve',{url:'https://weixin.qq.com.attacker.invalid/sph/abcde'}),env,async()=>{called=true;});
  assert.equal(response.status,400);assert.equal(called,false);
});
test('does not return success when a private session is absent',async()=>{
  const response=await handleRequest(request(),{...env,YUANBAO_COOKIE:''});
  assert.equal(response.status,503);assert.equal((await response.json()).error.code,'UPSTREAM_SESSION_MISSING');
});
test('bounded request prevents large input',async()=>{
  const response=await handleRequest(request('/v1/resolve','x'.repeat(9000)),env);
  assert.equal(response.status,413);
});
test('two stages preserve signature and never forward private access token',async()=>{
  const calls=[];const response=await handleRequest(request(),env,fakeTencent(calls));
  assert.equal(response.status,200);assert.equal((await response.json()).video.videoUrl,media);
  assert.equal(calls[0].headers.Cookie,env.YUANBAO_COOKIE);
  assert.equal(calls[1].headers.Cookie,undefined);
  assert.ok(calls.every(c=>!JSON.stringify(c).includes(env.RESOLVER_TOKEN)));
  assert.ok(calls.every(c=>c.redirect==='error'));
});
test('upstream rejection is not described as a used-up daily quota',async()=>{
  const response=await handleRequest(request(),env,async()=>new Response('private detail',{status:401}));
  const body=await response.text();assert.equal(response.status,502);
  assert.match(body,/UPSTREAM_SESSION_INVALID/);assert.ok(!body.includes('private detail'));assert.ok(!body.includes(env.YUANBAO_COOKIE));
});
test('no artificial three-request daily cap in private handler',async()=>{
  const calls=[];const fetcher=fakeTencent(calls);
  for(let i=0;i<5;i++) assert.equal((await handleRequest(request(),env,fetcher)).status,200);
  assert.equal(calls.length,10);
});
test('unknown business errors do not falsely claim quota exhaustion or an expired login',async()=>{
  const response=await handleRequest(request(),env,async()=>Response.json({error:{code:'20000',message:'unknown'}}));
  assert.equal((await response.json()).error.code,'UPSTREAM_PARSE_REJECTED');
});
test('untrusted returned media is never passed to Android',async()=>{
  let count=0;
  await assert.rejects(()=>resolveShare(source,env.YUANBAO_COOKIE,async()=>{
    count++;
    return Response.json(count===1?{data:{playable_url:'https://channels.weixin.qq.com/?token=t&eid=e'}}:
      {data:{feedInfo:{videoUrl:'https://attacker.invalid/video.mp4'}}});
  }),/UNTRUSTED_MEDIA/);
});
test('Node HTTP adapter handles actual authenticated and unauthorized requests',async()=>{
  const server=createNodeServer(env);
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const url='http://127.0.0.1:'+server.address().port+'/v1/health';
  try {
    assert.equal((await fetch(url)).status,401);
    const response=await fetch(url,{headers:{Authorization:'Bearer '+env.RESOLVER_TOKEN}});
    assert.equal(response.status,200);
    assert.equal((await response.json()).api,'wx-video-resolver');
  } finally { await new Promise(resolve=>server.close(resolve)); }
});

