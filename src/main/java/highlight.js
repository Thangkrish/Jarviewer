// When the document is loaded, add this script
document.addEventListener('DOMContentLoaded', function() {
    // Code highlighting setup for Java
    if (document.body.textContent.trim().startsWith("/*") ||
        document.body.textContent.trim().startsWith("package") ||
        document.body.textContent.trim().startsWith("import")) {

        // This is likely Java code - wrap it in a pre tag for proper formatting
        const content = document.body.innerHTML;
        document.body.innerHTML = '<pre>' + content + '</pre>';
    }
});

// Function to highlight all occurrences of a text string
function highlightText(text) {
    if (!text || text.length === 0) return;

    clearHighlights();

    const content = document.body.innerHTML;
    const escapedText = text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp('(' + escapedText + ')', 'gi');

    // Replace text with highlighted spans
    document.body.innerHTML = content.replace(regex, '<span class="highlight">$1</span>');
}

// Function to clear all highlights
function clearHighlights() {
    const highlights = document.querySelectorAll('.highlight, .current-highlight');
    highlights.forEach(el => {
        const parent = el.parentNode;
        parent.replaceChild(document.createTextNode(el.textContent), el);
    });

    // Clean up the HTML to normalize any broken elements
    const content = document.body.innerHTML;
    document.body.innerHTML = content;
}

// Function to clear just the current selection
function clearSelection() {
    const currentHighlights = document.querySelectorAll('.current-highlight');
    currentHighlights.forEach(el => {
        el.classList.remove('current-highlight');
        el.classList.add('highlight');
    });
}

// Function to select and scroll to a specific match
function selectMatch(position, searchText) {
    const textNodes = [];
    const walker = document.createTreeWalker(
        document.body,
        NodeFilter.SHOW_TEXT,
        null,
        false
    );

    let node;
    while(node = walker.nextNode()) {
        textNodes.push(node);
    }

    // Find the text node containing our position
    let currentPosition = 0;
    let targetNode = null;
    let nodePosition = 0;

    for (const node of textNodes) {
        const nodeLength = node.textContent.length;
        if (currentPosition + nodeLength > position) {
            targetNode = node;
            nodePosition = position - currentPosition;
            break;
        }
        currentPosition += nodeLength;
    }

    if (targetNode) {
        // Find the parent highlight span
        let parent = targetNode.parentNode;
        while (parent && !parent.classList.contains('highlight')) {
            parent = parent.parentNode;
        }

        if (parent && parent.classList.contains('highlight')) {
            parent.classList.remove('highlight');
            parent.classList.add('current-highlight');

            // Scroll to the element
            parent.scrollIntoView({
                behavior: 'smooth',
                block: 'center'
            });
        }
    }
}

