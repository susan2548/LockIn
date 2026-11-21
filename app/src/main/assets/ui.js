(function () {
  "use strict";
  var page = document.getElementById("page"), overlay = document.getElementById("modal"), tab = "today", filter = "now", modal = "", detail = "", journalRoute = "", toastTimer = 0, bridge = window.Native;
  var state, appList = [], appSearch = "", previousButton = null;
  function el(id) {
    return document.getElementById(id);
  }
  function h(v) {
    return String(v == null ? "" : v).replace(/[&<>"']/g, function (c) {
      return {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
      }[c];
    });
  }
  function parse(s, fallback) {
    try {
      return JSON.parse(s);
    } catch (e) {
      return fallback;
    }
  }
  function pad(n) {
    return String(n).padStart(2, "0");
  }
  function key(d) {
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }
  function today() {
    return key(new Date());
  }
  function fmtDay(d, opts) {
    return d.toLocaleDateString("th-TH", opts || {
      weekday: "long",
      day: "numeric",
      month: "long"
    });
  }
  function shownDate(k) {
    if (!k)
      return "";
    return fmtDay(new Date(k + "T12:00:00"), {
      day: "numeric",
      month: "short"
    });
  }
  function icon(n) {
    return "<span aria-hidden=\"true\" data-icon=\"" + h(n) + "\"></span>";
  }
  function id() {
    return Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 9);
  }
  var paths = {
    settings: "M4 6h16 M4 12h16 M4 18h16 M9 3v6 M16 9v6 M8 15v6",
    plus: "M12 5v14 M5 12h14",
    sun: "M3 16h18 M4 20h16 M6 16a6 6 0 0 1 12 0 M12 3v3 M4.2 7.5l2.2 2 M19.8 7.5l-2.2 2",
    journal: "M6 3.5h11a2 2 0 0 1 2 2v15H7a2.5 2.5 0 0 1-2.5-2.5V6A2.5 2.5 0 0 1 7 3.5 M8 4v13 M5 18a2 2 0 0 1 2-1h12 M11 8h5 M11 11h4",
    shield: "M12 2.5l8 3.5v5.5c0 4.3-3.1 7.8-8 10-4.9-2.2-8-5.7-8-10V6z M8.5 12l2.5 2.5 4.8-5.1",
    play: "M8 4.8l11 7.2-11 7.2z",
    right: "M9 5l7 7-7 7",
    close: "M6 6l12 12 M18 6L6 18",
    water: "M12 2.3C10 5.8 5.3 10.3 5.3 14.7a6.7 6.7 0 0 0 13.4 0C18.7 10.3 14 5.8 12 2.3z M9 15c0 1.6 1.2 2.7 2.6 2.9",
    exercise: "M4 8v8 M7 5v14 M7 12h10 M17 5v14 M20 8v8",
    leaf: "M4.5 17C3 9 9 5 20 4c-.6 11.5-5.3 17-11 15.1 M4 21l11-11",
    task: "M8.5 4H6a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-2.5 M9 2.8h6v4H9z M8 13l2.5 2.5 5-5",
    clock: "M12 3a9 9 0 1 0 .01 0 M12 6.5v6l3.8 1.8",
    chevron: "M5 9l7 7 7-7",
    check: "M4 12l5 5L20 6",
    note: "M5 19l3.8-.8L19 7l-3.3-3.1L5.6 15z M14 6l3.2 3.2",
    calendar: "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M4 10h16 M8 3v4 M16 3v4"
  };
  function paintIcons(root) {
    (root || document).querySelectorAll("[data-icon]").forEach(function (span) {
      if (span.firstChild)
        return;
      var n = span.dataset.icon;
      if (!paths[n])
        return;
      var cv = document.createElement("canvas");
      cv.width = cv.height = 66;
      cv.className = "icon";
      span.appendChild(cv);
      var ctx = cv.getContext("2d");
      if (!ctx)
        return;
      ctx.scale(66 / 24, 66 / 24);
      ctx.lineWidth = 1.65;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.strokeStyle = getComputedStyle(span).color;
      ctx.stroke(new Path2D(paths[n]));
    });
  }
  function seed() {
    return {
      tasks: [],
      habits: [
        {
          id: "water",
          title: "ดื่มน้ำ",
          unit: "แก้ว",
          goal: 6,
          period: "day",
          time: ""
        },
        {
          id: "exercise",
          title: "ออกกำลังกาย",
          unit: "ครั้ง",
          goal: 2,
          period: "week",
          time: ""
        }
      ],
      rules: {
        apps: {},
        breakMinutes: 5,
        dailyTime: "22:30",
        quietStart: "",
        quietEnd: ""
      },
      focus: null,
      events: []
    };
  }
  function read() {
    if (bridge) {
      state = parse(bridge.getState(), null) || seed();
    } else {
      try {
        state = parse(localStorage.getItem("pordee-demo"), null) || seed();
      } catch (e) {
        state = seed();
      }
    }
    state.events = state.events || [];
    state.rules = state.rules || seed().rules;
    state.rules.apps = state.rules.apps || {};
    return state;
  }
  function saveLocal() {
    if (!bridge)
      try {
        localStorage.setItem("pordee-demo", JSON.stringify(state));
      } catch (e) {
      }
  }
  function put(k, v) {
    if (bridge) {
      bridge.put(k, JSON.stringify(v));
      read();
      return;
    }
    if (k === "tasks") {
      v.forEach(function (t) {
        var prev = state.tasks.find(function (x) {
          return x.id === t.id;
        });
        if (t.done && (!prev || !prev.done))
          state.events.push({
            type: "task",
            at: Date.now(),
            title: t.title,
            id: t.id,
            value: 1
          });
        if (!t.done && prev && prev.done)
          state.events = state.events.filter(function (e) {
            return !(e.type === "task" && e.id === t.id);
          });
        if (t.note && (!prev || t.note !== prev.note))
          state.events.push({
            type: "note",
            at: Date.now(),
            id: t.id,
            title: t.title,
            note: t.note
          });
      });
    }
    state[k] = v;
    saveLocal();
  }
  function addEvent(e) {
    e.at = e.at || Date.now();
    if (bridge) {
      bridge.addEvent(JSON.stringify(e));
      read();
    } else {
      state.events.push(e);
      saveLocal();
    }
  }
  function toast(msg) {
    var box = el("toast");
    box.textContent = msg;
    box.className = "show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      box.className = "";
    }, 2400);
  }
  function go(dest, route) {
    closeSheet();
    tab = dest;
    journalRoute = route || "";
    render();
    window.scrollTo(0, 0);
  }
  function closeSheet() {
    if (!modal)
      return;
    overlay.hidden = true;
    overlay.innerHTML = "";
    document.body.classList.remove("modal-open");
    modal = "";
    detail = "";
    if (previousButton && document.contains(previousButton))
      previousButton.focus({ preventScroll: true });
  }
  function sheet(name, title, html, ref) {
    if (!modal)
      previousButton = document.activeElement;
    modal = name;
    detail = ref || "";
    overlay.hidden = false;
    document.body.classList.add("modal-open");
    overlay.innerHTML = "<section class=\"sheet\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"sheet-title\"><div class=\"grip\"></div><div class=\"sheet-head\"><h2 id=\"sheet-title\">" + h(title) + "</h2><button class=\"icon-button\" data-act=\"close\" aria-label=\"ปิด\">" + icon("close") + "</button></div>" + html + "</section>";
    paintIcons(overlay);
    var head = overlay.querySelector(".sheet-head button");
    if (head)
      head.focus({ preventScroll: true });
  }
  function btn(act, label, cl, ref, ico) {
    return "<button type=\"button\" class=\"" + (cl || "secondary") + "\" data-act=\"" + h(act) + "\"" + (ref ? " data-id=\"" + h(ref) + "\"" : "") + ">" + (ico ? icon(ico) : "") + h(label) + "</button>";
  }
  function getTask(ref) {
    return state.tasks.find(function (x) {
      return x.id === ref;
    });
  }
  function getHabit(ref) {
    return state.habits.find(function (x) {
      return x.id === ref;
    });
  }
  function defineFocus(ref, title, minutes) {
    if (bridge) {
      bridge.startFocus(ref || "", title, minutes);
      read();
    } else {
      var now = Date.now();
      state.focus = {
        taskId: ref || "",
        title: title,
        start: now,
        end: now + minutes * 60000
      };
      saveLocal();
    }
  }
  function endFocus() {
    if (bridge) {
      bridge.finishFocus();
      read();
    } else if (state.focus) {
      var f = state.focus;
      state.events.push({
        type: "focus",
        at: Math.min(Date.now(), f.end),
        title: f.title,
        id: f.taskId,
        value: Math.max(0, Math.round((Math.min(Date.now(), f.end) - f.start) / 1000))
      });
      state.focus = null;
      saveLocal();
    }
  }
  function begin(ref, minutes) {
    read();
    if (state.focus) {
      showFocus();
      return;
    }
    var task = getTask(ref);
    defineFocus(ref, task ? task.title : "โฟกัสอิสระ", minutes);
    render();
    showFocus();
  }
  function finish(ref, done, note) {
    var t = getTask(ref);
    if (t) {
      var changed = Object.assign({}, t, {
        done: done || t.done,
        note: note || t.note || ""
      });
      put("tasks", state.tasks.map(function (x) {
        return x.id === ref ? changed : x;
      }));
    } else if (note) {
      addEvent({
        type: "note",
        title: state.focus ? state.focus.title : "โฟกัสอิสระ",
        note: note
      });
    }
    endFocus();
    closeSheet();
    render();
    toast(done ? "บันทึกว่างานเสร็จแล้ว" : "บันทึกรอบโฟกัสแล้ว");
  }
  function remaining() {
    var f = state.focus;
    return f ? Math.max(0, Math.ceil((f.end - Date.now()) / 1000)) : 0;
  }
  function asClock(n) {
    return pad(Math.floor(n / 60)) + ":" + pad(n % 60);
  }
  function habitCount(x) {
    var now = new Date(), start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    if (x.period === "week")
      start.setDate(start.getDate() - (start.getDay() + 6) % 7);
    return state.events.filter(function (e) {
      return e.type === "habit" && e.id === x.id && e.at >= start.getTime() && e.at <= now.getTime();
    }).reduce(function (s, e) {
      return s + (Number(e.value) || 0);
    }, 0);
  }
  function addHabitCount(ref) {
    read();
    var x = getHabit(ref);
    if (!x)
      return;
    addEvent({
      type: "habit",
      id: x.id,
      title: x.title,
      unit: x.unit,
      value: 1
    });
    render();
    toast(x.title + " +1 " + x.unit);
  }
  function taskSort(a, b) {
    return (a.date || "9999").localeCompare(b.date || "9999") || (a.time || "99").localeCompare(b.time || "99") || a.title.localeCompare(b.title);
  }
  function planned() {
    var now = today();
    return state.tasks.filter(function (t) {
      return !t.done && (!t.date || t.date <= now);
    }).sort(taskSort);
  }
  function taskMeta(t) {
    var p = [];
    if (t.date)
      p.push((t.date === today() ? "วันนี้" : shownDate(t.date)) + (t.time ? " " + t.time : ""));
    if (t.due)
      p.push("ส่ง " + shownDate(t.due));
    return p.join(" · ") || "ยังไม่ได้กำหนดเวลา";
  }
  function taskRow(t) {
    return "<div class=\"task-row\"><button class=\"check-action " + (t.done ? "checked" : "") + "\" data-act=\"toggle-task\" data-id=\"" + h(t.id) + "\" aria-label=\"" + h((t.done ? "เปิดงานอีกครั้ง " : "ทำเสร็จ ") + t.title) + "\"></button><button class=\"row-main " + (t.done ? "done" : "") + "\" data-act=\"task-detail\" data-id=\"" + h(t.id) + "\"><strong>" + h(t.title) + "</strong><small>" + h(taskMeta(t)) + "</small></button><button class=\"row-control\" data-act=\"edit-task\" data-id=\"" + h(t.id) + "\" aria-label=\"แก้ไขงาน\">" + icon("note") + "</button></div>";
  }
  function habitRow(x) {
    var n = habitCount(x);
    var kind = x.id === "water" ? "water" : x.id === "exercise" ? "exercise" : "leaf";
    return "<div class=\"habit-row\"><span class=\"icon-soft " + (kind === "exercise" ? "peach" : "") + "\">" + icon(kind) + "</span><button class=\"row-main\" data-act=\"edit-habit\" data-id=\"" + h(x.id) + "\"><strong>" + h(x.title) + "</strong><small>" + n + " / " + h(x.goal) + " " + h(x.unit) + (x.period === "week" ? " สัปดาห์นี้" : " วันนี้") + "</small><span class=\"mini-progress\"><i style=\"width:" + Math.min(100, 100 * n / Math.max(1, x.goal)) + "%\"></i></span></button><button data-act=\"habit-plus\" data-id=\"" + h(x.id) + "\" class=\"round-step\" aria-label=\"" + h(x.title + " เพิ่มหนึ่ง" + x.unit) + "\">" + icon("plus") + "</button></div>";
  }
  function streakPanel() {
    var totals = Object.create(null);
    state.events.forEach(function (event) {
      if (event.type !== "focus" || !(event.value > 0)) return;
      var date = event.day || key(new Date(event.at));
      totals[date] = (totals[date] || 0) + Number(event.value);
    });
    var now = new Date(), todayKey = key(now);
    var cursor = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12);
    if (!(totals[todayKey] >= 300)) cursor.setDate(cursor.getDate() - 1);
    var streak = 0;
    while (totals[key(cursor)] >= 300) {
      streak++;
      cursor.setDate(cursor.getDate() - 1);
    }
    cursor = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (now.getDay() + 6) % 7, 12);
    var weekdays = ["จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา."];
    var week = weekdays.map(function (day) {
      var date = key(cursor), done = totals[date] >= 300;
      var label = fmtDay(cursor, {weekday:"long", day:"numeric", month:"short"}) + (done ? " ถึงเป้าหมาย" : date === todayKey ? " วันนี้" : "");
      var cell = '<li class="' + (done ? "hit " : "") + (date === todayKey ? "current" : "") + '" aria-label="' + h(label) + '"><span>' + day + '</span><b>' + (done ? "✓" : cursor.getDate()) + '</b></li>';
      cursor.setDate(cursor.getDate() + 1);
      return cell;
    }).join("");
    return '<section class="streak-panel" aria-label="สถิติโฟกัสต่อเนื่อง"><div class="streak-title"><h2>โฟกัสต่อเนื่อง</h2><strong>' + streak + ' วัน</strong></div><ol class="streak-week">' + week + '</ol><p>สะสมโฟกัสให้ครบ 5 นาทีต่อวัน นับจากรอบที่จบแล้ว</p></section>';
  }
  function todayPage() {
    var tasks = planned(), f = state.focus, heroTask = tasks[0], selected = filter === "now" ? tasks : state.tasks.filter(function (t) {
        return filter === "later" ? !t.done && t.date > today() : t.done;
      }).sort(taskSort);
    var hero = f ? "<div class=\"hero-kicker\">" + (remaining() ? "กำลังโฟกัส" : "จบรอบโฟกัส") + "</div><h2>" + h(f.title) + "</h2><p class=\"hero-detail\">เหลือ <b id=\"hero-clock\">" + asClock(remaining()) + "</b> นาที</p><div class=\"hero-actions\">" + btn("focus", "ดูรอบโฟกัส", "hero-button", null, "clock") + "</div>" : heroTask ? "<div class=\"hero-kicker\">งานถัดไป</div><h2>" + h(heroTask.title) + "</h2><p class=\"hero-detail\">" + h(heroTask.note || taskMeta(heroTask)) + "</p><div class=\"hero-actions\">" + btn("quick-5", "เริ่ม 5 นาที", "hero-button", heroTask.id, "play") + btn("duration", "เลือกระยะเวลา", "hero-secondary", heroTask.id) + "</div>" : "<div class=\"hero-kicker\">รอบโฟกัส</div><h2>เริ่มทีละ 5 นาที</h2><p class=\"hero-detail\">จับเวลาอ่านหนังสือหรือทำงานของคุณ</p><div class=\"hero-actions\">" + btn("quick-5", "เริ่มโฟกัส", "hero-button", null, "play") + btn("duration", "เลือกระยะเวลา", "hero-secondary") + "</div>";
    return "<div class=\"title-line\"><div><p class=\"eyebrow\">" + h(fmtDay(new Date(), {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric"
    })) + "</p><h1 class=\"page-title\">วันนี้</h1></div></div><section class=\"hero\"><div class=\"hero-copy\">" + hero + "</div><img class=\"hero-art\" src=\"lockin-mark.webp\" alt=\"\"></section>" + streakPanel() + "<section class=\"section\"><div class=\"section-head\"><h2>รายการงาน</h2>" + btn("new-task", "เพิ่มงาน", "subtle-button", null, "plus") + "</div><div class=\"plain-group\" style=\"margin-bottom:10px\">" + [
      "now",
      "later",
      "done"
    ].map(function (value, i) {
      return btn("filter", [
        "วันนี้",
        "ถัดไป",
        "เสร็จแล้ว"
      ][i], "pill " + (filter === value ? "active" : ""), value);
    }).join("") + "</div><div class=\"task-list\">" + (selected.length ? selected.map(taskRow).join("") : "<p class=\"empty-line\">" + (filter === "now" ? "ยังไม่มีงานที่ต้องทำวันนี้" : filter === "later" ? "ยังไม่มีงานที่กำหนดไว้ล่วงหน้า" : "งานที่ทำเสร็จจะอยู่ที่นี่") + "</p>") + "</div></section><section class=\"section\"><div class=\"section-head\"><h2>กิจวัตร</h2>" + btn("new-habit", "เพิ่มกิจวัตร", "subtle-button", null, "plus") + "</div><div class=\"task-list\">" + (state.habits.map(habitRow).join("") || "<p class=\"empty-line\">เพิ่มกิจวัตรที่อยากทำเป็นประจำ</p>") + "</div></section><button class=\"history-link\" data-act=\"nav-journal\">ดูบันทึกประจำวัน " + icon("right") + "</button>";
  }
  function inputField(name, label, type, value, extra) {
    if (type === "time") {
      return '<div class="time-field"><span id="' + name + '-label">' + h(label) + '</span>' +
        '<input type="hidden" id="' + name + '" value="' + h(value || "") + '">' +
        '<button type="button" class="time-trigger" data-act="pick-time" data-id="' + name +
        '" aria-haspopup="dialog" aria-labelledby="' + name + '-label ' + name + '-value">' +
        icon("clock") + '<b id="' + name + '-value">' + h(value || "เลือกเวลา") + '</b>' + icon("chevron") + '</button></div>';
    }
    return "<label class=\"field\">" + h(label) + "<input id=\"" + h(name) + "\" type=\"" + h(type) + "\" value=\"" + h(value) + "\" " + (extra || "") + "></label>";
  }
  function openTimePicker(name) {
    var field = el(name), parent = overlay.querySelector(".sheet");
    if (!field || !parent || el("time-picker")) return;
    var now = new Date();
    var parts = (field.value || pad(now.getHours()) + ":" + pad(now.getMinutes())).split(":");
    var editor = document.createElement("section");
    editor.className = "sheet time-sheet";
    editor.id = "time-picker";
    editor.dataset.field = name;
    editor.setAttribute("role", "dialog");
    editor.setAttribute("aria-modal", "true");
    editor.setAttribute("aria-labelledby", "time-title");
    function digits(id, label, value) {
      return '<label class="clock-part"><span>' + label + '</span><input id="' + id +
        '" inputmode="numeric" type="text" maxlength="2" autocomplete="off" value="' + h(value) + '"></label>';
    }
    editor.innerHTML = '<div class="grip"></div><div class="sheet-head"><h2 id="time-title">เลือกเวลา</h2>' +
      '<button class="icon-button" data-act="cancel-time" aria-label="ยกเลิก">' + icon("close") + '</button></div>' +
      '<p class="time-context">' + h(el(name + "-label").textContent) + ' · 24 ชั่วโมง</p>' +
      '<div class="clock-editor">' + digits("clock-hour", "ชั่วโมง", parts[0]) + '<b aria-hidden="true">:</b>' +
      digits("clock-minute", "นาที", parts[1]) + '</div><div class="time-shortcuts">' +
      ["07:00","12:00","18:00","21:00"].map(function (v) {return btn("preset-time", v, "time-chip", v);}).join("") +
      '</div><p class="error" id="clock-error" role="alert"></p><div class="choice-footer">' +
      btn("clear-time", "ล้างเวลา", "subtle-button") + btn("apply-time", "ใช้เวลานี้", "primary") + '</div>';
    parent.hidden = true;
    overlay.appendChild(editor);
    markTimePreset();
    paintIcons(editor);
    editor.querySelector(".icon-button").focus({preventScroll:true});
  }
  function markTimePreset() {
    var hour = v("clock-hour"), minute = v("clock-minute");
    var value = hour && minute ? pad(Number(hour)) + ":" + pad(Number(minute)) : "";
    el("time-picker").querySelectorAll("[data-act=preset-time]").forEach(function (chip) {
      chip.setAttribute("aria-pressed", chip.dataset.id === value);
    });
  }
  function closeTimePicker(save, value) {
    var editor = el("time-picker");
    if (!editor) return false;
    var name = editor.dataset.field;
    if (save) {
      el(name).value = value;
      el(name + "-value").textContent = value || "เลือกเวลา";
      if (name === "daily-time") el("daily-on").checked = Boolean(value);
    }
    editor.remove();
    overlay.querySelector(".sheet").hidden = false;
    overlay.querySelector('[data-act="pick-time"][data-id="' + name + '"]').focus({preventScroll:true});
    return true;
  }
  function applyTime() {
    var hour = v("clock-hour"), minute = v("clock-minute");
    function valid(value, max) {return /^[0-9]{1,2}$/.test(value) && Number(value) <= max;}
    if (!valid(hour, 23) || !valid(minute, 59)) {
      el("clock-error").textContent = "ใส่ชั่วโมง 0–23 และนาที 0–59";
      el(valid(hour, 23) ? "clock-minute" : "clock-hour").focus();
      return;
    }
    closeTimePicker(true, pad(Number(hour)) + ":" + pad(Number(minute)));
  }
  function noteField(value) {
    return "<label class=\"field\">บันทึกว่าทำถึงไหน<textarea id=\"task-note\" maxlength=\"600\" placeholder=\"เช่น ครั้งหน้าเริ่มข้อ 3\">" + h(value || "") + "</textarea></label>";
  }
  function footer(save, label, del, ref) {
    return "<div class=\"actions\">" + btn(save, label, "primary", ref) + (del ? btn(del, "ลบ", "danger", ref) : "") + "</div><p class=\"error\" id=\"form-error\" role=\"alert\"></p>";
  }
  function invalid(msg, field) {
    var error = el("form-error");
    if (error)
      error.textContent = msg;
    else
      toast(msg);
    if (field) {
      var control = overlay.querySelector('[data-act="pick-time"][data-id="' + field + '"]') || el(field);
      if (control) control.focus();
    }
    return false;
  }
  function v(k) {
    return el(k) ? el(k).value.trim() : "";
  }
  function taskEditor(ref) {
    var x = getTask(ref) || {
      title: "",
      date: today(),
      time: "",
      due: "",
      note: ""
    };
    sheet("task-editor", ref ? "แก้ไขงาน" : "เพิ่มงาน", inputField("task-title", "ชื่องาน", "text", x.title, "maxlength=\"160\" placeholder=\"เช่น ทำโจทย์สถิติข้อ 1–3\"") + "<div class=\"fields-2\">" + inputField("task-date", "วันที่จะทำ", "date", x.date) + inputField("task-time", "เวลาเตือน", "time", x.time) + "</div>" + inputField("task-due", "กำหนดส่ง (ถ้ามี)", "date", x.due) + noteField(x.note) + footer("save-task", "บันทึกงาน", ref ? "delete-task" : null, ref), ref);
  }
  function saveTask(ref) {
    var title = v("task-title"), date = v("task-date"), time = v("task-time");
    if (!title)
      return invalid("ใส่ชื่องานก่อนบันทึก", "task-title");
    if (time && !date)
      return invalid("เลือกวันที่สำหรับเวลาเตือน", "task-date");
    read();
    var prev = getTask(ref), x = Object.assign({}, prev || {
        id: id(),
        done: false
      }, {
        title: title,
        date: date,
        time: time,
        due: v("task-due"),
        note: v("task-note")
      });
    put("tasks", prev ? state.tasks.map(function (t) {
      return t.id === ref ? x : t;
    }) : state.tasks.concat([x]));
    closeSheet();
    if (tab === "today")
      filter = x.done ? "done" : date > today() ? "later" : "now";
    render();
    toast("บันทึกงานแล้ว");
    if (time)
      offerReminderPermission();
  }
  function taskDetail(ref) {
    var t = getTask(ref);
    if (!t)
      return;
    sheet("task-detail", "รายละเอียดงาน", "<h3>" + h(t.title) + "</h3><p class=\"small-spaced\">" + h(taskMeta(t)) + "</p>" + (t.note ? "<div class=\"status-box\"><h3>บันทึกที่ค้างไว้</h3><p>" + h(t.note) + "</p></div>" : "") + "<div class=\"actions\">" + (!t.done ? btn("duration", "เริ่มรอบโฟกัส", "primary", ref, "play") : "") + btn("edit-task", "แก้ไขงาน", "secondary", ref) + btn("toggle-task", t.done ? "เปิดงานอีกครั้ง" : "ทำเสร็จแล้ว", "secondary", ref) + "</div>", ref);
  }
  function toggleTask(ref) {
    read();
    var x = getTask(ref);
    if (!x)
      return;
    put("tasks", state.tasks.map(function (t) {
      return t.id === ref ? Object.assign({}, t, { done: !t.done }) : t;
    }));
    closeSheet();
    render();
    toast(x.done ? "เปิดงานอีกครั้งแล้ว" : "บันทึกว่างานเสร็จแล้ว");
  }
  function habitPeriod(period) {
    function choice(value, text) {
      return '<label><input type="radio" name="habit-period" value="' + value + '"' + (value === period ? ' checked' : '') +
        '><span>' + text + '</span></label>';
    }
    return '<fieldset class="period-field"><legend>รอบเป้าหมาย</legend><div class="segments">' +
      choice("day", "รายวัน") + choice("week", "รายสัปดาห์") + '</div><p id="period-help" aria-live="polite">' +
      periodHelp(period) + '</p></fieldset>';
  }
  function periodHelp(period) {
    return period === "week" ? "สะสมยอดตั้งแต่จันทร์ถึงอาทิตย์ เริ่มนับใหม่ทุกวันจันทร์" : "สะสมยอดภายในวันเดียว เริ่มนับใหม่ตอนเที่ยงคืน";
  }
  function habitEditor(ref) {
    var x = getHabit(ref) || {
      title: "",
      goal: 1,
      period: "day",
      unit: "ครั้ง",
      time: ""
    };
    sheet("habit-editor", ref ? "แก้ไขกิจวัตร" : "เพิ่มกิจวัตร", inputField("habit-title", "ชื่อกิจวัตร", "text", x.title, "maxlength=\"100\" placeholder=\"เช่น เดินหลังเลิกเรียน\"") + "<div class=\"fields-2\">" + inputField("habit-goal", "เป้าหมาย", "number", x.goal, "min=\"1\" max=\"999\" step=\"1\"") + inputField("habit-unit", "หน่วย", "text", x.unit, "maxlength=\"22\"") + "</div>" + habitPeriod(x.period === "week" ? "week" : "day") + inputField("habit-time", "เวลาเตือนในวันที่ยังไม่ครบเป้า (ถ้ามี)", "time", x.time) + footer("save-habit", "บันทึกกิจวัตร", ref ? "delete-habit" : null, ref), ref);
  }
  function saveHabit(ref) {
    var title = v("habit-title"), unit = v("habit-unit"), goal = Number(v("habit-goal"));
    if (!title)
      return invalid("ใส่ชื่อกิจวัตร", "habit-title");
    if (!Number.isInteger(goal) || goal < 1 || goal > 999)
      return invalid("ตั้งเป้าหมายระหว่าง 1 ถึง 999", "habit-goal");
    if (!unit)
      return invalid("ใส่หน่วย เช่น ครั้ง หรือ แก้ว", "habit-unit");
    read();
    var prev = getHabit(ref), x = {
        id: ref || id(),
        title: title,
        unit: unit,
        goal: goal,
        period: overlay.querySelector('input[name="habit-period"]:checked').value,
        time: v("habit-time")
      };
    put("habits", prev ? state.habits.map(function (t) {
      return t.id === ref ? x : t;
    }) : state.habits.concat([x]));
    closeSheet();
    render();
    toast("บันทึกกิจวัตรแล้ว");
    if (x.time)
      offerReminderPermission();
  }
  function removeItem(kind, ref) {
    var collection = kind === "task" ? "tasks" : "habits", x = kind === "task" ? getTask(ref) : getHabit(ref);
    if (!x)
      return;
    sheet("delete-confirm", "ลบ" + (kind === "task" ? "งาน" : "กิจวัตร") + "นี้?", "<p>" + h(x.title) + "</p><p class=\"small-spaced\">ประวัติที่บันทึกไว้จะยังดูย้อนหลังได้</p><div class=\"actions\">" + btn("confirm-delete-" + kind, "ยืนยันลบ", "primary", ref) + btn("close", "ยกเลิก", "secondary") + "</div>", ref);
  }
  function showDuration(ref) {
    if (state.focus) {
      showFocus();
      return;
    }
    var t = getTask(ref);
    sheet("duration", "เวลาโฟกัส", '<p class="time-context">' + h(t ? t.title : "โฟกัสอิสระ") +
      '</p>' + durationFields("focus-minutes", 25, 240, [5,15,25,50]) + footer("start-custom", "เริ่มโฟกัส", null, ref), ref);
  }
  function durationFields(field, value, max, presets) {
    return '<div class="duration-field">' + inputField(field, "กำหนดระยะเวลา (นาที)", "number", value,
      'inputmode="numeric" min="1" max="' + max + '" step="1"') + '<div class="time-shortcuts">' +
      presets.map(function (minutes) {
        return '<button class="time-chip" data-act="set-minutes" data-id="' + minutes + '" aria-pressed="' +
          (minutes === value) + '">' + minutes + ' นาที</button>';
      }).join("") + '</div><p class="time-context">เลือกได้ 1–' + max + ' นาที</p></div>';
  }
  function markDuration(field) {
    field.closest(".duration-field").querySelectorAll(".time-chip").forEach(function (chip) {
      chip.setAttribute("aria-pressed", Number(chip.dataset.id) === Number(field.value));
    });
  }
  function showFocus() {
    var f = state.focus;
    if (!f) {
      closeSheet();
      render();
      return;
    }
    var expired = remaining() === 0;
    sheet("focus", expired ? "จบรอบโฟกัส" : "รอบโฟกัส", "<p class=\"focus-title\">" + h(f.title) + "</p><div class=\"clock\" id=\"focus-clock\">" + asClock(remaining()) + "</div><p class=\"caption center\" id=\"focus-status\">" + (expired ? "ครบเวลาที่ตั้งไว้แล้ว" : "วางมือถือแล้วกลับมาดูเวลาที่เหลือได้") + "</p><div class=\"actions center\">" + btn("end-options", expired ? "บันทึกรอบนี้" : "จบรอบนี้", "primary") + btn("extend", "เพิ่ม 5 นาที", "secondary") + "</div>");
  }
  function endOptions() {
    var f = state.focus;
    if (!f)
      return;
    var t = getTask(f.taskId);
    sheet("end", "บันทึกรอบโฟกัส", "<h3>" + h(f.title) + "</h3>" + noteField(t ? t.note : "") + "<div class=\"actions\">" + (t ? btn("finish-done", "งานเสร็จแล้ว", "primary", f.taskId) : "") + btn("finish-keep", t ? "เก็บไว้ทำต่อ" : "บันทึกรอบนี้", t ? "secondary" : "primary", f.taskId) + btn("focus", "กลับไปจับเวลา", "secondary") + "</div>", f.taskId);
  }
  function guardOn() {
    return bridge ? !!bridge.guardEnabled() : false;
  }
  function notifsOn() {
    return bridge ? !!bridge.notificationsEnabled() : false;
  }
  function appNames() {
    if (bridge)
      return parse(bridge.getApps(), "") || [];
    return [
      {
        pkg: "com.zhiliaoapp.musically",
        name: "TikTok"
      },
      {
        pkg: "com.facebook.katana",
        name: "Facebook"
      },
      {
        pkg: "com.instagram.android",
        name: "Instagram"
      },
      {
        pkg: "com.twitter.android",
        name: "X"
      }
    ];
  }
  function appIcon(pkg, name) {
    var image = bridge ? '<img loading="lazy" decoding="async" alt="" src="/icons/' + encodeURIComponent(pkg) + '.png" onerror="this.remove()">' : "";
    return '<span class="app-monogram" aria-hidden="true">' + h(Array.from(name || pkg || "•")[0]) + image + '</span>';
  }
  function appRuleRow(pkg, record) {
    var mode = record.mode === "focus" ? "เฉพาะโฟกัส" : "คั่นทุกครั้ง";
    return '<div class="list-row rule-app">' + appIcon(pkg, record.name) + '<span class="row-main"><strong>' +
      h(record.name || pkg) + '</strong></span><button class="rule-mode" aria-haspopup="dialog" data-act="edit-rule" data-id="' +
      h(pkg) + '" aria-label="' + h((record.name || pkg) + " · " + mode) + '">' + mode + icon("chevron") + '</button></div>';
  }
  function editRule(pkg) {
    var app = state.rules.apps[pkg];
    if (!app) return;
    function option(value, title, description, glyph) {
      var selected = app.mode === value;
      return '<button class="mode-option" data-act="use-rule" data-id="' + value + '" aria-pressed="' + selected + '">' +
        '<span class="mode-glyph">' + icon(glyph) + '</span><span class="row-main"><strong>' + title + '</strong><small>' +
        description + '</small></span><span class="choice-dot" aria-hidden="true">' + (selected ? '✓' : '') + '</span></button>';
    }
    var name = app.name || pkg;
    sheet("rule", "ตั้งหน้าคั่น", '<div class="rule-identity">' + appIcon(pkg, name) + '<b>' + h(name) + '</b></div><div class="mode-options">' +
      option("always", "คั่นทุกครั้ง", "คั่นเมื่อเปิดแอป ยกเว้นช่วงพักที่คุณเลือก", "shield") +
      option("focus", "เฉพาะตอนโฟกัส", "ใช้งานได้ตามปกตินอกรอบโฟกัส", "clock") + '</div>' +
      btn("remove-rule", "เอาออกจากรายการคั่น", "remove-rule"), pkg);
  }
  function offerReminderPermission() {
    if (!bridge || notifsOn() || state.rules.reminderPromptShown)
      return;
    put("rules", Object.assign({}, state.rules, { reminderPromptShown: true }));
    bridge.requestNotifications();
  }
  function rulesPage() {
    var rules = state.rules, apps = Object.keys(rules.apps), enabled = guardOn();
    return "<div class=\"title-line\"><div><h1 class=\"page-title\">กฎแอป</h1><p class=\"small-spaced\">เลือกแอปที่อยากหยุดคิดสักนิดก่อนเปิด</p></div></div><div class=\"status-box\"><h3>" + (enabled ? "หน้าคั่นเปิดใช้งานแล้ว" : "เปิดหน้าคั่นบนเครื่อง") + "</h3><p>" + (enabled ? "กฎจะทำงานเมื่อเปิดแอปที่เลือก" : "เปิดบริการ Lock In ในเมนูการช่วยเหลือพิเศษ เพื่อให้ตรวจชื่อแอปที่อยู่หน้าจอและคั่นแอปที่เลือก") + "</p>" + btn("guard-settings", enabled ? "ดูการตั้งค่า Android" : "เปิดการตั้งค่า Android", "secondary") + "</div><div class=\"section\"><div class=\"section-head\"><h2>แอปที่อยากลด <span class=\"mini-count\">" + apps.length + "</span></h2>" + btn("apps", "เลือกแอป", "subtle-button", null, "plus") + "</div><div class=\"task-list\">" + (apps.length ? apps.map(function (p) {
      return appRuleRow(p, rules.apps[p]);
    }).join("") : "<p class=\"empty-line\">ยังไม่ได้เลือกแอป · เพิ่ม TikTok, Facebook, IG หรือแอปอื่นบนเครื่องได้</p>") + "</div></div><div class=\"section\"><div class=\"section-head\"><h2>เวลาพักร่วมกัน</h2></div><button class=\"option fill\" data-act=\"break-settings\"><span class=\"icon-soft peach\">" + icon("clock") + "</span><span class=\"row-main\"><strong>" + h(rules.breakMinutes || 5) + " นาทีเป็นค่าเริ่มต้น</strong><small>ปรับระยะเวลาของแต่ละรอบได้ที่หน้าคั่น</small></span>" + icon("right") + "</button></div>";
  }
  function appsPicker() {
    appList = appNames().slice().sort(function (a, b) {
      return a.name.localeCompare(b.name, "th");
    });
    appSearch = "";
    sheet("apps", "เลือกแอปในเครื่อง", "<label class=\"field\"><input class=\"field-input\" type=\"search\" id=\"app-search\" placeholder=\"ค้นหาชื่อแอป\" aria-label=\"ค้นหาแอป\"></label><div class=\"picker-list\" id=\"app-picker-list\"></div><div class=\"actions\">" + btn("close", "เสร็จแล้ว", "primary") + "</div>");
    paintPicker();
  }
  function paintPicker() {
    var root = el("app-picker-list");
    if (!root)
      return;
    var entries = appList.filter(function (a) {
      return (a.name + " " + a.pkg).toLowerCase().includes(appSearch.toLowerCase());
    });
    root.innerHTML = entries.length ? entries.map(function (a) {
      var checked = !!state.rules.apps[a.pkg];
      return "<button class=\"picker-item\" data-act=\"pick-app\" data-id=\"" + h(a.pkg) + "\" aria-pressed=\"" + checked + "\">" + appIcon(a.pkg, a.name) + "<span class=\"row-main\">" + h(a.name) + "</span><span class=\"mark\">" + (checked ? "เลือกแล้ว" : "เพิ่ม") + "</span></button>";
    }).join("") : "<p class=\"empty-line\">ไม่พบแอปที่ตรงกับการค้นหา</p>";
  }
  function updateMode(pkg, mode, name) {
    read();
    var apps = Object.assign({}, state.rules.apps);
    if (mode === "remove")
      delete apps[pkg];
    else
      apps[pkg] = {
        name: name || apps[pkg] && apps[pkg].name || pkg,
        mode: mode
      };
    put("rules", Object.assign({}, state.rules, { apps: apps }));
  }
  function showBreak() {
    sheet("break", "เวลาพักเริ่มต้น", '<p class="time-context">ใช้ร่วมกันระหว่างแอปที่คั่น คุณเปลี่ยนเวลาสำหรับแต่ละรอบที่หน้าคั่นได้</p>' +
      durationFields("break-min", Number(state.rules.breakMinutes) || 5, 120, [5,10,15,30]) + footer("save-break", "บันทึก"));
  }
  function settings() {
    var rules = state.rules, n = notifsOn();
    sheet("settings", "การแจ้งเตือน", "<div class=\"setting-group\"><h3>การแจ้งเตือนของเครื่อง</h3><p class=\"small\">" + (n ? "เปิดรับการแจ้งเตือนแล้ว" : "อนุญาตให้ Lock In เตือนงาน กิจวัตร และสรุปบันทึก") + "</p>" + (n ? "" : btn("notifications", "อนุญาตการแจ้งเตือน", "secondary")) + "</div>" + inputField("daily-time", "เวลาแจ้งสรุปวัน และสรุปเดือนเมื่อขึ้นเดือนใหม่", "time", rules.dailyTime) + "<label class=\"line-toggle\"><input type=\"checkbox\" id=\"daily-on\" " + (rules.dailyTime ? "checked" : "") + ">แจ้งเตือนสรุป</label><div class=\"fields-2\">" + inputField("quiet-start", "เงียบตั้งแต่", "time", rules.quietStart) + inputField("quiet-end", "จนถึง", "time", rules.quietEnd) + "</div><p class=\"caption\">ช่วงเงียบใช้กับการเตือนงานและกิจวัตร</p>" + footer("save-settings", "บันทึกการตั้งค่า"));
  }
  function render() {
    read();
    document.querySelectorAll(".tabbar button").forEach(function (b) {
      if (b.dataset.tab === tab)
        b.setAttribute("aria-current", "page");
      else
        b.removeAttribute("aria-current");
      b.querySelectorAll("[data-icon]").forEach(function (s) {
        s.innerHTML = "";
      });
    });
    if (tab === "journal") {
      if (!el("journal-content"))
        page.innerHTML = "<section id=\"journal-content\"></section>";
      if (typeof window.renderJournal === "function")
        window.renderJournal(el("journal-content"), state, journalRoute);
      else if (window.PordeeJournal)
        window.PordeeJournal.render(el("journal-content"), state, journalRoute);
    } else {
      page.innerHTML = tab === "rules" ? rulesPage() : todayPage();
    }
    paintIcons(document);
  }
  function routeNative(route) {
    if (!route)
      return false;
    var at = route.indexOf(":"), ref = at >= 0 ? route.slice(at + 1) : "";
    if (route === "focus") {
      go("today");
      if (state.focus)
        showFocus();
      return true;
    }
    if (route.startsWith("start:")) {
      go("today");
      var t = getTask(ref);
      if (t && !t.done) {
        begin(t.id, 25);
      }
      return true;
    }
    if (route.startsWith("habit:")) {
      go("today");
      if (getHabit(ref))
        habitEditor(ref);
      return true;
    }
    if (route.startsWith("journal:") || route.startsWith("month:") || route === "journal") {
      go("journal", route === "journal" ? "" : route);
      return true;
    }
    if (route === "rules" || route === "today") {
      go(route);
      return true;
    }
    return false;
  }
  function onResume() {
    read();
    var route = bridge ? bridge.takeRoute() : "";
    if (routeNative(route))
      return;
    render();
    if (modal === "focus") {
      showFocus();
    } else if (modal === "apps") {
      paintPicker();
    } else if (modal === "settings") {
      var action = overlay.querySelector("[data-act=\"notifications\"]");
      if (action && notifsOn()) {
        action.parentNode.querySelector(".small").textContent = "เปิดรับการแจ้งเตือนแล้ว";
        action.textContent = "เปิดใช้งานแล้ว";
        action.disabled = true;
      }
    }
  }
  function addMenu() {
    sheet("add", "เพิ่มในวันนี้", "<div class=\"options\"><button class=\"option\" data-act=\"new-task\"><span class=\"icon-soft\">" + icon("task") + "</span><span class=\"row-main\"><strong>งาน</strong><small>สิ่งที่ต้องทำให้เสร็จ เช่น อ่านบทที่ 3</small></span>" + icon("right") + "</button><button class=\"option\" data-act=\"new-habit\"><span class=\"icon-soft peach\">" + icon("leaf") + "</span><span class=\"row-main\"><strong>กิจวัตร</strong><small>สิ่งที่ทำซ้ำ เช่น ดื่มน้ำหรือออกกำลังกาย</small></span>" + icon("right") + "</button></div>");
  }
  function handle(action, ref) {
    switch (action) {
    case "pick-time":
      openTimePicker(ref);
      break;
    case "preset-time":
      el("clock-hour").value = ref.slice(0, 2);
      el("clock-minute").value = ref.slice(3);
      el("clock-error").textContent = "";
      markTimePreset();
      break;
    case "apply-time":
      applyTime();
      break;
    case "clear-time":
      closeTimePicker(true, "");
      break;
    case "cancel-time":
      closeTimePicker(false);
      break;
    case "edit-rule":
      editRule(ref);
      break;
    case "use-rule":
    case "remove-rule":
      updateMode(detail, action === "use-rule" ? ref : "remove");
      closeSheet();
      render();
      toast(action === "use-rule" ? "บันทึกกฎแล้ว" : "เอาแอปออกจากรายการแล้ว");
      break;
    case "nav-today":
      go("today");
      break;
    case "nav-journal":
      go("journal");
      break;
    case "nav-rules":
      go("rules");
      break;
    case "close":
      closeSheet();
      break;
    case "add":
      addMenu();
      break;
    case "filter":
      filter = ref;
      render();
      break;
    case "new-task":
      taskEditor("");
      break;
    case "edit-task":
      taskEditor(ref);
      break;
    case "save-task":
      saveTask(ref);
      break;
    case "toggle-task":
      toggleTask(ref);
      break;
    case "task-detail":
      taskDetail(ref);
      break;
    case "new-habit":
      habitEditor("");
      break;
    case "edit-habit":
      habitEditor(ref);
      break;
    case "save-habit":
      saveHabit(ref);
      break;
    case "habit-plus":
      addHabitCount(ref);
      break;
    case "delete-task":
      removeItem("task", ref);
      break;
    case "delete-habit":
      removeItem("habit", ref);
      break;
    case "confirm-delete-task":
    case "confirm-delete-habit":
      var prop = action.endsWith("-task") ? "tasks" : "habits";
      read();
      put(prop, state[prop].filter(function (x) {
        return x.id !== ref;
      }));
      closeSheet();
      render();
      toast("ลบแล้ว");
      break;
    case "quick-5":
      begin(ref, 5);
      break;
    case "duration":
      showDuration(ref);
      break;
    case "start-custom":
      var minutes = Number(v("focus-minutes"));
      if (!Number.isInteger(minutes) || minutes < 1 || minutes > 240) {
        invalid("เลือกระยะเวลา 1–240 นาที", "focus-minutes");
        break;
      }
      begin(ref, minutes);
      break;
    case "set-minutes":
      var amount = overlay.querySelector(".duration-field input");
      amount.value = ref;
      markDuration(amount);
      break;
    case "focus":
      showFocus();
      break;
    case "end-options":
      endOptions();
      break;
    case "finish-done":
    case "finish-keep":
      finish(ref, action === "finish-done", v("task-note"));
      break;
    case "extend":
      if (bridge) {
        bridge.extendFocus(5);
        read();
      } else if (state.focus) {
        state.focus.end = Math.max(Date.now(), state.focus.end) + 300000;
        saveLocal();
      }
      render();
      showFocus();
      break;
    case "guard-settings":
      if (bridge)
        bridge.openGuardSettings();
      else
        toast("เปิดสิทธิ์นี้จากแอปบน Android");
      break;
    case "apps":
      appsPicker();
      break;
    case "pick-app":
      var a = appList.find(function (x) {
        return x.pkg === ref;
      });
      if (a) {
        updateMode(ref, state.rules.apps[ref] ? "remove" : "always", a.name);
        render();
        paintPicker();
      }
      break;
    case "break-settings":
      showBreak();
      break;
    case "save-break":
      var n = Number(v("break-min"));
      if (!Number.isInteger(n) || n < 1 || n > 120) {
        invalid("ระบุระยะพัก 1–120 นาที", "break-min");
        break;
      }
      read();
      put("rules", Object.assign({}, state.rules, { breakMinutes: n }));
      closeSheet();
      render();
      toast("ปรับเวลาพักแล้ว");
      break;
    case "settings":
      settings();
      break;
    case "notifications":
      if (bridge) {
        if (notifsOn()) {
          toast("เปิดรับการแจ้งเตือนแล้ว");
        } else
          bridge.requestNotifications();
      } else
        toast("เปิดสิทธิ์นี้จากแอปบน Android");
      break;
    case "save-settings":
      var daily = v("daily-time"), quietStart = v("quiet-start"), quietEnd = v("quiet-end");
      if (el("daily-on").checked && !daily) {
        invalid("เลือกเวลาสรุปประจำวัน", "daily-time");
        break;
      }
      if (!!quietStart !== !!quietEnd) {
        invalid("กรอกทั้งเวลาเริ่มและเวลาสิ้นสุดช่วงเงียบ", quietStart ? "quiet-end" : "quiet-start");
        break;
      }
      read();
      put("rules", Object.assign({}, state.rules, {
        dailyTime: el("daily-on").checked ? daily : "",
        quietStart: quietStart,
        quietEnd: quietEnd
      }));
      closeSheet();
      render();
      toast("บันทึกการตั้งค่าแล้ว");
      break;
    }
  }
  document.addEventListener("click", function (e) {
    if (e.target === overlay) {
      if (!closeTimePicker(false)) closeSheet();
      return;
    }
    var b = e.target.closest("[data-act]");
    if (b) {
      handle(b.dataset.act, b.dataset.id || "");
    }
  });
  document.addEventListener("input", function (e) {
    if (e.target.id === "clock-hour" || e.target.id === "clock-minute") {
      el("clock-error").textContent = "";
      markTimePreset();
    }
    if (e.target.matches(".duration-field input")) markDuration(e.target);
    if (e.target.id === "app-search") {
      appSearch = e.target.value;
      paintPicker();
    }
  });
  document.addEventListener("change", function (e) {
    if (e.target.name === "habit-period") el("period-help").textContent = periodHelp(e.target.value);
  });
  function selectClockDigits(e) {
    if (e.target.id === "clock-hour" || e.target.id === "clock-minute") e.target.select();
  }
  overlay.addEventListener("focusin", selectClockDigits);
  function back() {
    if (closeTimePicker(false)) return true;
    if (window.PordeeJournal && window.PordeeJournal.closeDialog && window.PordeeJournal.closeDialog())
      return true;
    if (modal) {
      closeSheet();
      return true;
    }
    if (tab !== "today") {
      go("today");
      return true;
    }
    return false;
  }
  document.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && (e.target.id === "clock-hour" || e.target.id === "clock-minute")) { e.preventDefault(); applyTime(); }
    if (e.key === "Escape" && back())
      e.preventDefault();
    if (e.key === "Tab" && modal) {
      var nodes = overlay.querySelector(".sheet:not([hidden])").querySelectorAll("button:not([disabled]),input:not([type=hidden]):not([disabled]),textarea,select");
      if (!nodes.length)
        return;
      var first = nodes[0], last = nodes[nodes.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  });
  var prevSec = -1, lastDate = today();
  setInterval(function () {
    if (document.hidden)
      return;
    var nowDay = today();
    if (nowDay !== lastDate) {
      lastDate = nowDay;
      render();
    }
    if (!state || !state.focus) {
      prevSec = -1;
      return;
    }
    var sec = remaining();
    if (el("hero-clock"))
      el("hero-clock").textContent = asClock(sec);
    if (el("focus-clock"))
      el("focus-clock").textContent = asClock(sec);
    if (sec === 0 && prevSec !== 0) {
      if (bridge) read();
      render();
      if (modal === "focus")
        showFocus();
    }
    prevSec = sec;
  }, 500);
  window.Pordee = { toast: toast };
  window.onNativeResume = onResume;
  window.onNativeBack = back;
  onResume();
}());
