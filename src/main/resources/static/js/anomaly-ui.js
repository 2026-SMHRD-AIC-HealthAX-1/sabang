(() => {
    const store = window.FramAnomalies;
    const page = document.body.dataset.page;
    const main = document.querySelector('main');
    const el = (tag, text, className) => {
        const node = document.createElement(tag);
        if (text) node.textContent = text;
        if (className) node.className = className;
        return node;
    };
    let banner;
    if (page === 'dashboard') {
        banner = el('section', '', 'anomaly-banner');
        banner.setAttribute('aria-live', 'assertive');
        banner.setAttribute('aria-atomic', 'true');
        main.prepend(banner);
    }
    const body = document.getElementById('anomaly-rows');
    const inventoryStore = window.FramInventoryAlerts;
    const inventoryBody = document.getElementById('inventory-alert-rows');
    const description = row => `전표 ${row.expectedQuantity}개 · 객체탐지 ${row.detectedQuantity}개 · ${Math.abs(row.expectedQuantity - row.detectedQuantity)}개 ${row.expectedQuantity > row.detectedQuantity ? '부족' : '초과'}`;
    function render(rows) {
        if (banner) {
            banner.replaceChildren();
            const pending = rows.filter(row => !row.dismissed && !row.acknowledged);
            banner.hidden = !pending.length;
            if (pending.length) {
                const row = pending[0];
                const copy = el('div', '', 'anomaly-copy');
                copy.append(el('strong', `⚠ 이상 알림 · ${row.drugName} 수량 불일치`), el('p', description(row)), el('small', `전표 ${row.receiptId} · ${new Date(row.createdAt).toLocaleString('ko-KR')}`));
                const link = el('a', `이상 알림 ${pending.length}건 보기`);
                link.href = 'alerts.html';
                const close = el('button', '닫기');
                close.type = 'button';
                close.setAttribute('aria-label', '현재 이상 알림 닫기 (내역 유지)');
                close.onclick = () => store.dismiss(row.eventId);
                banner.append(copy, link, close);
            }
        }
        if (body) {
            body.replaceChildren();
            document.getElementById('anomaly-count').textContent = `누적 ${rows.length}건`;
            if (!rows.length) {
                const cell = el('td', t('noMismatchMessage'));
                cell.colSpan = 4;
                cell.className = 'alert-empty';
                const tr = el('tr'); tr.append(cell); body.append(tr);
            }
            rows.forEach(row => {
                const tr = el('tr');
                tr.append(el('td', new Date(row.createdAt).toLocaleString('ko-KR')), el('td', row.drugName), el('td', `${description(row)} / 전표 ${row.receiptId}`));
                const status = el('td');
                if (row.acknowledged) status.textContent = '확인 완료';
                else {
                    const button = el('button', '확인 처리');
                    button.type = 'button';
                    button.onclick = () => store.acknowledge(row.eventId);
                    status.append(button);
                }
                tr.append(status); body.append(tr);
            });
        }
    }
    store.subscribe(render);
    render(store.list());

    function renderInventory(rows) {
        if (!inventoryBody) return;
        inventoryBody.replaceChildren();
        document.getElementById('inventory-alert-count').textContent = `누적 ${rows.length}건`;
        if (!rows.length) {
            const cell = el('td', t('noInventoryAlertMessage'));
            cell.colSpan = 4;
            cell.className = 'alert-empty';
            const tr = el('tr'); tr.append(cell); inventoryBody.append(tr);
        }
        rows.forEach(row => {
            const tr = el('tr');
            const location = row.cameraName ? ` · ${row.cameraName}` : '';
            tr.append(
                el('td', new Date(row.createdAt).toLocaleString('ko-KR')),
                el('td', row.drugName),
                el('td', `현재 ${row.currentQuantity}개 / 최소 ${row.minimumQuantity}개${location}`)
            );
            const status = el('td');
            if (row.acknowledged) status.textContent = '확인 완료';
            else {
                const button = el('button', '확인 처리');
                button.type = 'button';
                button.onclick = () => inventoryStore.acknowledge(row.eventId);
                status.append(button);
            }
            tr.append(status); inventoryBody.append(tr);
        });
    }
    if (inventoryStore) {
        inventoryStore.subscribe(renderInventory);
        renderInventory(inventoryStore.list());
    }
})();
