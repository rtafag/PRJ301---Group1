<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AITA - Trang chủ</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; background: #f4f7fb; color: #172033; }
        main { max-width: 860px; margin: 3rem auto; padding: 0 1rem; }
        section { background: white; border: 1px solid #dce3ed; border-radius: 12px; padding: 1.5rem; }
        a { display: inline-block; margin: .5rem 1rem .5rem 0; color: #2457c5; }
    </style>
</head>
<body>
<main>
    <h1>Xin chào, <c:out value="${sessionScope.currentUser.fullName}" /></h1>
    <section>
        <h2>Bảng điều khiển</h2>
        <c:if test="${dashboardRole ne 'student'}">
            <p>Số bài nộp: <c:out value="${subCount}" /></p>
            <p>Số bài đã chấm: <c:out value="${scoreCount}" /></p>
        </c:if>
        <c:if test="${dashboardRole eq 'student'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/student/submissions">Cổng nộp bài</a>
        </c:if>
        <c:if test="${dashboardRole eq 'student' or dashboardRole eq 'teacher' or dashboardRole eq 'admin'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/analysis-dashboard">Kết quả phân tích AST</a>
        </c:if>
        <c:if test="${dashboardRole eq 'teacher'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/teacher/dashboard">Bảng điều khiển giảng viên</a>
        </c:if>
        <c:if test="${dashboardRole eq 'teacher' or dashboardRole eq 'admin'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/scores">Xem bảng điểm</a>
        </c:if>
        <a href="<c:out value='${pageContext.request.contextPath}' />/logout">Đăng xuất</a>
    </section>
</main>
</body>
</html>
