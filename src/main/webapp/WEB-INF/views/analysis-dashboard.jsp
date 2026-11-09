<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Kết quả phân tích AST - AITA</title>
    <style>
        :root { color-scheme: light; font-family: system-ui, sans-serif; color: #172033; background: #f4f7fb; }
        body { margin: 0; }
        main { max-width: 1000px; margin: 2rem auto; padding: 0 1rem; }
        nav { margin-bottom: 1.5rem; }
        nav a { color: #2457c5; margin-right: 1rem; }
        .toolbar, .analysis-card { background: white; border: 1px solid #dce3ed; border-radius: 12px; padding: 1.25rem; margin-bottom: 1rem; }
        .toolbar { display: flex; flex-wrap: wrap; gap: .75rem; align-items: end; }
        label { display: grid; gap: .35rem; }
        input, select { padding: .55rem; border: 1px solid #b9c5d5; border-radius: 6px; }
        .analysis-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem; }
        .finding { border-left: 4px solid #64748b; background: #f8fafc; padding: .8rem 1rem; margin: .75rem 0; }
        .severity-badge { display: inline-block; border-radius: 999px; padding: .15rem .55rem; font-size: .85rem; font-weight: 700; }
        .severity-critical { background: #fee4e2; color: #912018; }
        .severity-high, .severity-error { background: #ffead5; color: #93370d; }
        .severity-medium, .severity-warning { background: #fef0c7; color: #854a0e; }
        .severity-low, .severity-info { background: #dbeafe; color: #1e40af; }
        .fix { white-space: pre-wrap; overflow-wrap: anywhere; background: #eef2f7; padding: .75rem; border-radius: 6px; }
        .muted { color: #536176; }
        [hidden] { display: none !important; }
    </style>
</head>
<body>
<main>
    <nav aria-label="Điều hướng">
        <a href="<c:out value='${pageContext.request.contextPath}' />/home">Trang chủ</a>
        <c:if test="${dashboardScope eq 'personal'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/student/submissions">Cổng nộp bài</a>
        </c:if>
        <c:if test="${dashboardScope eq 'all'}">
            <a href="<c:out value='${pageContext.request.contextPath}' />/scores">Bảng điểm</a>
        </c:if>
        <a href="<c:out value='${pageContext.request.contextPath}' />/logout">Đăng xuất</a>
    </nav>
    <h1>Kết quả phân tích AST</h1>
    <p>
        <c:choose>
            <c:when test="${dashboardScope eq 'personal'}">Kết quả bài nộp của bạn.</c:when>
            <c:otherwise>Kết quả phân tích của tất cả sinh viên.</c:otherwise>
        </c:choose>
    </p>

    <section class="toolbar" aria-label="Bộ lọc kết quả">
        <label for="severity-filter">Mức độ nghiêm trọng
            <select id="severity-filter">
                <option value="">Tất cả</option>
                <option value="critical">Critical</option>
                <option value="high">High</option>
                <option value="error">Error</option>
                <option value="medium">Medium</option>
                <option value="warning">Warning</option>
                <option value="low">Low</option>
                <option value="info">Info</option>
            </select>
        </label>
        <label for="finding-search">Tìm theo quy tắc, tệp hoặc mô tả
            <input id="finding-search" type="search" placeholder="Ví dụ: NullCheck.java">
        </label>
        <span id="filter-count" class="muted" aria-live="polite"></span>
    </section>
    <p id="no-matching-findings" class="muted" role="status" hidden>
        Không tìm thấy phát hiện phù hợp với bộ lọc.
    </p>

    <c:forEach var="analysis" items="${analyses}">
        <section class="analysis-card">
            <div class="analysis-heading">
                <h2>Bài nộp <c:out value="${analysis.submissionId}" /></h2>
                <span class="muted">
                    Thời gian phân tích: <c:out value="${analysis.executionTimeMs}" /> ms
                </span>
            </div>
            <c:if test="${not empty analysis.aiFeedback}">
                <p><strong>Nhận xét:</strong> <c:out value="${analysis.aiFeedback}" /></p>
            </c:if>
            <c:choose>
                <c:when test="${empty analysis.findings}">
                    <p class="muted">Không phát hiện vi phạm quy tắc.</p>
                </c:when>
                <c:otherwise>
                    <h3>Các phát hiện (<c:out value="${fn:length(analysis.findings)}" />)</h3>
                    <c:forEach var="finding" items="${analysis.findings}">
                        <article class="finding"
                                 data-severity="<c:out value='${finding.severity}' />">
                            <p>
                                <strong><c:out value="${finding.ruleName}" /></strong>
                                <c:if test="${not empty finding.ruleCode}">
                                    (<c:out value="${finding.ruleCode}" />)
                                </c:if>
                                <c:choose>
                                    <c:when test="${finding.severity eq 'Critical'}">
                                        <span class="severity-badge severity-critical"><c:out value="${finding.severity}" /></span>
                                    </c:when>
                                    <c:when test="${finding.severity eq 'High' or finding.severity eq 'Error'}">
                                        <span class="severity-badge severity-high"><c:out value="${finding.severity}" /></span>
                                    </c:when>
                                    <c:when test="${finding.severity eq 'Medium' or finding.severity eq 'Warning'}">
                                        <span class="severity-badge severity-medium"><c:out value="${finding.severity}" /></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="severity-badge severity-info"><c:out value="${finding.severity}" /></span>
                                    </c:otherwise>
                                </c:choose>
                            </p>
                            <p>
                                <c:out value="${finding.fileName}" />
                                <c:if test="${finding.lineNumber gt 0}">
                                    , dòng <c:out value="${finding.lineNumber}" />
                                </c:if>
                                <c:if test="${finding.penaltyPoints gt 0}">
                                    · Trừ <c:out value="${finding.penaltyPoints}" /> điểm
                                </c:if>
                            </p>
                            <c:if test="${not empty finding.description}">
                                <p><c:out value="${finding.description}" /></p>
                            </c:if>
                            <c:if test="${not empty finding.suggestedFix}">
                                <p><strong>Gợi ý sửa:</strong></p>
                                <pre class="fix"><c:out value="${finding.suggestedFix}" /></pre>
                            </c:if>
                        </article>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </section>
    </c:forEach>

    <c:if test="${empty analyses}">
        <section class="analysis-card">
            <p>Chưa có kết quả phân tích để hiển thị.</p>
        </section>
    </c:if>
</main>
<script>
    (function () {
        const severityFilter = document.getElementById("severity-filter");
        const searchInput = document.getElementById("finding-search");
        const countLabel = document.getElementById("filter-count");
        const noMatchingFindings = document.getElementById("no-matching-findings");
        const findings = Array.from(document.querySelectorAll(".finding"));

        function applyFilters() {
            const severity = severityFilter.value.toLowerCase();
            const query = searchInput.value.trim().toLowerCase();
            let visible = 0;
            findings.forEach(function (finding) {
                const matchesSeverity = !severity
                    || (finding.dataset.severity || "").toLowerCase() === severity;
                const matchesQuery = !query || finding.textContent.toLowerCase().includes(query);
                finding.hidden = !(matchesSeverity && matchesQuery);
                if (!finding.hidden) visible += 1;
            });
            countLabel.textContent = visible + " / " + findings.length + " phát hiện";
            noMatchingFindings.hidden = visible !== 0 || findings.length === 0;
        }

        severityFilter.addEventListener("change", applyFilters);
        searchInput.addEventListener("input", applyFilters);
        applyFilters();
    }());
</script>
</body>
</html>
