// Legacy 1.2 private-service diagnostic. 1.3 uses Android LocalTencentResolver and does not call this service.
// Node >=22. No public parser, no Yuanbao Cookie in this client.
const root=new URL(process.env.RESOLVER_URL||'https://invalid.invalid/');
if(root.protocol!=='https:' || root.pathname!=='/' || root.search || root.hash || root.username || root.password)
  throw new Error('Set RESOLVER_URL to your HTTPS service root');
const access=process.env.RESOLVER_TOKEN;
if(!access || !process.argv[2]) throw new Error('Set RESOLVER_TOKEN and pass a weixin.qq.com/sph URL');
const share=new URL(process.argv[2]);
if(share.protocol!=='https:' || share.host!=='weixin.qq.com' || !/^\/sph\/[A-Za-z0-9_-]+$/.test(share.pathname))
  throw new Error('Only Tencent SPH links are supported');
try {
  const response=await fetch(new URL('/v1/resolve',root),{method:'POST',redirect:'error',signal:AbortSignal.timeout(40000),
    headers:{Authorization:'Bearer '+access,'Content-Type':'application/json'},body:JSON.stringify({url:share.href})});
  const json=await response.json();
  if(!response.ok) throw new Error(json.error?.code||'Private service HTTP '+response.status);
  const media=new URL(json.video?.videoUrl);
  if(media.protocol!=='https:' || media.username || media.password ||
      !(media.hostname.endsWith('.qq.com') || media.hostname.endsWith('.qpic.cn'))) throw new Error('Untrusted media URL');
  const probe=await fetch(media,{redirect:'error',headers:{Range:'bytes=0-65535'},signal:AbortSignal.timeout(30000)});
  if(!probe.ok) throw new Error('Media HTTP '+probe.status);
  const reader=probe.body.getReader();let head=Buffer.alloc(0);
  while(head.length<12){const {done,value}=await reader.read();if(done)break;head=Buffer.concat([head,value]);}
  await reader.cancel();
  const mp4=head.subarray(4,8).toString()==='ftyp';
  console.log(JSON.stringify({privateService:true,hasMedia:true,mp4Header:mp4,
    note:'Header probe only; perform a full Android download and decode to finish acceptance.'},null,2));
  if(!mp4) process.exitCode=2;
} catch(error) { console.error(error.message);process.exitCode=1; }

