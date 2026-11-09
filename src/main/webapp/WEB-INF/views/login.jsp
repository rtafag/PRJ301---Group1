<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đăng nhập AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; background: #f4f7fb; color: #172033; }
        main { max-width: 420px; margin: 8vh auto; padding: 2rem; background: white; border: 1px solid #dce3ed; border-radius: 12px; }
        label, input, button { display: block; width: 100%; box-sizing: border-box; }
        label { margin-top: 1rem; }
        input { margin-top: .4rem; padding: .65rem; }
        button { margin-top: 1.25rem; padding: .7rem; border: 0; border-radius: 6px; background: #2457c5; color: white; cursor: pointer; }
        .error { color: #a32020; }
    </style>
</head>
<body>
<main>
    <h1>Đăng nhập AITA</h1>
    <c:if test="${not empty errorMessage}">
        <p class="error" role="alert"><c:out value="${errorMessage}" /></p>
    </c:if>
    <form method="post" action="<c:out value='${pageContext.request.contextPath}' />/login">
        <label for="username">Tên đăng nhập</label>
        <input id="username" name="username" type="text" autocomplete="username"
               value="<c:out value='${lastUsername}' />" required>
        <label for="password">Mật khẩu</label>
        <input id="password" name="password" type="password" autocomplete="current-password" required>
        <button type="submit">Đăng nhập</button>
    </form>
</main>
</body>
</html>
