/**
 * 小红书内容审核系统 - 前端脚本
 */

(function () {
    'use strict';

    // State Management
    const state = {
        currentTab: 'single',
        batchJobId: null,
        pollingInterval: null,
        historyPage: 0,
        historySize: 20,
        historyTotalPages: 0,
        jobPage: 0,
        jobSize: 10,
        jobTotalPages: 0,
        currentJobDetail: null,
        currentJobId: null,
        currentDetail: null
    };

    // DOM Elements
    const elements = {
        // Menu
        menuItems: document.querySelectorAll('.menu-item'),
        tabPanels: document.querySelectorAll('.tab-panel'),

        // Single Audit
        singleUrl: document.getElementById('single-url'),
        forceRefresh: document.getElementById('force-refresh'),
        singleAuditBtn: document.getElementById('single-audit-btn'),
        singleResult: document.getElementById('single-result'),
        resultStatus: document.getElementById('result-status'),
        resultRisk: document.getElementById('result-risk'),
        resultConfidence: document.getElementById('result-confidence'),
        resultDetails: document.getElementById('result-details'),

        // Batch Upload
        uploadArea: document.getElementById('upload-area'),
        excelFile: document.getElementById('excel-file'),
        fileInfo: document.getElementById('file-info'),
        fileName: document.getElementById('file-name'),
        removeFile: document.getElementById('remove-file'),
        uploadBtn: document.getElementById('upload-btn'),
        batchProgress: document.getElementById('batch-progress'),
        progressBar: document.getElementById('progress-bar'),
        progressText: document.getElementById('progress-text'),
        statTotal: document.getElementById('stat-total'),
        statCompleted: document.getElementById('stat-completed'),
        statPassed: document.getElementById('stat-passed'),
        statRejected: document.getElementById('stat-rejected'),
        jobStatus: document.getElementById('job-status'),
        downloadBtn: document.getElementById('download-btn'),

        // History
        filterPostId: document.getElementById('filter-post-id'),
        filterJobId: document.getElementById('filter-job-id'),
        filterStatus: document.getElementById('filter-status'),
        filterStartDate: document.getElementById('filter-start-date'),
        filterEndDate: document.getElementById('filter-end-date'),
        searchBtn: document.getElementById('search-btn'),
        historyTable: document.getElementById('history-table'),
        historyTbody: document.getElementById('history-tbody'),
        emptyState: document.getElementById('empty-state'),
        prevPage: document.getElementById('prev-page'),
        nextPage: document.getElementById('next-page'),
        pageInfo: document.getElementById('page-info'),
        pageSize: document.getElementById('page-size'),
        exportBtn: document.getElementById('export-btn'),

        // Jobs
        jobFilterId: document.getElementById('job-filter-id'),
        jobFilterStatus: document.getElementById('job-filter-status'),
        jobFilterKeyword: document.getElementById('job-filter-keyword'),
        jobFilterStartDate: document.getElementById('job-filter-start-date'),
        jobFilterEndDate: document.getElementById('job-filter-end-date'),
        jobSearchBtn: document.getElementById('job-search-btn'),
        jobTable: document.getElementById('job-table'),
        jobTbody: document.getElementById('job-tbody'),
        jobEmptyState: document.getElementById('job-empty-state'),
        jobPrevPage: document.getElementById('job-prev-page'),
        jobNextPage: document.getElementById('job-next-page'),
        jobPageInfo: document.getElementById('job-page-info'),
        jobPageSize: document.getElementById('job-page-size'),
        jobDetailModal: document.getElementById('job-detail-modal'),
        jobDetailModalBody: document.getElementById('job-detail-modal-body'),

        // Modal
        imageModal: document.getElementById('image-modal'),
        modalImage: document.getElementById('modal-image'),

        // Toast
        toast: document.getElementById('toast')
    };

    // Initialize
    function init() {
        bindEvents();
        setDefaultDates();
    }

    // Bind Events
    function bindEvents() {
        // Menu switching
        elements.menuItems.forEach(btn => {
            btn.addEventListener('click', () => switchTab(btn.dataset.tab));
        });

        // Single audit
        elements.singleAuditBtn.addEventListener('click', handleSingleAudit);

        // Async audit result buttons
        document.getElementById('async-view-job')?.addEventListener('click', () => {
            const jobId = document.getElementById('async-job-id')?.textContent;
            if (jobId) {
                switchTab('jobs');
                document.getElementById('job-filter-id').value = jobId;
                loadJobs();
            }
        });

        document.getElementById('async-submit-new')?.addEventListener('click', () => {
            document.getElementById('async-result').classList.add('hidden');
            document.getElementById('single-url').value = '';
            document.getElementById('single-url').focus();
        });

        // File upload
        elements.uploadArea.addEventListener('click', () => elements.excelFile.click());
        elements.excelFile.addEventListener('change', handleFileSelect);
        elements.removeFile.addEventListener('click', clearFile);
        elements.uploadArea.addEventListener('dragover', handleDragOver);
        elements.uploadArea.addEventListener('dragleave', handleDragLeave);
        elements.uploadArea.addEventListener('drop', handleDrop);
        elements.uploadBtn.addEventListener('click', handleBatchUpload);

        // History search
        elements.searchBtn.addEventListener('click', () => {
            state.historyPage = 0;
            loadHistoryResults();
        });

        // Pagination
        elements.prevPage.addEventListener('click', () => {
            if (state.historyPage > 0) {
                state.historyPage--;
                loadHistoryResults();
            }
        });

        elements.nextPage.addEventListener('click', () => {
            if (state.historyPage < state.historyTotalPages - 1) {
                state.historyPage++;
                loadHistoryResults();
            }
        });

        elements.pageSize.addEventListener('change', () => {
            state.historySize = parseInt(elements.pageSize.value);
            state.historyPage = 0;
            loadHistoryResults();
        });

        // Export
        elements.exportBtn.addEventListener('click', handleExport);

        // Jobs
        elements.jobSearchBtn.addEventListener('click', () => {
            state.jobPage = 0;
            loadJobList();
        });

        // Job pagination
        elements.jobPrevPage.addEventListener('click', () => {
            if (state.jobPage > 0) {
                state.jobPage--;
                loadJobList();
            }
        });

        elements.jobNextPage.addEventListener('click', () => {
            if (state.jobPage < state.jobTotalPages - 1) {
                state.jobPage++;
                loadJobList();
            }
        });

        elements.jobPageSize.addEventListener('change', () => {
            state.jobSize = parseInt(elements.jobPageSize.value);
            state.jobPage = 0;
            loadJobList();
        });

        // Job detail modal
        document.querySelector('.job-detail-modal-close').addEventListener('click', hideJobDetail);
        elements.jobDetailModal.addEventListener('click', (e) => {
            if (e.target === elements.jobDetailModal) {
                hideJobDetail();
            }
        });

        // Batch download
        elements.downloadBtn.addEventListener('click', () => {
            if (state.batchJobId) {
                window.open(`/api/v1/audit/download/${state.batchJobId}`, '_blank');
            }
        });

        // Detail modal
        document.querySelector('.detail-modal-close').addEventListener('click', hideDetail);
        document.getElementById('detail-modal').addEventListener('click', (e) => {
            if (e.target === document.getElementById('detail-modal')) {
                hideDetail();
            }
        });

        // Modal
        document.querySelector('.modal-close').addEventListener('click', hideModal);
        elements.imageModal.addEventListener('click', (e) => {
            if (e.target === elements.imageModal) {
                hideModal();
            }
        });

        // Keyboard
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                hideModal();
                hideDetail();
            }
        });
    }

    // Tab Switching
    function switchTab(tabName) {
        state.currentTab = tabName;

        elements.menuItems.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        elements.tabPanels.forEach(panel => {
            panel.classList.toggle('active', panel.id === tabName + '-tab');
        });

        // Stop polling when switching away from batch tab
        if (tabName !== 'batch' && state.pollingInterval) {
            clearInterval(state.pollingInterval);
            state.pollingInterval = null;
        }

        // Load job list when switching to jobs tab
        if (tabName === 'jobs') {
            loadJobList();
        }
    }

    // Single Audit
    async function handleSingleAudit() {
        const url = elements.singleUrl.value.trim();

        if (!url) {
            showToast('请输入小红书链接', 'warning');
            return;
        }

        if (!validateUrl(url)) {
            showToast('请输入有效的小红书链接', 'warning');
            return;
        }

        // Get selected audit mode
        const auditMode = document.querySelector('input[name="audit-mode"]:checked')?.value || 'sync';

        setLoading(elements.singleAuditBtn, true);

        try {
            if (auditMode === 'async') {
                // Async mode: submit to queue
                await handleAsyncAudit(url);
            } else {
                // Sync mode: direct audit
                await handleSyncAudit(url);
            }
        } catch (error) {
            showToast(error.message || '审核失败，请稍后重试', 'error');
            elements.singleResult.classList.add('hidden');
            document.getElementById('async-result').classList.add('hidden');
        } finally {
            setLoading(elements.singleAuditBtn, false);
        }
    }

    // Sync audit (original behavior)
    async function handleSyncAudit(url) {
        const response = await fetch('/api/v1/audit/content', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                url: url,
                forceRefresh: elements.forceRefresh.checked
            })
        });

        const data = await response.json();

        if (data.code !== '000000') {
            throw new Error(data.message || '审核失败');
        }

        // Hide async result, show sync result
        document.getElementById('async-result').classList.add('hidden');
        displaySingleResult(data.data);
    }

    // Async audit (submit to queue)
    async function handleAsyncAudit(url) {
        const response = await fetch('/api/audit/async', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                url: url,
                forceRefresh: elements.forceRefresh.checked,
                source: 'WEB_SINGLE_ASYNC'
            })
        });

        const data = await response.json();

        if (data.status !== 'accepted') {
            throw new Error(data.message || '任务提交失败');
        }

        // Hide sync result, show async result
        elements.singleResult.classList.add('hidden');
        displayAsyncResult(data.jobId);
    }

    // Display async audit result
    function displayAsyncResult(jobId) {
        document.getElementById('async-result').classList.remove('hidden');
        document.getElementById('async-job-id').textContent = jobId;
    }

    function displaySingleResult(result) {
        elements.singleResult.classList.remove('hidden');

        // Handle both camelCase and snake_case from backend
        const riskLevel = result.riskLevel || result.risk_level || 'LOW';
        const status = result.status || 'UNCERTAIN';

        // Status badge
        elements.resultStatus.textContent = getStatusText(status);
        elements.resultStatus.className = 'status-badge ' + status.toLowerCase();

        // Risk badge
        elements.resultRisk.textContent = getRiskText(riskLevel);
        elements.resultRisk.className = 'risk-badge ' + riskLevel.toLowerCase();

        // Confidence
        elements.resultConfidence.textContent = `置信度: ${(result.confidenceScore * 100).toFixed(1)}%`;

        // Details
        let detailsHtml = '';

        if (result.reasons && result.reasons.length > 0) {
            result.reasons.forEach(reason => {
                detailsHtml += `
                    <div class="reason-item">
                        <div class="reason-dimension">${getDimensionText(reason.dimension)}</div>
                        <div class="reason-text">${reason.reason}</div>
                        <div class="reason-severity">严重程度: ${getSeverityText(reason.severity)}</div>
                    </div>
                `;
            });
        } else {
            detailsHtml = '<div class="reason-item"><div class="reason-text">未发现违规内容</div></div>';
        }

        elements.resultDetails.innerHTML = detailsHtml;
    }

    // File Upload
    function handleFileSelect(e) {
        const file = e.target.files[0];
        if (file) {
            displayFileInfo(file);
        }
    }

    function handleDragOver(e) {
        e.preventDefault();
        elements.uploadArea.classList.add('dragover');
    }

    function handleDragLeave(e) {
        e.preventDefault();
        elements.uploadArea.classList.remove('dragover');
    }

    function handleDrop(e) {
        e.preventDefault();
        elements.uploadArea.classList.remove('dragover');

        const file = e.dataTransfer.files[0];
        if (file) {
            displayFileInfo(file);
        }
    }

    function displayFileInfo(file) {
        // Validate file type
        const validTypes = [
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'application/vnd.ms-excel'
        ];

        const fileName = file.name.toLowerCase();
        if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
            showToast('请上传 Excel 文件 (.xlsx 或 .xls)', 'warning');
            return;
        }

        // Validate file size (max 10MB)
        if (file.size > 10 * 1024 * 1024) {
            showToast('文件大小不能超过 10MB', 'warning');
            return;
        }

        elements.fileName.textContent = file.name;
        elements.fileInfo.classList.remove('hidden');
        elements.uploadBtn.disabled = false;

        // Store file reference
        elements.excelFile.dataset.fileName = file.name;
    }

    function clearFile() {
        elements.excelFile.value = '';
        elements.fileInfo.classList.add('hidden');
        elements.uploadBtn.disabled = true;
    }

    async function handleBatchUpload() {
        const fileInput = elements.excelFile;
        const file = fileInput.files[0];

        if (!file) {
            showToast('请选择要上传的Excel文件', 'warning');
            return;
        }

        // Get selected batch mode
        const modeInput = document.querySelector('input[name="batch-mode"]:checked');
        const mode = modeInput ? modeInput.value : 'sync';

        setLoading(elements.uploadBtn, true);

        try {
            const formData = new FormData();
            formData.append('file', file);

            // Use different endpoint based on mode
            const endpoint = mode === 'async' ? '/api/v1/audit/upload-async' : '/api/v1/audit/upload';
            const response = await fetch(endpoint, {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            if (data.code !== '000000') {
                throw new Error(data.message || '上传失败');
            }

            if (mode === 'async') {
                // For async mode, show job ID and redirect to jobs list
                showToast(`任务已提交到队列，任务ID: ${data.data.jobId}`, 'success');
                setLoading(elements.uploadBtn, false);
                clearFile();

                // Switch to jobs tab after 1 second
                setTimeout(() => {
                    document.querySelector('[data-tab="jobs"]').click();
                }, 1000);
            } else {
                // For sync mode, start polling
                state.batchJobId = data.data.jobId;
                elements.batchProgress.classList.remove('hidden');
                startPolling();
                showToast('文件上传成功，任务已开始处理', 'success');
            }
        } catch (error) {
            showToast(error.message || '上传失败，请稍后重试', 'error');
            setLoading(elements.uploadBtn, false);
        }
    }

    function startPolling() {
        if (state.pollingInterval) {
            clearInterval(state.pollingInterval);
        }

        state.pollingInterval = setInterval(async () => {
            try {
                const response = await fetch(`/api/v1/audit/job/${state.batchJobId}`);
                const data = await response.json();

                if (data.code !== '000000') {
                    throw new Error(data.message || '查询失败');
                }

                updateProgress(data.data);

                // Check if completed
                if (data.data.status === 'COMPLETED' || data.data.status === 'FAILED') {
                    clearInterval(state.pollingInterval);
                    state.pollingInterval = null;
                    // 停止loading状态
                    elements.uploadBtn.disabled = false;
                    const uploadBtnText = elements.uploadBtn.querySelector('.btn-text');
                    const uploadBtnSpinner = elements.uploadBtn.querySelector('.loading-spinner');
                    if (uploadBtnText) uploadBtnText.classList.remove('hidden');
                    if (uploadBtnSpinner) uploadBtnSpinner.classList.add('hidden');

                    if (data.data.status === 'COMPLETED') {
                        elements.downloadBtn.classList.remove('hidden');
                        elements.jobStatus.textContent = '任务已完成';
                        elements.jobStatus.className = 'status-badge completed';
                    } else {
                        elements.jobStatus.textContent = '任务失败';
                        elements.jobStatus.className = 'status-badge failed';
                    }
                }
            } catch (error) {
                console.error('Polling error:', error);
            }
        }, 2000);
    }

    function updateProgress(job) {
        const percent = job.progressPercent || 0;
        elements.progressBar.style.width = percent + '%';
        elements.progressText.textContent = percent + '%';

        elements.statTotal.textContent = job.totalLinks || 0;
        elements.statCompleted.textContent = job.completedCount || 0;
        elements.statPassed.textContent = job.passedCount || 0;
        elements.statRejected.textContent = job.rejectedCount || 0;

        elements.jobStatus.textContent = getJobStatusText(job.status);
        elements.jobStatus.className = 'status-badge ' + (job.status || '').toLowerCase();
    }

    function handleExport() {
        // Download current history results
        const params = new URLSearchParams({
            page: state.historyPage,
            size: state.historySize,
            status: elements.filterStatus.value || '',
            url: elements.filterUrl.value || '',
            startDate: elements.filterStartDate.value || '',
            endDate: elements.filterEndDate.value || ''
        });

        window.open(`/api/v1/audit/results?${params.toString()}`, '_blank');
    }

    // Job List
    async function loadJobList() {
        const params = new URLSearchParams({
            page: state.jobPage,
            size: state.jobSize
        });

        if (elements.jobFilterId.value.trim()) {
            params.append('jobId', elements.jobFilterId.value.trim());
        }
        if (elements.jobFilterStatus.value) {
            params.append('status', elements.jobFilterStatus.value);
        }
        if (elements.jobFilterKeyword.value.trim()) {
            params.append('keyword', elements.jobFilterKeyword.value.trim());
        }
        if (elements.jobFilterStartDate.value) {
            params.append('startDate', elements.jobFilterStartDate.value);
        }
        if (elements.jobFilterEndDate.value) {
            params.append('endDate', elements.jobFilterEndDate.value);
        }

        try {
            const response = await fetch(`/api/v1/audit/jobs?${params.toString()}`);
            const data = await response.json();

            if (data.code !== '000000') {
                throw new Error(data.message || '查询失败');
            }

            displayJobList(data.data);
        } catch (error) {
            showToast(error.message || '查询失败，请稍后重试', 'error');
        }
    }

    function displayJobList(pageData) {
        const content = pageData.content || [];

        state.jobTotalPages = pageData.totalPages || 0;

        // Update pagination
        elements.jobPageInfo.textContent = `第 ${state.jobPage + 1} 页 / 共 ${Math.max(1, state.jobTotalPages)} 页`;
        elements.jobPrevPage.disabled = state.jobPage <= 0;
        elements.jobNextPage.disabled = state.jobPage >= state.jobTotalPages - 1;

        // Display table or empty state
        if (content.length === 0) {
            elements.jobTable.classList.add('hidden');
            elements.jobEmptyState.classList.remove('hidden');
        } else {
            elements.jobTable.classList.remove('hidden');
            elements.jobEmptyState.classList.add('hidden');

            const tbodyHtml = content.map(job => {
                // 判断任务是否完成（可以下载）
                const canDownload = ['COMPLETED', 'PARTIAL_SUCCESS', 'FAILED'].includes(job.status);
                const downloadBtnHtml = canDownload
                    ? `<button class="btn btn-sm btn-primary download-job-excel" data-job-id="${job.jobId}" style="margin-left: 8px;">
                            <span style="font-size: 14px;">&#8595;</span> 下载
                       </button>`
                    : `<button class="btn btn-sm btn-disabled" disabled style="margin-left: 8px; cursor: not-allowed; opacity: 0.5;" title="任务未完成，无法下载">
                            <span style="font-size: 14px;">&#8595;</span> 下载
                       </button>`;

                return `
                <tr data-job-id="${job.jobId}">
                    <td class="table-job-id" title="${job.jobId || ''}">${job.jobId || '-'}</td>
                    <td class="table-file-name" title="${escapeHtml(job.fileName || '')}">${escapeHtml(job.fileName || '-')}</td>
                    <td>${job.totalLinks || 0}</td>
                    <td>${job.completedCount || 0}</td>
                    <td>${job.passedCount || 0}</td>
                    <td>${job.rejectedCount || 0}</td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <div class="progress-bar-wrap" style="width: 100px; height: 8px;">
                                <div class="progress-bar" style="height: 100%;"></div>
                            </div>
                            <span style="font-size: 13px; color: #666; min-width: 45px;">${job.progressPercent || 0}%</span>
                        </div>
                    </td>
                    <td><span class="status-badge ${(job.status || '').toLowerCase()}">${getJobStatusText(job.status)}</span></td>
                    <td>${formatDateTime(job.createdAt)}</td>
                    <td>
                        <button class="btn btn-sm btn-outline view-job-detail" data-job-id="${job.jobId}">查看详情</button>
                        ${downloadBtnHtml}
                    </td>
                </tr>
                `;
            }).join('');

            elements.jobTbody.innerHTML = tbodyHtml;

            // Set progress bar widths
            elements.jobTbody.querySelectorAll('.progress-bar').forEach((bar, index) => {
                bar.style.width = (content[index].progressPercent || 0) + '%';
            });

            // Bind click events
            elements.jobTbody.querySelectorAll('.view-job-detail').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const jobId = btn.dataset.jobId;
                    openHistoryWithJobId(jobId);
                });
            });

            // Bind download click events
            elements.jobTbody.querySelectorAll('.download-job-excel').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const jobId = btn.dataset.jobId;
                    downloadJobExcel(jobId);
                });
            });
        }
    }

    // 跳转到历史记录页面并自动查询该任务的帖子
    async function openHistoryWithJobId(jobId) {
        // 切换到历史记录Tab
        switchTab('history');

        // 填充任务ID到筛选框
        elements.filterJobId.value = jobId;

        // 重置其他筛选条件
        elements.filterPostId.value = '';
        elements.filterStatus.value = '';

        // 重新加载历史记录
        state.historyPage = 0;
        await loadHistoryResults();
    }

    // 下载任务的Excel结果
    function downloadJobExcel(jobId) {
        showToast('正在准备下载...', 'info');

        // 创建一个隐藏的下载链接
        const downloadUrl = `/api/v1/audit/download/${jobId}`;
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = `audit_result_${jobId}.xlsx`;
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // 延迟显示成功提示，让下载有时间启动
        setTimeout(() => {
            showToast('下载已开始', 'success');
        }, 500);
    }

    async function openJobDetail(jobId) {
        state.currentJobId = jobId;

        try {
            const response = await fetch(`/api/v1/audit/jobs/${jobId}?page=0&size=20`);
            const data = await response.json();

            if (data.code !== '000000') {
                throw new Error(data.message || '查询失败');
            }

            showJobDetail(data.data);
        } catch (error) {
            showToast(error.message || '查询失败，请稍后重试', 'error');
        }
    }

    function showJobDetail(jobData) {
        state.currentJobDetail = jobData;

        // Build header stats
        let html = `
            <div class="job-detail-header">
                <div class="job-detail-stat">
                    <span class="job-detail-stat-num">${jobData.totalLinks || 0}</span>
                    <span class="job-detail-stat-label">总计</span>
                </div>
                <div class="job-detail-stat">
                    <span class="job-detail-stat-num">${jobData.completedCount || 0}</span>
                    <span class="job-detail-stat-label">已完成</span>
                </div>
                <div class="job-detail-stat" style="color: #07c160;">
                    <span class="job-detail-stat-num">${jobData.passedCount || 0}</span>
                    <span class="job-detail-stat-label">通过</span>
                </div>
                <div class="job-detail-stat" style="color: #fe2c55;">
                    <span class="job-detail-stat-num">${jobData.rejectedCount || 0}</span>
                    <span class="job-detail-stat-label">驳回</span>
                </div>
                <div class="job-detail-stat">
                    <span class="job-detail-stat-num">${jobData.progressPercent || 0}%</span>
                    <span class="job-detail-stat-label">进度</span>
                </div>
                <div class="job-detail-stat">
                    <span class="job-detail-stat-num">${getJobStatusText(jobData.status)}</span>
                    <span class="job-detail-stat-label">状态</span>
                </div>
            </div>
            <p style="font-size: 13px; color: #999; margin-bottom: 12px;">
                文件名: ${escapeHtml(jobData.fileName || '-')} |
                创建时间: ${formatDateTime(jobData.createdAt)} |
                任务ID: <span style="font-family: monospace;">${jobData.jobId}</span>
            </p>
        `;

        // Build results table
        const results = jobData.results || [];
        if (results.length > 0) {
            html += `
                <div class="job-detail-results">
                    <table>
                        <thead>
                            <tr>
                                <th>标题</th>
                                <th>状态</th>
                                <th>风险</th>
                                <th>置信度</th>
                                <th>审核时间</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${results.map(item => `
                                <tr data-item='${encodeURIComponent(JSON.stringify(item))}'>
                                    <td class="table-title" title="${escapeHtml(item.title || '')}">${escapeHtml(truncate(item.title || '-', 25))}</td>
                                    <td><span class="status-badge ${(item.status || '').toLowerCase()}">${getStatusText(item.status)}</span></td>
                                    <td><span class="risk-badge ${(item.riskLevel || 'low').toLowerCase()}">${getRiskText(item.riskLevel)}</span></td>
                                    <td>${item.confidenceScore ? (item.confidenceScore * 100).toFixed(1) + '%' : '-'}</td>
                                    <td>${formatDateTime(item.auditedTime)}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="job-detail-pagination">
                    <button class="btn btn-sm" onclick="loadMoreJobResults('${jobData.jobId}', 0)">首页</button>
                    <span style="font-size: 13px; color: #666;">
                        第 ${jobData.currentPage + 1} 页 / 共 ${Math.max(1, jobData.totalPages)} 页
                    </span>
                    <button class="btn btn-sm" onclick="loadMoreJobResults('${jobData.jobId}', ${jobData.totalPages - 1})">末页</button>
                </div>
            `;
        } else {
            html += '<div class="empty-state"><p>暂无审核结果</p></div>';
        }

        elements.jobDetailModalBody.innerHTML = html;
        elements.jobDetailModal.classList.remove('hidden');

        // Bind row click events
        elements.jobDetailModalBody.querySelectorAll('tbody tr').forEach(row => {
            row.addEventListener('click', () => {
                const item = JSON.parse(decodeURIComponent(row.dataset.item));
                showDetail(item);
            });
        });
    }

    window.loadMoreJobResults = function (jobId, page) {
        fetch(`/api/v1/audit/jobs/${jobId}?page=${page}&size=20`)
            .then(response => response.json())
            .then(data => {
                if (data.code === '000000') {
                    showJobDetail(data.data);
                }
            })
            .catch(error => {
                showToast('加载失败，请稍后重试', 'error');
            });
    };

    function hideJobDetail() {
        elements.jobDetailModal.classList.add('hidden');
        state.currentJobDetail = null;
        state.currentJobId = null;
    }

    // History Results
    async function loadHistoryResults() {
        const params = new URLSearchParams({
            page: state.historyPage,
            size: state.historySize
        });

        if (elements.filterPostId.value.trim()) {
            params.append('postId', elements.filterPostId.value.trim());
        }
        if (elements.filterJobId.value.trim()) {
            params.append('jobId', elements.filterJobId.value.trim());
        }
        if (elements.filterStatus.value) {
            params.append('status', elements.filterStatus.value);
        }
        if (elements.filterStartDate.value) {
            params.append('startDate', elements.filterStartDate.value);
        }
        if (elements.filterEndDate.value) {
            params.append('endDate', elements.filterEndDate.value);
        }

        try {
            const response = await fetch(`/api/v1/audit/results?${params.toString()}`);
            const data = await response.json();

            if (data.code !== '000000') {
                throw new Error(data.message || '查询失败');
            }

            displayHistoryResults(data.data);
        } catch (error) {
            showToast(error.message || '查询失败，请稍后重试', 'error');
        }
    }

    function displayHistoryResults(pageData) {
        const content = pageData.content || [];

        state.historyTotalPages = pageData.totalPages || 0;

        // Update pagination
        elements.pageInfo.textContent = `第 ${state.historyPage + 1} 页 / 共 ${Math.max(1, state.historyTotalPages)} 页`;
        elements.prevPage.disabled = state.historyPage <= 0;
        elements.nextPage.disabled = state.historyPage >= state.historyTotalPages - 1;

        // Display table or empty state
        if (content.length === 0) {
            elements.historyTable.classList.add('hidden');
            elements.emptyState.classList.remove('hidden');
        } else {
            elements.historyTable.classList.remove('hidden');
            elements.emptyState.classList.add('hidden');

            const tbodyHtml = content.map(item => `
                <tr data-post-id="${item.post_id || item.postId}">
                    <td class="table-post-id" title="${item.post_id || item.postId || ''}">${item.post_id || item.postId || '-'}</td>
                    <td class="table-job-id" title="${item.job_id || item.jobId || ''}">${item.job_id || item.jobId || '-'}</td>
                    <td><span class="status-badge ${(item.status || '').toLowerCase()}">${getStatusText(item.status)}</span></td>
                    <td><span class="risk-badge ${(item.risk_level || item.riskLevel || 'low').toLowerCase()}">${getRiskText(item.risk_level || item.riskLevel)}</span></td>
                    <td>${item.confidence_score || item.confidenceScore ? ((item.confidence_score || item.confidenceScore) * 100).toFixed(1) + '%' : '-'}</td>
                    <td>${formatDateTime(item.audited_time || item.auditedTime)}</td>
                </tr>
            `).join('');

            elements.historyTbody.innerHTML = tbodyHtml;

            // Bind click events
            elements.historyTbody.querySelectorAll('tr').forEach(row => {
                row.addEventListener('click', () => {
                    const postId = row.dataset.postId;
                    openHistoryDetail(postId);
                });
            });
        }
    }

    async function openHistoryDetail(postId) {
        try {
            const response = await fetch(`/api/v1/audit/results/${postId}/detail`);
            const data = await response.json();

            if (data.code !== '000000') {
                throw new Error(data.message || '查询失败');
            }

            showDetail(data.data);
        } catch (error) {
            showToast(error.message || '查询失败，请稍后重试', 'error');
        }
    }

    function showDetail(item) {
        state.currentDetail = item;
        const detailModal = document.getElementById('detail-modal');
        const detailModalBody = document.getElementById('detail-modal-body');

        // Build detail content
        let html = `
            <div class="detail-row">
                <span class="detail-label">帖子ID</span>
                <span class="detail-value">${item.post_id || item.postId || '-'}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">任务ID</span>
                <span class="detail-value">${item.job_id || item.jobId || '-'}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">链接</span>
                <span class="detail-value">
                    <a href="${item.url || '#'}" target="_blank" class="detail-url">${item.url || '-'}</a>
                </span>
            </div>
            <div class="detail-row">
                <span class="detail-label">标题</span>
                <span class="detail-value">${escapeHtml(item.title || '-')}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">内容</span>
                <span class="detail-value">${escapeHtml(item.content || '-')}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">标签</span>
                <span class="detail-value detail-tags">
                    ${(item.tags || []).map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join('') || '-'}
                </span>
            </div>
            <div class="detail-row">
                <span class="detail-label">图片</span>
                <span class="detail-value detail-images">
                    ${(item.images || []).map(img => `<img src="${img}" alt="图片" onclick="showImagePreview('${img}')">`).join('') || '-'}
                </span>
            </div>
            <div class="detail-row">
                <span class="detail-label">作者ID</span>
                <span class="detail-value">${item.author_id || item.authorId || '-'}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">发布时间</span>
                <span class="detail-value">${formatDateTime(item.published_at || item.publishedAt)}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">抓取时间</span>
                <span class="detail-value">${formatDateTime(item.crawled_at || item.crawledAt)}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">审核状态</span>
                <span class="detail-value"><span class="status-badge ${(item.status || '').toLowerCase()}">${getStatusText(item.status)}</span></span>
            </div>
            <div class="detail-row">
                <span class="detail-label">风险等级</span>
                <span class="detail-value"><span class="risk-badge ${(item.risk_level || item.riskLevel || 'low').toLowerCase()}">${getRiskText(item.risk_level || item.riskLevel)}</span></span>
            </div>
            <div class="detail-row">
                <span class="detail-label">置信度</span>
                <span class="detail-value">${item.confidence_score || item.confidenceScore ? ((item.confidence_score || item.confidenceScore) * 100).toFixed(1) + '%' : '-'}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">审核时间</span>
                <span class="detail-value">${formatDateTime(item.audited_time || item.auditedTime)}</span>
            </div>
        `;

        if (item.reasons && item.reasons.length > 0) {
            html += `
                <div class="detail-row" style="flex-direction: column; align-items: flex-start;">
                    <span class="detail-label" style="margin-bottom: 10px;">驳回原因</span>
                    <span class="detail-value">
                        ${item.reasons.map(r => `
                            <div class="reason-item" style="margin-bottom: 10px; padding: 10px; background: #f8fafc; border-radius: 4px;">
                                <div class="reason-dimension">${getDimensionText(r.dimension)}</div>
                                <div class="reason-text">${r.reason}</div>
                                <div class="reason-severity">严重程度: ${getSeverityText(r.severity)}</div>
                            </div>
                        `).join('')}
                    </span>
                </div>
            `;
        }

        detailModalBody.innerHTML = html;
        detailModal.classList.remove('hidden');
    }

    function hideDetail() {
        document.getElementById('detail-modal').classList.add('hidden');
        state.currentDetail = null;
    }

    // Image Preview
    window.showImagePreview = function (imageUrl) {
        elements.modalImage.src = imageUrl;
        elements.imageModal.classList.remove('hidden');
    };

    function hideModal() {
        elements.imageModal.classList.add('hidden');
    }

    // Utility Functions
    function setLoading(button, loading) {
        const btnText = button.querySelector('.btn-text');
        const spinner = button.querySelector('.loading-spinner');

        if (loading) {
            button.disabled = true;
            if (btnText) btnText.classList.add('hidden');
            if (spinner) spinner.classList.remove('hidden');
        } else {
            button.disabled = false;
            if (btnText) btnText.classList.remove('hidden');
            if (spinner) spinner.classList.add('hidden');
        }
    }

    function showToast(message, type = 'info') {
        elements.toast.textContent = message;
        elements.toast.className = 'toast ' + type;
        elements.toast.classList.remove('hidden');

        setTimeout(() => {
            elements.toast.classList.add('hidden');
        }, 3000);
    }

    function validateUrl(url) {
        // Support: xiaohongshu.com explore/discovery links, xhslink.com short links
        return url.includes('xiaohongshu.com') ||
            url.includes('xhslink.com') ||
            url.includes('xhs.com');
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function truncate(text, maxLength) {
        if (!text) return '';
        return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    }

    function formatDateTime(dateTime) {
        if (!dateTime) return '-';
        const date = new Date(dateTime);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function getStatusText(status) {
        const statusMap = {
            'PASSED': '通过',
            'REJECTED': '驳回',
            'UNCERTAIN': '待审核'
        };
        return statusMap[status] || status || '-';
    }

    function getRiskText(risk) {
        const riskMap = {
            'LOW': '低风险',
            'MEDIUM': '中风险',
            'HIGH': '高风险',
            'CRITICAL': '极高风险'
        };
        return riskMap[risk] || risk || '-';
    }

    function getDimensionText(dimension) {
        const dimensionMap = {
            'title': '标题',
            'content': '内容',
            'tag': '标签',
            'image': '图片'
        };
        return dimensionMap[dimension] || dimension || '-';
    }

    function getSeverityText(severity) {
        const severityMap = {
            'LOW': '低',
            'MEDIUM': '中',
            'HIGH': '高',
            'CRITICAL': '极高'
        };
        return severityMap[severity] || severity || '-';
    }

    function getJobStatusText(status) {
        const statusMap = {
            'PENDING': '等待中',
            'PROCESSING': '处理中',
            'COMPLETED': '已完成',
            'FAILED': '失败'
        };
        return statusMap[status] || status || '-';
    }

    function setDefaultDates() {
        const today = new Date();
        const thirtyDaysAgo = new Date(today);
        thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

        elements.filterEndDate.value = today.toISOString().split('T')[0];
        elements.filterStartDate.value = thirtyDaysAgo.toISOString().split('T')[0];
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
