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
        const data = event.data;
        if (resultsDiv.innerHTML === 'Searching...') {
            resultsDiv.innerHTML = '';
        }
        resultsDiv.innerHTML += data;
    });

    window.eventSource.onerror = function (error) {
        console.error('EventSource failed:', error);
        resultsDiv.innerHTML += '<p>Error: Search failed</p>';
        window.eventSource.close();
    };
}