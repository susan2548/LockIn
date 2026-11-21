(function () {
  'use strict';

  var zone = null, data = null, scope = 'day', cursor = null, lastRoute = null;
  var followsToday = true, dialog = null, previousFocus = null;
  var months = ['มกราคม','กุมภาพันธ์','มีนาคม','เมษายน','พฤษภาคม','มิถุนายน','กรกฎาคม','สิงหาคม','กันยายน','ตุลาคม','พฤศจิกายน','ธันวาคม'];
  var shortMonths = ['ม.ค.','ก.พ.','มี.ค.','เม.ย.','พ.ค.','มิ.ย.','ก.ค.','ส.ค.','ก.ย.','ต.ค.','พ.ย.','ธ.ค.'];
  var weeks = ['อาทิตย์','จันทร์','อังคาร','พุธ','พฤหัสบดี','ศุกร์','เสาร์'];
  var weekdays = ['จ','อ','พ','พฤ','ศ','ส','อา'];
  var paths = {
    left:'M15 18l-6-6 6-6',right:'M9 18l6-6-6-6',done:'m5 12 4 4 10-10',
    bookmark:'M6 4h12v17l-6-4-6 4z',habit:'M12 3c-2 4-7 8-7 12a7 7 0 0 0 14 0c0-4-5-8-7-12z',
    clock:'M12 7v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
    phone:'M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M10 17h4',
    save:'M12 3v12m-5-5 5 5 5-5 M4 16v4h16v-4'
  };
  function icon(name,size) {
    return '<svg aria-hidden="true" width="'+(size||18)+'" height="'+(size||18)+'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="'+paths[name]+'"/></svg>';
  }
  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});
  }
  function pad(n) { return String(n).padStart(2,'0'); }
  function dateKey(d) { return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate()); }
  function monthKey(d) { return dateKey(d).slice(0,7); }
  function fromKey(s) {
    var match = /^(\d{4})-(\d{2})(?:-(\d{2}))?$/.exec(s||'');
    if (!match) return null;
    var d = new Date(+match[1],+match[2]-1,+(match[3]||1),12);
    if (d.getFullYear()!==+match[1] || d.getMonth()!==+match[2]-1 || d.getDate()!==+(match[3]||1)) return null;
    return d;
  }
  function bound(d) {
    var today = fromKey(dateKey(new Date()));
    return d && d<=today && d.getFullYear()>=1970 ? d : today;
  }
  function monthName(d) { return months[d.getMonth()]+' '+(d.getFullYear()+543); }
  function dayName(d) { return d.getDate()+' '+monthName(d); }
  function shortDay(ts) { var d=new Date(ts); return d.getDate()+' '+shortMonths[d.getMonth()]; }
  function fmt(n) { return Number(n||0).toLocaleString('en-US'); }
  function clock(sec) {
    if (sec>0 && sec<60) return 'น้อยกว่า 1 นาที';
    var m=Math.floor(sec/60), h=Math.floor(m/60);
    return h ? fmt(h)+' ชม.'+(m%60?' '+(m%60)+' นาที':'') : fmt(m)+' นาที';
  }
  function minNum(sec) { return sec>0 && sec<60 ? '<1' : fmt(Math.floor(sec/60)); }
  function wallTime(ts) { var d=new Date(ts); return pad(d.getHours())+':'+pad(d.getMinutes()); }
  function classify(list, period, date) {
    var key = period==='month' ? monthKey(date) : dateKey(date);
    var out = {tasks:[], notes:[], habits:[], focus:0, sessions:0, apps:[], usage:0, returns:0, opens:0, days:{}, activities:0};
    var habitMap=new Map(), appMap=new Map(), sessions=new Set();
    (list||[]).forEach(function(e){
      if (!e || !isFinite(+e.at) || +e.at<=0) return;
      var d=new Date(+e.at);
      var day=/^\d{4}-\d{2}-\d{2}$/.test(e.day||'') ? e.day : dateKey(d);
      if ((period==='month'?day.slice(0,7):day)!==key) return;
      var value=Math.max(0, Number(e.value)||0), group;
      switch (e.type) {
        case 'task': out.tasks.push(e); break;
        case 'note': if(!e.note && !e.title)return; out.notes.push(e); break;
        case 'habit':
          group=String(e.id||e.title||'')+'\n'+String(e.unit||'ครั้ง');
          if(!habitMap.has(group)) habitMap.set(group,{name:e.title||'กิจวัตร',unit:e.unit||'ครั้ง',count:0});
          habitMap.get(group).count+=value; break;
        case 'focus': if (!value) return; out.focus+=value; sessions.add(String(e.session||e.at)); break;
        case 'usage':
          if(!value) return;
          group=e.pkg||e.title||'แอปที่เลือก';
          if(!appMap.has(group)) appMap.set(group,{name:e.title||e.pkg||'แอปที่เลือก',seconds:0});
          appMap.get(group).seconds+=value; out.usage+=value; break;
        case 'return': out.returns++; break;
        case 'open': out.opens++; break;
        default: return;
      }
      if (!out.days[day]) out.days[day]={focus:0,items:0};
      if (e.type==='focus') out.days[day].focus+=value;
      out.days[day].items++; out.activities++;
    });
    out.habits=Array.from(habitMap.values());
    out.sessions=sessions.size;
    out.apps=Array.from(appMap.values()).sort(function(a,b){return b.seconds-a.seconds;});
    out.tasks.sort(function(a,b){return b.at-a.at;}); out.notes.sort(function(a,b){return b.at-a.at;});
    return out;
  }
  function level(day) {
    if(!day || !day.focus) return 0;
    return day.focus>=3600 ? 4 : day.focus>=1500 ? 3 : day.focus>=600 ? 2 : 1;
  }
  function calendar(d,s) {
    var first = new Date(d.getFullYear(),d.getMonth(),1,12), start=(first.getDay()+6)%7;
    var count=new Date(d.getFullYear(),d.getMonth()+1,0).getDate(), today=dateKey(new Date());
    var html='<div class="jr-calendar">'+weekdays.map(function(w){return '<div class="jr-weekday">'+w+'</div>';}).join('');
    for (var j=0;j<start;j++) html+='<span></span>';
    for (var i=1;i<=count;i++){
      var itemDate=new Date(d.getFullYear(),d.getMonth(),i,12), key=dateKey(itemDate), day=s.days[key];
      html+='<button type="button" data-jday="'+key+'" class="jr-day l'+level(day)+(key===today?' today':'')+'" '+(key>today?'disabled ':'')+'aria-label="'+dayName(itemDate)+(day?' · จับเวลาโฟกัส '+clock(day.focus):' · ไม่มีบันทึก')+'">'+i+'</button>';
    }
    return html+'</div><div class="jr-legend">เวลาโฟกัสต่อวัน · น้อย <i style="background:#f0f3f9"></i><i style="background:#d7e2ff"></i><i style="background:#96b0ff"></i><i style="background:#4b65be"></i><i style="background:#1935a9"></i> มาก</div>';
  }
  function row(label,detail,value,glyph,orange) {
    return '<li class="jr-row"><span class="jr-glyph'+(orange?' orange':'')+'">'+icon(glyph,16)+'</span><span class="jr-row-text">'+esc(label)+(detail?'<small>'+esc(detail)+'</small>':'')+'</span>'+(value?'<span class="jr-quantity">'+esc(value)+'</span>':'')+'</li>';
  }
  function section(title,body,small) {
    return '<section class="jr-sec"><div class="jr-section-title"><h2>'+esc(title)+'</h2>'+(small?'<small>'+esc(small)+'</small>':'')+'</div>'+body+'</section>';
  }
  function stat(value,title) { return '<div><strong>'+esc(value)+'</strong><span>'+esc(title)+'</span></div>'; }
  function hero(s,isMonth) {
    var monthlyDays=Object.keys(s.days).length;
    return '<div class="jr-hero"><p class="jr-hero-label">'+(isMonth?'ภาพรวมประจำเดือน':'ภาพรวมประจำวัน')+'</p><div class="jr-stats">'+
      stat(fmt(s.tasks.length),'งานที่เสร็จ')+stat(minNum(s.focus),'นาทีโฟกัส')+
      (isMonth?stat(fmt(monthlyDays),'วันที่มีบันทึก'):stat(fmt(s.returns),'ครั้งที่กดกลับ'))+'</div></div>';
  }
  function detail(s,monthly) {
    var out='', empty='<p class="jr-muted">ยังไม่มีงานที่บันทึกว่าเสร็จ</p>';
    if (s.sessions) {
      var items=row('รอบโฟกัสที่บันทึก',fmt(s.sessions)+' รอบ',clock(s.focus),'clock');
      if(monthly){
        var best=Object.keys(s.days).sort(function(a,b){return s.days[b].focus-s.days[a].focus || a.localeCompare(b);})[0];
        if(best && s.days[best].focus) items+=row('วันที่โฟกัสนานที่สุด',dayName(fromKey(best)),clock(s.days[best].focus),'clock',true);
      }
      out+=section('เวลาที่ตั้งใจโฟกัส','<ul class="jr-list">'+items+'</ul>');
    }
    if (s.tasks.length || !monthly) {
      out+=section('งานที่ทำเสร็จ',s.tasks.length?'<ul class="jr-list">'+s.tasks.map(function(e){return row(e.title||'งานที่เสร็จ',e.note,monthly?shortDay(e.at):wallTime(e.at),'done');}).join('')+'</ul>':empty,s.tasks.length?fmt(s.tasks.length)+' งาน':'');
    }
    if (s.notes.length) out+=section('บันทึกระหว่างทำ','<ul class="jr-list">'+s.notes.map(function(e){return row(e.title||'บันทึกงาน',e.note,monthly?shortDay(e.at):wallTime(e.at),'bookmark',true);}).join('')+'</ul>');
    if (s.habits.length) out+=section('กิจวัตรที่บันทึก','<ul class="jr-list">'+s.habits.map(function(h){return row(h.name,'',fmt(h.count)+' '+h.unit,'habit');}).join('')+'</ul>');
    if (s.apps.length || s.returns || s.opens) {
      var apps='<p class="jr-muted">กดกลับไปทำต่อ '+fmt(s.returns)+' ครั้ง · เลือกเปิดดู '+fmt(s.opens)+' ครั้ง</p>';
      if(s.apps.length) apps+='<ul class="jr-list">'+s.apps.map(function(a){return row(a.name,'',clock(a.seconds),'phone',true);}).join('')+'</ul>';
      out+=section('แอปที่เลือกคั่น',apps,s.usage?clock(s.usage):'');
    }
    return out;
  }
  function repaint() {
    if(!zone || !data) return;
    if(followsToday) cursor=fromKey(dateKey(new Date()));
    var m=scope==='month', today=dateKey(new Date());
    var s=classify(data.events,scope,cursor);
    var title=m?monthName(cursor):dayName(cursor), key=m?monthKey(cursor):dateKey(cursor);
    var atEnd=m?key===today.slice(0,7):key===today;
    var atStart=m?key==='1970-01':key==='1970-01-01';
    var subtitle=m?(atEnd?'สรุปถึงวันนี้':'สรุปครบเดือน'):'วัน'+weeks[cursor.getDay()];
    zone.innerHTML='<div class="jr"><header class="jr-heading"><h1>บันทึก</h1></header>'+
      '<div class="jr-switch" aria-label="ช่วงเวลาของบันทึก"><button type="button" data-jmode="day" class="'+(!m?'active':'')+'" aria-pressed="'+(!m)+'">รายวัน</button><button type="button" data-jmode="month" class="'+(m?'active':'')+'" aria-pressed="'+m+'">รายเดือน</button></div>'+
      '<div class="jr-when"><button type="button" class="jr-round" data-jmove="-1" aria-label="'+(m?'เดือน':'วัน')+'ก่อนหน้า" '+(atStart?'disabled':'')+'>'+icon('left')+'</button><label class="jr-date"><strong>'+title+'</strong><small>'+subtitle+'</small><input aria-label="เลือก'+(m?'เดือน':'วันที่')+'" type="'+(m?'month':'date')+'" value="'+key+'" min="'+(m?'1970-01':'1970-01-01')+'" max="'+(m?today.slice(0,7):today)+'"/></label><button type="button" class="jr-round" data-jmove="1" aria-label="'+(m?'เดือน':'วัน')+'ถัดไป" '+(atEnd?'disabled':'')+'>'+icon('right')+'</button></div>'+
      hero(s,m)+(m?section('ปฏิทินโฟกัส',calendar(cursor,s),'แตะวันที่เพื่อดูรายละเอียด'):'')+
      (!s.activities?'<div class="jr-empty"><strong>ยังไม่มีบันทึก'+(m?'ในเดือนนี้':'ในวันนี้')+'</strong><p class="jr-muted">งาน กิจวัตร และรอบโฟกัสที่บันทึกจะแสดงที่นี่</p></div>':detail(s,m))+
      '<button type="button" class="jr-save" data-jsave>'+icon('save')+' บันทึกสรุป'+(m?'เดือน':'วัน')+'เป็นภาพ</button></div>';
    zone.querySelectorAll('[data-jmode]').forEach(function(b){b.onclick=function(){
      if (scope===b.dataset.jmode) return;
      scope=b.dataset.jmode;
      if(scope==='day' && monthKey(cursor)===today.slice(0,7)) {cursor=fromKey(today);followsToday=true;}
      repaint();
    };});
    zone.querySelectorAll('[data-jmove]').forEach(function(b){b.onclick=function(){
      var dir=Number(b.dataset.jmove);
      var d=m?new Date(cursor.getFullYear(),cursor.getMonth()+dir,1,12):new Date(cursor.getFullYear(),cursor.getMonth(),cursor.getDate()+dir,12);
      cursor=bound(d); followsToday=m?monthKey(cursor)===monthKey(new Date()):dateKey(cursor)===dateKey(new Date()); repaint();
    };});
    zone.querySelector('.jr-date input').onchange=function(e){
      if(!e.target.value)return;
      cursor=bound(fromKey(e.target.value)); followsToday=m?monthKey(cursor)===monthKey(new Date()):dateKey(cursor)===dateKey(new Date()); repaint();
    };
    zone.querySelectorAll('[data-jday]').forEach(function(b){b.onclick=function(){
      cursor=bound(fromKey(b.dataset.jday)); scope='day'; followsToday=dateKey(cursor)===dateKey(new Date()); repaint();
    };});
    zone.querySelector('[data-jsave]').onclick=function(){ showExport(s,m,new Date(cursor)); };
  }
  function render(container,state,route) {
    if(!container || !state)return;
    var newPage = zone !== container || !container.querySelector('.jr');
    zone=container; data=state;
    if (!cursor) cursor=fromKey(dateKey(new Date()));
    if (typeof route==='string' && route && (newPage || route!==lastRoute)) {
      var parts=route.split(':'), d=fromKey(parts[1]);
      if(d && (parts[0]==='month'||parts[0]==='journal')) {
        cursor=bound(d); scope=parts[0]==='month'?'month':'day';
        followsToday=scope==='day'?dateKey(cursor)===dateKey(new Date()):monthKey(cursor)===monthKey(new Date());
      }
    }
    lastRoute=typeof route==='string'?route:'';
    repaint();
  }
  function fitText(ctx,text,width) {
    var str=String(text||'').replace(/\s+/g,' ').trim();
    if(ctx.measureText(str).width<=width) return str;
    var chars=Array.from(str), lo=0, hi=chars.length;
    while(lo<hi) {
      var mid=Math.ceil((lo+hi)/2);
      if(ctx.measureText(chars.slice(0,mid).join('')+'…').width<=width) lo=mid;
      else hi=mid-1;
    }
    return chars.slice(0,lo).join('').replace(/[\u0e31\u0e34-\u0e3a\u0e47-\u0e4e]+$/,'')+'…';
  }
  function paintSummary(s,monthly,date,showTasks,showApps) {
    var paper='#f4f6fb', ink='#101c3a', muted='#50617b', soft='#d7e2ff';
    var w=900, padX=54, events=s.tasks, blocks=[];
    function block(name, rows, count) {
      if(!rows.length) return;
      blocks.push({name:name,rows:rows,count:count||0});
    }
    if(s.sessions) block('รอบโฟกัสที่บันทึก',[[fmt(s.sessions)+' รอบ',clock(s.focus)]]);
    if(showTasks) {
      block('งานที่ทำเสร็จ',events.slice(0,6).map(function(e){return [e.title||'งานที่เสร็จ',monthly?shortDay(e.at):wallTime(e.at)];}),Math.max(0,events.length-6));
      block('บันทึกระหว่างทำ',s.notes.slice(0,2).map(function(e){return [(e.title?e.title+' · ':'')+(e.note||''),''];}),Math.max(0,s.notes.length-2));
    }
    block('กิจวัตรที่บันทึก',s.habits.slice(0,5).map(function(h){return [h.name,fmt(h.count)+' '+h.unit];}),Math.max(0,s.habits.length-5));
    if(showApps && (s.apps.length||s.returns||s.opens)) {
      var appRows=s.apps.slice(0,4).map(function(a){return [a.name,clock(a.seconds)];});
      if(s.apps.length>4)appRows.push(['แอปอื่นอีก '+fmt(s.apps.length-4)+' แอป',clock(s.apps.slice(4).reduce(function(t,a){return t+a.seconds;},0))]);
      appRows.push(['กดกลับไปทำต่อ / เลือกเปิดดู',fmt(s.returns)+' / '+fmt(s.opens)+' ครั้ง']);
      block('แอปที่เลือกคั่น',appRows);
    }
    var first=(new Date(date.getFullYear(),date.getMonth(),1).getDay()+6)%7;
    var nd=new Date(date.getFullYear(),date.getMonth()+1,0).getDate(), lines=Math.ceil((first+nd)/7);
    var calH=monthly?138+lines*65:0;
    var base=437, h=base+calH+blocks.reduce(function(t,b){return t+84+b.rows.length*53+(b.count?36:0);},0)+(s.activities?0:77)+92;
    var canvas=document.createElement('canvas'); canvas.width=w; canvas.height=h;
    var ctx=canvas.getContext('2d');
    function label(str,x,y,size,color,weight,width,align){
      ctx.font=(weight||'500')+' '+size+'px sans-serif';
      ctx.fillStyle=color; ctx.textAlign=align||'left'; ctx.textBaseline='alphabetic';
      ctx.fillText(width?fitText(ctx,str,width):String(str),x,y);
    }
    function line(x1,y1,x2,y2,color,lw) {
      ctx.beginPath(); ctx.moveTo(x1,y1); ctx.lineTo(x2,y2); ctx.strokeStyle=color; ctx.lineWidth=lw||1; ctx.stroke();
    }
    ctx.fillStyle=paper;ctx.fillRect(0,0,w,h);
    ctx.fillStyle=ink;ctx.fillRect(0,0,w,base-24);
    ctx.save();ctx.beginPath();ctx.rect(w-185,0,185,133);ctx.clip();
    [0,35,70].forEach(function(offset){line(w-150+offset,130,w-50+offset,-12,'#4461b8',10);});
    ctx.restore();
    label('Lock In',padX,72,34,'#ffffff','700');
    label(monthly?'บันทึกรายเดือน':'บันทึกประจำวัน',padX,146,30,'#cddcff');
    label(monthly?monthName(date):dayName(date),padX,218,58,'#ffffff','700',770);
    line(padX,257,w-padX,257,'#4b65be',1.5);
    var metrics=[[fmt(s.tasks.length),'งานที่เสร็จ'],[minNum(s.focus),'นาทีโฟกัส'],monthly?[fmt(Object.keys(s.days).length),'วันที่มีบันทึก']:[fmt(s.habits.length),'กิจวัตรที่ทำ']];
    metrics.forEach(function(item,i){
      var x=padX+i*268;
      label(item[0],x,333,56,'#ffffff','700',245);
      label(item[1],x,379,25,'#cddcff','500',245);
    });
    var y=base;
    if(monthly){
      label('ปฏิทินโฟกัส',padX,y+40,34,ink,'700');
      label('นาทีโฟกัสต่อวัน',w-padX,y+39,23,muted,'500',230,'right');
      var gap=7, cell=(w-2*padX-6*gap)/7, today=dateKey(new Date());
      weekdays.forEach(function(v,i){label(v,padX+i*(cell+gap)+cell/2,y+85,23,muted,'500',null,'center');});
      var bg=['#f0f3f9','#d7e2ff','#96b0ff','#4b65be','#1935a9'];
      for(var i=1;i<=nd;i++){
        var k=first+i-1, x=padX+(k%7)*(cell+gap), by=y+101+Math.floor(k/7)*65;
        var dk=dateKey(new Date(date.getFullYear(),date.getMonth(),i,12)), lv=level(s.days[dk]);
        if(dk<=today){ctx.fillStyle=bg[lv];ctx.fillRect(x,by,cell,55);}
        label(i,x+cell/2,by+37,26,dk>today?'#71819b':lv>=3?'#fff':ink,'600',null,'center');
      }
      y+=calH;
    }
    blocks.forEach(function(b){
      label(b.name,padX,y+43,34,ink,'700');
      y+=69;
      b.rows.forEach(function(r){
        ctx.font='500 24px sans-serif';
        var rightWidth=Math.min(260,ctx.measureText(r[1]).width);
        label(r[0],padX,y+30,26,ink,'500',w-2*padX-(rightWidth?rightWidth+28:0));
        if(r[1]) label(r[1],w-padX,y+30,24,muted,'500',260,'right');
        line(padX,y+47,w-padX,y+47,soft);y+=53;
      });
      if(b.count){label('และอีก '+fmt(b.count)+' รายการ',padX,y+26,23,muted);y+=36;}
      y+=15;
    });
    if(!s.activities){label('ยังไม่มีบันทึกในช่วงนี้',padX,y+45,28,muted);y+=77;}
    line(padX,h-72,w-padX,h-72,'#cbd6e9');
    label('Lock In  /  บันทึกของฉัน',padX,h-30,23,muted,'600');
    label(monthly?monthKey(date):dateKey(date),w-padX,h-30,23,muted,'500',null,'right');
    return canvas;
  }
  function closeDialog() {
    if(!dialog)return false;
    dialog.remove(); dialog=null;
    if(previousFocus && document.contains(previousFocus))previousFocus.focus();
    previousFocus=null; return true;
  }
  function showExport(s,monthly,date) {
    closeDialog(); previousFocus=document.activeElement;
    var wrap=document.createElement('div');
    wrap.className='jr jr-modal';
    wrap.innerHTML='<div class="jr-dialog" role="dialog" aria-modal="true" aria-labelledby="jr-export-title">'+
      '<h2 id="jr-export-title">บันทึกภาพสรุป</h2><p class="jr-muted">'+esc(monthly?monthName(date):dayName(date))+' · เลือกข้อมูลที่จะใส่ในภาพ</p>'+
      '<label><input type="checkbox" id="jr-export-names" checked> ชื่องานและข้อความที่จดไว้</label>'+
      '<label><input type="checkbox" id="jr-export-apps" checked> ชื่อแอป เวลาใช้งาน และจำนวนครั้งที่เปิด</label>'+
      '<p class="jr-error" role="alert" hidden></p><div class="jr-dialog-actions"><button type="button" data-cancel>ยกเลิก</button><button type="button" class="jr-primary" data-confirm>บันทึก PNG</button></div></div>';
    document.body.appendChild(wrap); dialog=wrap;
    wrap.querySelector('[data-cancel]').onclick=closeDialog;
    wrap.onclick=function(e){if(e.target===wrap)closeDialog();};
    wrap.addEventListener('keydown',function(e){
      if(e.key==='Escape'){e.preventDefault();closeDialog();return;}
      if(e.key==='Tab'){
        var c=wrap.querySelectorAll('input,button');
        if(e.shiftKey && document.activeElement===c[0]){e.preventDefault();c[c.length-1].focus();}
        else if(!e.shiftKey && document.activeElement===c[c.length-1]){e.preventDefault();c[0].focus();}
      }
    });
    var confirm=wrap.querySelector('[data-confirm]');
    wrap.querySelector('#jr-export-names').focus();
    confirm.onclick=function(){
      confirm.disabled=true;confirm.textContent='กำลังบันทึก…';
      var names=wrap.querySelector('#jr-export-names').checked, apps=wrap.querySelector('#jr-export-apps').checked;
      var err=wrap.querySelector('.jr-error');err.hidden=true;
      requestAnimationFrame(function(){setTimeout(function(){
        try {
          var canvas=paintSummary(s,monthly,date,names,apps);
          var key=monthly?monthKey(date):dateKey(date);
          var filename='LockIn-'+(monthly?'month-':'day-')+key+'.png';
          var png=canvas.toDataURL('image/png');
          if(!png.startsWith('data:image/png;base64,')) throw Error('สร้างภาพไม่สำเร็จ');
          if(window.Native && typeof window.Native.saveImage==='function'){
            var message=window.Native.saveImage(png.slice(png.indexOf(',')+1),filename);
            if(message)throw Error(String(message));
          } else {
            var dl=document.createElement('a');dl.href=png;dl.download=filename;document.body.appendChild(dl);dl.click();dl.remove();
          }
          closeDialog();
          if(window.Pordee && typeof window.Pordee.toast==='function')window.Pordee.toast('บันทึกภาพสรุปแล้ว');
        } catch(e){
          err.hidden=false;err.textContent='บันทึกภาพไม่ได้ · '+String(e.message||e);
          confirm.disabled=false;confirm.textContent='ลองอีกครั้ง';
        }
      },20);});
    };
  }
  window.renderJournal=render;
  window.PordeeJournal={render:render,refresh:repaint,closeDialog:closeDialog};
})();
