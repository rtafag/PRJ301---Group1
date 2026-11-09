<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="csrf-token" content="<c:out value='${submissionCsrfToken}' />">
    <title>Cổng nộp bài AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; background: #f4f7fb; color: #172033; }
        main { max-width: 860px; margin: 3rem auto; padding: 0 1rem; }
        nav { margin-bottom: 1.5rem; }
        nav a { color: #2457c5; margin-right: 1rem; }
        section { background: white; border: 1px solid #dce3ed; border-radius: 12px; padding: 1.5rem; margin-bottom: 1.25rem; }
        label, button { display: block; margin-top: 1rem; }
        input[type=file] { display: block; margin-top: .5rem; }
        button { border: 0; border-radius: 6px; padding: .7rem 1rem; background: #2457c5; color: white; cursor: pointer; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: .7rem; border-bottom: 1px solid #e6ebf2; text-align: left; }
        #submission-message { min-height: 1.5rem; }
    </style>
</head>
<body>
<main>
    <nav aria-label="Điều hướng">
        <a href="<c:out value='${pageContext.request.contextPath}' />/home">Trang chủ</a>
        <a href="<c:out value='${pageContext.request.contextPath}' />/analysis-dashboard">Kết quả phân tích AST</a>
        <a href="<c:out value='${pageContext.request.contextPath}' />/logout">Đăng xuất</a>
    </nav>
    <h1>Cổng nộp bài</h1>
    <p>Sinh viên: <c:out value="${userId}" /></p>

    <section aria-labelledby="upload-heading">
        <h2 id="upload-heading">Nộp bài tập</h2>
        <p>Chỉ chấp nhận tệp ZIP hợp lệ, tối đa 10 MB.</p>
        <form id="submission-form" action="<c:out value='${pageContext.request.contextPath}' />/student/submissions"
              method="post" enctype="multipart/form-data">
            <label for="submissionFile">Tệp bài nộp (.zip)</label>
            <input id="submissionFile" name="submissionFile" type="file" accept=".zip,application/zip" required>
            <button id="submit-button" type="submit">Nộp bài</button>
        </form>
        <p id="submission-message" role="status" aria-live="polite"></p>
    </section>

    <section aria-labelledby="history-heading">
        <h2 id="history-heading">Lịch sử nộp bài</h2>
        <table>
            <thead>
            <tr><th>Mã bài nộp</th><th>Trạng thái</th><th>Thời gian</th></tr>
            </thead>
            <tbody>
            <c:forEach var="submission" items="${submissions}">
                <tr>
                    <td><c:out value="${submission.submissionId}" /></td>
                    <td><c:out value="${submission.status}" /></td>
                    <td><c:out value="${submission.submittedAt}" /></td>
                </tr>
            </c:forEach>
            <c:if test="${empty submissions}">
                <tr><td colspan="3">Bạn chưa có bài nộp nào.</td></tr>
            </c:if>
            </tbody>
        </table>
    </section>
</main>
<script>
    document.getElementById("submission-form").addEventListener("submit", async function (event) {
        event.preventDefault();
        const message = document.getElementById("submission-message");
        const submitButton = document.getElementById("submit-button");
        const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
        submitButton.disabled = true;
        message.textContent = "Đang tải bài nộp...";
        try {
            const response = await fetch(this.action, {
                method: "POST",
                body: new FormData(this),
                headers: { "X-CSRF-Token": csrfToken },
                credentials: "same-origin"
            });
            if (!response.ok) {
                message.textContent = "Không thể nộp bài (HTTP " + response.status + "). Hãy kiểm tra tệp ZIP và thử lại.";
                submitButton.disabled = false;
                return;
            }
            const result = await response.json();
            sessionStorage.setItem("submissionNotice",
                "Nộp bài thành công. Mã bài nộp: " + result.submissionId + " · SHA-256: " + result.sha256);
            window.location.reload();
        } catch (error) {
            message.textContent = "Không thể kết nối máy chủ. Vui lòng thử lại.";
            submitButton.disabled = false;
        }
    });
    const submissionNotice = sessionStorage.getItem("submissionNotice");
    if (submissionNotice) {
        document.getElementById("submission-message").textContent = submissionNotice;
        sessionStorage.removeItem("submissionNotice");
    }
</script>
</body>
</html>
