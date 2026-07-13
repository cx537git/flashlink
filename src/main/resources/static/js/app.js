// ========== 常量 ==========
const STORAGE_KEY = 'flashlink_history';
const HISTORY_MAX = 50;

// ========== DOM 引用 ==========
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ========== 过期时间选择 ==========
let selectedExpire = '1h';

document.addEventListener('DOMContentLoaded', () => {
    renderHistory();
    initExpireButtons();
});

function initExpireButtons() {
    $$('.expire-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            $$('.expire-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            selectedExpire = btn.dataset.value;

            const customDate = $('#customExpire');
            if (selectedExpire === 'custom') {
                customDate.classList.remove('hidden');
                customDate.min = toLocalDatetimeStr(new Date()).slice(0, 16);
                customDate.focus();
            } else {
                customDate.classList.add('hidden');
            }
        });
    });

    // 自定义时间：输入时校验不得早于当前时间
    $('#customExpire').addEventListener('change', function () {
        if (!this.value) return;
        const selected = new Date(this.value);
        if (selected.getTime() <= Date.now()) {
            showToast('过期时间不得早于当前时间', 'error');
            this.value = '';
        }
    });
}

// ========== 计算过期时间 ==========
// 生成本地时间格式 YYYY-MM-DDTHH:mm:ss（不带 Z，避免时区偏移）
function toLocalDatetimeStr(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return date.getFullYear() + '-' +
        pad(date.getMonth() + 1) + '-' +
        pad(date.getDate()) + 'T' +
        pad(date.getHours()) + ':' +
        pad(date.getMinutes()) + ':' +
        pad(date.getSeconds());
}

function calcExpireTime() {
    // null = 永不过期（合法值，后端收 null 后不校验 @Future）
    if (selectedExpire === 'never' || selectedExpire === '') {
        return null;
    }

    // undefined = 校验失败，调用方应终止流程
    if (selectedExpire === 'custom') {
        const val = $('#customExpire').value;
        if (!val) {
            showToast('请选择自定义过期时间', 'error');
            return undefined;
        }
        const selected = new Date(val);
        if (selected.getTime() <= Date.now()) {
            showToast('过期时间不得早于当前时间', 'error');
            return undefined;
        }
        return val + ':00';
    }

    const now = new Date();
    const map = {
        '1h': 60 * 60 * 1000,
        '1d': 24 * 60 * 60 * 1000,
        '7d': 7 * 24 * 60 * 60 * 1000,
        '30d': 30 * 24 * 60 * 60 * 1000,
    };
    now.setTime(now.getTime() + (map[selectedExpire] || 0));
    return toLocalDatetimeStr(now);
}

// ========== 生成短链 ==========
async function generateShortLink() {
    const url = $('#originalUrl').value.trim();

    // 校验
    if (!url) {
        showToast('请输入需要缩短的链接', 'error');
        $('#originalUrl').focus();
        return;
    }

    if (!isValidUrl(url)) {
        showToast('请输入有效的 URL（以 http:// 或 https:// 开头）', 'error');
        $('#originalUrl').focus();
        return;
    }

    const expireTime = calcExpireTime();
    if (expireTime === undefined) return; // 自定义时间校验失败

    // UI 状态
    setGenerateLoading(true);
    hideResult();

    try {
        const body = { originalUrl: url };
        if (expireTime !== null) body.expireTime = expireTime;

        const resp = await fetch('/api/short-link', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });

        const json = await resp.json();

        if (json.code === 200 && json.data) {
            showResult(json.data, url);
            const expireAt = expireTime ? new Date(expireTime).getTime() : null;
            saveHistory(json.data, url, expireAt);
            showToast('短链生成成功', 'success');
        } else {
            const msg = getErrorMessage(json.code, json.message);
            showToast(msg, 'error');
        }
    } catch (err) {
        showToast('网络异常，请检查后端服务是否启动', 'error');
    } finally {
        setGenerateLoading(false);
    }
}

// ========== 结果展示 ==========
function showResult(shortCode, originalUrl) {
    const result = $('#result');
    result.classList.remove('hidden');
    $('#resultCode').textContent = shortCode;

    const fullUrl = window.location.origin + '/s/' + shortCode;
    $('#resultFull').textContent = fullUrl;
}

function hideResult() {
    $('#result').classList.add('hidden');
}

function setGenerateLoading(loading) {
    const btn = $('#generateBtn');
    const text = btn.querySelector('.btn-text');
    const loadingEl = btn.querySelector('.btn-loading');
    btn.disabled = loading;
    if (loading) {
        text.classList.add('hidden');
        loadingEl.classList.remove('hidden');
    } else {
        text.classList.remove('hidden');
        loadingEl.classList.add('hidden');
    }
}

function setJumpLoading(loading) {
    const btn = $('#jumpBtn');
    const text = btn.querySelector('.btn-text');
    const loadingEl = btn.querySelector('.btn-loading');
    btn.disabled = loading;
    if (loading) {
        text.classList.add('hidden');
        loadingEl.classList.remove('hidden');
    } else {
        text.classList.remove('hidden');
        loadingEl.classList.add('hidden');
    }
}

// ========== 短链跳转 ==========
async function jumpToShortLink() {
    const input = $('#shortCodeInput').value.trim();
    if (!input) {
        showToast('请输入短码或短链', 'error');
        $('#shortCodeInput').focus();
        return;
    }

    const shortCode = extractShortCode(input);
    if (!shortCode) {
        showToast('无法识别短码，请输入有效的短码或短链', 'error');
        return;
    }

    const url = '/s/' + shortCode;
    setJumpLoading(true);

    try {
        // redirect: 'manual' 阻止自动跟随，手动判断后端意图
        const resp = await fetch(url, { redirect: 'manual' });

        // opaqueredirect = 后端返回了302重定向，短链有效
        if (resp.type === 'opaqueredirect') {
            window.open(url, '_blank');
            return;
        }

        // 非重定向 = 后端返回了错误 JSON
        const json = await resp.json().catch(() => null);
        if (json) {
            showToast(getErrorMessage(json.code, json.message), 'error');
        } else {
            showToast('短链访问异常（' + resp.status + '）', 'error');
        }
    } catch {
        // fetch 因跨域等原因失败，直接尝试跳转
        window.open(url, '_blank');
    } finally {
        setJumpLoading(false);
    }
}

function extractShortCode(input) {
    // 去除首尾空格和斜杠
    let s = input.trim().replace(/\/+$/, '');

    // 如果是完整 URL，提取最后的路径段
    if (s.includes('://') || s.includes('/s/')) {
        const parts = s.split('/s/');
        if (parts.length === 2 && parts[1]) {
            return parts[1].split('/')[0].split('?')[0];
        }
        // 也支持直接输入完整路径如 example.com/s/xxx
        const urlParts = s.split('/');
        const last = urlParts[urlParts.length - 1];
        if (last && /^[a-zA-Z0-9_-]+$/.test(last)) {
            return last;
        }
        return null;
    }

    // 纯短码，只包含字母数字
    if (/^[a-zA-Z0-9_-]+$/.test(s) && s.length > 0 && s.length <= 50) {
        return s;
    }

    return null;
}

// ========== 复制 ==========
async function copyToClipboard(code) {
    const fullUrl = window.location.origin + '/s/' + code;
    try {
        await navigator.clipboard.writeText(fullUrl);
    } catch {
        const ta = document.createElement('textarea');
        ta.value = fullUrl;
        ta.style.cssText = 'position:fixed;left:-9999px';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
    }
    showToast('已复制到剪贴板', 'success');
}

async function copyResult() {
    await copyToClipboard($('#resultCode').textContent);
}

async function copyHistoryCode(code) {
    await copyToClipboard(code);
}

// ========== 历史记录 ==========
function getHistory() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
    } catch {
        return [];
    }
}

function saveHistory(shortCode, originalUrl, expireAt) {
    const history = getHistory();
    history.unshift({
        shortCode,
        originalUrl,
        expireAt,
        createdAt: Date.now(),
    });
    if (history.length > HISTORY_MAX) {
        history.length = HISTORY_MAX;
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    renderHistory();
}

function deleteHistory(index) {
    const history = getHistory();
    history.splice(index, 1);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    renderHistory();
}

function clearHistory() {
    localStorage.removeItem(STORAGE_KEY);
    renderHistory();
    showToast('历史已清空', 'info');
}

function renderHistory() {
    const history = getHistory();
    const emptyEl = $('#historyEmpty');
    const tableWrap = $('#historyTableWrap');
    const tbody = $('#historyBody');

    if (history.length === 0) {
        emptyEl.classList.remove('hidden');
        tableWrap.classList.add('hidden');
        return;
    }

    emptyEl.classList.add('hidden');
    tableWrap.classList.remove('hidden');

    tbody.innerHTML = history.map((item, i) => `
        <tr>
            <td>
                <span class="history-code" onclick="copyHistoryCode('${escapeHtml(item.shortCode)}')" title="点击复制完整短链">${escapeHtml(item.shortCode)}</span>
            </td>
            <td>
                <span class="history-url" title="${escapeHtml(item.originalUrl)}">${escapeHtml(item.originalUrl)}</span>
            </td>
            <td>
                <span class="history-expire">${item.expireAt ? formatTime(item.expireAt) : '永不过期'}</span>
            </td>
            <td>
                <span class="history-time">${formatTime(item.createdAt)}</span>
            </td>
            <td class="history-actions">
                <button class="history-delete" onclick="deleteHistory(${i})" title="删除">✕</button>
            </td>
        </tr>
    `).join('');
}

// ========== Toast 提示 ==========
function showToast(message, type = 'info') {
    const container = $('#toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 3000);
}

// ========== 工具函数 ==========
function isValidUrl(str) {
    try {
        const url = new URL(str);
        return url.protocol === 'http:' || url.protocol === 'https:';
    } catch {
        return false;
    }
}

// 后端 GlobalExceptionHandler 和限流拦截器均返回了 message 字段，
// 此处 switch 仅作为兜底防御，正常流程不会进入。
function getErrorMessage(code, message) {
    if (message) {
        if (code === 500) return '系统异常，请稍后重试';
        return message;
    }
    switch (code) {
        case 400: return '参数错误，请检查输入';
        case 404: return '短链不存在';
        case 410: return '短链已过期';
        case 429: return '请求过于频繁，请稍后重试';
        case 500: return '系统异常，请稍后重试';
        default: return '请求失败（' + code + '）';
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function formatTime(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    const pad = (n) => String(n).padStart(2, '0');
    return d.getFullYear() + '-' +
        pad(d.getMonth() + 1) + '-' +
        pad(d.getDate()) + ' ' +
        pad(d.getHours()) + ':' +
        pad(d.getMinutes());
}

// 回车提交
document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        const active = document.activeElement;
        if (active && active.id === 'originalUrl') {
            generateShortLink();
        } else if (active && active.id === 'shortCodeInput') {
            jumpToShortLink();
        }
    }
});
