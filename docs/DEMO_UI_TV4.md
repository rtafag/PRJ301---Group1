# Kịch bản demo UI TV4

## Chuẩn bị trước buổi demo

1. Khởi chạy ứng dụng web trên Servlet container đã cấu hình cho dự án.
2. Xác nhận SQL Server có tài khoản mẫu thuộc cả hai vai trò `Student` và `Teacher`, cùng dữ liệu Submission/AnalysisResult/RuleViolation/RuleDefinition.
3. Chuẩn bị một ZIP mã nguồn hợp lệ dưới 10 MB để nộp; giữ một bản sao ZIP để minh họa cơ chế phát hiện nộp trùng.
4. Mở trang `/login` trước, đăng nhập riêng cho từng vai trò. Không dùng chung session giữa hai lượt demo.

## Luồng sinh viên

1. Đăng nhập bằng tài khoản `Student`; xác nhận trang chủ chào đúng tên và chỉ hiện tác vụ sinh viên.
2. Chọn **Mở bài nộp của tôi** để tới `/student/submissions`.
3. Nộp ZIP hợp lệ. Chờ trạng thái thành công và kiểm tra bài mới xuất hiện ngay trên đầu lịch sử, không cần tải lại trang.
4. Nộp lại cùng ZIP để minh họa thông báo bài trùng; thử chọn tệp không phải ZIP để minh họa kiểm tra đầu vào.
5. Mở **Xem kết quả** ở một bài đã có phân tích. Trình bày mức độ lỗi, vị trí file/dòng, gợi ý sửa, nhận xét AI và AST JSON.
6. Nếu bài vừa nộp chưa có AnalysisResult, Dashboard hiển thị rõ trạng thái chưa phân tích thay vì trang trống hoặc lỗi.

## Luồng giảng viên

1. Đăng xuất, sau đó đăng nhập bằng tài khoản `Teacher`; xác nhận trang chủ chỉ hiện tác vụ giảng viên và thống kê tổng quan.
2. Chọn **Mở bảng điều khiển** để tới `/teacher/dashboard`.
3. Chọn một bài nộp bất kỳ để xem Dashboard phân tích. Giảng viên có thể xem bài của sinh viên khác; cùng URL trên phiên sinh viên khác vẫn trả về 404.
4. Quay lại danh sách bài nộp rồi đăng xuất.

## Điểm kiểm thử quyền truy cập khi trình diễn

- Chưa đăng nhập: truy cập trang trong `/student/*` hoặc `/teacher/*` sẽ được chuyển tới đăng nhập.
- Sinh viên: được phép nộp và xem bài của mình; không được truy cập dashboard giảng viên.
- Giảng viên: được xem danh sách lớp và phân tích bài nộp; không được sử dụng luồng nộp bài sinh viên.
- Kết quả phân tích chưa tồn tại được trình bày bằng trạng thái chờ; không giả định upload tự chạy bộ phân tích.
