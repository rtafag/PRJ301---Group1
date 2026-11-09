# BÁO CÁO KỸ THUẬT CUỐI KỲ — AITA

**Môn học:** PRJ301
**Nhóm:** Group 1
**Phân công:** TV5 — Integration & QA/DevOps Engineer
**Ngày lập:** 09/11/2026
**Phạm vi:** Kiến trúc và chức năng hiện có trong repository, tập trung vào tích hợp, kiểm thử, CI, Redis, SHA-256 và sandbox.

---

## 1. Tóm tắt

AITA (AI-Powered Teaching Assistant) là ứng dụng hỗ trợ nhận bài Java, phân tích mã nguồn, trả về vi phạm quy tắc và điểm tham khảo cho sinh viên, đồng thời cung cấp chức năng quản lý lớp và bài nộp cho giảng viên. Repository hiện có hai ứng dụng chạy song song: ứng dụng Java Servlet/JDBC và portal Node.js/Express. Chúng chia sẻ dữ liệu CSV ban đầu nhưng chưa hợp nhất thành một ứng dụng hay một kho dữ liệu nghiệp vụ thống nhất.

Các hạng mục TV5 tập trung vào việc bảo vệ luồng nộp file, đưa phân tích vào hàng đợi có khả năng ưu tiên và thử lại, cô lập việc chạy Java trong Docker, và tự động hóa kiểm thử. Báo cáo này mô tả đúng trạng thái có thể xác minh từ mã nguồn và kết quả kiểm thử, không coi các tính năng chưa chạy trong môi trường đích là đã nghiệm thu.

## 2. Mục tiêu và yêu cầu hệ thống

### 2.1. Tác nhân và nhu cầu

| Tác nhân | Nhu cầu chính |
|---|---|
| Sinh viên | Đăng nhập, nộp mã Java/tệp hỗ trợ, theo dõi kết quả và điểm, tải lại tệp của mình. |
| Giảng viên | Xem lớp và bài nộp được phân công, quản lý quy tắc/chấm điểm, xem báo cáo và phản hồi phân tích. |
| Hệ thống | Kiểm tra đầu vào, phân tích cú pháp và quy tắc, lưu kết quả, thông báo trạng thái và cô lập việc thực thi mã tùy chọn. |

### 2.2. Yêu cầu phi chức năng liên quan đến TV5

- Từ chối tệp nộp không hợp lệ; giới hạn kích thước và chống ZIP path traversal/ZIP bomb ở luồng Java Servlet.
- Tính SHA-256 trên nội dung ZIP để phát hiện bài trùng trước khi lưu bản nộp mới.
- Không để lỗi phân tích tạm thời làm mất job; hỗ trợ ưu tiên, retry có backoff và thu hồi job hết lease.
- Không cấp mạng hoặc quyền không cần thiết cho mã Java khi sandbox được bật.
- Tự động chạy kiểm thử Java, Node.js, Redis integration và smoke test trên GitHub Actions.

## 3. Kiến trúc hiện tại

```text
Sinh viên / Giảng viên
        |
        +-- Java Servlet / JDBC
        |      multipart ZIP -> Sha256Filter -> SubmissionController -> SubmissionDAO
        |                                      -> tệp ZIP trên máy chủ + SQL Server
        |
        +-- Node.js / Express
               REST API + portal UI -> store.js -> JSON runtime mặc định
                                      |          -> PortalRuntimeState nếu bật SQL Server
                                      +-> Redis priority queue -> worker
                                                                -> JavaParser + static rules
                                                                -> OpenAI/Gemini tùy chọn
                                      +-> Docker sandbox tùy chọn
```

### 3.1. Thành phần

| Thành phần | Công nghệ / vai trò |
|---|---|
| Java AST Core | Java 8 source/target, JavaParser, JSON AST và visitor/rule modules. |
| Java Web | Servlet/JSP, JDBC/DAO và SQL Server; luồng nhận ZIP lưu tệp trên máy chủ. |
| Submission Portal | Node.js 22, Express, REST API và giao diện portal. |
| Hàng đợi phân tích | Redis qua giao thức RESP; queue hỗ trợ priority, retry, lease/heartbeat và failed-job handling. |
| Phân tích mã | `java-parser` xác thực/cấu trúc cú pháp; bộ rule portal hiện dùng các pattern tĩnh trên source. OpenAI hoặc Gemini chỉ được gọi khi cấu hình provider và API key. |
| Lưu trữ portal | `store.js` đọc dữ liệu mẫu từ CSV. Mặc định ghi snapshot JSON vào `portal-node/storage/portal-data.json`; tùy chọn SQL Server ghi toàn bộ runtime JSON vào bảng `PortalRuntimeState`. |
| CI | GitHub Actions: Maven/Java, cài Node dependencies, Node tests, khởi động portal với Redis rồi chạy smoke test. |

### 3.2. Ranh giới dữ liệu

Java Servlet/JDBC và Node portal là hai luồng triển khai riêng. Chế độ SQL Server của Node hiện lưu snapshot `RuntimeJson` trong bảng `PortalRuntimeState`, không đồng nghĩa với việc portal đã đồng bộ từng bản ghi vào sáu bảng nghiệp vụ `Users`, `Submission`, `AnalysisResult`, `RuleViolation`, `RuleDefinition`, `Score`. Đây là giới hạn tích hợp cần được tính đến khi triển khai hoặc demo.

## 4. Luồng nghiệp vụ và chức năng

### 4.1. Nộp ZIP qua Java Servlet

1. Người dùng đăng nhập và có `userId` trong session.
2. `Sha256Filter` chỉ xử lý request POST đã xác thực, đọc part `submissionFile`, tính SHA-256 và đặt digest vào request attribute. Part được bọc lại để servlet vẫn đọc được đúng nội dung sau khi filter đã đọc stream.
3. `SubmissionController` kiểm tra phần mở rộng ZIP, kích thước, cấu trúc archive, số entry, kích thước giải nén và tên đường dẫn.
4. `SubmissionDAO.findByFileHash` kiểm tra trùng. Nếu hợp lệ, server lưu nguyên byte của ZIP và ghi metadata bản nộp.
5. Upload trùng nhận HTTP 409; request chưa đăng nhập nhận HTTP 401; loại file hoặc archive không hợp lệ bị từ chối.

Giới hạn hiện có: ZIP tối đa 10 MiB khi upload, tối đa 1.000 entry và 50 MiB nội dung sau giải nén. E2E Jetty sử dụng JDBC fake để cô lập kiểm thử, không thay thế kiểm thử SQL Server thực.

### 4.2. Phân tích bất đồng bộ qua Node portal

1. Portal xác thực người dùng theo role và nhận bài qua REST API.
2. Bản Java phù hợp được xếp vào Redis; priority dựa trên hạn bài: mức 1 nếu hạn trong 24 giờ, mức 5 nếu trong 7 ngày, còn lại mức 10.
3. Worker nhận job theo priority, cập nhật trạng thái và chạy parser/static rules. Mặc định job có tối đa 3 lần thử; retry dùng exponential backoff từ 1 giây, tối đa 30 giây. Lease mặc định 120 giây được gia hạn bằng heartbeat.
4. Kết quả phân tích, điểm và thông báo được cập nhật trong store rồi được lưu theo adapter cấu hình.
5. Sinh viên đọc lại trạng thái và kết quả; giảng viên có các API xem lớp, bài nộp, rule và báo cáo.

### 4.3. Phân tích và AI

- Parser Java tạo tóm tắt cú pháp; portal hiện chạy các rule như NPE, OOM, naming convention và một số rule code smell trên source text. Vì vậy không nên mô tả toàn bộ rule portal là AST-based.
- Điểm được tính từ tổng mức trừ của finding, giới hạn mức trừ ở 10 và điểm sàn ở 0.
- Provider mặc định là `disabled`. Khi bật OpenAI/Gemini và cung cấp API key, hệ thống gửi prompt chứa source cùng findings; phản hồi AI được dùng làm feedback. Nếu không cấu hình, hệ thống vẫn trả kết quả static analysis.
- Báo cáo đo false-positive riêng của TV3 là [FALSE_POSITIVE_REPORT.md](./FALSE_POSITIVE_REPORT.md); số liệu trong báo cáo đó thuộc tập benchmark và phạm vi của TV3, không phải cam kết chất lượng cho mọi mã nguồn.

### 4.4. Docker sandbox

Sandbox là chức năng tùy chọn **chỉ dành cho Node.js portal**, không tự động chạy các ZIP được nộp qua Java Servlet/JDBC. Tính năng mặc định tắt và chỉ hoạt động khi đặt `SANDBOX_ENABLED=true`, Docker daemon đang chạy và image `aita-java-sandbox:8` đã được build cục bộ. Image dùng Java 8 JDK, được định nghĩa tại `portal-node/sandbox/Dockerfile`; runner dùng `--pull=never` để tránh tải image trong lúc xử lý bài nộp.

Sinh viên gửi yêu cầu qua `POST /api/student/submissions/:submissionId/run`. Server chỉ lấy source từ bài Java thuộc về sinh viên đã xác thực. API danh sách bài nộp chỉ trả metadata `hasSourceCode` để giao diện quyết định hiển thị nút chạy; source code không bị lộ trong response đó. Runner hiện hỗ trợ một tệp `.java`, class phải khớp tên tệp và chương trình cần có `main` method để chạy bình thường.

Mỗi lần chạy dùng container riêng với `--network=none`, filesystem read-only, source mount chỉ đọc, capabilities bị loại bỏ, `no-new-privileges`, user không đặc quyền, giới hạn 128 MiB RAM, 0,5 CPU, 32 process, timeout 12 giây và output có giới hạn. Khi timeout, runner gọi `docker kill` trên container có tên riêng; source tạm trên host được dọn sau khi chạy.

Trước demo trên Windows, cần khởi động Docker Desktop ở chế độ Linux containers, xác nhận `docker info` thành công, build image và bật cấu hình sandbox trong `.env`. Cần có Redis để portal hoạt động. Các bước PowerShell chi tiết được ghi ở [portal-node/README.md](../portal-node/README.md#sandbox) và tóm tắt tại mục 6.4 bên dưới. Không nên đưa portal demo trực tiếp ra Internet.

Kiểm thử hiện có mock Docker để xác minh lệnh chạy, flags cách ly và xử lý các tình huống thiếu image/daemon hoặc timeout. CI không chạy container sandbox thật. Vì vậy kết quả unit tests không chứng minh runtime isolation, bind mount hay giới hạn tài nguyên trên Docker host; các nội dung này vẫn cần được xác minh trên máy demo.

## 5. Kiểm thử và CI

### 5.1. Bằng chứng kiểm thử tại thời điểm lập báo cáo

| Lớp kiểm thử | Kết quả | Phạm vi / giới hạn |
|---|---:|---|
| Java JUnit | **52 tests passed** | Chạy năm JUnit test classes từ cache cục bộ; bao gồm AST/rule tests và hai Servlet E2E qua embedded Jetty. |
| Node.js unit tests | **21 passed, 1 skipped** | Node.js 22; 7 sandbox tests dùng mocked Docker và đều đạt. Redis integration bị skip vì máy local không có Redis đang chạy. |
| Redis integration trong CI | Có bước kiểm thử | Workflow cung cấp Redis service để kiểm thử priority và retry; cần một CI run mới để xác nhận phiên bản code hiện tại. |
| Smoke test qua HTTP | Có trong workflow | Bao gồm đăng nhập, phân quyền, nộp bài Java, đợi kết quả queue và một số luồng portal khác. Chưa xác nhận bằng CI run mới sau các chỉnh sửa local. |
| Docker sandbox | **7 unit tests mocked passed**; runtime chưa nghiệm thu | Docker daemon không khả dụng trong môi trường kiểm thử; chưa build image hoặc chạy container thật; sandbox không bật mặc định. |

### 5.2. Trạng thái CI

Workflow `.github/workflows/ci.yml` chạy Maven tests, `npm ci`, Node tests, khởi động portal với Redis và chạy smoke test. Workflow dùng Node.js 22 để khớp yêu cầu engine của dependency.

Run GitHub Actions gần nhất có thể đối chiếu tại thời điểm lập báo cáo là [CI run #11](https://github.com/rtafag/PRJ301---Group1/actions/runs/42551783229), trên commit `5699bb8`. Run đó **thất bại** trong smoke test vì test truy cập `violations[0]` và giả định finding đầu tiên luôn là `NPE`. Mã sửa assertion và các thay đổi test/workflow trong working tree hiện tại **chưa có CI run mới**; do đó báo cáo không kết luận CI đã xanh. Cần push commit chứa các thay đổi này để GitHub Actions chạy lại.

## 6. Hướng dẫn chạy và demo

### 6.1. Kiểm thử Java

```powershell
mvn --batch-mode test
```

Test Servlet tự khởi chạy embedded Jetty và fake JDBC; không cần SQL Server thật cho nhóm test này.

### 6.2. Kiểm thử portal

Yêu cầu Node.js 22 trở lên:

```powershell
cd portal-node
npm ci
npm test
```

Để chạy Redis integration và smoke test, khởi động Redis trước, rồi trong một terminal:

```powershell
$env:REDIS_URL = "redis://127.0.0.1:6379"
npm start
```

Trong terminal khác, chạy:

```powershell
cd portal-node
$env:REDIS_URL = "redis://127.0.0.1:6379"
npm run smoke
```

Smoke test dùng tài khoản demo trong `data/users.csv`, tạo bản nộp thử và có thể thay đổi runtime state khi bật rule NPE. Nên chạy với dữ liệu thử nghiệm, không dùng snapshot dữ liệu cần giữ nguyên.

### 6.3. Cấu hình vận hành

- Redis: `REDIS_URL`; prefix queue tùy chọn: `REDIS_QUEUE_PREFIX`.
- Lưu portal mặc định: file JSON; SQL Server tùy chọn: `PORTAL_STORAGE=sqlserver` cùng `DB_SERVER`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` và các cờ TLS.
- AI tùy chọn: `AI_PROVIDER=disabled|openai|gemini` cùng API key tương ứng.
- Sandbox tùy chọn: `SANDBOX_ENABLED=true`, Docker daemon hoạt động và image sandbox đã có sẵn cục bộ. Luồng này thuộc Node.js portal, không phải Java Servlet.

Không ghi API key hay thông tin đăng nhập thật vào repository. Dùng biến môi trường hoặc file `.env` không được commit.

### 6.4. Chuẩn bị và chạy Docker sandbox trên Windows

Các lệnh sau chạy trong PowerShell. Trước tiên mở Docker Desktop, chọn Linux containers và chờ lệnh `docker info` kết thúc thành công. Nếu lệnh này báo không kết nối được daemon, chưa thể build hoặc chạy sandbox.

Từ thư mục gốc repository:

```powershell
cd portal-node
docker build -t aita-java-sandbox:8 -f sandbox\Dockerfile sandbox
docker image inspect aita-java-sandbox:8
```

Khởi động Redis nếu chưa có container:

```powershell
docker run -d --name aita-redis -p 6379:6379 redis:7-alpine
```

Nếu container `aita-redis` đã được tạo nhưng đang dừng, dùng `docker start aita-redis`. Tạo `portal-node\.env` từ `.env.example` (nếu chưa có) và đặt:

```env
REDIS_URL=redis://127.0.0.1:6379
SANDBOX_ENABLED=true
SANDBOX_IMAGE=aita-java-sandbox:8
```

Cần Node.js 22 trở lên. Trong `portal-node`, cài dependencies và khởi động ứng dụng:

```powershell
npm.cmd ci
npm.cmd start
```

Mở `http://localhost:3000`, đăng nhập tài khoản sinh viên demo, nộp một file `.java` có class trùng tên file và `main` method, sau đó vào **Analysis & fixes** và chọn **Run in sandbox**. Kết quả hiện dưới nút chạy. Sau demo, có thể dừng và xóa Redis container bằng `docker rm -f aita-redis`; image sandbox có thể giữ lại để dùng lần sau.

## 7. Hạn chế và rủi ro còn lại

1. **Hai luồng web chưa hợp nhất:** Servlet và Node portal dùng luồng xác thực, xử lý upload và persist khác nhau; portal mặc định lưu JSON.
2. **Lưu SQL của portal chưa chuẩn hóa:** SQL adapter lưu snapshot JSON trong `PortalRuntimeState`; chưa thay thế các DAO nghiệp vụ trong luồng Servlet.
3. **Sandbox chưa được kiểm thử runtime trong báo cáo này:** Chưa build image hoặc chạy container thật do Docker daemon không khả dụng trong môi trường kiểm thử. Unit tests dùng mock; cần kiểm tra Docker daemon/image, bind mount, timeout và giới hạn tài nguyên trên môi trường demo thật. Sandbox hiện chỉ tích hợp với Node.js portal, không với luồng ZIP của Java Servlet.
4. **CI chưa xanh được xác nhận cho thay đổi hiện tại:** Cần push các sửa đổi, xem workflow run mới, và xử lý tiếp nếu có lỗi.
5. **Kiểm thử tải chưa có bằng chứng:** Priority/retry được kiểm thử chức năng; chưa có benchmark throughput, concurrency hoặc độ bền khi Redis/service restart.
6. **AI phụ thuộc cấu hình ngoài:** Provider mặc định tắt; kết quả external AI phụ thuộc API key, quota, network và dịch vụ nhà cung cấp. Static rules vẫn hoạt động khi provider tắt.

## 8. Kết luận theo trách nhiệm TV5

| Hạng mục TV5 | Kết quả hiện tại |
|---|---|
| ZIP an toàn và chống nộp trùng | Có giới hạn archive, kiểm tra đường dẫn và SHA-256 trước insert. |
| SHA-256 Filter | Đã tách băm sang `Sha256Filter`; Servlet E2E đăng ký và gọi Filter thật. |
| Redis Priority/Retry | Có queue/worker, priority theo hạn nộp, retry, lease và failed-job handling; có test Redis tích hợp trong CI. |
| E2E/QA | Có Servlet E2E qua Jetty/fake JDBC và Node portal smoke test. |
| CI | Workflow đã cấu hình; cần run mới trên commit chứa các sửa đổi hiện tại để kết luận xanh. |
| Sandbox | Có runner, UI/API và Dockerfile để build image local; đã có hướng dẫn chuẩn bị và chạy trên Windows. Cần xác minh build image, Docker runtime và bind mount trên máy demo; chưa kết nối với luồng ZIP Java Servlet. |
| Final Report | Báo cáo kỹ thuật này hoàn thiện phần mô tả yêu cầu, kiến trúc, chức năng, AI, kiểm thử và giới hạn hiện tại. |

**Kết luận:** Các cấu phần chính của trách nhiệm tích hợp/QA TV5 đã được triển khai ở mức mã nguồn và có kiểm thử tự động tương ứng. Điều kiện để chốt nghiệm thu là push thay đổi và xác nhận CI xanh; đồng thời chạy thử sandbox trên Docker host của buổi demo. Báo cáo không xem hai việc xác minh đó là đã hoàn tất.
