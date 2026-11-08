<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bảng điểm AITA</title>
</head>
<body>
    <main>
        <h1>Bảng điểm</h1>
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
    </main>
</body>
</html>