<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bảng điểm AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; background: #f4f7fb; color: #172033; }
        main { max-width: 1000px; margin: 2rem auto; padding: 0 1rem; }
        nav { margin-bottom: 1.5rem; }
        nav a { color: #2457c5; margin-right: 1rem; }
        section { background: white; border: 1px solid #dce3ed; border-radius: 12px; padding: 1.25rem; overflow-x: auto; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: .7rem; border-bottom: 1px solid #e6ebf2; text-align: left; }
    </style>
</head>
<body>
    <main>
        <nav aria-label="Điều hướng">
            <a href="<c:out value='${pageContext.request.contextPath}' />/home">Trang chủ</a>
            <a href="<c:out value='${pageContext.request.contextPath}' />/analysis-dashboard">Kết quả phân tích AST</a>
            <a href="<c:out value='${pageContext.request.contextPath}' />/logout">Đăng xuất</a>
        </nav>
        <h1>Bảng điểm</h1>
        <section>
            <table>
                <thead>
                    <tr>
                        <th>Mã điểm</th>
                        <th>Mã bài nộp</th>
                        <th>Điểm</th>
                        <th>Điểm trừ</th>
                        <th>Ghi chú</th>
                        <th>Cập nhật lúc</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="score" items="${scores}">
                        <tr>
                            <td><c:out value="${score.scoreId}" /></td>
                            <td><c:out value="${score.submissionId}" /></td>
                            <td><c:out value="${score.totalScore}" /></td>
                            <td><c:out value="${score.deduction}" /></td>
                            <td><c:out value="${score.gradingNotes}" /></td>
                            <td><c:out value="${score.updatedAt}" /></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty scores}">
                        <tr><td colspan="6">Chưa có dữ liệu điểm.</td></tr>
                    </c:if>
                </tbody>
            </table>
        </section>
    </main>
</body>
</html>