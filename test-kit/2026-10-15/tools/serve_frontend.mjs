// Built frontend, isolated API proxy. Run from repository root; no production files changed.
import http from 'node:http';import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../../frontend/dist');
const mime={'.js':'application/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png'};
http.createServer((req,res)=>{
 if(req.url.startsWith('/api/')||req.url.startsWith('/actuator/')){const p=http.request({host:'127.0.0.1',port:Number(process.env.MRP_TEST_API_PORT||8180),path:req.url,method:req.method,headers:req.headers},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res);});p.on('error',()=>{res.writeHead(502);res.end('Isolated API unavailable');});req.pipe(p);return;}
 const url=new URL(req.url,'http://localhost');let file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
 if(!file.startsWith(root+path.sep)&&file!==root){res.writeHead(403);res.end();return;}
 if(!fs.existsSync(file)||fs.statSync(file).isDirectory())file=path.join(root,'index.html');
 res.setHeader('Content-Type',mime[path.extname(file)]||'application/octet-stream');res.setHeader('Cache-Control','no-store');fs.createReadStream(file).pipe(res);
}).listen(Number(process.env.MRP_TEST_FRONTEND_PORT||5181),'127.0.0.1',()=>console.log('Isolated frontend proxy ready'));
