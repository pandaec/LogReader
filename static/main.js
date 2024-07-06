function startSearch() {
    const searchTerm = document.getElementById('searchInput').value;
    const resultsDiv = document.getElementById('results');
    resultsDiv.innerHTML = 'Searching...';

    // Close any existing SSE connection
    if (window.eventSource) {
        window.eventSource.close();
    }

    // Open a new SSE connection
    let fromFile = "WV-ST-20240110-0136.log";
    window.eventSource = new EventSource(`/search?q=${encodeURIComponent(searchTerm)}&from=${encodeURIComponent(fromFile)}`);

    window.eventSource.addEventListener('log-message', function (event) {
        const data = JSON.parse(event.data);
        if (!data) {
            return;
        }
        if (resultsDiv.innerHTML === 'Searching...') {
            resultsDiv.innerHTML = '';
        }
        const detailDiv = templateRowDetail(data);
        resultsDiv.appendChild(detailDiv)
    });

    window.eventSource.onerror = function (error) {
        console.error('EventSource failed:', error);
        resultsDiv.innerHTML += '<p>Error: Search failed</p>';
        window.eventSource.close();
    };
}

function templateRowDetail(detail) {
    const container = document.createElement('div');
    container.className = 'log-entry';

    const timestampDiv = document.createElement('div');
    timestampDiv.className = 'timestamp';
    timestampDiv.textContent = formatDate(new Date(detail['timestamp']));

    const threadDiv = document.createElement('div');
    threadDiv.className = 'thread';
    threadDiv.textContent = detail['thread'];

    const priorityDiv = document.createElement('div');
    priorityDiv.className = 'priority';
    priorityDiv.textContent = detail['priority'];

    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';
    contentDiv.textContent = detail['content'];

    container.appendChild(timestampDiv);
    container.appendChild(threadDiv);
    container.appendChild(priorityDiv);
    container.appendChild(contentDiv);

    return container;
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