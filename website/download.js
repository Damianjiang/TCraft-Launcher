(function () {
    const API = 'https://api.github.com/repos/Damianjiang/TCraft-Launcher/releases/latest';
    const RELEASES = 'https://github.com/Damianjiang/TCraft-Launcher/releases';

    // 能代理 GitHub API 与产物的加速节点
    const NODES = [
        { name: 'gh-proxy.com', base: 'https://gh-proxy.com/' },
        { name: 'gh-proxy.org', base: 'https://gh-proxy.org/' },
        { name: 'ghfast.top', base: 'https://ghfast.top/' },
        { name: 'ghproxy.net', base: 'https://ghproxy.net/' },
        { name: 'ghproxy.imciel.com', base: 'https://ghproxy.imciel.com/' },
        { name: 'GitHub 直连', base: '' }
    ];

    const modal = document.getElementById('dlModal');
    if (!modal) return;
    const step1 = document.getElementById('dlStep1');
    const step2 = document.getElementById('dlStep2');
    const step3 = document.getElementById('dlStep3');
    const nodesBox = document.getElementById('dlNodes');
    const title = document.getElementById('dlTitle');
    const meta = document.getElementById('dlMeta');
    const log = document.getElementById('dlLog');
    const assetsBox = document.getElementById('dlAssets');
    const testBtn = document.getElementById('dlTest');
    const backBtn = document.getElementById('dlBack');
    const copyBtn = document.getElementById('dlCopy');
    let release = null;
    let selected = null;

    function show(step) {
        [step1, step2, step3].forEach(s => s.hidden = s !== step);
    }

    function open() {
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('locked');
        show(step1);
    }

    function close() {
        modal.classList.remove('open');
        modal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('locked');
    }

    function withTimeout(url, ms) {
        const ctl = new AbortController();
        const timer = setTimeout(() => ctl.abort(), ms);
        return fetch(url, { signal: ctl.signal, cache: 'no-store' })
            .finally(() => clearTimeout(timer));
    }

    async function loadRelease(node) {
        const urls = [];
        if (node.base) urls.push(node.base + API);
        urls.push(API);
        let lastError = null;
        for (const url of urls) {
            try {
                const resp = await withTimeout(url, 12000);
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                const data = await resp.json();
                if (!data || !data.tag_name) throw new Error('返回内容不完整');
                return data;
            } catch (e) {
                lastError = e;
            }
        }
        throw lastError || new Error('获取版本信息失败');
    }

    function platformOf(name) {
        if (/-win\.exe$/i.test(name)) return 'Windows';
        if (/-linux\.sh$/i.test(name)) return 'Linux';
        if (/\.jar$/i.test(name)) return 'macOS / 通用';
        if (/\.deb$/i.test(name)) return 'Debian / Ubuntu';
        return '其他';
    }

    function sizeOf(bytes) {
        if (!bytes) return '';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    // 复制给群里转发的纯文本：Markdown 语法剥掉，只留内容
    function plainText(r) {
        const lines = String(r.body || '该版本未填写更新日志。')
            .replace(/<!--[\s\S]*?-->/g, '')
            .replace(/\r\n?/g, '\n')
            .split('\n');
        const out = [];
        for (const raw of lines) {
            let t = raw.trimEnd()
                .replace(/^#{1,3}\s+/, '')
                .replace(/^[-*]\s+/, '· ')
                .replace(/`([^`]+)`/g, '$1')
                .replace(/\*\*([^*]+)\*\*/g, '$1');
            t = t.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, (m, text, url) =>
                text === url ? text : text + '（' + url + '）');
            out.push(t);
        }
        return out.join('\n').replace(/\n{3,}/g, '\n\n').trim();
    }

    function copyInfo() {
        if (!release) return;
        const text = 'TCraft Launcher ' + release.tag_name + '\n'
            + '发布时间：' + (release.published_at || '').slice(0, 10) + '\n\n'
            + '更新内容：\n' + plainText(release) + '\n\n'
            + '下载：' + RELEASES;
        const done = ok => {
            copyBtn.textContent = ok ? '已复制' : '复制失败，请手动复制';
            setTimeout(() => { copyBtn.textContent = '复制版本信息'; }, 1800);
        };
        const fallback = () => {
            const ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            let ok = false;
            try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
            ta.remove();
            done(ok);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(() => done(true), fallback);
        } else {
            fallback();
        }
    }

    function render(node) {
        title.textContent = '最新版本 ' + release.tag_name;
        meta.textContent = (release.published_at || '').slice(0, 10) + ' 发布';
        log.innerHTML = renderMarkdown(release.body || '该版本未填写更新日志。');
        copyBtn.disabled = false;
        copyBtn.textContent = '复制版本信息';
        assetsBox.innerHTML = '';
        const assets = (release.assets || []).filter(a => !/\.sha256$/i.test(a.name));
        const sorted = assets.slice().sort((a, b) => a.name.localeCompare(b.name));
        sorted.forEach(asset => {
            const a = document.createElement('a');
            a.className = 'dl-asset';
            a.href = node.base ? node.base + asset.browser_download_url : asset.browser_download_url;
            a.target = '_blank';
            a.rel = 'noopener';
            a.innerHTML = '<span class="dl-os">' + platformOf(asset.name) + '</span>'
                + '<span class="dl-name">' + asset.name + '</span>'
                + '<span class="dl-size">' + sizeOf(asset.size) + '</span>';
            assetsBox.appendChild(a);
        });
        if (!sorted.length) {
            const p = document.createElement('p');
            p.className = 'dl-empty';
            p.textContent = '这个版本没有可下载的产物。';
            assetsBox.appendChild(p);
        }
        show(step3);
    }

    async function pick(node, btn) {
        selected = node;
        [...nodesBox.querySelectorAll('.dl-node')].forEach(n => n.classList.remove('selected'));
        if (btn) btn.classList.add('selected');
        copyBtn.disabled = true;
        title.textContent = '正在获取版本信息…';
        meta.textContent = '';
        log.textContent = '正在通过 ' + node.name + ' 读取最新版本，请稍候。';
        assetsBox.innerHTML = '';
        show(step3);
        try {
            release = await loadRelease(node);
            render(node);
        } catch (e) {
            title.textContent = '获取失败';
            log.textContent = '无法读取最新版本：' + (e.message || e)
                + '\n\n可以点下面的按钮直接前往 GitHub Releases。';
            assetsBox.innerHTML = '';
            const a = document.createElement('a');
            a.className = 'dl-asset';
            a.href = RELEASES;
            a.target = '_blank';
            a.rel = 'noopener';
            a.innerHTML = '<span class="dl-os">GitHub</span><span class="dl-name">前往 Releases 页面</span><span class="dl-size"></span>';
            assetsBox.appendChild(a);
        }
    }

    function buildNodes() {
        nodesBox.innerHTML = '';
        NODES.forEach(node => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'dl-node';
            btn.innerHTML = '<span class="dl-node-name">' + node.name + '</span>'
                + '<span class="dl-node-ping">未测速</span>';
            btn.addEventListener('click', () => pick(node, btn));
            nodesBox.appendChild(btn);
        });
    }

    async function testNodes() {
        testBtn.disabled = true;
        testBtn.textContent = '测速中…';
        const probe = (release && (release.assets || []).find(a => /\.sha256$/i.test(a.name)))
            || { browser_download_url: RELEASES };
        let sample = probe.browser_download_url;
        if (/releases$/.test(sample)) {
            try {
                release = release || await loadRelease(NODES[0]);
                const found = (release.assets || []).find(a => /\.sha256$/i.test(a.name));
                if (found) sample = found.browser_download_url;
            } catch (e) {
                // 测速失败按超时处理
            }
        }
        const rows = [...nodesBox.querySelectorAll('.dl-node')];
        await Promise.all(rows.map(async (row, i) => {
            const node = NODES[i];
            const ping = row.querySelector('.dl-node-ping');
            const url = node.base ? node.base + sample : sample;
            const start = performance.now();
            try {
                await withTimeout(url, 8000);
                ping.textContent = Math.round(performance.now() - start) + ' ms';
                ping.classList.add('ok');
            } catch (e) {
                ping.textContent = '不可用';
                ping.classList.add('bad');
            }
        }));
        testBtn.disabled = false;
        testBtn.textContent = '重新测速';
    }

    const params = new URLSearchParams(location.search);
    if (params.get('download')) {
        const step = params.get('download');
        setTimeout(() => {
            open();
            if (step === 'mirror') {
                buildNodes();
                show(step2);
            }
        }, 300);
    }

    document.querySelectorAll('.btn-download').forEach(btn => {
        btn.addEventListener('click', e => {
            e.preventDefault();
            open();
        });
    });
    document.getElementById('dlClose').addEventListener('click', close);
    document.getElementById('dlGithub').addEventListener('click', () => {
        window.open(RELEASES, '_blank', 'noopener');
        close();
    });
    document.getElementById('dlMirror').addEventListener('click', () => {
        buildNodes();
        show(step2);
    });
    backBtn.addEventListener('click', () => show(step2));
    testBtn.addEventListener('click', testNodes);
    copyBtn.addEventListener('click', copyInfo);
    modal.addEventListener('click', e => {
        if (e.target === modal) close();
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && modal.classList.contains('open')) close();
    });
})();
