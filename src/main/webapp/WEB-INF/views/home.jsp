<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AITA - Trang chủ & Bảng điều khiển</title>
    <style>
        :root {
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --bg: #0f172a;
            --card-bg: #1e293b;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --border: #334155;
            --accent: #10b981;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background-color: var(--bg); color: var(--text); padding: 24px; }
        .navbar {
            display: flex; justify-content: space-between; align-items: center;
            background-color: var(--card-bg); padding: 16px 24px;
            border-radius: 10px; border: 1px solid var(--border); margin-bottom: 24px;
        }
        .navbar .brand { font-size: 20px; font-weight: 700; color: #60a5fa; }
        .navbar .user-info { display: flex; align-items: center; gap: 16px; font-size: 14px; }
        .navbar .badge-role {
            background-color: rgba(37, 99, 235, 0.2); color: #93c5fd;
            padding: 4px 10px; border-radius: 20px; font-weight: 600;
        }
        .btn-logout {
            padding: 6px 14px; background-color: #ef4444; color: white;
            text-decoration: none; border-radius: 6px; font-size: 13px; font-weight: 600;
        }
        .stats-grid {
            display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 20px; margin-bottom: 28px;
        }
        .stat-card {
            background-color: var(--card-bg); border: 1px solid var(--border);
            border-radius: 10px; padding: 20px; text-align: center;
        }
        .stat-card .label { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }
        .stat-card .value { font-size: 32px; font-weight: 700; color: #38bdf8; }
        .panel {
            background-color: var(--card-bg); border: 1px solid var(--border);
            border-radius: 10px; padding: 24px; margin-bottom: 24px;
        }
        .panel h2 { font-size: 18px; margin-bottom: 16px; color: #f1f5f9; }
        .links-grid { display: flex; gap: 12px; flex-wrap: wrap; }
        .btn-link {
            padding: 10px 18px; background-color: var(--primary); color: white;
            text-decoration: none; border-radius: 6px; font-size: 14px; font-weight: 600;
        }
        .btn-link:hover { background-color: var(--primary-hover); }
        .jwt-info {
            background-color: #0b1120; border: 1px solid #1e3a8a;
            border-radius: 6px; padding: 12px; font-family: monospace; font-size: 13px;
            color: #93c5fd; word-break: break-all; margin-top: 12px;
        }
    </style>
</head>
<body>
    <div class="navbar">
        <div class="brand">AITA Dashboard (PRJ301 - Nhóm 1)</div>
        <div class="user-info">
            <c:choose>
                <c:when test="${not empty currentUser}">
                    <span>Xin chào, <strong><c:out value="${currentUser.fullName}" /></strong></span>
                    <span class="badge-role"><c:out value="${currentUser.role}" /></span>
                    <a href="${pageContext.request.contextPath}/logout" class="btn-logout">Đăng xuất</a>
                </c:when>
                <c:otherwise>
                    <a href="${pageContext.request.contextPath}/login" class="btn-link">Đăng nhập</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

    <!-- Quick Stats from Database 6 Normalized Tables -->
    <div class="stats-grid">
        <div class="stat-card">
            <div class="label">Người dùng (Users)</div>
            <div class="value"><c:out value="${userCount != null ? userCount : 1100}" /></div>
        </div>
        <div class="stat-card">
            <div class="label">Bài nộp (Submissions)</div>
            <div class="value"><c:out value="${subCount != null ? subCount : 200}" /></div>
        </div>
        <div class="stat-card">
            <div class="label">Điểm số đã chấm (Scores)</div>
            <div class="value"><c:out value="${scoreCount != null ? scoreCount : 200}" /></div>
        </div>
        <div class="stat-card">
            <div class="label">Quy tắc AST (RuleDefinition)</div>
            <div class="value"><c:out value="${ruleCount != null ? ruleCount : 6}" /></div>
        </div>
    </div>

    <!-- Actions & Nav -->
    <div class="panel">
        <h2>Chức năng hệ thống</h2>
        <div class="links-grid">
            <a href="${pageContext.request.contextPath}/score" class="btn-link">Xem Bảng Điểm (Score Table)</a>
            <a href="http://localhost:3000" target="_blank" class="btn-link" style="background-color: #10b981;">Mở Portal Web (Node / Express)</a>
        </div>

        <c:if test="${not empty jwtToken}">
            <div style="margin-top: 20px;">
                <strong>RFC 7519 JWT Token phiên hiện tại:</strong>
                <div class="jwt-info"><c:out value="${jwtToken}" /></div>
            </div>
        </c:if>
    </div>
</body>
</html>
