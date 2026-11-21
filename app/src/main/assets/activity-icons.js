(function () {
  "use strict";
  var types = [
    { key: "water", label: "ดื่มน้ำ", match: /ดื่มน้ำ|กินน้ำ|จิบน้ำ|ทานน้ำ|น้ำเปล่า|^น้ำ$|\b(water|hydrate|hydration)\b/i,
      path: "M12 2.5C10 6 5.5 10.5 5.5 14.8a6.5 6.5 0 0 0 13 0C18.5 10.5 14 6 12 2.5z M9 15a3 3 0 0 0 3 3" },
    { key: "run", label: "เดิน / วิ่ง", match: /วิ่ง|เดิน|จ๊อกกิ้ง|\b(run|running|jog|jogging|walk|walking)\b/i,
      path: "M16.5 3a1.6 1.6 0 1 1-.01 0z M5 10l4-3 4.4.8 2.7 4.2 3.9 1 M13.4 8l-3.6 6 4.4 3-.9 4 M10 14l-3 4-5 1" },
    { key: "exercise", label: "ออกกำลังกาย", match: /ออกกำลัง|ฟิตเนส|ยกเวท|เล่นเวท|วิดพื้น|ปั่นจักรยาน|ว่ายน้ำ|ฟุตบอล|กีฬา|\b(exercise|gym|workout|fitness|swim|cycling)\b/i,
      path: "M2 10v4 M5 7v10 M8 5v14 M8 12h8 M16 5v14 M19 7v10 M22 10v4" },
    { key: "read", label: "อ่าน / เรียน", match: /อ่าน|เรียน|ทบทวน|ท่องศัพท์|\b(read|reading|study|studying)\b/i,
      path: "M3 5q5-1.8 9 1.5Q16 3.2 21 5v14q-5-1.8-9 1-4-2.8-9-1z M12 6.5V20" },
    { key: "sleep", label: "พักผ่อน", match: /นอน|งีบ|พักผ่อน|\b(sleep|bedtime|nap)\b/i,
      path: "M20.5 14.5A8.8 8.8 0 0 1 9.5 3a9 9 0 1 0 11 11.5z" },
    { key: "food", label: "อาหาร", match: /อาหาร|กินข้าว|ทานข้าว|ผัก|ผลไม้|\b(meal|food|breakfast|lunch|dinner)\b/i,
      path: "M4 3v5a3 3 0 0 0 6 0V3 M7 3v18 M19 21V3c-3 2-4 6-4 10h4" },
    { key: "leaf", label: "ทั่วไป", path: "M4.5 17C3 9 9 5 20 4c-.6 11.5-5.3 17-11 15.1 M4 21l11-11" }
  ];
  function resolve(title, override) {
    return types.find(function (t) { return t.key === override; }) ||
      types.find(function (t) { return t.match && t.match.test(String(title || "").trim()); }) || types[types.length - 1];
  }
  function glyph(type, size) {
    return '<svg aria-hidden="true" width="' + size + '" height="' + size + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="' + type.path + '"/></svg>';
  }
  function app(pkg, name) {
    var first = Array.from(String(name || pkg || "•").trim())[0] || "•";
    var fallback = document.createElement("span");
    fallback.className = "app-monogram";
    fallback.setAttribute("aria-hidden", "true");
    fallback.textContent = first;
    if (window.Native && /^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*$/.test(pkg || "")) {
      var img = document.createElement("img");
      img.alt = "";
      img.setAttribute("loading", "lazy");
      img.src = "/icons/" + pkg + ".png";
      img.setAttribute("onerror", "this.remove()");
      fallback.appendChild(img);
    }
    return fallback.outerHTML;
  }
  window.LockInIcons = { types: types, resolve: resolve, glyph: glyph, app: app };
}());
