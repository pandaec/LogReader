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

function createLogEntry(detail) {
    const el = document.createElement('div');
    el.className = 'log-entry';
    el.innerHTML = `
        <div class="timestamp">${formatDate(new Date(detail.timestamp))}</div>
        <div class="thread">${detail.thread}</div>
        <div class="priority">${detail.priority}</div>
        <div class="content">${detail.content}</div>
    `;

    let toggleWrap = () => el.querySelector('.content').classList.toggle('content-wrap');
    el.querySelector('.timestamp').addEventListener('click', toggleWrap);
    el.querySelector('.thread').addEventListener('click', toggleWrap);
    el.querySelector('.priority').addEventListener('click', toggleWrap);

    return el;
}

function App() {
    let isSearching = false;
    let eventSource;

    const searchInput = document.querySelector('#searchInput');
    const searchButton = document.querySelector('#searchButton');
    const searchingStatus = document.querySelector('#searchingStatus');
    const results = document.querySelector('#results');

    searchButton.addEventListener('click', startSearch);

    function startSearch() {
        if (isSearching) return;

        isSearching = true;
        results.innerHTML = '';
        searchingStatus.textContent = 'Searching...';

        const searchTerm = searchInput.value;
        eventSource = new EventSource(`/search?q=${encodeURIComponent(searchTerm)}`);

        eventSource.addEventListener('log-message', function (event) {
            const data = JSON.parse(event.data);
            if (data) {
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

App();