<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bài nộp | AITA</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/portal.css">
</head>
<body>
    <header class="topbar">
        <a class="brand" href="${pageContext.request.contextPath}/submissions">AITA<span>Submission Portal</span></a>
        <div class="account">
            <span>Xin chào, <strong><c:out value="${sessionUser.fullName}" /></strong></span>
            <form method="post" action="${pageContext.request.contextPath}/logout">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <button class="button button-quiet" type="submit">Đăng xuất</button>
            </form>
        </div>
    </header>

    <main class="page-shell">
        <section class="page-heading">
            <p class="eyebrow">KHÔNG GIAN SINH VIÊN</p>
            <h1>Bài nộp của bạn</h1>
            <p class="muted">Tải lên tệp Java hoặc ZIP. Tệp được lưu riêng tư và gắn với tài khoản của bạn.</p>
        </section>

        <c:if test="${param.submitted eq '1'}">
            <div class="alert alert-success" role="status">Nộp bài thành công. Bài làm đang chờ xử lý.</div>
        </c:if>
        <c:if test="${not empty errorMessage}">
            <div class="alert alert-error" role="alert"><c:out value="${errorMessage}" /></div>
        </c:if>

        <section class="panel upload-panel">
            <div>
                <p class="eyebrow">BÀI TẬP</p>
                <h2>Nộp mã nguồn</h2>
                <p class="muted">Định dạng .java hoặc .zip · Tối đa 10 MB</p>
            </div>
            <form method="post" action="${pageContext.request.contextPath}/submissions"
                  enctype="multipart/form-data" class="upload-form">
                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                <label class="file-picker" for="sourceFile">
                    <span>Chọn tệp từ thiết bị</span>
                    <input id="sourceFile" name="sourceFile" type="file" accept=".java,.zip" required>
                </label>
                <button class="button button-primary" type="submit">Nộp bài</button>
            </form>
        </section>

        <section class="panel">
            <div class="section-heading">
                <div>
                    <p class="eyebrow">LỊCH SỬ</p>
                    <h2>Các lần nộp gần đây</h2>
                </div>
                <span class="count"><c:out value="${submissions.size()}" /> bài</span>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr><th>Mã bài nộp</th><th>Tệp</th><th>SHA-256</th><th>Trạng thái</th><th>Thời gian</th></tr>
                    </thead>
                    <tbody>
                        <c:forEach var="submission" items="${submissions}">
                            <tr>
                                <td><code><c:out value="${submission.submissionId}" /></code></td>
                                <td><c:out value="${submission.fileName}" /></td>
                                <td><code class="hash"><c:out value="${submission.fileHash}" /></code></td>
                                <td><span class="status"><c:out value="${submission.status}" /></span></td>
                                <td><c:out value="${submission.submittedAt}" /></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty submissions}">
                            <tr><td class="empty-state" colspan="5">Bạn chưa có bài nộp nào. Tải lên bài đầu tiên ở phía trên.</td></tr>
                        </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</body>
</html>
