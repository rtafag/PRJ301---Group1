<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Kết quả phân tích - AITA</title>
    <style>
        body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 2rem auto; max-width: 1000px; padding: 0 1rem; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border-bottom: 1px solid #ccc; padding: .7rem; text-align: left; vertical-align: top; }
        .severity { border-radius: .25rem; display: inline-block; font-weight: 700; padding: .15rem .5rem; }
        .severity-high, .severity-critical { background: #fee2e2; color: #991b1b; }
        .severity-medium { background: #fef3c7; color: #92400e; }
        .severity-low, .severity-info { background: #dcfce7; color: #166534; }
        pre { background: #f3f4f6; overflow: auto; padding: 1rem; white-space: pre-wrap; overflow-wrap: anywhere; }
    </style>
</head>
<body>
    <main>
        <nav>
            <c:choose>
                <c:when test="${sessionScope.currentUser.role eq 'Teacher'}">
                    <a href="<c:url value='/teacher/dashboard'/>">Danh sách bài nộp</a>
                </c:when>
                <c:otherwise>
                    <a href="<c:url value='/student/submissions'/>">Lịch sử bài nộp</a>
                </c:otherwise>
            </c:choose>
            <a href="<c:url value='/home'/>">Trang chủ</a>
        </nav>
        <h1>Kết quả phân tích AST</h1>
        <p>Bài nộp: <strong><c:out value="${submission.submissionId}" /></strong>
            · Trạng thái: <c:out value="${submission.status}" /></p>

        <c:choose>
            <c:when test="${empty analysis}">
                <p role="status">Bài nộp chưa có kết quả phân tích.</p>
            </c:when>
            <c:otherwise>
                <section aria-labelledby="summary-heading">
                    <h2 id="summary-heading">Tổng quan</h2>
                    <p><c:out value="${fn:length(findings)}" /> vấn đề được phát hiện</p>
                    <c:if test="${not empty analysis.aiFeedback}">
                        <h3>Nhận xét</h3>
                        <p><c:out value="${analysis.aiFeedback}" /></p>
                    </c:if>
                </section>

                <section aria-labelledby="findings-heading">
                    <h2 id="findings-heading">Vấn đề và gợi ý sửa</h2>
                    <table>
                        <thead>
                            <tr><th>Lỗi</th><th>Mức độ</th><th>Vị trí</th><th>Mô tả và gợi ý sửa</th></tr>
                        </thead>
                        <tbody>
                            <c:forEach var="finding" items="${findings}">
                                <tr>
                                    <td>
                                        <strong><c:out value="${finding.ruleName}" /></strong>
                                        <c:if test="${not empty finding.ruleCode}">
                                            <br><small><c:out value="${finding.ruleCode}" /></small>
                                        </c:if>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${finding.severity eq 'Critical' or finding.severity eq 'High'}">
                                                <span class="severity severity-high"><c:out value="${finding.severity}" /></span>
                                            </c:when>
                                            <c:when test="${finding.severity eq 'Medium'}">
                                                <span class="severity severity-medium"><c:out value="${finding.severity}" /></span>
                                            </c:when>
                                            <c:when test="${finding.severity eq 'Low' or finding.severity eq 'Info'}">
                                                <span class="severity severity-low"><c:out value="${finding.severity}" /></span>
                                            </c:when>
                                            <c:otherwise><c:out value="${finding.severity}" /></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td><c:out value="${finding.fileName}" />:<c:out value="${finding.lineNumber}" /></td>
                                    <td>
                                        <c:if test="${not empty finding.description}">
                                            <p><c:out value="${finding.description}" /></p>
                                        </c:if>
                                        <c:out value="${finding.suggestedFix}" />
                                    </td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty findings}">
                                <tr><td colspan="4">Không phát hiện vi phạm quy tắc.</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </section>

                <details>
                    <summary>Xem AST JSON</summary>
                    <pre><c:out value="${analysis.astJson}" /></pre>
                </details>
            </c:otherwise>
        </c:choose>
    </main>
</body>
</html>
