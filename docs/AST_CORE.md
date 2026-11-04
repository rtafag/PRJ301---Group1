# AST Core Engine (TV1)

## Quyết định kỹ thuật
- Dự án phân tích mã **Java**, nên dùng **JavaParser** (`javaparser-core`). Roslyn chỉ dành cho C#/.NET nên không dùng; README cần ghi rõ điểm này khi báo cáo.
- Không phụ thuộc thư viện JSON ngoài: `MiniJson` tự ghi JSON.

## Thành phần (`com.group1.ast`)
| Lớp | Vai trò |
|---|---|
| `SourceLoader` | Nạp `.java`, thư mục hoặc `.zip` (chặn zip-slip, giới hạn 1000 entry / 300 file Java / 2 MB mỗi file / 50 MB tổng, bỏ qua `target`, `.git`, `__MACOSX`) |
| `AstParser` | Parse từng file, thu lỗi cú pháp (dòng, cột), đếm node, dựng cây outline (tối đa 8 tầng, 300 node) |
| `AstSyntaxVisitor` | `VoidVisitorAdapter` duyệt cây, thu type, method, vòng lặp, `return null`, cấp phát trong vòng lặp, độ phức tạp cyclomatic, fingerprint thân hàm |
| `AstProjectAnalyzer` | Phân tích nhiều file: chỉ mục lớp, đồ thị phụ thuộc, import không resolve được, lớp trùng, thân hàm trùng giữa các file |
| `AstJsonSerializer` | Xuất JSON chuẩn hóa, schema `src/main/resources/aita-ast-schema.json` (draft-07, version 1.0) |
| `AstCli` | Demo: `java -cp target/classes:<javaparser> com.group1.ast.AstCli <file\|thư mục\|zip> [--pretty] [--no-tree]` |

## Cách các thành viên dùng
- **TV3 (Rule Engine):** lấy `FileAst` từ `ProjectAst.files`. `file.unit()` là `CompilationUnit` đã parse sẵn (không cần parse lại). Dữ kiện có sẵn: `loops[].infiniteCandidate` + `reason` (`constant-true-condition`, `condition-never-updated`), `nullReturns`, `loopAllocations`, `methods[].lineCount / cyclomaticComplexity / hasJavadoc / fingerprint`, `project.duplicateMethodBodies`.
- **TV4 (UI):** đọc `ast_json` theo schema; `files[].tree` cho AST Viewer, `files[].errors` để hiện lỗi cú pháp, `project.dependencies` để vẽ quan hệ lớp.
- **TV2 (DB):** `AnalysisResult.astJson` (NVARCHAR(MAX)) lưu nguyên chuỗi JSON; tối đa vài chục KB cho một bài nộp.

## Thay đổi ở code cũ
- `AppController`: phân tích qua `AstProjectAnalyzer`, hỗ trợ nộp `.zip` nhiều file (menu 2 và 4); `ast_json` giờ là JSON schema chuẩn; `rule_violation.fileName` ghi đường dẫn file trong zip.
- `Finding`: thêm trường `file` (giữ constructor cũ).
- `pom.xml`: thêm JUnit 4 (test scope).

## Giới hạn đã biết
- Phân tích tĩnh, không resolve kiểu đầy đủ (không dùng SymbolSolver): đồ thị phụ thuộc dựa trên tên đơn giản + import/package.
- `condition-never-updated` chỉ xét điều kiện toàn biến cục bộ/tham số, không có lời gọi hàm; vòng lặp phụ thuộc trường hoặc luồng khác không bị gắn cờ.
- Nhãn `break` có tên được coi là lối thoát (thận trọng, có thể bỏ sót).
- Rule vòng lặp vô hạn chưa có trong `rule_definition.csv`; việc đăng ký rule và trừ điểm thuộc TV3.
