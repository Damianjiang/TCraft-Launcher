// 轻量 Markdown 渲染：标题、列表、粗体、行内代码、链接
(function () {
    function esc(s) {
        return s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
    }

    function inline(s) {
        return esc(s)
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
                '<a href="$2" target="_blank" rel="noopener">$1</a>');
    }

    window.renderMarkdown = function (md) {
        const lines = String(md || '').replace(/\r\n?/g, '\n').split('\n');
        const out = [];
        let list = false;
        const closeList = () => {
            if (list) {
                out.push('</ul>');
                list = false;
            }
        };
        for (const raw of lines) {
            const line = raw.trimEnd();
            const t = line.trim();
            if (!t) {
                closeList();
                continue;
            }
            const h = /^(#{1,3})\s+(.*)$/.exec(t);
            if (h) {
                closeList();
                const level = h[1].length + 3;
                out.push('<h' + level + '>' + inline(h[2]) + '</h' + level + '>');
                continue;
            }
            if (/^[-*]\s+/.test(t)) {
                if (!list) {
                    out.push('<ul>');
                    list = true;
                }
                out.push('<li>' + inline(t.replace(/^[-*]\s+/, '')) + '</li>');
                continue;
            }
            closeList();
            out.push('<p>' + inline(t) + '</p>');
        }
        closeList();
        return out.join('\n');
    };
})();
