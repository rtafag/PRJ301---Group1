const fs = require('fs');
const path = require('path');

function escapeXml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function makeTextSp({ id, name, x, y, cx, cy, paragraphs, bgClr = null, borderClr = null, align = 'l' }) {
  let fillXml = bgClr
    ? `<a:solidFill><a:srgbClr val="${bgClr}"/></a:solidFill>`
    : '<a:noFill/>';

  let lnXml = borderClr
    ? `<a:ln w="12700"><a:solidFill><a:srgbClr val="${borderClr}"/></a:solidFill></a:ln>`
    : '<a:ln><a:noFill/></a:ln>';

  let geom = bgClr ? 'roundRect' : 'rect';
  let avLst = bgClr ? '<a:avLst><a:gd name="adj" fmla="val 20000"/></a:avLst>' : '<a:avLst/>';

  let pXml = paragraphs.map(p => {
    let pAlgn = p.align || align;
    let bulletXml = p.bullet ? '<a:buChar char="•"/>' : '<a:buNone/>';
    let marL = p.bullet ? 'marL="285750" indent="-285750"' : '';

    let runs = (p.runs || [{ t: p.text || '', sz: p.sz, b: p.b, clr: p.clr, i: p.i }]).map(r => `
      <a:r>
        <a:rPr lang="vi-VN" sz="${r.sz || 1400}" b="${r.b ? '1' : '0'}" i="${r.i ? '1' : '0'}">
          <a:solidFill><a:srgbClr val="${r.clr || 'FFFFFF'}"/></a:solidFill>
          <a:latin typeface="Segoe UI"/>
          <a:ea typeface="Segoe UI"/>
        </a:rPr>
        <a:t>${escapeXml(r.t || '')}</a:t>
      </a:r>
    `).join('');

    return `
      <a:p>
        <a:pPr algn="${pAlgn}" ${marL}>
          ${bulletXml}
        </a:pPr>
        ${runs}
      </a:p>
    `;
  }).join('');

  return `
    <p:sp>
      <p:nvSpPr>
        <p:cNvPr id="${id}" name="${escapeXml(name)}"/>
        <p:cNvSpPr txBox="1"/>
        <p:nvPr/>
      </p:nvSpPr>
      <p:spPr>
        <a:xfrm>
          <a:off x="${x}" y="${y}"/>
          <a:ext cx="${cx}" cy="${cy}"/>
        </a:xfrm>
        <a:prstGeom prst="${geom}">${avLst}</a:prstGeom>
        ${fillXml}
        ${lnXml}
      </p:spPr>
      <p:txBody>
        <a:bodyPr wrap="square" rtlCol="0">
          <a:spAutoFit/>
        </a:bodyPr>
        <a:lstStyle/>
        ${pXml}
      </p:txBody>
    </p:sp>
  `;
}

function makeHeader(title, subtitle, tagText = null) {
  const list = [];
  let yOffset = 500000;
  if (tagText) {
    list.push(makeTextSp({
      id: 99,
      name: "Header Tag",
      x: 812800,
      y: yOffset,
      cx: 6000000,
      cy: 280000,
      paragraphs: [{
        runs: [{ t: tagText.toUpperCase(), sz: 1200, b: true, clr: "F59E0B" }]
      }]
    }));
    yOffset += 260000;
  }

  list.push(makeTextSp({
    id: 100,
    name: "Header Title",
    x: 812800,
    y: yOffset,
    cx: 10500000,
    cy: 520000,
    paragraphs: [{
      runs: [{ t: title, sz: 2400, b: true, clr: "EEF2F8" }]
    }]
  }));

  list.push(makeTextSp({
    id: 101,
    name: "Header Subtitle",
    x: 812800,
    y: yOffset + 500000,
    cx: 10500000,
    cy: 350000,
    paragraphs: [{
      runs: [{ t: subtitle, sz: 1400, clr: "38BDF8" }]
    }]
  }));

  return list;
}

function updateSlideXml(slideNum, newShapesXml) {
  const filePath = path.join(__dirname, 'template_extracted', 'ppt', 'slides', `slide${slideNum}.xml`);
  let xml = fs.readFileSync(filePath, 'utf-8');

  const spTreeMatch = xml.match(/<p:spTree>([\s\S]*?)<\/p:spTree>/);
  if (!spTreeMatch) return;
  const inner = spTreeMatch[1];

  const nvGrp = inner.match(/<p:nvGrpSpPr>[\s\S]*?<\/p:nvGrpSpPr>/)?.[0] || '';
  const grpSpPr = inner.match(/<p:grpSpPr>[\s\S]*?<\/p:grpSpPr>/)?.[0] || '';
  const pics = [...inner.matchAll(/<p:pic>[\s\S]*?<\/p:pic>/g)].map(m => m[0]);

  // Keep shapes that are large containers or special graphics
  const sps = [...inner.matchAll(/<p:sp>[\s\S]*?<\/p:sp>/g)].map(m => m[0]);
  const keptSps = [];

  sps.forEach(sp => {
    const ext = sp.match(/<a:ext cx="(\d+)" cy="(\d+)"\/>/);
    const off = sp.match(/<a:off x="(\d+)" y="(\d+)"\/>/);
    if (ext && off) {
      const cx = parseInt(ext[1]), cy = parseInt(ext[2]);
      if (cx > 1500000 && cy > 1000000) {
        keptSps.push(sp); // Card background
      } else if (cy > 700000 && cx > 700000) {
        keptSps.push(sp); // Icon container
      } else if (slideNum === 1 && (cx > 2000000 || (sp.includes('F59E0B') && cx < 200000))) {
        keptSps.push(sp);
      } else if ([6, 7, 8, 12, 13, 16, 19, 26, 27].includes(slideNum) && (sp.includes('ln') || cx < 200000)) {
        keptSps.push(sp);
      }
    }
  });

  const newSpTree = `
    <p:spTree>
      ${nvGrp}
      ${grpSpPr}
      ${pics.join('\n')}
      ${keptSps.join('\n')}
      ${newShapesXml.join('\n')}
    </p:spTree>
  `;

  xml = xml.replace(/<p:spTree>[\s\S]*?<\/p:spTree>/, newSpTree);
  fs.writeFileSync(filePath, xml, 'utf-8');
  console.log(`Updated slide ${slideNum}`);
}

// ==========================================
// BUILD ALL 30 SLIDES CONTENT
// ==========================================

console.log("Generating 30 slides content...");

// SLIDE 1: COVER
updateSlideXml(1, [
  makeTextSp({
    id: 101, name: "Tag", x: 812800, y: 1750000, cx: 5000000, cy: 380000,
    paragraphs: [{ runs: [{ t: "BÁO CÁO NGHIÊN CỨU & PHÁT TRIỂN (RBL)", sz: 1400, b: true, clr: "F59E0B" }] }]
  }),
  makeTextSp({
    id: 102, name: "Main Title", x: 812800, y: 2200000, cx: 5800000, cy: 1200000,
    paragraphs: [
      { runs: [{ t: "AITA: HỆ THỐNG TRỢ GIẢNG AI", sz: 2800, b: true, clr: "EEF2F8" }] },
      { runs: [{ t: "CHẤM MÃ NGUỒN JAVA TỰ ĐỘNG", sz: 2400, b: true, clr: "38BDF8" }] }
    ]
  }),
  makeTextSp({
    id: 103, name: "Subtitle", x: 812800, y: 3500000, cx: 5800000, cy: 800000,
    paragraphs: [
      { runs: [{ t: "Môn học: Java Web Applications (PRJ301) — Nhóm 1", sz: 1500, b: true, clr: "FFFFFF" }] },
      { runs: [{ t: "Phân tích cú pháp trừu tượng (AST) & Trí tuệ nhân tạo (AI Feedback)", sz: 1400, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 104, name: "Button", x: 812800, y: 4500000, cx: 4000000, cy: 500000,
    bgClr: "15213B", borderClr: "38BDF8",
    paragraphs: [{ align: "ctr", runs: [{ t: "ĐỘI NGŨ THỰC HIỆN: TV1 — TV2 — TV3 — TV4 — TV5", sz: 1300, b: true, clr: "38BDF8" }] }]
  })
]);

// SLIDE 2: 4-STEP PIPELINE
updateSlideXml(2, [
  ...makeHeader("KIẾN TRÚC HỆ THỐNG & PIPELINE DỮ LIỆU", "Luồng dữ liệu khép kín từ tiếp nhận bài nộp đến xuất báo cáo sư phạm", "Pipeline Tổng Thể"),
  makeTextSp({
    id: 201, name: "Card 1", x: 920000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "1. Tiếp Nhận (Portal)", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Sinh viên nộp ZIP/.java qua JSP", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Filter chặn Zip-slip an toàn", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Băm SHA-256 chống nộp trùng", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Phụ trách: TV4 & TV5", sz: 1100, b: true, clr: "0077B6" }] }
    ]
  }),
  makeTextSp({
    id: 202, name: "Card 2", x: 3640000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "2. Cô Lập (Sandbox)", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Docker Sandbox cô lập tuyệt đối", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Ngắt mạng hoàn toàn (--network=none)", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Giới hạn CPU 50%, RAM 512MB", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Barem: Sandbox 30%", sz: 1100, b: true, clr: "D97706" }] }
    ]
  }),
  makeTextSp({
    id: 203, name: "Card 3", x: 6360000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "3. AST Core Engine", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "JavaParser 3.25.10 bóc tách cây AST", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "15 AST Visitors bắt lỗi logic", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Xuất JSON Schema chuẩn hóa", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Phụ trách: TV1 & TV3", sz: 1100, b: true, clr: "0077B6" }] }
    ]
  }),
  makeTextSp({
    id: 204, name: "Card 4", x: 9080000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "4. AI & Lưu Trữ CSDL", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "AIService gọi OpenAI / Gemini", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Local Fallback ngoại tuyến 100%", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "ORM lưu 6 bảng SQL Server", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Phụ trách: TV2 & TV3", sz: 1100, b: true, clr: "10B981" }] }
    ]
  })
]);

// SLIDE 3: SECTION 1
updateSlideXml(3, [
  ...makeHeader("PHẦN I: TỔ CHỨC ĐỘI NGŨ & BAREM ĐÁNH GIÁ", "Cơ chế phân vai kỹ thuật độc lập và phân bổ trọng số điểm môn PRJ301", "Tổng Quan Phân Công"),
  makeTextSp({
    id: 301, name: "Desc", x: 812800, y: 2200000, cx: 10000000, cy: 3000000,
    paragraphs: [
      { runs: [{ t: "Cam Kết Tuân Thủ Barem Môn PRJ301 Của Nhóm 1:", sz: 1800, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Mô hình MVC2 & Clean Code (40% Barem): Tách bạch Model (DAO), Controller (Servlet) và View (JSP).", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Môi trường Sandbox Compliance (30% Barem): Cô lập an toàn tuyệt đối qua Docker, ngắt mạng hoàn toàn.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Phản biện cá nhân Peer Defense (30% Barem): Cả 5 thành viên đều tự tin bảo vệ chuyên sâu module của mình.", sz: 1400, clr: "FFFFFF" }] },
      { runs: [{ t: "Mỗi thành viên chịu trách nhiệm 1 module độc lập xuyên suốt từ Tuần 1 đến Tuần 10.", sz: 1400, i: true, clr: "F59E0B" }] }
    ]
  })
]);

// SLIDE 21: 4 TEAM MEMBERS
updateSlideXml(21, [
  ...makeHeader("PHÂN CÔNG 5 THÀNH VIÊN THEO CHUYÊN MÔN", "Bảo đảm từng thành viên phụ trách một mảng kỹ thuật cốt lõi", "Nhân Sự Đề Tài"),
  makeTextSp({
    id: 2101, name: "TV1", x: 920000, y: 3100000, cx: 2200000, cy: 2000000,
    paragraphs: [
      { runs: [{ t: "TV1 (Trưởng nhóm)", sz: 1500, b: true, clr: "0B1324" }] },
      { runs: [{ t: "AST Engine Lead", sz: 1300, b: true, clr: "0077B6" }] },
      { bullet: true, runs: [{ t: "Kiến trúc hệ thống", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "JavaParser AST Core", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Chuẩn JSON Schema", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Barem: Application (25đ)", sz: 1100, b: true, clr: "D97706" }] }
    ]
  }),
  makeTextSp({
    id: 2102, name: "TV2", x: 3640000, y: 3100000, cx: 2200000, cy: 2000000,
    paragraphs: [
      { runs: [{ t: "TV2 (Backend & DB)", sz: 1500, b: true, clr: "0B1324" }] },
      { runs: [{ t: "Database Engineer", sz: 1300, b: true, clr: "0077B6" }] },
      { bullet: true, runs: [{ t: "ERD 6 bảng SQL Server", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "6 Lớp DAO JDBC", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "ORM Transactional", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Barem: Database Design (10đ)", sz: 1100, b: true, clr: "D97706" }] }
    ]
  }),
  makeTextSp({
    id: 2103, name: "TV3", x: 6360000, y: 3100000, cx: 2200000, cy: 2000000,
    paragraphs: [
      { runs: [{ t: "TV3 (AI & Rule)", sz: 1500, b: true, clr: "0B1324" }] },
      { runs: [{ t: "AI Rule Engineer", sz: 1300, b: true, clr: "0077B6" }] },
      { bullet: true, runs: [{ t: "15 AST Rule Visitors", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "AIService Java / Fallback", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "0.00% False Positive", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Barem: AI Integration (10đ)", sz: 1100, b: true, clr: "D97706" }] }
    ]
  }),
  makeTextSp({
    id: 2104, name: "TV4_5", x: 9080000, y: 3100000, cx: 2200000, cy: 2000000,
    paragraphs: [
      { runs: [{ t: "TV4 & TV5 (UI/DevOps)", sz: 1500, b: true, clr: "0B1324" }] },
      { runs: [{ t: "Frontend & Sandbox Lead", sz: 1300, b: true, clr: "0077B6" }] },
      { bullet: true, runs: [{ t: "MVC2 Portal JSP (TV4)", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Docker Sandbox ngắt mạng (TV5)", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Băm SHA-256 & CI/CD (TV5)", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Barem: Sandbox 30% + UI 10đ", sz: 1100, b: true, clr: "D97706" }] }
    ]
  })
]);

// SLIDE 9: BAREM ĐIỂM CHI TIẾT
updateSlideXml(9, [
  ...makeHeader("BẢNG BAREM ĐÁNH GIÁ ĐỒ ÁN MÔN PRJ301", "Chi tiết các hạng mục chấm điểm và giải pháp kỹ thuật đáp ứng của Nhóm 1", "Barem Điểm & Cam Kết"),
  makeTextSp({
    id: 901, name: "Table View", x: 812800, y: 1800000, cx: 10500000, cy: 4500000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "1. Kiến Trúc MVC2 & Clean Code (Trọng số 40%):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Mã nguồn phân tách rõ ràng: model/, dao/, controller/, service/, rule/, util/.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tuyệt đối không dùng Scriptlet Java trong JSP; dùng JSTL và Expression Language (EL).", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "2. Môi Trường Sandbox Compliance (Trọng số 30%):", sz: 1600, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "Mã nguồn sinh viên được biên dịch và phân tích trong Docker Container cách ly tuyệt đối.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Giới hạn RAM 512MB, CPU 50%, Timeout 10s, cờ --network=none ngăn chặn mã độc ra ngoài.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "3. Phản Biện Chuyên Sâu Peer Defense (Trọng số 30%):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Cả 5 thành viên nắm vững luồng dữ liệu, cơ chế AST, thuật toán băm SHA-256 và ORM Transaction.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 6: SƠ ĐỒ TRUNG CHUYỂN DỮ LIỆU
updateSlideXml(6, [
  ...makeHeader("SƠ ĐỒ TRUNG CHUYỂN DỮ LIỆU ĐA MODULE", "Khế ước giao tiếp không nghẽn luồng giữa các phân hệ của 5 thành viên", "Kiến Trúc Tích Hợp"),
  makeTextSp({
    id: 601, name: "Module Flow", x: 812800, y: 4800000, cx: 10500000, cy: 1500000,
    bgClr: "131B2E", borderClr: "38BDF8",
    paragraphs: [
      { align: "ctr", runs: [{ t: "Luồng Dữ Liệu Đồng Bộ:", sz: 1400, b: true, clr: "F59E0B" }] },
      { align: "ctr", runs: [{ t: "Portal (TV4) → Security Filter (TV5) → Docker Sandbox (TV5) → AST Core (TV1) → 15 Rule Visitors (TV3) → AIService Host (TV3) → SQL Server (TV2)", sz: 1200, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 20: SECTION 2 - BÁO CÁO TỪNG THÀNH VIÊN
updateSlideXml(20, [
  ...makeHeader("PHẦN II: BÁO CÁO KỸ THUẬT CHI TIẾT TỪNG THÀNH VIÊN", "Hành trình phát triển qua 4 mốc: 3 tuần đầu (PR1), 3 tuần sau (PR2), 3 tuần cuối (PR3) và Final Demo", "Tiến Độ RBL"),
  makeTextSp({
    id: 2001, name: "Phase Info", x: 812800, y: 2200000, cx: 10000000, cy: 3500000,
    paragraphs: [
      { runs: [{ t: "Lộ Trình Phát Triển 10 Tuần Đồ Án RBL:", sz: 1800, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "3 Tuần Đầu (Tuần 1 - 3: PR1): Khởi tạo kiến trúc, thiết kế ERD, khảo sát danh mục Rule và UI prototype.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "3 Tuần Sau (Tuần 4 - 6: PR2): Hoàn thiện Core Parser, tầng DAO, Rule Engine v1 và Submission Portal.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "3 Tuần Cuối (Tuần 7 - 9: PR3): Tích hợp 15 AST Visitors, Docker Sandbox ngắt mạng, AIService và ORM Transaction.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tuần 10 (Final Demo): Benchmark 0% False Positive, Live Demo 4 kịch bản lỗi mẫu và bảo vệ trước Hội đồng.", sz: 1400, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 4: TV1 - 3 TUẦN ĐẦU (PR1)
updateSlideXml(4, [
  ...makeHeader("TV1 (Trưởng nhóm) — 3 Tuần Đầu (Tuần 1 - 3: PR1)", "Xác định kiến trúc luồng dữ liệu AST và lên kế hoạch bộ bóc tách cú pháp", "TV1: PR1"),
  makeTextSp({
    id: 401, name: "Card 1", x: 1000000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Sơ Đồ Khối Hệ Thống", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Thiết kế sơ đồ kiến trúc tổng thể AITA.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Phân định ranh giới giữa AST Engine, SQL Server và JSP Portal.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Thống nhất chuẩn giao tiếp giữa 5 thành viên.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 402, name: "Card 2", x: 4600000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Đặc Tả Luồng Dữ Liệu", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Định nghĩa luồng trung chuyển: Code → AST Tree → JSON Schema → CSDL → UI.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Định dạng cấu trúc metadata đầu ra cho TV3.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Khử rủi ro xung đột dữ liệu giữa các module.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 403, name: "Card 3", x: 8200000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Khảo Sát JavaParser", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Khảo sát thư viện JavaParser 3.25.10.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Cam kết không dùng Regex quét text thô.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Thuyết trình bảo vệ kiến trúc tại mốc PR1.", sz: 1300, clr: "8EA0BD" }] }
    ]
  })
]);

// SLIDE 5: TV1 - 3 TUẦN SAU (PR2)
updateSlideXml(5, [
  ...makeHeader("TV1 (Trưởng nhóm) — 3 Tuần Sau (Tuần 4 - 6: PR2)", "Triển khai parser với JavaParser và chuẩn hóa dữ liệu JSON Schema", "TV1: PR2"),
  makeTextSp({
    id: 501, name: "Card 1", x: 1000000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "StaticJavaParser Core", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Biên dịch thành công module bóc tách mã nguồn Java.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Chuyển chuỗi mã thành đối tượng CompilationUnit.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Bắt lỗi cú pháp (Syntax Error) tức thì.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 502, name: "Card 2", x: 4600000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Syntax Visitor Khung", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Xây dựng khung mẫu duyệt cây VoidVisitorAdapter.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Bóc tách danh sách ClassDeclaration, MethodDeclaration.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Đo lường độ sâu phân tích cú pháp tĩnh.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 503, name: "Card 3", x: 8200000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "JSON Schema Chuẩn Hóa", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Xuất dữ liệu: {classes, methods, syntaxValid}.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Thời gian parse siêu tốc: 80 - 150ms / file Java.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Demo Live phân tích file Java thật tại PR2.", sz: 1300, clr: "8EA0BD" }] }
    ]
  })
]);

// SLIDE 14: TV1 - 3 TUẦN CUỐI (PR3) & TUẦN 10 (FINAL DEMO)
updateSlideXml(14, [
  ...makeHeader("TV1 (Trưởng nhóm) — 3 Tuần Cuối (PR3) & Tuần 10 (Final Demo)", "Tích hợp đa file, xử lý cấu trúc phức tạp và điều phối phát hành toàn hệ thống", "TV1: PR3 & Final"),
  makeTextSp({
    id: 1401, name: "Features List", x: 812800, y: 2200000, cx: 10500000, cy: 4000000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "1. Tích Hợp Đa File ZIP (PR3):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Mở rộng Parser hỗ trợ gói nộp ZIP chứa nhiều file Java liên kết quan hệ kế thừa và import package.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "2. Xử Lý Các Cấu Trúc Mã Nâng Cao (PR3):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Bóc tách chính xác các inner class, anonymous class, nested loops và lambda expression không gây nghẽn luồng.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "3. Tích Hợp Sâu Cùng 15 Rule Visitor Của TV3 (PR3):", sz: 1600, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "Kết nối 15 AST Visitors vào pipeline, đảm bảo mỗi file Java chỉ duyệt 1 lượt duy nhất qua cây cú pháp.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "4. Rà Soát Release Package & Chủ Trì Bảo Vệ (Tuần 10 - Final Demo):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Kiểm tra tính toàn vẹn 44 file Java, CSDL và tài liệu README; điều phối phần trình bày của 5 thành viên.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 7: TV2 - 3 TUẦN ĐẦU (PR1)
updateSlideXml(7, [
  ...makeHeader("TV2 (Backend & Database) — 3 Tuần Đầu (Tuần 1 - 3: PR1)", "Thiết kế ERD 6 bảng chuẩn hóa 3NF và triển khai CSDL thật trên Microsoft SQL Server", "TV2: PR1"),
  makeTextSp({
    id: 701, name: "Card 1", x: 920000, y: 3200000, cx: 2200000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "ERD 6 Bảng 3NF", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Users: Tài khoản, quyền hạn.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Submission: Bài nộp, hash.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "AnalysisResult: AI feedback.", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 702, name: "Card 2", x: 3640000, y: 3200000, cx: 2200000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Bảng Vi Phạm & Điểm", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "RuleDefinition: 15 rules.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "RuleViolation: Dòng lỗi, fix.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Score: Điểm cuối (0-10đ).", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 703, name: "Card 3", x: 6360000, y: 3200000, cx: 2200000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "SQL Server Thật", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Chạy script DDL tạo bảng.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Ràng buộc PK, FK đầy đủ.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Demo Live trên SSMS tại PR1.", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 704, name: "Card 4", x: 9080000, y: 3200000, cx: 2200000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "JWT Authentication", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Khởi tạo JwtUtil tạo token.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Xác thực token khi đăng nhập.", sz: 1200, clr: "2A3A5C" }] },
      { runs: [{ t: "Đạt 10đ DB Design", sz: 1100, b: true, clr: "D97706" }] }
    ]
  })
]);

// SLIDE 8: TV2 - 3 TUẦN SAU (PR2)
updateSlideXml(8, [
  ...makeHeader("TV2 (Backend & Database) — 3 Tuần Sau (Tuần 4 - 6: PR2)", "Hoàn thiện tầng DAO/JDBC an toàn và xây dựng bộ dữ liệu mẫu 200 bản ghi", "TV2: PR2"),
  makeTextSp({
    id: 801, name: "DAO Details", x: 812800, y: 2200000, cx: 6000000, cy: 4000000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "Hoàn Thiện 6 Lớp DAO Chuẩn JDBC:", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "UsersDAO: Quản lý người dùng và phân quyền.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "SubmissionDAO: Lưu nhận bài nộp kèm file_hash.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "AnalysisResultDAO & RuleViolationDAO: Lưu chi tiết vi phạm.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "RuleDefinitionDAO & ScoreDAO: Quản lý barem và điểm số.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "Tự Động Hóa Dữ Liệu & Bảo Mật:", sz: 1600, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "DatabaseSeeder & CSVHelper: Nạp 200 bài nộp mẫu, 1,100 users.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "100% dùng PreparedStatement chống triệt để SQL Injection.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 12: TV2 - 3 TUẦN CUỐI (PR3) & TUẦN 10 (FINAL DEMO)
updateSlideXml(12, [
  ...makeHeader("TV2 (Backend & Database) — 3 Tuần Cuối (PR3) & Tuần 10 (Final Demo)", "Kiến trúc ORM Transactional, đồng bộ trạng thái và bảo vệ tính toàn vẹn CSDL", "TV2: PR3 & Final"),
  makeTextSp({
    id: 1201, name: "ORM Details", x: 812800, y: 2200000, cx: 10500000, cy: 4000000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "1. Dịch Vụ GradingJobORMService Transactional (PR3):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Đảm bảo tính nguyên tử (Atomicity): Cập nhật Submission (status) ↔ AnalysisResult ↔ Score trong 1 giao dịch.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Nếu việc chấm bài hoặc gọi AI bị lỗi, dữ liệu tự động rollback, không để lại trạng thái rác.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "2. Toàn Vẹn CSDL Trước Hội Đồng (Tuần 10 - Final Demo):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Rà soát 100% khóa ngoại hợp lệ, đạt Zero Orphan Records (không có bản ghi mồ côi).", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Ràng buộc kiểm tra điểm số nghiêm ngặt trong khoảng từ 0.0 đến 10.0 điểm.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Truy vấn trực tiếp CSDL trên SSMS thời gian thực để chứng minh điểm bài nộp demo được lưu tức thì.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 10: TV3 - 3 TUẦN ĐẦU (PR1)
updateSlideXml(10, [
  ...makeHeader("TV3 (AI Rule & Detection) — 3 Tuần Đầu (Tuần 1 - 3: PR1)", "Khảo sát chuẩn công nghiệp SonarQube và kết nối thử nghiệm Cloud AI", "TV3: PR1"),
  makeTextSp({
    id: 1001, name: "PR1 Content", x: 812800, y: 2200000, cx: 10000000, cy: 3500000,
    paragraphs: [
      { runs: [{ t: "Định Hướng Xây Dựng Bộ Quy Tắc & Mô Hình AI:", sz: 1800, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Khảo sát chuẩn SonarQube Java Rules & PMD để lọc ra danh mục lỗi lập trình phổ biến nhất của sinh viên.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Xác định 3 nhóm lỗi trọng yếu: Lỗi sập app (Crash/Logic), Rò rỉ tài nguyên (Resource Leak) và Chuẩn code (Smell).", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Thử nghiệm kết nối gọi API OpenAI và Gemini với đoạn mã mẫu đơn giản.", sz: 1400, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Cam kết tại PR1: Sử dụng phân tích cây cú pháp AST thay vì regex để đạt độ chính xác cao nhất.", sz: 1400, i: true, clr: "F59E0B" }] }
    ]
  })
]);

// SLIDE 25: TV3 - 3 TUẦN SAU (PR2)
updateSlideXml(25, [
  ...makeHeader("TV3 (AI Rule & Detection) — 3 Tuần Sau (Tuần 4 - 6: PR2)", "Triển khai phiên bản Rule Engine v1 và bộ test case sơ khai", "TV3: PR2"),
  makeTextSp({
    id: 2501, name: "Card 1", x: 1000000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Các Rule Logic Ban Đầu", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Phát hiện Null Return (NPE).", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Cấp phát mảng trong loop (OOM).", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Kiểm tra chuẩn đặt tên class (CS).", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2502, name: "Card 2", x: 4600000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Quy Tắc Code Smell", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Phương thức dài quá 40 dòng (LONG_METHOD).", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Thiếu chú thích JavaDoc (COMMENT).", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Tự động tính toán điểm trừ theo mức độ.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2503, name: "Card 3", x: 8200000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Đo Lường & Đánh Giá", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Xây dựng 2 test case đầu tiên trong Node.js.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Nhận diện hạn chế của rule sơ khai đối với False Positive.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Lên kế hoạch chuyển sang 100% JavaParser.", sz: 1300, clr: "8EA0BD" }] }
    ]
  })
]);

// SLIDE 15: TV3 - 3 TUẦN CUỐI (PR3)
updateSlideXml(15, [
  ...makeHeader("TV3 (AI Rule & Detection) — 3 Tuần Cuối (Tuần 7 - 9: PR3)", "Đột phá 15 AST Visitors độc lập, AIService thuần Java & Prompt Engineering", "TV3: PR3 Đột Phá"),
  makeTextSp({
    id: 1501, name: "Step 1", x: 920000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "1. 15 AST Visitors", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Vô hạn (INF_LOOP): while(true), i--.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Logic: String ==, chia cho 0, if(a=b).", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Rò rỉ JDBC, empty catch, equals.", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 1502, name: "Step 2", x: 3640000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "2. Context Slicing", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Chỉ trích xuất 5 dòng quanh lỗi.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Tiết kiệm 85% token chi phí.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Phản hồi AI siêu tốc: dưới 1.5s.", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 1503, name: "Step 3", x: 6360000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "3. AIService Java", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Dùng HttpURLConnection thuần.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Kết nối song song OpenAI & Gemini.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Bảo mật key trong .env, timeout 15s.", sz: 1200, clr: "2A3A5C" }] }
    ]
  }),
  makeTextSp({
    id: 1504, name: "Step 4", x: 9080000, y: 3300000, cx: 2200000, cy: 1700000,
    paragraphs: [
      { runs: [{ t: "4. Local Fallback", sz: 1500, b: true, clr: "0B1324" }] },
      { bullet: true, runs: [{ t: "Tự động kích hoạt khi mất mạng.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Sinh giải thích Markdown sư phạm.", sz: 1200, clr: "2A3A5C" }] },
      { bullet: true, runs: [{ t: "Cung cấp code Before/After trực quan.", sz: 1200, clr: "2A3A5C" }] }
    ]
  })
]);

// SLIDE 28: TV3 - DANH MỤC 15 RULE
updateSlideXml(28, [
  ...makeHeader("BẢNG DANH MỤC 15 RULE LOGIC AST (TV3)", "Phân loại chi tiết mã rule, mức độ cảnh báo và mức điểm trừ", "Bảng Rule Definition"),
  makeTextSp({
    id: 2801, name: "Rule Grid", x: 812800, y: 1800000, cx: 10500000, cy: 4500000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "Nhóm 1: Lỗi Sập Ứng Dụng & Vòng Lặp Vô Hạn (Trừ nặng 2.0 - 10.0đ)", sz: 1400, b: true, clr: "EF4444" }] },
      { bullet: true, runs: [{ t: "INF_LOOP: Vòng lặp vô hạn (while true, i--) [-10.0đ] | NPE: Trả về null không an toàn [-2.0đ]", sz: 1200, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "DIV_ZERO: Chia cho hằng số 0 [-3.0đ] | MEM_LEAK: Cấp phát mảng trong vòng lặp [-1.5đ]", sz: 1200, clr: "FFFFFF" }] },
      { runs: [{ t: "Nhóm 2: Lỗi Logic Nghiêm Trọng & Rò Rỉ Tài Nguyên (Trừ 1.0 - 2.5đ)", sz: 1400, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "STR_EQ: So sánh chuỗi bằng == [-2.0đ] | ASSIGN_IN_COND: Gán nhầm trong điều kiện if [-2.5đ]", sz: 1200, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "RES_LEAK: Quên đóng Connection/Statement JDBC [-2.5đ] | EMPTY_CATCH: Nuốt ngoại lệ catch rỗng [-1.5đ]", sz: 1200, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "DEAD_CODE: Mã không bao giờ thực thi sau return [-1.0đ] | DUP_CODE: Khối lệnh trùng lặp [-1.0đ]", sz: 1200, clr: "FFFFFF" }] },
      { runs: [{ t: "Nhóm 3: Chuẩn Lập Trình Hướng Đối Tượng & Clean Code (Trừ 0.5 - 1.5đ)", sz: 1400, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "OVERRIDE: Thiếu annotation @Override [-1.0đ] | EQUALS_HASH: Có equals mà thiếu hashCode [-1.5đ]", sz: 1200, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "CS: Tên class không viết hoa chữ cái đầu [-0.5đ] | LONG_METHOD: Phương thức quá 40 dòng [-0.5đ] | COMMENT [-0.5đ]", sz: 1200, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 17: TV3 - TUẦN 10 (FINAL DEMO - 0% FP)
updateSlideXml(17, [
  ...makeHeader("TV3 (AI Rule & Detection) — Tuần 10 (Final Demo): 0% False Positive", "Minh chứng độ chính xác tuyệt đối qua bộ benchmark và 4 kịch bản lỗi mẫu", "TV3: Final Demo"),
  makeTextSp({
    id: 1701, name: "Left Bad Code", x: 1000000, y: 2200000, cx: 4800000, cy: 4000000,
    bgClr: "131B2E", borderClr: "EF4444",
    paragraphs: [
      { runs: [{ t: "Kiểm Thử Bad Code (15/15 Pass):", sz: 1600, b: true, clr: "EF4444" }] },
      { bullet: true, runs: [{ t: "Tỉ lệ phát hiện lỗi đúng (Recall / TPR): 100.0%.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Sample 01: Bắt 3 ca vô hạn (while true, bước nhảy i-- ngược chiều) → Trừ 10 điểm.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Sample 02: Bắt String ==, chia cho 0, gán nhầm trong if, catch rỗng.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Sample 03: Bắt rò rỉ JDBC, thiếu @Override, class viết thường.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Thời gian phân tích: 60 - 80ms / file.", sz: 1300, clr: "38BDF8" }] }
    ]
  }),
  makeTextSp({
    id: 1702, name: "Right Good Code", x: 6400000, y: 2200000, cx: 4800000, cy: 4000000,
    bgClr: "131B2E", borderClr: "10B981",
    paragraphs: [
      { runs: [{ t: "Kiểm Thử Clean Code (0.00% FP):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Tỉ lệ báo lỗi nhầm (False Positive Rate): 0.00%.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "5/5 ca kiểm thử mã nguồn sạch đạt 0 vi phạm.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Độ chính xác (Precision): Đạt trọn vẹn 100.0%.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Sample 04 (Good Code): Điểm số bài sạch đạt tuyệt đối 10.0 / 10.0 điểm.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Đạt điểm tối đa 10đ AI Integration.", sz: 1300, clr: "F59E0B" }] }
    ]
  })
]);

// SLIDE 11: TV3 - CHỈ SỐ KỸ THUẬT
updateSlideXml(11, [
  ...makeHeader("BỘ CHỈ SỐ KỸ THUẬT ĐỘT PHÁ CỦA TV3", "Thành quả nghiên cứu RBL đáp ứng trọn vẹn barem điểm AI Integration", "TV3: Số Liệu Thực Nghiệm"),
  makeTextSp({
    id: 1101, name: "Stat 1", x: 1000000, y: 3800000, cx: 2400000, cy: 2000000,
    paragraphs: [
      { align: "ctr", runs: [{ t: "15 / 15", sz: 2400, b: true, clr: "38BDF8" }] },
      { align: "ctr", runs: [{ t: "Rule Visitors Active", sz: 1300, b: true, clr: "FFFFFF" }] },
      { align: "ctr", runs: [{ t: "Duyệt cây AST 100% JavaParser", sz: 1100, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1102, name: "Stat 2", x: 3700000, y: 3800000, cx: 2400000, cy: 2000000,
    paragraphs: [
      { align: "ctr", runs: [{ t: "100.0%", sz: 2400, b: true, clr: "10B981" }] },
      { align: "ctr", runs: [{ t: "Độ Nhạy (Recall)", sz: 1300, b: true, clr: "FFFFFF" }] },
      { align: "ctr", runs: [{ t: "Bắt trọn 15/15 ca lỗi bad code", sz: 1100, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1103, name: "Stat 3", x: 6400000, y: 3800000, cx: 2400000, cy: 2000000,
    paragraphs: [
      { align: "ctr", runs: [{ t: "0.00%", sz: 2400, b: true, clr: "F59E0B" }] },
      { align: "ctr", runs: [{ t: "False Positive Rate", sz: 1300, b: true, clr: "FFFFFF" }] },
      { align: "ctr", runs: [{ t: "Không báo nhầm trên code sạch", sz: 1100, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1104, name: "Stat 4", x: 9100000, y: 3800000, cx: 2400000, cy: 2000000,
    paragraphs: [
      { align: "ctr", runs: [{ t: "< 80ms", sz: 2400, b: true, clr: "EEF2F8" }] },
      { align: "ctr", runs: [{ t: "Tốc Độ Phân Tích", sz: 1300, b: true, clr: "FFFFFF" }] },
      { align: "ctr", runs: [{ t: "Duyệt toàn bộ 15 rule siêu tốc", sz: 1100, clr: "8EA0BD" }] }
    ]
  })
]);

// SLIDE 24: TV4 - 3 TUẦN ĐẦU (PR1)
updateSlideXml(24, [
  ...makeHeader("TV4 (Frontend & MVC2) — 3 Tuần Đầu (Tuần 1 - 3: PR1)", "Khởi tạo thiết kế Wireframe Prototype, Use-Case Diagram & User Flow", "TV4: PR1"),
  makeTextSp({
    id: 2401, name: "Card 1", x: 1000000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Wireframe Submission", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Thiết kế giao diện nộp bài cho sinh viên.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Dashboard quản lý chấm điểm cho giảng viên.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Định hình bố cục bảng điểm rõ ràng.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2402, name: "Card 2", x: 4600000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "Use-case Diagram", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Actor Sinh viên: Nộp bài, xem điểm, xem giải thích.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Actor Giảng viên: Cấu hình rule, quản lý lớp.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Actor Hệ thống: Kích hoạt engine chấm tự động.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2403, name: "Card 3", x: 8200000, y: 3200000, cx: 2900000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "User Flow Điều Hướng", sz: 1600, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Luồng: Login → Portal nộp bài → Kết quả.", sz: 1300, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Tối ưu số bước thao tác cho người dùng.", sz: 1300, clr: "8EA0BD" }] },
      { runs: [{ t: "Đạt 10đ UI/UX Prototype", sz: 1200, b: true, clr: "F59E0B" }] }
    ]
  })
]);

// SLIDE 22: TV4 - 3 TUẦN SAU (PR2)
updateSlideXml(22, [
  ...makeHeader("TV4 (Frontend & MVC2) — 3 Tuần Sau (Tuần 4 - 6: PR2)", "Hoàn thiện Submission Portal thuần kiến trúc MVC2 với Servlet, JSP & Filter", "TV4: PR2"),
  makeTextSp({
    id: 2201, name: "Left Col", x: 1000000, y: 2200000, cx: 4800000, cy: 4000000,
    bgClr: "131B2E", borderClr: "38BDF8",
    paragraphs: [
      { runs: [{ t: "Tầng Điều Phối (Servlet & Filter):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "HomeController: Điều hướng trang chủ và dashboard.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "LoginController & LogoutController: Quản lý phiên làm việc.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "SubmissionController: Xử lý upload file multipart.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Filter kiểm tra Session đăng nhập, ngăn truy cập trái phép.", sz: 1300, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2202, name: "Right Col", x: 6400000, y: 2200000, cx: 4800000, cy: 4000000,
    bgClr: "131B2E", borderClr: "10B981",
    paragraphs: [
      { runs: [{ t: "Giao Diện JSP Chuẩn MVC2 (Không Scriptlet):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Sử dụng Expression Language (EL) ${...} hiển thị dữ liệu.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Sử dụng JSTL (c:forEach, c:if) duyệt danh sách bài nộp.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tuyệt đối không viết code Java scriptlet (<% ... %>) trong file JSP.", sz: 1300, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "Tuân thủ chặt chẽ tiêu chí 40% MVC2 Clean Code.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 23: TV4 - 3 TUẦN CUỐI (PR3) & TUẦN 10 (FINAL DEMO)
updateSlideXml(23, [
  ...makeHeader("TV4 (Frontend & MVC2) — 3 Tuần Cuối (PR3) & Tuần 10 (Final Demo)", "Dashboard hiển thị trực quan AST, diễn giải AI sư phạm và live demo", "TV4: PR3 & Final"),
  makeTextSp({
    id: 2301, name: "Feature 1", x: 920000, y: 2000000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "1. AST Result Dashboard", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Hiển thị danh sách vi phạm.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Highlight chính xác dòng code lỗi.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Phân loại Error / Warning.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2302, name: "Feature 2", x: 4440000, y: 2000000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "2. Render Markdown AI", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Hiển thị phản hồi từ AIService.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Khối code Before/After đẹp mắt.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Lời giải thích sư phạm tự nhiên.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2303, name: "Feature 3", x: 7960000, y: 2000000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "3. Responsive Design", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Tương thích tốt trên Desktop & Tablet.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Tốc độ tải trang dưới 0.5 giây.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Giao diện hiện đại, dễ thao tác.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2304, name: "Feature 4", x: 920000, y: 4100000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "4. Phân Quyền 2 Chiều", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Màn hình riêng cho Sinh viên.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Bảng quản lý riêng cho Giảng viên.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Chấm phúc khảo và điều chỉnh điểm.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2305, name: "Feature 5", x: 4440000, y: 4100000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "5. Live Demo Hoàn Chỉnh", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Sinh viên nộp file bài làm Java.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Hệ thống tự động chấm điểm.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Xuất bảng điểm tức thì.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 2306, name: "Feature 6", x: 7960000, y: 4100000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "6. Bảo Vệ Barem MVC2", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Tự tin trả lời Q&A kiến trúc Web.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Giải thích rõ phân tầng MVC2.", sz: 1200, clr: "8EA0BD" }] },
      { runs: [{ t: "Đạt 40% Barem MVC2", sz: 1100, b: true, clr: "10B981" }] }
    ]
  })
]);

// SLIDE 18: TV5 - 3 TUẦN ĐẦU (PR1)
updateSlideXml(18, [
  ...makeHeader("TV5 (Sandbox & DevOps) — 3 Tuần Đầu (Tuần 1 - 3: PR1)", "Khởi tạo hạ tầng Git, luồng CI/CD và thiết kế kiến trúc Docker Sandbox", "TV5: PR1"),
  makeTextSp({
    id: 1801, name: "Git & Repo", x: 920000, y: 2200000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "1. Khởi Tạo Repository", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Thiết lập GitHub repo chung.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Cấu hình .gitignore chuẩn Java/Maven.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Phân chia nhánh branch cho 5 thành viên.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1802, name: "Quy Chuẩn Git", x: 4440000, y: 2200000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "2. Quy Chuẩn Làm Việc", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Định nghĩa format commit message.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Cơ chế Pull Request có review chéo.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Bảo đảm commit history minh bạch.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1803, name: "CI Pipeline", x: 7960000, y: 2200000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "3. CI Pipeline Tự Động", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Tự động build mã nguồn khi push.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Chạy kiểm thử cú pháp tự động.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Phát hiện xung đột mã sớm.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1804, name: "Sandbox Plan", x: 920000, y: 4300000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "4. Kế Hoạch Docker Sandbox", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Nghiên cứu cơ chế cô lập container.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Xác định giới hạn tài nguyên khắt khe.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Bảo vệ máy chủ trước code độc hại.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1805, name: "Barem 30%", x: 4440000, y: 4300000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "5. Barem Sandbox (30%)", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Định hình tiêu chuẩn Sandbox Compliance.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Xây dựng kịch bản kiểm thử phá hoại.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Cam kết đạt điểm tuyệt đối 30%.", sz: 1200, clr: "8EA0BD" }] }
    ]
  }),
  makeTextSp({
    id: 1806, name: "Thuyết Trình PR1", x: 7960000, y: 4300000, cx: 3300000, cy: 1900000,
    paragraphs: [
      { runs: [{ t: "6. Thuyết Trình PR1", sz: 1500, b: true, clr: "EEF2F8" }] },
      { bullet: true, runs: [{ t: "Bảo vệ giải pháp hạ tầng.", sz: 1200, clr: "8EA0BD" }] },
      { bullet: true, runs: [{ t: "Minh họa cấu trúc thư mục repo.", sz: 1200, clr: "8EA0BD" }] },
      { runs: [{ t: "Hoàn tất mốc PR1", sz: 1100, b: true, clr: "F59E0B" }] }
    ]
  })
]);

// SLIDE 16: TV5 - 3 TUẦN SAU (PR2)
updateSlideXml(16, [
  ...makeHeader("TV5 (Sandbox & DevOps) — 3 Tuần Sau (Tuần 4 - 6: PR2)", "Nén ZIP an toàn, chặn đứng lỗ hổng Zip-slip và xác thực băm mật mã SHA-256", "TV5: PR2"),
  makeTextSp({
    id: 1601, name: "Security Filter Info", x: 812800, y: 2200000, cx: 10500000, cy: 4000000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "1. Bộ Lọc An Toàn Tệp Tin (Security Filter):", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Chặn đứng triệt để lỗ hổng Zip-slip: Loại bỏ các file nén chứa đường dẫn '../' có ý đồ ghi đè file hệ điều hành.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Chặn đứng Zip-bomb: Giới hạn tỉ lệ nén và dung lượng giải nén tối đa 10MB để chống tràn bộ nhớ máy chủ.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "2. Thuật Toán Băm Mật Mã SHA-256:", sz: 1600, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "Tính toán chuỗi hash SHA-256 64 ký tự hex trực tiếp từ luồng byte nộp bài.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Lưu vết chuỗi băm vào cột file_hash của bảng Submission trong CSDL SQL Server.", sz: 1300, clr: "FFFFFF" }] },
      { runs: [{ t: "3. Cơ Chế Chống Nộp Trùng (Deduplication):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Tra cứu nhanh trong CSDL: Nếu phát hiện mã hash trùng lặp, hệ thống tái sử dụng kết quả hoặc từ chối chấm lại, tiết kiệm 100% tài nguyên Sandbox.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 19: TV5 - CHI TIẾT DOCKER SANDBOX & QUEUE
updateSlideXml(19, [
  ...makeHeader("CHI TIẾT LUỒNG DOCKER SANDBOX & HÀNG ĐỢI (TV5)", "Kiến trúc cô lập tuyệt đối trước các đoạn mã phá hoại hệ thống", "TV5: Sandbox Architecture"),
  makeTextSp({
    id: 1901, name: "Sandbox Flow", x: 812800, y: 4800000, cx: 10500000, cy: 1500000,
    bgClr: "131B2E", borderClr: "F59E0B",
    paragraphs: [
      { align: "ctr", runs: [{ t: "Cơ Chế Cách Ly Tuyệt Đối Của Docker Sandbox:", sz: 1400, b: true, clr: "F59E0B" }] },
      { align: "ctr", runs: [{ t: "Cờ --network=none (Ngắt mạng) + RAM 512MB + CPU 50% + Timeout 10s dập tắt vòng lặp vô hạn.", sz: 1200, clr: "FFFFFF" }] },
      { align: "ctr", runs: [{ t: "Sau khi bóc tách AST ra JSON an toàn, dữ liệu mới được chuyển ra ngoài ứng dụng chính để gọi AI.", sz: 1200, i: true, clr: "38BDF8" }] }
    ]
  })
]);

// SLIDE 26: TV5 - 3 TUẦN CUỐI (PR3) & TUẦN 10 (FINAL DEMO)
updateSlideXml(26, [
  ...makeHeader("TV5 (Sandbox & DevOps) — 3 Tuần Cuối (PR3) & Tuần 10 (Final Demo)", "Kim tự tháp bảo mật, hàng đợi Redis Queue và chủ trì bảo vệ Sandbox Compliance", "TV5: PR3 & Final"),
  makeTextSp({
    id: 2601, name: "Pyramid Details", x: 812800, y: 2200000, cx: 6000000, cy: 4000000,
    bgClr: "131B2E", borderClr: "1E293B",
    paragraphs: [
      { runs: [{ t: "4 Tầng Bảo Mật Kim Tự Tháp AITA:", sz: 1600, b: true, clr: "38BDF8" }] },
      { bullet: true, runs: [{ t: "Tầng 1 (Đỉnh): Final Technical Report & Disaster Recovery Plan.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tầng 2: Redis Queue phân phối tải, có cơ chế Retry khi lỗi.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tầng 3: Docker Sandbox giới hạn RAM 512MB, CPU 50%, Timeout 10s.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Tầng 4 (Đáy): Cờ --network=none ngắt hoàn toàn Internet khỏi mã sinh viên.", sz: 1300, clr: "F59E0B" }] },
      { runs: [{ t: "Bảo Vệ Tiêu Chí Sandbox Compliance (30% Barem):", sz: 1600, b: true, clr: "10B981" }] },
      { bullet: true, runs: [{ t: "Chứng minh mọi thành viên đều nắm rõ luồng dữ liệu an toàn.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "Đạt trọn vẹn điểm tối đa cho tiêu chí Sandbox.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 27: TV5 - QUY TRÌNH CI/CD & BUILD
updateSlideXml(27, [
  ...makeHeader("QUY TRÌNH KIỂM THỬ TỰ ĐỘNG & BẢO ĐẢM CHẤT LƯỢNG (TV5)", "Quy trình CI/CD tích hợp tự động kiểm tra tính tương thích của 44 file Java", "TV5: CI/CD Pipeline"),
  makeTextSp({
    id: 2701, name: "CI Steps", x: 812800, y: 4800000, cx: 10500000, cy: 1500000,
    bgClr: "131B2E", borderClr: "10B981",
    paragraphs: [
      { align: "ctr", runs: [{ t: "Quy Trình 4 Bước Đóng Gói Tự Động:", sz: 1400, b: true, clr: "10B981" }] },
      { align: "ctr", runs: [{ t: "1. Code Push & Lint → 2. Test Suite 15/15 Rule Pass → 3. Benchmark 0% False Positive → 4. Đóng Gói Shaded JAR", sz: 1200, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 13: BIỂU ĐỒ TIẾN ĐỘ 10 TUẦN
updateSlideXml(13, [
  ...makeHeader("TIẾN ĐỘ HOÀN THIỆN ĐỒ ÁN QUA 10 TUẦN", "Đường cong tích lũy tính năng và mức độ sẵn sàng qua 4 mốc đánh giá RBL", "Tiến Độ Dự Án"),
  makeTextSp({
    id: 1301, name: "Timeline Text", x: 812800, y: 4800000, cx: 10500000, cy: 1500000,
    bgClr: "131B2E", borderClr: "38BDF8",
    paragraphs: [
      { align: "ctr", runs: [{ t: "Các Cột Mốc Hoàn Thiện:", sz: 1400, b: true, clr: "38BDF8" }] },
      { align: "ctr", runs: [{ t: "Tuần 1-3 (PR1: 25%) → Tuần 4-6 (PR2: 55%) → Tuần 7-9 (PR3: 90%) → Tuần 10 (Final Demo: 100% Hoàn Tất)", sz: 1200, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 29: Q&A DEFENSE
updateSlideXml(29, [
  ...makeHeader("TỔNG KẾT & SẴN SÀNG PHẢN BIỆN Q&A HỘI ĐỒNG", "Cả 5 thành viên tự tin bảo vệ 30% barem điểm Peer Defense", "Sẵn Sàng Q&A"),
  makeTextSp({
    id: 2901, name: "QA Guide", x: 812800, y: 2200000, cx: 10500000, cy: 4000000,
    bgClr: "131B2E", borderClr: "F59E0B",
    paragraphs: [
      { runs: [{ t: "Chiến Lược Trả Lời Câu Hỏi Của Từng Thành Viên:", sz: 1600, b: true, clr: "F59E0B" }] },
      { bullet: true, runs: [{ t: "TV1: Bảo vệ tính ưu việt của phân tích cú pháp AST so với Regex thông thường.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "TV2: Bảo vệ CSDL chuẩn 3NF và tính toàn vẹn Transaction khi nhiều sinh viên nộp cùng lúc.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "TV3: Bảo vệ cơ chế duyệt 15 Rule AST, Prompt Slicing, dự phòng Local Fallback và kết quả 0% False Positive.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "TV4: Bảo vệ cấu trúc Web MVC2 sạch sẽ, giao diện JSP không scriptlet và trải nghiệm người dùng.", sz: 1300, clr: "FFFFFF" }] },
      { bullet: true, runs: [{ t: "TV5: Bảo vệ môi trường Docker Sandbox ngắt mạng (--network=none) an toàn tuyệt đối.", sz: 1300, clr: "FFFFFF" }] }
    ]
  })
]);

// SLIDE 30: CLOSING
updateSlideXml(30, [
  ...makeHeader("XIN TRÂN TRỌNG CẢM ƠN THẦY CÔ VÀ CÁC BẠN!", "AITA — AI-Powered Teaching Assistant System | Môn PRJ301 (Nhóm 1)", "Hoàn Tất Báo Cáo"),
  makeTextSp({
    id: 3001, name: "Contact Info", x: 812800, y: 2200000, cx: 10500000, cy: 3500000,
    paragraphs: [
      { align: "ctr", runs: [{ t: "Dự Án: Hệ Thống Trợ Giảng AI Chấm Mã Nguồn Tự Động", sz: 2000, b: true, clr: "EEF2F8" }] },
      { align: "ctr", runs: [{ t: "Nhóm Thực Hiện: Nhóm 1 (PRJ301) — Đại Học FPT", sz: 1600, clr: "38BDF8" }] },
      { align: "ctr", runs: [{ t: "5 Thành Viên: TV1 (Lead) — TV2 (DB) — TV3 (AI) — TV4 (Web) — TV5 (DevOps)", sz: 1400, b: true, clr: "F59E0B" }] },
      { align: "ctr", runs: [{ t: "GitHub Repository: PRJ301---Group1", sz: 1300, clr: "8EA0BD" }] }
    ]
  })
]);

console.log("All 30 slides updated successfully!");
