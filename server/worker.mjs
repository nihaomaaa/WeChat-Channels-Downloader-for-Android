// Private WeChat Channels resolver. No public parsing services, daily counter, or LLM API.
// Independent implementation of the observed web protocol; do not log cookies or signed URLs.
const PARSE = 'https://yuanbao.tencent.com/api/weixin/get_parse_result';
const FEED = 'https://channels.weixin.qq.com/finder-preview/api/feed/get_feed_info';
const AGENT = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';

class Failure extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
function reply(data, status = 200) {
  return Response.json(data, {status, headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
}
export function shareUrl(value) {
  let url;
  try { url = new URL(value); } catch { throw new Failure(400, 'INVALID_LINK'); }
  if (url.protocol !== 'https:' || url.host !== 'weixin.qq.com' || url.username || url.password ||
      !/^\/sph\/[A-Za-z0-9_-]{5,80}\/?$/.test(url.pathname)) throw new Failure(400, 'INVALID_LINK');
  return 'https://weixin.qq.com' + url.pathname.replace(/\/$/,'');
}
export function trustedMedia(value) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password && (!u.port || u.port === '443') &&
      (u.hostname.endsWith('.qq.com') || u.hostname.endsWith('.qpic.cn'));
  } catch { return false; }
}
async function matchingToken(supplied, expected) {
  if (!supplied || supplied.length > 256) return false;
  const digest = async value => new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
  const [a,b] = await Promise.all([digest(supplied), digest(expected)]);
  let difference=0;
  for(let i=0;i<a.length;i++) difference |= a[i]^b[i];
  return difference===0;
}
async function boundedText(message, maximum) {
  const reader=message.body?.getReader();
  if (!reader) throw new Failure(400,'EMPTY_BODY');
  const chunks=[]; let size=0;
  try {
    while(true) {
      const {done,value}=await reader.read();
      if(done) break;
      size+=value.byteLength;
      if(size>maximum) { await reader.cancel(); throw new Failure(413,'BODY_TOO_LARGE'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes=new Uint8Array(size); let offset=0;
  for(const chunk of chunks) { bytes.set(chunk,offset); offset+=chunk.length; }
  return new TextDecoder().decode(bytes);
}
async function upstream(url, body, headers, fetcher) {
  const response = await fetcher(url, {method:'POST',redirect:'error',signal:AbortSignal.timeout(15000),
    headers:{'Content-Type':'application/json','Accept':'application/json','User-Agent':AGENT,...headers},
    body:JSON.stringify(body)});
  if(response.status===401 || response.status===403) throw new Failure(502,'UPSTREAM_SESSION_INVALID');
  if(response.status===429) throw new Failure(503,'UPSTREAM_RATE_LIMITED');
  if(!response.ok) throw new Failure(502,'UPSTREAM_UNAVAILABLE');
  let json;
  try { json=JSON.parse(await boundedText(response,2*1024*1024)); }
  catch(e) { if(e instanceof Failure) throw e; throw new Failure(502,'UPSTREAM_SCHEMA_CHANGED'); }
  return json;
}
export async function resolveShare(source, cookie, fetcher=fetch) {
  const parsed = await upstream(PARSE,{type:'video_channel_url',url:source,scene:1},
    {Origin:'https://yuanbao.tencent.com',Referer:'https://yuanbao.tencent.com/',Cookie:cookie},fetcher);
  if(parsed.error || (parsed.code != null && Number(parsed.code)!==0))
    throw new Failure(502,'UPSTREAM_PARSE_REJECTED');
  let playback;
  try { playback=new URL(parsed.data?.playable_url); } catch { throw new Failure(502,'UPSTREAM_PARSE_REJECTED'); }
  if(playback.protocol!=='https:' || playback.host!=='channels.weixin.qq.com' || playback.username || playback.password)
    throw new Failure(502,'UPSTREAM_SCHEMA_CHANGED');
  const token=playback.searchParams.get('token'), eid=playback.searchParams.get('eid');
  if(!token || !eid) throw new Failure(502,'UPSTREAM_SCHEMA_CHANGED');
  const feed=await upstream(FEED,{baseReq:{generalToken:token},exportId:eid},
    {Origin:'https://channels.weixin.qq.com',Referer:playback.href},fetcher);
  if(feed.errCode || feed.data?.errMsg?.type || feed.data?.errMsg?.title)
    throw new Failure(422,'VIDEO_UNAVAILABLE');
  const item=feed.data?.feedInfo;
  const media=item?.h264VideoInfo?.videoUrl || item?.videoUrl || item?.h265VideoInfo?.videoUrl;
  if(!media) throw new Failure(422,'VIDEO_UNAVAILABLE');
  if(!trustedMedia(media)) throw new Failure(502,'UNTRUSTED_MEDIA');
  return {sourceUrl:source,title:String(item.description||'视频号视频').slice(0,2000),
    author:String(feed.data?.authorInfo?.nickname||'').slice(0,200),
    coverUrl:trustedMedia(item.coverUrl)?item.coverUrl:'',videoUrl:media};
}
export async function handleRequest(request, env, fetcher=fetch) {
  try {
    const path=new URL(request.url).pathname.replace(/\/$/,'');
    if(path==='' && request.method==='GET')
      return reply({service:'Private WeChat Video Resolver',apiVersion:1});
    if(!['/v1/health','/v1/resolve'].includes(path)) throw new Failure(404,'NOT_FOUND');
    const expected=String(env.RESOLVER_TOKEN||'');
    if(!/^[A-Za-z0-9._~-]{24,256}$/.test(expected) || expected.startsWith('REPLACE_'))
      throw new Failure(503,'SERVER_TOKEN_NOT_CONFIGURED');
    const authorization=request.headers.get('Authorization')||'';
    if(!await matchingToken(authorization.startsWith('Bearer ')?authorization.slice(7):'',expected))
      throw new Failure(401,'UNAUTHORIZED');
    const cookie=String(env.YUANBAO_COOKIE||'').trim();
    const sessionConfigured=Boolean(cookie) && cookie.includes('=') && !cookie.startsWith('REPLACE_') && cookie.length<=32768 && !/[\r\n]/.test(cookie);
    if(path==='/v1/health' && request.method==='GET')
      return reply({api:'wx-video-resolver',version:1,sessionConfigured});
    if(path!=='/v1/resolve' || request.method!=='POST') throw new Failure(405,'METHOD_NOT_ALLOWED');
    if(!sessionConfigured) throw new Failure(503,'UPSTREAM_SESSION_MISSING');
    let input;
    try { input=JSON.parse(await boundedText(request,8192)); }
    catch(e) { if(e instanceof Failure) throw e; throw new Failure(400,'INVALID_JSON'); }
    const source=shareUrl(input?.url);
    return reply({video:await resolveShare(source,cookie,fetcher)});
  } catch(e) {
    if(e instanceof Failure) return reply({error:{code:e.code}},e.status);
    return reply({error:{code:e?.name==='TimeoutError'?'UPSTREAM_TIMEOUT':'UPSTREAM_UNAVAILABLE'}},502);
  }
}
export default {fetch(request, env) { return handleRequest(request, env); }};

