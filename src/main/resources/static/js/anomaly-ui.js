/*
    이상 알림 화면 (이상 알림 페이지 목록 + 대시보드 상단 배너)

    알림은 서버 DB(ALERT)에서 /api/alerts 로 읽고,
    관리자의 "확인 처리"도 서버에 저장한다. (관리자 전용 API)
    - 한 건: 행의 "확인 처리" 버튼 (바로 처리)
    - 여러 건: 행의 체크박스로 고른 뒤 "선택 항목 처리" 버튼 (확인창 후 일괄 처리)
    대시보드 배너의 "닫기"는 이 브라우저에서 배너만 숨기는 것이라 내역/처리 상태는 그대로다.
*/
(() => {
    const page = document.body.dataset.page;
    const main = document.querySelector('main');
    const el = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text) node.textContent = text;
        if (className) node.className = className;
        return node;
    };

    const STATUS_PENDING = 'PENDING';
    const TYPE_LOW_STOCK = 'LOW_STOCK';
    const dismissedKey = 'framVision.dismissedAlerts';

    const loadDismissed = () => {
        try { return new Set(JSON.parse(localStorage.getItem(dismissedKey) || '[]')); }
        catch (_) { return new Set(); }
    };
    const saveDismissed = ids => {
        try { localStorage.setItem(dismissedKey, JSON.stringify([...ids])); }
        catch (_) { /* 저장소를 못 쓰면 배너만 다시 뜰 뿐이다 */ }
    };

    let banner;
    if (page === 'dashboard') {
        banner = el('section', '', 'anomaly-banner');
        banner.setAttribute('aria-live', 'assertive');
        banner.setAttribute('aria-atomic', 'true');
        banner.hidden = true;
        main.prepend(banner);
    }

    // 이상 알림 페이지의 두 목록(카드). 카드마다 체크박스 선택 상태를 따로 가진다.
    const cards = [
        {
            bodyId: 'anomaly-rows', countId: 'anomaly-count',
            selectAllId: 'anomaly-select-all', bulkBtnId: 'anomaly-bulk-btn',
            emptyKey: 'noMismatchMessage',
            match: alert => alert.alertType !== TYPE_LOW_STOCK,
            selected: new Set()
        },
        {
            bodyId: 'inventory-alert-rows', countId: 'inventory-alert-count',
            selectAllId: 'inventory-select-all', bulkBtnId: 'inventory-bulk-btn',
            emptyKey: 'noInventoryAlertMessage',
            match: alert => alert.alertType === TYPE_LOW_STOCK,
            selected: new Set()
        }
    ];

    let alerts = [];

    const formatTime = value => new Date(value).toLocaleString('ko-KR');

    async function load() {
        try {
            const response = await fetch('/api/alerts');
            alerts = response.ok ? await response.json() : [];
        } catch (_) {
            alerts = [];
        }
        render();
    }

    // 처리 후에는 목록과 헤더 종 숫자를 서버 기준으로 다시 그린다
    async function reloadAfterProcess() {
        cards.forEach(card => card.selected.clear());
        await load();
        if (typeof updateNotificationCount === 'function') updateNotificationCount();
    }

    // 한 건 확인 처리 (확인창 없이 바로)
    async function process(alertId) {
        const response = await fetch(`/api/alerts/${alertId}/process`, { method: 'PUT' });
        if (!response.ok) return;
        await reloadAfterProcess();
    }

    // 여러 건 일괄 확인 처리 (확인창 후)
    async function processSelected(card) {
        const ids = [...card.selected];
        if (!ids.length) return;
        if (!window.confirm(t('confirmBatchProcess').replace('{n}', ids.length))) return;
        const response = await fetch('/api/alerts/process-batch', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ alertIds: ids })
        });
        if (!response.ok) {
            window.alert(t('batchProcessFailed'));
            await reloadAfterProcess();
            return;
        }
        await reloadAfterProcess();
    }

    function renderBanner() {
        if (!banner) return;
        banner.replaceChildren();
        const dismissed = loadDismissed();
        const pending = alerts.filter(a => a.processStatus === STATUS_PENDING && !dismissed.has(a.alertId));
        banner.hidden = !pending.length;
        if (!pending.length) return;
        const row = pending[0];
        const kind = row.alertType === TYPE_LOW_STOCK ? '재고 부족' : '수량 불일치';
        const copy = el('div', '', 'anomaly-copy');
        copy.append(el('strong', `⚠ 이상 알림 · ${row.medicineName} ${kind}`), el('p', row.alertContent), el('small', formatTime(row.alertTime)));
        const link = el('a', `이상 알림 ${pending.length}건 보기`);
        link.href = 'alerts.html';
        const close = el('button', '닫기');
        close.type = 'button';
        close.setAttribute('aria-label', '현재 이상 알림 닫기 (내역 유지)');
        close.onclick = () => {
            dismissed.add(row.alertId);
            saveDismissed(dismissed);
            renderBanner();
        };
        banner.append(copy, link, close);
    }

    // 선택 건수에 맞춰 "선택 항목 처리 (N건)" 버튼과 전체 선택 체크박스 상태를 갱신
    function updateBulkUi(card, pendingCount) {
        const count = card.selected.size;
        const button = document.getElementById(card.bulkBtnId);
        const selectAll = document.getElementById(card.selectAllId);
        button.textContent = `${t('processSelected')} (${count}${t('countUnit')})`;
        button.disabled = count === 0;
        selectAll.setAttribute('aria-label', t('selectAllLabel'));
        selectAll.disabled = pendingCount === 0;
        selectAll.checked = pendingCount > 0 && count === pendingCount;
        selectAll.indeterminate = count > 0 && count < pendingCount;
    }

    function renderCard(card) {
        const body = document.getElementById(card.bodyId);
        if (!body) return;

        const rows = alerts.filter(card.match);
        const pendingRows = rows.filter(row => row.processStatus === STATUS_PENDING);

        // 이미 처리됐거나 사라진 알림은 선택에서 뺀다
        const pendingIds = new Set(pendingRows.map(row => row.alertId));
        [...card.selected].forEach(id => { if (!pendingIds.has(id)) card.selected.delete(id); });

        body.replaceChildren();
        document.getElementById(card.countId).textContent = `누적 ${rows.length}건`;

        if (!rows.length) {
            const cell = el('td', t(card.emptyKey));
            cell.colSpan = 5;
            cell.className = 'alert-empty';
            const tr = el('tr'); tr.append(cell); body.append(tr);
        }

        rows.forEach(row => {
            const tr = el('tr');

            const checkCell = el('td', '', 'check-col');
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.setAttribute('aria-label', t('selectAllLabel'));
            if (row.processStatus === STATUS_PENDING) {
                checkbox.checked = card.selected.has(row.alertId);
                checkbox.onchange = () => {
                    if (checkbox.checked) card.selected.add(row.alertId);
                    else card.selected.delete(row.alertId);
                    updateBulkUi(card, pendingRows.length);
                };
            } else {
                checkbox.disabled = true;   // 이미 처리된 알림은 선택할 수 없다
            }
            checkCell.append(checkbox);

            tr.append(checkCell, el('td', formatTime(row.alertTime)), el('td', row.medicineName), el('td', row.alertContent));

            const status = el('td');
            if (row.processStatus === STATUS_PENDING) {
                const button = el('button', '확인 처리');
                button.type = 'button';
                button.onclick = () => process(row.alertId);
                status.append(button);
            } else {
                status.textContent = '확인 완료';
            }
            tr.append(status); body.append(tr);
        });

        updateBulkUi(card, pendingRows.length);
    }

    function render() {
        renderBanner();
        cards.forEach(renderCard);
    }

    // 전체 선택 체크박스 / 일괄 처리 버튼 연결 (이상 알림 페이지에만 있는 요소)
    cards.forEach(card => {
        const selectAll = document.getElementById(card.selectAllId);
        const button = document.getElementById(card.bulkBtnId);
        if (!selectAll || !button) return;

        selectAll.onchange = () => {
            const pendingIds = alerts
                .filter(row => card.match(row) && row.processStatus === STATUS_PENDING)
                .map(row => row.alertId);
            card.selected.clear();
            if (selectAll.checked) pendingIds.forEach(id => card.selected.add(id));
            renderCard(card);
        };
        button.onclick = () => processSelected(card);
    });

    load();
})();
