// SearchConsole — vanilla JS client
(() => {
    /* ── DOM refs ── */
    const input              = document.getElementById('search-input');
    const suggestionsEl      = document.getElementById('suggestions');
    const loadingEl          = document.getElementById('loading');
    const errorEl            = document.getElementById('error');
    const searchBtn          = document.getElementById('search-btn');
    const resultEl           = document.getElementById('search-result');
    const trendingList       = document.getElementById('trending-list');
    const dropdownContainer  = document.getElementById('dropdown-container');

    /* ── State ── */
    let suggestions  = [];   // array of { text, count }
    let selIndex     = -1;
    let controller   = null;
    let debounceTimer = null;
    let isSearching  = false;
    let originalInput = '';

    /* ───────────── DROPDOWN VISIBILITY ───────────── */
    function updateDropdown() {
        const hasSuggestions = suggestions.length > 0;
        const isLoading = !loadingEl.classList.contains('hidden');
        const hasError  = !errorEl.classList.contains('hidden');
        if (hasSuggestions || isLoading || hasError) {
            dropdownContainer.classList.remove('hidden');
        } else {
            dropdownContainer.classList.add('hidden');
        }
    }

    /* ───────────── LOADING / ERROR ───────────── */
    function showLoading(show, text = 'Loading...') {
        const span = loadingEl.querySelector('span:last-child');
        if (span) span.textContent = text;
        loadingEl.classList.toggle('hidden', !show);
        updateDropdown();
    }

    function showError(msg) {
        if (msg) {
            errorEl.textContent = msg;
            errorEl.classList.remove('hidden');
        } else {
            errorEl.classList.add('hidden');
            errorEl.textContent = '';
        }
        updateDropdown();
    }

    /* ───────────── SUGGESTIONS ───────────── */
    function renderSuggestions() {
        suggestionsEl.innerHTML = '';
        if (!suggestions || suggestions.length === 0) {
            suggestionsEl.classList.add('hidden');
            updateDropdown();
            return;
        }
        suggestionsEl.classList.remove('hidden');

        suggestions.forEach((s, i) => {
            const li = document.createElement('li');
            li.setAttribute('role', 'option');
            li.dataset.index = i;
            li.setAttribute('aria-selected', i === selIndex ? 'true' : 'false');

            // s is either a string or { text, count }
            const label = typeof s === 'string' ? s : s.text;
            const count = typeof s === 'object' && s.count != null ? s.count : null;

            const textSpan = document.createElement('span');
            textSpan.className = 'suggestion-text';
            textSpan.textContent = label;
            li.appendChild(textSpan);

            if (count !== null) {
                const countBadge = document.createElement('span');
                countBadge.className = 'suggestion-count';
                countBadge.textContent = count.toLocaleString();
                li.appendChild(countBadge);
            }

            li.addEventListener('mousedown', (e) => {
                e.preventDefault();
                selectSuggestion(i);
                submitSearch(label);
            });

            suggestionsEl.appendChild(li);
        });

        updateDropdown();
    }

    function selectSuggestion(i) {
        selIndex = i;
        const label = typeof suggestions[i] === 'string' ? suggestions[i] : suggestions[i].text;
        input.value = label;
        renderSuggestions();
    }

    function clearSuggestions() {
        suggestions = [];
        selIndex = -1;
        renderSuggestions();
    }

    /* ───────────── FETCH SUGGESTIONS ───────────── */
    function fetchSuggest(q) {
        if (!q || q.trim().length === 0) { clearSuggestions(); return; }
        if (controller) controller.abort();
        controller = new AbortController();
        showLoading(true, 'Fetching suggestions…');
        showError(null);

        fetch(`/api/suggest?q=${encodeURIComponent(q)}`, { signal: controller.signal })
            .then(res => {
                if (!res.ok) throw new Error('Suggest failed: ' + res.status);
                return res.json();
            })
            .then(data => {
                // Backend returns { query, suggestions: [{text, count}] }
                if (Array.isArray(data)) {
                    suggestions = data;
                } else if (data && Array.isArray(data.suggestions)) {
                    suggestions = data.suggestions; // keep as objects { text, count }
                } else {
                    suggestions = [];
                }
                selIndex = -1;
                renderSuggestions();
            })
            .catch(err => {
                if (err.name === 'AbortError') return;
                showError('Unable to load suggestions');
                console.error(err);
            })
            .finally(() => {
                if (!isSearching) showLoading(false);
                fetchMetrics();
            });
    }

    function debounceFetch(q) {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => fetchSuggest(q), 200);
    }

    /* ───────────── INPUT EVENTS ───────────── */
    input.addEventListener('input', (e) => {
        originalInput = e.target.value;
        debounceFetch(originalInput);
    });

    input.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowDown') {
            if (suggestions.length === 0) return;
            e.preventDefault();
            if (selIndex === -1) originalInput = input.value;
            selIndex = (selIndex + 1) >= suggestions.length ? -1 : selIndex + 1;
            if (selIndex === -1) {
                input.value = originalInput;
            } else {
                const s = suggestions[selIndex];
                input.value = typeof s === 'string' ? s : s.text;
            }
            renderSuggestions();
        } else if (e.key === 'ArrowUp') {
            if (suggestions.length === 0) return;
            e.preventDefault();
            if (selIndex === -1) {
                originalInput = input.value;
                selIndex = suggestions.length - 1;
            } else {
                selIndex--;
            }
            if (selIndex < 0) {
                selIndex = -1;
                input.value = originalInput;
            } else {
                const s = suggestions[selIndex];
                input.value = typeof s === 'string' ? s : s.text;
            }
            renderSuggestions();
        } else if (e.key === 'Enter') {
            e.preventDefault();
            const query = selIndex >= 0 && selIndex < suggestions.length
                ? (typeof suggestions[selIndex] === 'string' ? suggestions[selIndex] : suggestions[selIndex].text)
                : input.value;
            clearSuggestions();
            submitSearch(query);
        } else if (e.key === 'Escape') {
            clearSuggestions();
        }
    });

    /* ───────────── SEARCH BUTTON ───────────── */
    searchBtn.addEventListener('click', () => {
        const query = input.value.trim();
        clearSuggestions();
        if (query) {
            submitSearch(query);
        } else {
            resultEl.classList.add('result-placeholder');
            resultEl.textContent = 'Please type a query first.';
        }
    });

    /* ───────────── SUBMIT SEARCH ───────────── */
    function submitSearch(query) {
        const q = (query || '').trim();
        if (!q) {
            resultEl.classList.add('result-placeholder');
            resultEl.textContent = 'Please type a query.';
            return;
        }

        isSearching = true;
        showError(null);

        // Immediately show in-progress message in result box
        resultEl.classList.remove('result-placeholder');
        resultEl.textContent = 'Searching for "' + q + '"…';

        fetch('/api/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query: q })
        })
            .then(function(res) {
                if (!res.ok) throw new Error('HTTP ' + res.status);
                return res.json();
            })
            .then(function(data) {
                console.log('Search response:', data);
                // SearchResponse: { message: "Success", result: "<query>" }
                var submittedQuery = data.result || data.message || q;
                resultEl.classList.remove('result-placeholder');
                resultEl.innerHTML = buildResultHTML(submittedQuery);
                fetchMetrics();
                loadTrending();
                setTimeout(function() { fetchMetrics(); loadTrending(); }, 5500);
            })
            .catch(function(err) {
                console.error('Search error:', err);
                resultEl.classList.remove('result-placeholder');
                resultEl.innerHTML = '<span class="result-error">&#10060; Search failed: ' + escapeHtml(err.message) + '</span>';
            })
            .finally(function() {
                isSearching = false;
            });
    }

    function buildResultHTML(query) {
        var safe = escapeHtml(query);
        return '<div class="result-success">' +
               '<div class="result-check">&#10003;</div>' +
               '<div class="result-body">' +
               '<div class="result-label">Query Recorded</div>' +
               '<div class="result-query">&quot;' + safe + '&quot;</div>' +
               '<div class="result-meta">Success &middot; Metrics &amp; trending updated</div>' +
               '</div>' +
               '</div>';
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    /* ───────────── TRENDING ───────────── */
    function loadTrending() {
        trendingList.innerHTML = '<div class="trending-loading"><span class="spinner"></span><span>Loading…</span></div>';

        fetch('/api/trending')
            .then(res => res.ok ? res.json() : Promise.reject(res.status))
            .then(data => {
                trendingList.innerHTML = '';
                const list = Array.isArray(data)
                    ? data
                    : (data && Array.isArray(data.trending) ? data.trending : []);

                if (list.length === 0) {
                    trendingList.innerHTML = '<li class="trending-empty">No trending items.</li>';
                    return;
                }

                list.forEach((item, idx) => {
                    const li = document.createElement('li');
                    const queryText = item.query || item.text || JSON.stringify(item);
                    const score = item.score != null
                        ? item.score.toFixed(1)
                        : (item.count || 0);

                    li.innerHTML = `
                        <span class="trending-rank">#${idx + 1}</span>
                        <span class="trending-query">${escapeHtml(queryText)}</span>
                        <span class="trending-score">${Number(score).toLocaleString(undefined, { maximumFractionDigits: 0 })}</span>`;

                    li.title = `Click to search: ${queryText}`;
                    li.addEventListener('click', () => {
                        input.value = queryText;
                        clearSuggestions();
                        submitSearch(queryText);
                        // Scroll to top so user can see result
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                    });

                    trendingList.appendChild(li);
                });
            })
            .catch(err => {
                trendingList.innerHTML = '<li class="trending-empty">Unable to load trending.</li>';
                console.error('Trending error:', err);
            });
    }

    /* ───────────── METRICS ───────────── */
    function fetchMetrics() {
        fetch('/api/metrics?t=' + Date.now())
            .then(res => res.ok ? res.json() : Promise.reject(res.status))
            .then(data => {
                setText('metric-hit-rate',   data.cacheHitRatePercent         || '0.00%');
                setText('metric-latency',    (data.averageSuggestLatencyMs    || '0.00 ms') + ' / ' + (data.p95SuggestLatencyMs || '0 ms'));
                setText('metric-searches',   data.totalSearchRequestsSubmitted != null ? data.totalSearchRequestsSubmitted : '0');
                setText('metric-reduction',  data.writeReductionPercent        || '0.00%');
            })
            .catch(err => console.error('Metrics error:', err));
    }

    function setText(id, val) {
        const el = document.getElementById(id);
        if (el) el.textContent = val;
    }

    document.getElementById('refresh-metrics-btn').addEventListener('click', fetchMetrics);

    /* ───────────── CLOSE DROPDOWN ON OUTSIDE CLICK ───────────── */
    document.addEventListener('click', (e) => {
        if (!input.contains(e.target) && !dropdownContainer.contains(e.target)) {
            clearSuggestions();
        }
    });

    /* ───────────── INIT ───────────── */
    loadTrending();
    fetchMetrics();

    setInterval(() => { loadTrending(); fetchMetrics(); }, 30000);
})();
