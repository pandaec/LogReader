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

    searchButton.addEventListener('click', startSearch);

    function startSearch() {
        if(eventSource){
            eventSource.close();
        }

        if (isSearching) return;

        isSearching = true;
        results.innerHTML = '';
        searchingStatus.textContent = 'Searching...';

        let count = 0;
        let pathLoaded = new Set();

        const searchTerm = searchInput.value;
        eventSource = new EventSource(`/search?q=${encodeURIComponent(searchTerm)}`);

        eventSource.addEventListener('log-message', function (event) {
            const data = JSON.parse(event.data);
            if (data) {
                count += 1;
                // TODO Will not show fileName if no line found in that file. 
                pathLoaded.add(data['fileName']);
                lastFileName = data['fileName'];

                searchCount.innerHTML = `${count}`;
                searchFiles.innerHTML = `${Array.from(pathLoaded).join(', ')}`;
                results.appendChild(createLogEntry(data));
            }
        });

        eventSource.onerror = function (error) {
            console.error('EventSource failed:', error);
            isSearching = false;
            searchingStatus.textContent = '';
            eventSource.close();
        };
    }

    nextButton.addEventListener('click', progressSearch);

    function progressSearch() {
        if(!lastFileName) {
            return startSearch();
        }

        if(eventSource){
            eventSource.close();
        }

        isSearching = true;
        results.innerHTML = '';
        searchingStatus.textContent = 'Searching...';

        let count = 0;
        let pathLoaded = new Set();

        const searchTerm = searchInput.value;
        eventSource = new EventSource(`/search?q=${encodeURIComponent(searchTerm)}&from=${encodeURIComponent(lastFileName)}`);

        eventSource.addEventListener('log-message', function (event) {
            const data = JSON.parse(event.data);
            if (data) {
                count += 1;
                // TODO Will not show fileName if no line found in that file. 
                pathLoaded.add(data['fileName']);

                searchCount.innerHTML = `${count}`;
                searchFiles.innerHTML = `${Array.from(pathLoaded).join(', ')}`;
                results.appendChild(createLogEntry(data));
            }
        });

        eventSource.onerror = function (error) {
            console.error('EventSource failed:', error);
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

    return `${month}-${day} ${hours}:${minutes}:${seconds}${milliseconds}`;
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