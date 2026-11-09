<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nộp bài - AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 2rem auto; max-width: 900px; padding: 0 1rem; }
        form { display: flex; flex-wrap: wrap; gap: .75rem; align-items: center; margin: 1rem 0 2rem; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border-bottom: 1px solid #ccc; padding: .65rem; text-align: left; }
        #upload-message { min-height: 1.5em; }
    </style>
</head>
<body>
    <main>
        <nav>
            <a href="<c:url value='/home'/>">Trang chủ</a>
            <a href="<c:url value='/logout'/>">Đăng xuất</a>
        </nav>
        <h1>Nộp bài lập trình</h1>
        <p>Chọn tệp ZIP tối đa 10 MB. Hệ thống kiểm tra định dạng, tính toàn vẹn và bài nộp trùng trước khi lưu.</p>
        <form id="submission-form" method="post" enctype="multipart/form-data"
              action="<c:url value='/student/submissions'/>">
            <label for="submissionFile">Tệp dự án (.zip)</label>
            <input id="submissionFile" name="submissionFile" type="file" accept=".zip,application/zip" required>
            <button type="submit">Nộp bài</button>
        </form>
        <p id="upload-message" role="status" aria-live="polite"></p>

        <h2>Lịch sử bài nộp của bạn</h2>
        <table>
            <thead>
                <tr><th>Mã bài nộp</th><th>Trạng thái</th><th>Thời gian</th><th>Kết quả</th></tr>
            </thead>
            <tbody id="submission-list">
                <c:forEach var="submission" items="${submissions}">
                    <tr>
                        <td><c:out value="${submission.submissionId}" /></td>
                        <td><c:out value="${submission.status}" /></td>
                        <td><c:out value="${submission.submittedAt}" /></td>
                        <td><a href="<c:url value='/student/analysis'><c:param name='submissionId' value='${submission.submissionId}'/></c:url>">Xem kết quả</a></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty submissions}">
                    <tr id="empty-submissions"><td colspan="4">Bạn chưa có bài nộp nào.</td></tr>
                </c:if>
            </tbody>
        </table>
    </main>
    <script>
        (function () {
            var form = document.getElementById("submission-form");
            var message = document.getElementById("upload-message");
            var list = document.getElementById("submission-list");

            form.addEventListener("submit", function (event) {
                event.preventDefault();
                message.textContent = "Đang tải bài nộp...";
                fetch(form.action, { method: "POST", body: new FormData(form), credentials: "same-origin" })
                    .then(function (response) {
                        if (!response.ok) {
                            var messages = {
                                401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                                403: "Chỉ sinh viên mới được phép nộp bài.",
                                409: "Tệp này đã được nộp trước đó.",
                                413: "Tệp ZIP vượt quá giới hạn 10 MB.",
                                400: "Vui lòng chọn tệp ZIP hợp lệ."
                            };
                            throw new Error(messages[response.status] || "Không thể nộp bài. Vui lòng thử lại.");
                        }
                        return response.json();
                    })
                    .then(function (result) {
                        var emptyRow = document.getElementById("empty-submissions");
                        if (emptyRow) {
                            emptyRow.remove();
                        }
                        var row = document.createElement("tr");
                        [result.submissionId, result.status, new Date().toLocaleString()].forEach(function (value) {
                            var cell = document.createElement("td");
                            cell.textContent = value;
                            row.appendChild(cell);
                        });
                        var resultCell = document.createElement("td");
                        var resultLink = document.createElement("a");
                        resultLink.href = "<c:url value='/student/analysis'/>?submissionId="
                            + encodeURIComponent(result.submissionId);
                        resultLink.textContent = "Xem kết quả";
                        resultCell.appendChild(resultLink);
                        row.appendChild(resultCell);
                        list.insertBefore(row, list.firstChild);
                        form.reset();
                        message.textContent = "Nộp bài thành công. Mã bài nộp: " + result.submissionId;
                    })
                    .catch(function (error) {
                        message.textContent = error.message;
                    });
            });
        }());
    </script>
</body>
</html>
