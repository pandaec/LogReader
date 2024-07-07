const { html, render, useState, useEffect } = htmPreact;

function formatDate(date) {
    const pad = (num, size) => {
        let s = "000" + num;
        return s.slice(-size);
    };

    const month = pad(date.getMonth() + 1, 2);
    const day = pad(date.getDate(), 2);
    const hours = pad(date.getHours(), 2);
    const minutes = pad(date.getMinutes(), 2);
    const seconds = pad(date.getSeconds(), 2);
    const milliseconds = pad(date.getMilliseconds(), 3);

    return `${month}-${day} ${hours}:${minutes}:${seconds}${milliseconds}`;
}

function LogEntry({ detail }) {
    return html`
        <div class="log-entry">
            <div class="timestamp">${formatDate(new Date(detail.timestamp))}</div>
            <div class="thread">${detail.thread}</div>
            <div class="priority">${detail.priority}</div>
            <div class="content">${detail.content}</div>
        </div>
    `;
}

function App() {
    const [searchTerm, setSearchTerm] = useState('');
    const [results, setResults] = useState([]);
    const [isSearching, setIsSearching] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        let eventSource;

        if (isSearching) {
            setResults([]);
            setError(null);

            const fromFile = "WV-ST-20240110-0136.log";
            eventSource = new EventSource(`/search?q=${encodeURIComponent(searchTerm)}&from=${encodeURIComponent(fromFile)}`);

            eventSource.addEventListener('log-message', function (event) {
                const data = JSON.parse(event.data);
                if (data) {
                    setResults(prevResults => [...prevResults, data]);
                }
            });

            eventSource.onerror = function (error) {
                console.error('EventSource failed:', error);
                setError('Error: Search failed');
                setIsSearching(false);
                eventSource.close();
            };
        }

        return () => {
            if (eventSource) {
                eventSource.close();
            }
        };
    }, [isSearching, searchTerm]);

    const startSearch = () => {
        setIsSearching(true);
    };

    return html`
        <main>
            <h1>Log Search Example</h1>
            <input 
                type="text" 
                placeholder="Enter search term" 
                value=${searchTerm}
                onInput=${e => setSearchTerm(e.target.value)}
            />
            <button onClick=${startSearch}>Search</button>
            <div id="results">
                ${isSearching && results.length === 0 ? 'Searching...' : ''}
                ${error ? html`<p>${error}</p>` : ''}
                ${results.map(detail => html`<${LogEntry} detail=${detail} />`)}
            </div>
        </main>
    `;
}

render(html`<${App} />`, document.getElementById('app'));
