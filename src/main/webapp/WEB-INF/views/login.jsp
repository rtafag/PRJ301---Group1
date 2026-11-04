<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đăng nhập | AITA</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/portal.css">
</head>
<body>
    <main class="auth-shell">
        <section class="auth-card">
            <a class="brand" href="${pageContext.request.contextPath}/login">AITA<span>Submission Portal</span></a>
            <p class="eyebrow">CỔNG NỘP BÀI</p>
            <h1>Chào mừng trở lại</h1>
            <p class="muted">Đăng nhập bằng tài khoản sinh viên để gửi bài và theo dõi trạng thái.</p>

            <c:if test="${not empty errorMessage}">
                <div class="alert alert-error" role="alert"><c:out value="${errorMessage}" /></div>
            </c:if>

            <form method="post" action="${pageContext.request.contextPath}/login" class="form-stack">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <label for="username">Tên đăng nhập</label>
                <input id="username" name="username" autocomplete="username" required maxlength="100">
                <label for="password">Mật khẩu</label>
                <input id="password" name="password" type="password" autocomplete="current-password" required>
                <button class="button button-primary" type="submit">Đăng nhập</button>
            </form>
            <p class="demo-hint">Tài khoản demo dùng mật khẩu mẫu <code>hash_&lt;tên-đăng-nhập&gt;</code>.</p>
        </section>
    </main>
</body>
</html>
