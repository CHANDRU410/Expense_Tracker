"""Exercise the bundled dashboard in Chrome with a small, synthetic ledger."""
import pathlib
import shutil
import subprocess
import tempfile

assets = pathlib.Path("app/src/main/assets")
scenario = """
window.SpendSense={range:(from,to)=>window.selection=[from,to]};
const data={from:'2024-12-01',to:'2025-05-31',today:'2026-11-26',enabled:true,rows:[
 {id:1,date:'2025-05-31',credit:false,merchant:'SWIGGY',category:'Food and Dining',amount:25000},
 {id:2,date:'2024-12-01',credit:false,merchant:'METRO',category:'Transport',amount:8000},
 {id:3,date:'2025-01-02',credit:true,merchant:'ACME',category:'Income',amount:550000}]};
function check(ok,reason){if(!ok)throw Error(reason);}
try {
 render(data);
 check(el('net').textContent==='₹5,170','cash flow');
 check(document.querySelectorAll('.bucket').length===6,'monthly trend');
 check(trendBuckets(data.from,data.to,data.rows).reduce((sum,b)=>sum+b.value,0)===33000,'inclusive endpoints');
 check(addMonths('2023-08-31',6)==='2024-02-29','leap year');
 check(document.querySelectorAll('#composition span').length===2,'category proportions');
 el('scope').value='Transport';el('scope').onchange();
 check(el('scopeSummary').textContent.includes('₹80') && el('merchants').textContent.includes('METRO') && !el('merchants').textContent.includes('SWIGGY'),'category analytics excludes other payments');
 el('chartDates').click();check(!el('overlay').hidden,'chart date selector');el('close').click();
 document.querySelector('.key').click();
 check(el('count').textContent==='1 transaction' && el('results').textContent.includes('SWIGGY'),'category drill-down');
 el('dates').click();el('to').value='2025-06-01';el('apply').click();check(el('error').textContent.includes('six'),'oversized range');
 el('to').value=data.to;el('apply').click();check(window.selection.join('|')===data.from+'|'+data.to,'inclusive range');
 el('homeTab').click();render({...data,rows:[]});check(el('net').textContent==='₹0','empty state');
 document.body.dataset.qa='passed';
} catch(e){document.body.dataset.qa=e.message;}
"""
with tempfile.TemporaryDirectory() as tmp:
    site = pathlib.Path(tmp) / "assets"
    shutil.copytree(assets, site)
    entry = site / "index.html"
    entry.write_text(entry.read_text().replace("</head>", '<script defer src="scenario.js"></script></head>'))
    (site / "scenario.js").write_text(scenario)
    browser = shutil.which("google-chrome") or shutil.which("chromium")
    result = subprocess.run([browser, "--headless", "--no-sandbox", "--disable-gpu",
                             "--window-size=360,760","--dump-dom",entry.as_uri()],
                            capture_output=True, text=True, check=True, timeout=45)
    if 'data-qa="passed"' not in result.stdout:
        raise AssertionError(result.stdout[:6500] + result.stderr[-1500:])
