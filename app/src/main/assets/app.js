'use strict';
const el = id => document.getElementById(id);
const currency = new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2,minimumFractionDigits:0});
const money = paise => currency.format(paise / 100);
const utc = day => new Date(day+'T00:00:00Z');
const iso = d => d.toISOString().slice(0,10);
function shift(day,n) { const d=utc(day); d.setUTCDate(d.getUTCDate()+n); return iso(d); }
function addMonths(day,n) {
    const d=utc(day), original=d.getUTCDate();
    d.setUTCDate(1); d.setUTCMonth(d.getUTCMonth()+n);
    const next=new Date(d); next.setUTCMonth(next.getUTCMonth()+1); next.setUTCDate(0);
    d.setUTCDate(Math.min(original,next.getUTCDate())); return iso(d);
}
function dateText(day,options) {return utc(day).toLocaleDateString('en-IN',Object.assign({timeZone:'UTC'},options));}
function node(tag,cls,text) {
    const n=document.createElement(tag); if(cls)n.className=cls;
    if(text!==undefined)n.textContent=text; return n;
}
let state, page='overview', type='all', category='', limit=60;
const palette=['#26755f','#c77b3f','#6d70a6','#41849b','#a94d69','#7b874c','#90694e'];
function groupExpenses(rows) {
    const groups=new Map();
    rows.filter(p=>!p.credit).forEach(p=>groups.set(p.category,(groups.get(p.category)||0)+p.amount));
    return Array.from(groups).sort((a,b)=>b[1]-a[1] || a[0].localeCompare(b[0]));
}
function render(next) {
    if(state && (next.from!==state.from || next.to!==state.to)) {category='';limit=60;}
    state=next;
    const opts={day:'numeric',month:'short'};
    const left=dateText(state.from,state.from.slice(0,4)===state.to.slice(0,4)?opts:{...opts,year:'numeric'});
    el('dates').textContent=left+' – '+dateText(state.to,{...opts,year:'numeric'})+' ▾';
    el('tracking').textContent=state.enabled?'SMS on':'Enable SMS';
    el('tracking').classList.toggle('off',!state.enabled);
    el('import').disabled=state.importing;
    el('import').textContent=state.importing?'Importing bank alerts…':'Import bank SMS for this range';
    let income=0,expense=0;
    state.rows.forEach(p=>{if(p.credit)income+=p.amount;else expense+=p.amount;});
    el('net').textContent=money(income-expense);
    el('income').textContent=money(income); el('expenses').textContent=money(expense);
    const groups=groupExpenses(state.rows);
    drawCategories(groups,expense); populateScope(groups); drawTrend();
    showRows(el('recent'),state.rows.slice(0,3),'No transactions in this range.');
    showPage(page,false);
}
function drawCategories(groups,total) {
    const strip=el('composition');strip.textContent='';strip.hidden=!groups.length;
    const legend=el('legend'); legend.textContent='';
    groups.forEach(([name,value],i)=>{
        const colour=palette[i%palette.length];
        const segment=node('span');segment.style.width=(100*value/total)+'%';segment.style.background=colour;strip.appendChild(segment);
        const row=node('button','key'); const dot=node('i'); dot.style.background=colour;
        row.append(dot,node('span','',name),node('small','',Math.round(100*value/total)+'%'),node('b','',money(value)));
        row.onclick=()=>{category=name;type='expense';el('search').value='';showPage('activity');};
        legend.appendChild(row);
    });
    if(!groups.length)legend.appendChild(node('div','empty','Your expense categories will appear here after you add a payment or import bank alerts.'));
}
function trendBuckets(from,to,rows) {
    const days=Math.round((utc(to)-utc(from))/86400000)+1;
    const buckets=[];
    for(let start=from;start<=to;) {
        let next;
        if(days<=7)next=shift(start,1);
        else if(days<=49)next=shift(start,7);
        else next=addMonths(start.slice(0,7)+'-01',1);
        const end=next>to?to:shift(next,-1);
        const label=days>49?dateText(start,{month:'short',year:'2-digit'}):dateText(start,{day:'numeric',month:'short'});
        buckets.push({start,end,label,value:0});start=next;
    }
    rows.filter(p=>!p.credit).forEach(p=>{const bucket=buckets.find(b=>p.date>=b.start && p.date<=b.end);if(bucket)bucket.value+=p.amount;});
    return buckets;
}
function populateScope(groups) {
    const select=el('scope'), previous=select.value;
    select.textContent='';
    select.add(new Option('All categories',''));
    groups.forEach(([name])=>select.add(new Option(name,name)));
    if(previous && groups.some(([name])=>name===previous))select.value=previous;
}
function drawTrend() {
    const host=el('trend'), merchants=el('merchants'),scope=el('scope').value;
    host.textContent='';merchants.textContent='';
    const expenses=state.rows.filter(p=>!p.credit);
    const rows=expenses.filter(p=>!scope||p.category===scope);
    const total=rows.reduce((sum,p)=>sum+p.amount,0), overall=expenses.reduce((sum,p)=>sum+p.amount,0);
    el('scopeSummary').textContent=money(total)+' spent · '+rows.length+' payment'+(rows.length===1?'':'s')+
        (scope && overall?' · '+Math.round(100*total/overall)+'% of expenses':'');
    if(!rows.length){host.appendChild(node('div','empty','No expenses in this range.'));return;}
    const buckets=trendBuckets(state.from,state.to,rows), max=Math.max(...buckets.map(b=>b.value));
    const canvas=node('canvas','lineChart');canvas.width=720;canvas.height=200;canvas.setAttribute('aria-hidden','true');
    host.appendChild(canvas);
    const ctx=canvas.getContext('2d'),points=buckets.map((b,i)=>({
        x:buckets.length===1?360:12+696*i/(buckets.length-1), y:182-160*b.value/max
    }));
    ctx.strokeStyle='#dbe7df';ctx.lineWidth=2;
    [22,102,182].forEach(y=>{ctx.beginPath();ctx.moveTo(12,y);ctx.lineTo(708,y);ctx.stroke();});
    ctx.beginPath();points.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y));
    ctx.lineWidth=5;ctx.strokeStyle='#226957';ctx.stroke();
    points.forEach(p=>{ctx.beginPath();ctx.arc(p.x,p.y,5,0,Math.PI*2);ctx.fillStyle='#226957';ctx.fill();});
    const axis=node('div','axis');axis.append(node('span','',buckets[0].label),node('span','',buckets.length>1?buckets[buckets.length-1].label:''));host.appendChild(axis);
    const details=node('details');details.appendChild(node('summary','','View period totals'));
    buckets.forEach(b=>details.appendChild(node('div','bucket',b.start+' – '+b.end+' · '+money(b.value))));
    host.appendChild(details);
    if(scope){
        merchants.appendChild(node('h3','','Spending by merchant'));
        const sums=new Map();
        rows.forEach(p=>sums.set(p.merchant,(sums.get(p.merchant)||0)+p.amount));
        const ranked=Array.from(sums).sort((a,b)=>b[1]-a[1]||a[0].localeCompare(b[0]));
        ranked.forEach(([name,value])=>{
            const row=node('div','merchantRow'),bar=node('div','merchantBar');
            const label=node('div','axis');label.append(node('span','',name),node('b','',money(value)));
            bar.style.width=100*value/ranked[0][1]+'%';row.append(label,bar);merchants.appendChild(row);
        });
    }
}
el('scope').onchange=drawTrend;
el('chartDates').onclick=()=>el('dates').click();
function showRows(host,rows,empty) {
    host.textContent='';
    if(!rows.length){host.appendChild(node('div','empty',empty));return;}
    rows.forEach(p=>{
        const row=node('button','entry'),who=node('span','who');
        who.append(node('b','',p.merchant),node('small','sub',p.category+' · '+dateText(p.date,{day:'numeric',month:'short'})));
        row.append(node('span','avatar',p.merchant.trim().slice(0,1).toUpperCase()),who,node('strong',p.credit?'credit':'',(p.credit?'+':'−')+money(p.amount)));
        row.onclick=()=>SpendSense.editPayment(p.id);host.appendChild(row);
    });
}
function filterActivity() {
    const q=el('search').value.toLowerCase().trim();
    const rows=state.rows.filter(p=>(!category || p.category===category) &&
        (type==='all'||(p.credit?'income':'expense')===type) &&
        [p.merchant,p.category,p.credit?'income credit':'expense debit',money(p.amount),String(p.amount/100)].join(' ').toLowerCase().includes(q));
    el('count').textContent=rows.length+(rows.length===1?' transaction':' transactions');
    el('clearCat').hidden=!category;el('clearCat').textContent=category+' ×';
    el('more').hidden=rows.length<=limit;
    document.querySelectorAll('[data-type]').forEach(b=>b.classList.toggle('active',b.dataset.type===type));
    showRows(el('results'),rows.slice(0,limit),'No matching transactions.');
}
function showPage(next,scroll=true) {
    page=next; const overview=page==='overview';
    el('pageTitle').textContent=overview?'Overview':'Activity';
    el('overview').hidden=!overview;el('activity').hidden=overview;
    el('homeTab').classList.toggle('active',overview);el('activityTab').classList.toggle('active',!overview);
    if(!overview)filterActivity();if(scroll)window.scrollTo(0,0);
}
function closeRange(){el('overlay').hidden=true;el('dates').focus();}
el('dates').onclick=()=>{
    el('from').value=state.from;el('to').value=state.to;
    el('from').max=el('to').max=state.today;el('error').textContent='';
    el('overlay').hidden=false;el('close').focus();
};
el('close').onclick=closeRange;
el('overlay').onclick=e=>{if(e.target===el('overlay'))closeRange();};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!el('overlay').hidden)closeRange();});
el('presets').onclick=e=>{
    const choice=e.target.dataset.preset;if(!choice)return;
    const today=state.today,first=today.slice(0,7)+'-01';
    el('from').value=choice==='month'?first:choice==='30'?shift(today,-29):addMonths(first,-5);
    el('to').value=today;el('error').textContent='';
};
el('apply').onclick=()=>{
    const from=el('from').value,to=el('to').value;
    let error='';
    if(!from||!to)error='Select both dates.';
    else if(from>to)error='End date must be on or after start date.';
    else if(to>state.today)error='Choose an end date up to today.';
    else if(to>=addMonths(from,6))error='Choose a range of up to six calendar months.';
    if(error){el('error').textContent=error;return;}
    SpendSense.range(from,to);closeRange();
};
el('search').oninput=()=>{limit=60;filterActivity();};
el('types').onclick=e=>{if(e.target.dataset.type){type=e.target.dataset.type;category='';limit=60;filterActivity();}};
el('clearCat').onclick=()=>{category='';filterActivity();};
el('more').onclick=()=>{limit+=60;filterActivity();};
el('tracking').onclick=()=>{if(!state.enabled)SpendSense.tracking();};
el('import').onclick=()=>SpendSense.importHistory();
el('add').onclick=()=>SpendSense.add();
el('homeTab').onclick=()=>showPage('overview');
el('activityTab').onclick=()=>showPage('activity');
el('all').onclick=()=>{category='';type='all';el('search').value='';showPage('activity');};

el('updates').onclick=()=>SpendSense.updates();
