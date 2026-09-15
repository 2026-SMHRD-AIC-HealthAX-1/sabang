/* Completed withdrawal results only; eventId must identify one withdrawal line. */
(() => {
    const prefix = 'framVision.anomaly.v1.';
    const listeners = new Set();
    function list() {
        const rows = [];
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (!key.startsWith(prefix)) continue;
            try {
                const row = JSON.parse(localStorage.getItem(key));
                if (row && typeof row.eventId === 'string' && typeof row.drugName === 'string' &&
                    Number.isSafeInteger(row.expectedQuantity) && Number.isSafeInteger(row.detectedQuantity)) rows.push(row);
            } catch (_) { /* Ignore unrelated/corrupt records. */ }
        }
        return rows.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    }
    const notify = () => listeners.forEach(fn => fn(list()));
    function record(result) {
        if (!result || result.finalized !== true) return null;
        for (const field of ['eventId', 'receiptId', 'drugId', 'drugName']) {
            if (typeof result[field] !== 'string' || !result[field].trim()) throw new Error(field + ' is required');
        }
        for (const field of ['expectedQuantity', 'detectedQuantity']) {
            if (!Number.isSafeInteger(result[field]) || result[field] < 0) throw new Error(field + ' must be a nonnegative integer');
        }
        if (result.expectedQuantity === result.detectedQuantity) return null;
        const key = prefix + encodeURIComponent(result.eventId);
        const existing = localStorage.getItem(key);
        if (existing) return JSON.parse(existing);
        const row = {
            eventId: result.eventId, receiptId: result.receiptId, drugId: result.drugId,
            drugName: result.drugName, expectedQuantity: result.expectedQuantity,
            detectedQuantity: result.detectedQuantity, createdAt: new Date().toISOString(),
            dismissed: false, acknowledged: false
        };
        localStorage.setItem(key, JSON.stringify(row));
        notify();
        return row;
    }
    function update(id, field) {
        const key = prefix + encodeURIComponent(id);
        const row = JSON.parse(localStorage.getItem(key) || 'null');
        if (!row) return;
        row[field] = true;
        localStorage.setItem(key, JSON.stringify(row));
        notify();
    }
    window.FramAnomalies = {
        record, list, dismiss: id => update(id, 'dismissed'),
        acknowledge: id => update(id, 'acknowledged'),
        subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }
    };
    window.addEventListener('storage', e => { if (e.key === null || e.key.startsWith(prefix)) notify(); });
    window.addEventListener('framvision:withdrawal-completed', e => {
        try { record(e.detail); }
        catch (error) { window.dispatchEvent(new CustomEvent('framvision:anomaly-error', {detail: error})); }
    });
})();
