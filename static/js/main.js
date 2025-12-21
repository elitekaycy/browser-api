document.addEventListener('DOMContentLoaded', function() {
    // Copy to clipboard for index.html
    const copyButton = document.getElementById('copy-button');
    const codeBlock = document.getElementById('code-block');

    if (copyButton && codeBlock) {
        copyButton.addEventListener('click', () => {
            const code = codeBlock.querySelector('code').innerText;
            navigator.clipboard.writeText(code).then(() => {
                copyButton.textContent = 'Copied!';
                setTimeout(() => {
                    copyButton.textContent = 'Copy';
                }, 2000);
            }).catch(err => {
                console.error('Failed to copy text: ', err);
            });
        });
    }

    // Smooth scrolling for docs.html
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();

            const targetId = this.getAttribute('href');
            const targetElement = document.querySelector(targetId);

            if (targetElement) {
                targetElement.scrollIntoView({
                    behavior: 'smooth'
                });
            }
        });
    });
});
