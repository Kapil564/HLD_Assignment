// Simple vanilla JS typeahead client
(() => {
    const input = document.getElementById('search-input');
    const suggestionsEl = document.getElementById('suggestions');
    const loadingEl = document.getElementById('loading');
    const errorEl = document.getElementById('error');
    const searchBtn = document.getElementById('search-btn');
    const resultEl = document.getElementById('search-result');
    const trendingList = document.getElementById('trending-list');

    let suggestions = [];
    let selIndex = -1;
    let controller = null;
    let debounceTimer = null;
    let isSearching = false;

    function showLoading(show, text = 'Loading...') {
        if (show) {
            const span = loadingEl.querySelector('span:last-child');
            if (span) span.textContent = text;
            loadingEl.classList.remove('hidden');
        } else {
            loadingEl.classList.add('hidden');
        }
    }
    function showError(msg) { if (msg) { errorEl.textContent = msg; errorEl.classList.remove('hidden'); } else { errorEl.classList.add('hidden'); errorEl.textContent = ''; } }

    function renderSuggestions() {
        suggestionsEl.innerHTML = '';
        if (!suggestions || suggestions.length === 0) {
            suggestionsEl.classList.add('hidden');
            return;
        }
        suggestionsEl.classList.remove('hidden');
        suggestions.forEach((s, i) => {
            const li = document.createElement('li');
            li.textContent = s;
            li.setAttribute('role','option');
            li.dataset.index = i;
            li.setAttribute('aria-selected', i === selIndex ? 'true' : 'false');
            li.addEventListener('mousedown', (e) => {
                // mousedown to avoid losing focus before click
                e.preventDefault();
                selectSuggestion(i);
                submitSearch(s);
            });
            suggestionsEl.appendChild(li);
        });
    }

    function selectSuggestion(i) {
        selIndex = i;
        input.value = suggestions[i] || '';
        renderSuggestions();
    }

    function clearSuggestions() {
        suggestions = [];
        selIndex = -1;
        renderSuggestions();
    }

    function fetchSuggest(q) {
        if (!q || q.trim().length === 0) { clearSuggestions(); return; }
        if (controller) controller.abort();
        controller = new AbortController();
        showLoading(true, 'Fetching suggestions...');
        showError(null);

        fetch(`/api/suggest?q=${encodeURIComponent(q)}`, { signal: controller.signal })
            .then(res => {
                if (!res.ok) throw new Error('Suggest request failed: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (Array.isArray(data)) {
                    suggestions = data;
                } else if (data && Array.isArray(data.suggestions)) {
                    suggestions = data.suggestions.map(s => typeof s === 'string' ? s : (s.text || s.query || JSON.stringify(s)));
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

    input.addEventListener('input', (e) => {
        const q = e.target.value;
        debounceFetch(q);
    });

    input.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowDown') {
            if (suggestions.length === 0) return;
            e.preventDefault();
            selIndex = Math.min(suggestions.length - 1, selIndex + 1);
            input.value = suggestions[selIndex];
            renderSuggestions();
        } else if (e.key === 'ArrowUp') {
            if (suggestions.length === 0) return;
            e.preventDefault();
            selIndex = Math.max(0, selIndex - 1);
            input.value = suggestions[selIndex];
            renderSuggestions();
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (selIndex >= 0 && selIndex < suggestions.length) {
                submitSearch(suggestions[selIndex]);
            } else {
                submitSearch(input.value);
            }
            clearSuggestions();
        } else if (e.key === 'Escape') {
            clearSuggestions();
        }
    });

    searchBtn.addEventListener('click', () => {
        submitSearch(input.value);
        clearSuggestions();
    });

    function submitSearch(query) {
        if (!query || query.trim().length === 0) {
            resultEl.textContent = 'Please type a query.';
            return;
        }
        isSearching = true;
        showLoading(true, 'Searching...');
        showError(null);
        fetch('/api/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query })
        })
            .then(res => {
                if (!res.ok) throw new Error('Search failed: ' + res.status);
                return res.json();
            })
            .then(data => {
                if (data && data.result) {
                    resultEl.textContent = data.result;
                } else if (data && data.message) {
                    resultEl.textContent = data.message;
                } else {
                    resultEl.textContent = 'Searched';
                }
                resultEl.classList.remove('result-placeholder');
                fetchMetrics();
                setTimeout(fetchMetrics, 5500); // refresh after buffer flush
            })
            .catch(err => {
                showError('Search failed');
                console.error(err);
            })
            .finally(() => { 
                isSearching = false; 
                showLoading(false); 
            });
    }

    function loadTrending() {
        trendingList.textContent = '';
        fetch('/api/trending')
            .then(res => res.ok ? res.json() : Promise.reject(res.status))
            .then(data => {
                trendingList.innerHTML = '';
                const list = Array.isArray(data) ? data : (data && Array.isArray(data.trending) ? data.trending : []);
                if (list.length === 0) {
                    trendingList.textContent = 'No trending items found.';
                    return;
                }
                list.forEach(item => {
                    const li = document.createElement('li');
                    
                    const querySpan = document.createElement('span');
                    querySpan.className = 'trending-query';
                    querySpan.textContent = item.query ? item.query : JSON.stringify(item);
                    
                    const scoreSpan = document.createElement('span');
                    scoreSpan.className = 'trending-score';
                    scoreSpan.textContent = item.score !== undefined ? `Score: ${item.score.toFixed(1)}` : (item.count ? `${item.count}` : '0');
                    
                    li.appendChild(querySpan);
                    li.appendChild(scoreSpan);
                    trendingList.appendChild(li);
                });
            })
            .catch(err => {
                trendingList.textContent = 'Unable to load trending.';
                console.error(err);
            });
    }

    function fetchMetrics() {
        fetch('/api/metrics?t=' + Date.now())
            .then(res => res.ok ? res.json() : Promise.reject(res.status))
            .then(data => {
                document.getElementById('metric-hit-rate').textContent = data.cacheHitRatePercent || '0.00%';
                document.getElementById('metric-latency').textContent = (data.averageSuggestLatencyMs || '0.00 ms') + ' / ' + (data.p95SuggestLatencyMs || '0 ms');
                document.getElementById('metric-searches').textContent = data.totalSearchRequestsSubmitted !== undefined ? data.totalSearchRequestsSubmitted : '0';
                document.getElementById('metric-reduction').textContent = data.writeReductionPercent || '0.00%';
            })
            .catch(err => console.error('Error loading metrics:', err));
    }

    document.getElementById('refresh-metrics-btn').addEventListener('click', fetchMetrics);

    // Initialize
    loadTrending();
    fetchMetrics();

    // Optionally refresh trending and metrics every 30s
    setInterval(() => {
        loadTrending();
        fetchMetrics();
    }, 30000);
})();
