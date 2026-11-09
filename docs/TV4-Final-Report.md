# Báo cáo hoàn thiện TV4 - Submission Portal và Dashboard AST

**Dự án:** AITA - PRJ301, Nhóm 1  
**Phạm vi:** UI/MVC2 Servlet/JSP, bảo vệ luồng nộp bài, dashboard kết quả AST, tài liệu demo  
**Cập nhật:** 09/11/2026

## 1. Tóm tắt

TV4 cung cấp luồng giao diện Servlet/JSP riêng cho sinh viên và giảng viên, sử dụng Servlet làm controller, DAO/JDBC làm tầng dữ liệu, JSP/JSTL/EL làm presentation. Tài khoản sinh viên chỉ xem được kết quả của chính mình; giảng viên/admin xem được dashboard tổng hợp và bảng điểm. Luồng upload được bảo vệ bởi `AuthFilter` và `Sha256Filter` trước `SubmissionController`.

## 2. Phạm vi và đối chiếu yêu cầu

| Yêu cầu TV4 | Kết quả | Bằng chứng |
|---|---|---|
| Submission Portal theo MVC2 | Đã triển khai | `SubmissionController`, `SubmissionDAO`, `submission-portal.jsp` |
| Đăng nhập và trang chủ dùng JSP/EL/JSTL | Đã triển khai | `LoginController`, `HomeController`, `login.jsp`, `home.jsp` |
| Filter kiểm tra phiên, định danh, role và CSRF | Đã triển khai | `AuthFilter`; các route được ánh xạ trong `WEB-INF/web.xml` |
| Tính SHA-256 trước Servlet | Đã triển khai | `Sha256Filter` đọc multipart có giới hạn và gắn bytes/hash vào request attributes |
| Dashboard lỗi AST | Đã triển khai | `AnalysisDashboardController`, `AnalysisResultDAO`, `analysis-dashboard.jsp` |
| Bảo vệ dữ liệu theo vai trò | Đã triển khai | Sinh viên bị giới hạn theo `submission.user_id`; teacher/admin xem toàn bộ |
| Wireframe và use-case | Đã triển khai | [TV4-UI-UX.md](./TV4-UI-UX.md) |
| Report và slide demo | Đã chuẩn bị | Báo cáo này và [TV4-Defense-Slides.pptx](./TV4-Defense-Slides.pptx) |

## 3. Kiến trúc và luồng dữ liệu

```text
Browser
  -> AuthFilter (session / identity / role / CSRF header)
  -> Sha256Filter (multipart size bound / SHA-256 / request attributes)
  -> SubmissionController (ZIP structure and zip-slip checks)
  -> SubmissionDAO / SQL Server + artifact file

SQL Server (existing analysis records)
  -> AnalysisResultDAO (scope by student ID or all for teacher/admin)
  -> AnalysisDashboardController
  -> JSP / JSTL / EL
```

Thứ tự filter được khai báo tường minh trong `src/main/webapp/WEB-INF/web.xml`: `AuthFilter` chạy trước `Sha256Filter`. CSRF token được gửi bằng header `X-CSRF-Token`; nhờ vậy AuthFilter xác thực trước khi Sha256Filter đọc multipart body. Controller tiếp tục kiểm tra cấu trúc ZIP, giới hạn số entry và tổng dữ liệu giải nén, kiểm tra hash trùng, lưu artifact và tạo bản ghi.

Dashboard hiển thị mã submission, thời gian phân tích, feedback, tên/mã rule, severity, file/dòng, mô tả, điểm trừ và gợi ý sửa. Tìm kiếm và bộ lọc chạy phía client trên dữ liệu đã được scope ở server; nội dung động được escape bằng `c:out`.

## 4. Quyền truy cập và trải nghiệm

- Khách được chuyển về login khi mở route GET cần xác thực.
- Chỉ sinh viên có session identity trùng khớp mới được dùng submission portal; teacher/admin bị chặn.
- Chỉ teacher/admin xem được bảng điểm tổng hợp.
- Sinh viên chỉ thấy analysis thuộc submission của chính mình.
- Các trang dữ liệu đặt `Cache-Control: no-store`; lỗi truy cập/DB không được biến thành trang thành công giả.
- UI có điều hướng theo vai trò, trạng thái upload và thông báo bộ lọc dùng vùng hỗ trợ trình đọc màn hình.

## 5. Kiểm chứng

Kiểm chứng trong môi trường phát triển:

- Java 25: `mvn --batch-mode clean package` thành công; **69 test đạt, 0 lỗi** (40 test portal/dashboard và 29 test AST/AI).
- HTTP E2E chạy trên Jetty test container: xác nhận CSRF GET/POST, từ chối người chưa đăng nhập và role sai, SHA-256, ZIP không hợp lệ, chống nộp trùng.
- Controller tests kiểm tra session mismatch, role student/teacher/admin, scope kết quả, lỗi khởi tạo schema và quyền bảng điểm.
- WAR chứa JSP đăng nhập/trang chủ/portal/dashboard, AuthFilter, Sha256Filter và JSTL.
- Portal Node (ứng dụng riêng trong repo): 12 test đạt, một bài kiểm thử Redis bị skip do Redis chưa cài/chạy; `npm audit --omit=dev` báo 0 lỗ hổng.
- `git diff --check` sạch.

## 6. Giới hạn và việc ngoài phạm vi TV4

1. Servlet upload tạo submission trạng thái `Submitted`, nhưng chưa enqueue bài đó sang Redis/AST worker. Dashboard hiển thị các kết quả đã được phân tích và lưu trước đó; demo cần dữ liệu mẫu có liên kết đầy đủ.
2. Chưa triển khai WAR trực tiếp lên Tomcat kết nối SQL Server trong phiên làm việc này; chưa có kiểm thử người dùng thật. Vì vậy không tuyên bố đã xác nhận runtime production hay thu thập phản hồi người dùng.
3. Luồng đăng nhập phát hành JWT và đồng bộ với `HttpSession`; `AuthFilter` hỗ trợ token Bearer/cookie và session tương thích. Cần cấu hình `JWT_SECRET` (ít nhất 32 ký tự) qua môi trường chạy, không đặt secret trong mã nguồn.
4. Ảnh Excel đính kèm xác định danh sách công việc, không phải mẫu thiết kế giao diện. Tài liệu wireframe trong repo là đặc tả thao tác, không giả định pixel-perfect từ ảnh đó.

## 7. Kịch bản demo đề xuất (5 phút)

1. Mở hai browser profile độc lập; đăng nhập một tài khoản sinh viên và một tài khoản giảng viên mẫu.
2. Trong profile sinh viên, mở trang chủ → cổng nộp bài; upload ZIP hợp lệ và xác nhận mã/trạng thái/lịch sử.
3. Thử CSRF thiếu/sai, ZIP sai, nộp lặp và truy cập bảng điểm: các request phải bị từ chối phù hợp.
4. Mở dashboard AST có dữ liệu seed; lọc severity, tìm theo file/rule, trình bày vị trí và gợi ý sửa.
5. Đăng nhập giảng viên ở profile thứ hai; xác nhận dashboard tổng hợp và bảng điểm hoạt động, submission portal bị từ chối.
6. Giải thích rõ bài vừa upload chưa tự chạy phân tích trong Servlet flow hiện tại.

**Tài khoản demo:** chỉ dùng tài khoản mẫu được README/data CSV mô tả trên database dùng riêng; không dùng thông tin mẫu trong môi trường thật.
