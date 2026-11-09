<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bảng điều khiển giảng viên - AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 2rem auto; max-width: 1000px; padding: 0 1rem; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border-bottom: 1px solid #ccc; padding: .7rem; text-align: left; }
        .status { border-radius: .25rem; background: #e0f2fe; padding: .15rem .5rem; }
    </style>
</head>
<body>
    <main>
        <nav>
            <a href="<c:url value='/home'/>">Trang chủ</a>
            <a href="<c:url value='/logout'/>">Đăng xuất</a>
        </nav>
        <h1>Bảng điều khiển giảng viên</h1>
        <p>Xem bài nộp của lớp và mở kết quả phân tích để hỗ trợ phản hồi.</p>
        <table>
            <thead>
                <tr><th>Mã bài nộp</th><th>Mã sinh viên</th><th>Trạng thái</th><th>Thời gian</th><th>Phân tích</th></tr>
            </thead>
            <tbody>
                <c:forEach var="submission" items="${submissions}">
                    <tr>
                        <td><c:out value="${submission.submissionId}" /></td>
                        <td><c:out value="${submission.userId}" /></td>
                        <td><span class="status"><c:out value="${submission.status}" /></span></td>
                        <td><c:out value="${submission.submittedAt}" /></td>
                        <td>
                            <a href="<c:url value='/teacher/analysis'><c:param name='submissionId' value='${submission.submissionId}'/></c:url>">
                                Xem kết quả
                            </a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty submissions}">
                    <tr><td colspan="5">Chưa có bài nộp nào.</td></tr>
                </c:if>
            </tbody>
        </table>
    </main>
</body>
</html>
