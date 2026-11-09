<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AITA - Đăng nhập hệ thống</title>
    <style>
        :root {
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --bg: #0f172a;
            --card-bg: #1e293b;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --border: #334155;
            --error: #ef4444;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body {
            background-color: var(--bg);
            color: var(--text);
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
        }
        .login-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 32px;
            width: 100%;
            max-width: 420px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
        }
        .header { text-align: center; margin-bottom: 24px; }
        .header h1 { font-size: 24px; font-weight: 700; color: #60a5fa; margin-bottom: 8px; }
        .header p { font-size: 14px; color: var(--text-muted); }
        .form-group { margin-bottom: 20px; }
        label { display: block; font-size: 14px; font-weight: 500; margin-bottom: 6px; }
        input[type="text"], input[type="password"] {
            width: 100%;
            padding: 10px 12px;
            background-color: #0f172a;
            border: 1px solid var(--border);
            border-radius: 6px;
            color: var(--text);
            font-size: 14px;
        }
        input:focus { outline: none; border-color: var(--primary); }
        .btn-submit {
            width: 100%;
            padding: 12px;
            background-color: var(--primary);
            color: white;
            border: none;
            border-radius: 6px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        .btn-submit:hover { background-color: var(--primary-hover); }
        .alert-error {
            background-color: rgba(239, 68, 68, 0.15);
            border: 1px solid var(--error);
            color: #fca5a5;
            padding: 12px;
            border-radius: 6px;
            font-size: 14px;
            margin-bottom: 20px;
        }
        .footer-hint {
            margin-top: 24px;
            padding-top: 16px;
            border-top: 1px solid var(--border);
            font-size: 12px;
            color: var(--text-muted);
            text-align: center;
            line-height: 1.6;
        }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="header">
            <h1>AITA Automated Grader</h1>
            <p>Hệ thống chấm bài & phân tích AST tự động</p>
        </div>

        <c:if test="${not empty errorMessage}">
            <div class="alert-error">
                <c:out value="${errorMessage}" />
            </div>
        </c:if>

        <form action="${pageContext.request.contextPath}/login" method="POST">
            <div class="form-group">
                <label for="username">Tên đăng nhập / User ID</label>
                <input type="text" id="username" name="username" value="${lastUsername}" placeholder="Ví dụ: sondang619 hoặc dungdang442" required autofocus />
            </div>
            <div class="form-group">
                <label for="password">Mật khẩu</label>
                <input type="password" id="password" name="password" placeholder="Nhập mật khẩu (Mặc định: password123)" required />
            </div>
            <button type="submit" class="btn-submit">Đăng nhập với JWT</button>
        </form>

        <div class="footer-hint">
            <strong>Tài khoản Demo có sẵn:</strong><br/>
            • Giảng viên: <code>sondang619</code> | Mật khẩu: <code>password123</code><br/>
            • Sinh viên: <code>dungdang442</code> | Mật khẩu: <code>password123</code><br/>
            <em>Xác thực RFC 7519 JWT tự động lưu vào HttpOnly Cookie.</em>
        </div>
    </div>
</body>
</html>
