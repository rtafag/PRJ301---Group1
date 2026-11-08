<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AITA - Trang chủ</title>
    <style>
        body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 2rem auto; max-width: 900px; padding: 0 1rem; }
        .actions { display: flex; flex-wrap: wrap; gap: 1rem; margin: 1.5rem 0; }
        .action { border: 1px solid #d1d5db; border-radius: .5rem; flex: 1 1 240px; padding: 1rem; }
        .action a { font-weight: 700; }
        dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: .75rem; }
        dl div { background: #f3f4f6; border-radius: .5rem; padding: .75rem; }
        dt { color: #4b5563; } dd { font-size: 1.4rem; font-weight: 700; margin: .25rem 0 0; }
    </style>
</head>
<body>
    <main>
        <h1>Trợ lý giảng dạy AITA</h1>
        <p>Xin chào <strong><c:out value="${sessionScope.currentUser.fullName}" /></strong>
            · <c:out value="${sessionScope.currentUser.role}" /></p>
        <c:if test="${not empty dbError}">
            <p role="alert">Không thể tải thống kê: <c:out value="${dbError}" /></p>
        </c:if>
        <section class="actions" aria-label="Tác vụ theo vai trò">
            <c:choose>
                <c:when test="${sessionScope.currentUser.role eq 'Student'}">
                    <article class="action">
                        <h2>Sinh viên</h2>
                        <p>Nộp dự án ZIP, theo dõi trạng thái và xem lỗi cùng gợi ý sửa.</p>
                        <a href="<c:url value='/student/submissions'/>">Mở bài nộp của tôi</a>
                    </article>
                </c:when>
                <c:when test="${sessionScope.currentUser.role eq 'Teacher'}">
                    <article class="action">
                        <h2>Giảng viên</h2>
                        <p>Duyệt bài nộp của lớp và mở kết quả phân tích để phản hồi.</p>
                        <a href="<c:url value='/teacher/dashboard'/>">Mở bảng điều khiển</a>
                    </article>
                    <section class="action" aria-label="Thống kê hệ thống">
                        <h2>Tổng quan</h2>
                        <dl>
                            <div><dt>Người dùng</dt><dd><c:out value="${userCount}" /></dd></div>
                            <div><dt>Bài nộp</dt><dd><c:out value="${subCount}" /></dd></div>
                            <div><dt>Bảng điểm</dt><dd><c:out value="${scoreCount}" /></dd></div>
                            <div><dt>Quy tắc phân tích</dt><dd><c:out value="${ruleCount}" /></dd></div>
                        </dl>
                    </section>
                </c:when>
            </c:choose>
        </section>
        <nav>
            <a href="<c:url value='/logout'/>">Đăng xuất</a>
        </nav>
    </main>
</body>
</html>
