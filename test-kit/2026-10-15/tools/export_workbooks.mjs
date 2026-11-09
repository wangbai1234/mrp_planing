// Run from a temporary directory linked to the bundled @oai/artifact-tool runtime; see handbook.
import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';
const root=path.resolve(process.argv[2]);
const specs=JSON.parse(await fs.readFile(process.argv[3]||path.join(root,'data/workbooks.json'),'utf8'));
await fs.mkdir(path.join(root,'data/xlsx'),{recursive:true});
function col(n){let s='';for(;n>0;n=Math.floor((n-1)/26))s=String.fromCharCode(65+(n-1)%26)+s;return s;}
for(const spec of specs){
 const wb=Workbook.create();const sh=wb.worksheets.add('导入数据');const end=col(spec.headers.length);const size=spec.rows.length+2;
 sh.getRange(`A1:${end}2`).values=[spec.headers,spec.tips];
 for(let i=0;i<spec.rows.length;i+=2000){const batch=spec.rows.slice(i,i+2000);sh.getRange(`A${i+3}:${end}${i+2+batch.length}`).values=batch;}
 sh.getRange(`A1:${end}${size}`).format.font.name='Arial';
 sh.getRange(`A1:${end}1`).format={fill:'#1677FF',font:{bold:true,color:'#FFFFFF'},rowHeight:26};
 sh.getRange(`A2:${end}2`).format={fill:'#EEF4FF',font:{color:'#4E5969'},wrapText:true,rowHeight:42};
 sh.getRange(`A1:${end}${size}`).format.columnWidth=20;
 sh.getRange(`A3:${end}${size}`).format.numberFormat='@'; // identifiers/date/month are literal text; parsers use BigDecimal strings.
 if(spec.name.startsWith('forecast-'))sh.getRange(`F3:L${size}`).format.numberFormat='0';
 sh.freezePanes.freezeRows(2);sh.showGridLines=false;wb.recalculate();
 const out=await SpreadsheetFile.exportXlsx(wb);await out.save(path.join(root,'data/xlsx',spec.name+'.xlsx'));
 if(['forecast-accuracy','inventory-accuracy-stock','config-calendar'].includes(spec.name)){
  const img=await wb.render({sheetName:'导入数据',range:`A1:${spec.name==='forecast-accuracy'?'L':end}8`,scale:1.3,format:'png'});
  await fs.writeFile(path.join(root,'evidence',spec.name+'-preview.png'),new Uint8Array(await img.arrayBuffer()));
 }
 console.log(spec.name+': '+spec.rows.length+' rows');
}
