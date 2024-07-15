App();

function App() {
    let isSearching = false;
    let lastFileName;
    let eventSource;

    const searchInput = document.querySelector('#searchInput');
    const searchButton = document.querySelector('#searchButton');
    const nextButton = document.querySelector('#nextButton');
    const searchingStatus = document.querySelector('#searchingStatus');
    const searchFiles = document.querySelector('#searchFiles');
    const searchCount = document.querySelector('#searchCount');
    const results = document.querySelector('#results');

    searchButton.addEventListener('click', () => progressSearch({}));
    nextButton.addEventListener('click', () => progressSearch({ 'from': lastFileName }));
    searchInput.addEventListener('keydown', e => {
        if (e.key == 'Enter') {
            progressSearch({});
        }
    });

    function progressSearch(param) {
        if (eventSource) {
            eventSource.close();
        }

        if (isSearching) return;

        isSearching = true;
        results.innerHTML = '';
        searchingStatus.textContent = 'Searching...';
        searchCount.textContent = '';
        searchFiles.textContent = '';

        let count = 0;
        let pathLoaded = new Set();
        let resultsRenderBuffer = [];

        const searchTerm = searchInput.value;
        let path = `/search?q=${encodeURIComponent(searchTerm)}`;
        if (param && param['from']) {
            path += `&from=${encodeURIComponent(param['from'])}`;
        }
        eventSource = new EventSource(path);

        eventSource.addEventListener('log-message', function (event) {
            const data = JSON.parse(event.data);
            if (data) {
                if (data['name'] === 'logDetail') {
                    count += 1;
                    searchCount.innerHTML = `${count}`;
                    resultsRenderBuffer.push(createLogEntry(data['body']));
                    if (resultsRenderBuffer.length >= 100) {
                        results.append(...resultsRenderBuffer);
                        resultsRenderBuffer = [];
                    }
                } else if (data['name'] === 'logProgress') {
                    let path = data['body']['loadedPath'];
                    let progress = data['body']['progress'];
                    if (progress === 'start') {
                        pathLoaded.add(path);
                        lastFileName = path;
                        searchFiles.innerHTML = `${Array.from(pathLoaded).join(', ')}`;
                    }
                }
            }
        });

        eventSource.onerror = function (error) {
            console.error('EventSource failed:', error);

            if (resultsRenderBuffer.length > 0) {
                results.append(...resultsRenderBuffer);
                resultsRenderBuffer = [];
            }

            isSearching = false;
            searchingStatus.textContent = '';
            eventSource.close();
        };
    }
}

function createLogEntry(detail) {
    const el = document.createElement('div');
    el.className = 'log-entry';
    el.innerHTML = `
        <div class="timestamp">${formatDate(new Date(detail.timestamp))}</div>
        <div class="thread">${detail.thread}</div>
        <div class="priority">${detail.priority}</div>
        <div class="content">${escapeXML(detail.content)}</div>
    `;

    let toggleWrap = () => el.querySelector('.content').classList.toggle('content-wrap');
    el.querySelector('.timestamp').addEventListener('click', toggleWrap);
    el.querySelector('.thread').addEventListener('click', toggleWrap);
    el.querySelector('.priority').addEventListener('click', toggleWrap);

    return el;
}

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

    return `${month}-${day} ${hours}:${minutes}:${seconds}.${milliseconds}`;
}

function escapeXML(str) {
    return str.replace(/[&<>'"]/g,
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}