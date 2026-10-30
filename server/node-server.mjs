// Node 22+ adapter for the same Worker handler. Bind to loopback unless behind a container proxy.
import {createServer} from 'node:http';
import {Readable} from 'node:stream';
import {pathToFileURL} from 'node:url';
import {handleRequest} from './worker.mjs';
export function createNodeServer(env=process.env) { return createServer(async(req,res)=>{
  try {
    const headers=new Headers();
    for(const [name,value] of Object.entries(req.headers)) if(value) headers.set(name,Array.isArray(value)?value.join(','):value);
    const request=new Request('http://private.local'+req.url,{method:req.method,headers,
      ...(['GET','HEAD'].includes(req.method)?{}:{body:Readable.toWeb(req),duplex:'half'})});
    const response=await handleRequest(request,env);
    res.writeHead(response.status,Object.fromEntries(response.headers));
    res.end(Buffer.from(await response.arrayBuffer()));
  } catch {
    res.writeHead(500,{'Content-Type':'application/json','Cache-Control':'no-store'});
    res.end('{"error":{"code":"INTERNAL_ERROR"}}');
  }
}); }
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  createNodeServer().listen(Number(process.env.PORT||8787),process.env.HOST||'127.0.0.1',()=>
    console.log('Private resolver listening. Request bodies, credentials and media URLs are not logged.'));
}

