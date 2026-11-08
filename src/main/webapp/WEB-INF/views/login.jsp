<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đăng nhập AITA</title>
</head>
<body>
    <main>
        <h1>Đăng nhập AITA</h1>
        <c:if test="${not empty errorMessage}">
            <p role="alert"><c:out value="${errorMessage}" /></p>
        </c:if>
        <form method="post" action="<c:url value='/login'/>">
            <label for="username">Tên đăng nhập</label>
            <input id="username" name="username" type="text" autocomplete="username"
                   value="<c:out value='${lastUsername}'/>" required>
            <label for="password">Mật khẩu</label>
            <input id="password" name="password" type="password" autocomplete="current-password" required>
            <button type="submit">Đăng nhập</button>
        </form>
    </main>
</body>
</html>
