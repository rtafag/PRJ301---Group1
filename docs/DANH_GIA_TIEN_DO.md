# AITA – Nhóm 1 (PRJ301): Bảng Đánh Giá Tiến Độ Dự Án (TV4 cập nhật 09/11/2026)

> **Căn cứ đánh giá:** 
> - Mã nguồn dự án `PRJ301---Group1-main` (bao gồm gói AST Core Engine cập nhật ngày 04/11/2026).
> - Bản phân công công việc: `PhanCong_Nhom1_AITA_revised_1.docx`.
> - Các hàng TV1–TV3 và TV5 giữ số liệu đợt 04/11; riêng các hạng mục TV4 và tổng hợp được đối chiếu lại với mã nguồn ngày **09/11/2026**.

---

## 1. Tổng Quan Tiến Độ Các Thành Viên

| TV | Vai trò kỹ thuật | Số hạng mục | Đạt | Một phần | Chưa có | Mức hoàn thành | Nhận xét tóm tắt |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---|
| **TV1** | **AST Core Engine Lead (Trưởng nhóm)** | 4 | **4** | 0 | 0 | **100.0%** (1.00) | **Hoàn thành 100% AST Core:** Đã có `AstParser`, `AstSyntaxVisitor` (`VoidVisitorAdapter`), xuất JSON schema chuẩn hóa (`aita-ast-schema.json`), phân tích dự án đa file/zip qua `SourceLoader` & `AstProjectAnalyzer`, tích hợp 21 unit test JUnit trong `AstEngineTest`. |
| **TV2** | **Backend & Database Engineer** | 7 | 4 | 1 | 2 | **64.3%** (0.64) | ERD 6 bảng, Database/Table thật (SQL Server), DAO/JDBC, Sample data đã có. Thiếu JWT (đang dùng HttpSession/Map) và JPA/ORM; portal Node chưa ghi đồng bộ vào 6 bảng. |
| **TV3** | **AI Rule / Detection Engineer** | 4 | 0 | 3 | 1 | **37.5%** (0.38) | Có 6 rule và gọi LLM (OpenAI/Gemini), nhưng rule hiện tại vẫn dùng regex trên text. Cần tích hợp ngay các dữ kiện AST đã được TV1 cung cấp sẵn (`infiniteCandidate`, `allocations`, `duplicateMethodBodies`) và đăng ký rule vòng lặp vô hạn. |
| **TV4** | **Web / MVC2 Frontend Engineer** | 4 | **4** | 0 | 0 | **100.0%** (1.00) | Đã có Filter session/role + SHA-256, WAR Servlet/JSP, login/home/submission/AST dashboard/teacher dashboard, E2E theo vai trò, wireframe và Use-case. Node portal được ghi rõ là prototype độc lập. |
| **TV5** | **Integration & QA/DevOps Engineer** | 7 | 2 | 2 | 3 | **42.9%** (0.43) | Xử lý ZIP an toàn + SHA-256 chống nộp trùng hoạt động tốt; SHA-256 hiện được tính tại Filter. Docker sandbox có nền tảng. Thiếu hàng đợi Redis (Priority/Retry), CI pipeline và Report cuối. |
| **Cả nhóm** | | **26** | **14** *(+4)* | **6** *(-2)* | **6** *(-2)* | **65.38%** *(tính lại sau cập nhật TV4)* | **Kết luận: CHƯA hoàn thành toàn bộ nhiệm vụ.** TV4 đã hoàn thành 4 hạng mục theo bằng chứng repo; các phần JWT/JPA (TV2), AST Rule (TV3), Redis/CI/Report (TV5) vẫn cần xử lý. Tổng hợp dùng số liệu cũ của TV1–TV3/TV5 cộng trạng thái mới TV4. |

---

## 2. Chi Tiết Hạng Mục Theo Từng Thành Viên

| TV | Vai trò | Hạng mục được giao | Trạng thái | Điểm | Bằng chứng trong mã nguồn / Ghi chú kỹ thuật |
|:---|:---|:---|:---:|:---:|:---|
| **TV1** | AST Core | Parse mã nguồn thành AST | **Đạt** | 1.0 | Lớp `AstParser` cấu hình JavaParser hỗ trợ Java 17+, parse AST, thu thập chi tiết lỗi cú pháp (dòng, cột, message), đếm tổng node và sinh outline tree có chặn tràn độ sâu (tối đa 8 tầng / 300 node). Tích hợp vào `AppController`. |
| **TV1** | AST Core | Syntax Visitor duyệt cây | **Đạt** | 1.0 | Lớp `AstSyntaxVisitor` kế thừa `VoidVisitorAdapter<Void>`, duyệt type, method, vòng lặp (độ sâu, cờ `infiniteCandidate` do hằng `true` hoặc biến điều kiện không cập nhật), `return null`, cấp phát bộ nhớ trong vòng lặp (`ArrayCreationExpr`, `ObjectCreationExpr`), tính cyclomatic complexity. Có 21 unit test trong `AstEngineTest`. |
| **TV1** | AST Core | Chuẩn hóa output JSON schema cho Rule/DB/UI | **Đạt** | 1.0 | Đã xây dựng schema JSON chuẩn tại `src/main/resources/aita-ast-schema.json` (draft-07, v1.0). Lớp `AstJsonSerializer` và `MiniJson` xuất JSON chuẩn hóa không phụ thuộc thư viện ngoài, lưu vào `AnalysisResult.astJson`. Có CLI `AstCli` hỗ trợ xuất `--pretty` và `--no-tree`. |
| **TV1** | AST Core | Xử lý code phức tạp / đa file | **Đạt** | 1.0 | Lớp `SourceLoader` nạp `.java`, duyệt folder đệ quy và file `.zip` (chống zip-slip, zip-bomb, giới hạn 1000 entry/50MB). `AstProjectAnalyzer` phân tích toàn bộ dự án đa file: index lớp, dependency graph, phát hiện trùng kiểu và trùng thân hàm xuyên file (`duplicateMethodBodies`). `AppController` hỗ trợ nộp zip đa file. |
| **TV2** | Backend & DB | ERD 6 bảng | **Đạt** | 1.0 | 6 bảng: Users, Submission, AnalysisResult, RuleViolation, RuleDefinition, Score (định nghĩa trong README + model). |
| **TV2** | Backend & DB | Tạo Database/Table thật trong SQL Server | **Đạt** | 1.0 | `DatabaseSeeder` chạy 6 lệnh CREATE TABLE trực tiếp trên SQL Server. |
| **TV2** | Backend & DB | Tầng DAO/JDBC | **Đạt** | 1.0 | Đầy đủ 6 DAO + `DBConnection`. |
| **TV2** | Backend & DB | Sample data | **Đạt** | 1.0 | CSV dữ liệu mẫu 200 bản ghi; `submission.csv` dùng hashSUB0001. |
| **TV2** | Backend & DB | JWT Authentication | **Chưa có** | 0.0 | Java dùng `HttpSession`; Node dùng token ngẫu nhiên trong bộ nhớ Map; chưa tích hợp thư viện JWT (jjwt). |
| **TV2** | Backend & DB | JPA/ORM cho Grading Job (PR3) | **Chưa có** | 0.0 | Chưa có JPA/Hibernate; hiện chỉ dùng JDBC thuần. |
| **TV2** | Backend & DB | Dữ liệu đồng bộ Submission–AnalysisResult–Score | **Một phần** | 0.5 | Phía Java đã lưu AnalysisResult chứa `ast_json` chuẩn mới vào Console/DB; tuy nhiên Portal Node lưu snapshot JSON (`PortalRuntimeState`), chưa ghi trực tiếp vào 6 bảng SQL Server. |
| **TV3** | AI Rule | Rule phát hiện lỗi trên AST | **Một phần** | 0.5 | Có 6 rule (NPE, OOM, CS, COMMENT, LONG_METHOD, DUPLICATE_CODE) nhưng hiện vẫn chạy regex trên plain text thô, chưa khai thác cây AST của JavaParser. |
| **TV3** | AI Rule | Rule vòng lặp vô hạn | **Chưa có** | 0.0 | Chưa đăng ký rule vòng lặp vô hạn trong `rule_definition.csv`. (TV1 đã trích xuất sẵn cờ `infiniteCandidate` và `reason` trong AST, TV3 cần kết nối vào Rule Engine). |
| **TV3** | AI Rule | Tích hợp OpenAI/Gemini | **Một phần** | 0.5 | Có code gọi API (tùy chọn qua `.env`), nhưng prompt còn sơ sài, gửi nguyên source code thay vì gửi dữ kiện AST đã trích xuất. |
| **TV3** | AI Rule | Test case từng rule + tỉ lệ false positive | **Một phần** | 0.5 | File `ai-engine.test.js` mới có vài test chung; chưa có test riêng từng rule, chưa có số liệu thống kê false positive. |
| **TV4** | Web / MVC2 | Submission Portal Servlet/JSP, MVC2 | **Đạt** | 1.0 | `pom.xml` đóng gói WAR; có `LoginController`, `HomeController`, `SubmissionController`; các JSP `login.jsp`, `home.jsp`, `submissions.jsp`, `analysis-dashboard.jsp`, `teacher-dashboard.jsp` dùng JSTL/EL. |
| **TV4** | Web / MVC2 | Filter bảo mật | **Đạt** | 1.0 | `SubmissionSecurityFilter` xác thực session/ID/role trước route Student/Teacher; POST upload kiểm tra Part, ZIP, giới hạn 10 MB và tính SHA-256 trước Servlet. Test xác nhận file sai bị Filter chặn. |
| **TV4** | Web / MVC2 | Dashboard kết quả AST (lỗi, mức độ, gợi ý sửa) | **Đạt** | 1.0 | `AnalysisDashboardController` kết hợp AnalysisResult, RuleViolation và RuleDefinition; JSP hiển thị AST, severity, vị trí, gợi ý và trạng thái chưa phân tích; E2E có các luồng owner/teacher/other user. |
| **TV4** | Web / MVC2 | Wireframe + Use-case diagram | **Đạt** | 1.0 | Có wireframe login, home theo role, submission, AST dashboard, teacher dashboard; Mermaid Use-case diagram và đặc tả actor/precondition/alternate path tại `docs/TV4_WIREFRAME_USE_CASE.md`. |
| **TV5** | Integration/DevOps | Nén ZIP + kiểm tra an toàn ZIP | **Đạt** | 1.0 | Cả `SubmissionController` và `SourceLoader` đều chặn triệt để zip-slip, zip-bomb, giới hạn kích thước nạp. |
| **TV5** | Integration/DevOps | SHA-256 + chống nộp trùng | **Đạt** | 1.0 | `SubmissionSecurityFilter` tính SHA-256 trước Servlet; `SubmissionDAO.findByFileHash` tiếp tục kiểm tra file trùng trước khi lưu. |
| **TV5** | Integration/DevOps | Docker Sandbox + Resource Limits | **Một phần** | 0.5 | `sandbox.js` cấu hình `--network=none`, giới hạn RAM, CPU, pids, timeout; tuy nhiên mặc định đang tắt và mới chạy file đơn lẻ. |
| **TV5** | Integration/DevOps | Hàng đợi Redis (Priority & Retry) | **Chưa có** | 0.0 | Chưa có Redis; 'queue' hiện chỉ là nhãn trạng thái trong DB hoặc Promise chain. |
| **TV5** | Integration/DevOps | Test E2E | **Một phần** | 0.5 | Có `smoke-test.js` cho portal Node và `SubmissionServletE2ETest` cho Servlet MVC2 (Filter, role, upload, dashboard); chưa đánh giá lại độ bao phủ toàn dự án. |
| **TV5** | Integration/DevOps | CI cơ bản | **Chưa có** | 0.0 | Chưa có thư mục `.github/workflows` hoặc cấu hình CI (GitHub Actions). |
| **TV5** | Integration/DevOps | Report cuối | **Chưa có** | 0.0 | Chưa có báo cáo tổng kết PR1 / PR3 trong repo. |

---

## 3. Điểm Lệch So Với Bảng Phân Công / README

1. **Hai hệ thống chạy song song:** Java Servlet/JSP MVC2 là UI chính cho demo PRJ301; Portal Node/Express vẫn là prototype/tích hợp riêng và chưa dùng chung hoàn toàn nguồn dữ liệu SQL Server.
2. **Đính chính công nghệ AST (Roslyn vs JavaParser):** README cũ ghi dùng Roslyn, nhưng mã nguồn phân tích Java nên TV1 đã dùng **JavaParser** (`javaparser-core`) là hoàn toàn chính xác. Đã có tài liệu chuẩn hóa tại `docs/AST_CORE.md`. Cần cập nhật lại README để loại bỏ các ghi chú cũ về Roslyn, `fix_sql.ps1` và `mssql-jdbc.jar`.
3. **Dữ liệu mẫu hash giả:** `data/submission.csv` vẫn dùng hash giả dạng `hashSUB0001`; mật khẩu mẫu dạng `hash_<username>` viết thường.
4. **Tài liệu bổ trợ:** Wireframe và Use-case cho TV4 đã bổ sung trong `docs/TV4_WIREFRAME_USE_CASE.md`; Report cuối và slide tổng kết vẫn cần hoàn thiện.

---

## 4. Kế Hoạch Ưu Tiên Tiếp Theo (Việc Cần Làm)

| Ưu tiên | Việc cần làm | Người phụ trách | Liên quan hạng mục | Trạng thái |
|:---:|:---|:---:|:---|:---:|
| **1** | Cài JWT thật (dùng thư viện `jjwt`) thay cho `HttpSession` / token Map trong bộ nhớ | **TV2** | JWT Authentication | Chưa bắt đầu |
| **2** | Hoàn tất Filter auth/role + SHA-256, login/home/dashboard JSP và đóng gói WAR; bổ sung wireframe/Use-case | **TV4 + TV5** | Filter bảo mật; Dashboard AST; SHA-256 qua Filter; MVC2 UI | **Hoàn tất TV4 ngày 09/11/2026** |
| **3** | Chuyển rule sang duyệt dữ kiện AST từ TV1 (`infiniteCandidate`, `allocations`, `duplicateMethodBodies`); đăng ký rule vòng lặp vô hạn vào `rule_definition.csv` | **TV3** *(TV1 đã bàn giao xong AST Core)* | Rule phát hiện lỗi trên AST; Rule vòng lặp vô hạn | **Sẵn sàng thực hiện** (Nút thắt AST đã gỡ) |
| **4** | Thêm hàng đợi Redis có Priority và Retry (dùng thư viện Jedis) | **TV5** | Hàng đợi Redis | Chưa bắt đầu |
| **5** | Thống nhất dữ liệu: gom về Servlet/JSP đúng chuẩn PRJ301 và đồng bộ ghi vào 6 bảng SQL Server | **Cả nhóm** | Dữ liệu đồng bộ; Chuẩn MVC2 | UI Java đã có; đồng bộ dữ liệu toàn hệ thống còn mở |
| **6** | Bổ sung wireframe, Use-case diagram, CI cơ bản (GitHub Actions), JPA/ORM, Report cuối | **TV4 / TV5 / TV2** | Wireframe; CI; JPA; Report | Wireframe/Use-case TV4 hoàn tất; CI/JPA/Report còn mở |
