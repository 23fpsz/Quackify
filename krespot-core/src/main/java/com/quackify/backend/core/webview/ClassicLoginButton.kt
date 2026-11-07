package com.quackify.backend.core.webview

/**
 * Injects an "Email + Password Classic Login" pill into Spotify's accounts
 * login page, right above the first alternative sign-in anchor. The link
 * reloads the page with ?allow_password=1, the flag that reveals Spotify's
 * classic email + password form.
 *
 * Like [BrowserSpoof] the script lives in a constant and is injected at
 * document start for the accounts origin (plus an onPageFinished fallback in
 * [LoginWebViewHost] for devices without DOCUMENT_START_SCRIPT support).
 * The pill is DOM-dependent, so the installer is retried from a
 * MutationObserver until the sign-in anchors render — and re-inserted if a
 * React re-render drops it. The .fuckd guard inside the installer keeps a
 * second pill from ever stacking.
 */
object ClassicLoginButton {

    const val CONTENT = """
        (function(){
            if (window.__classicLoginButton) return;
            window.__classicLoginButton = true;

            function install(){
                const gl = document.querySelector(
                    'section>div>div>div>div>a:first-child:not(.fuckd)'
                );

                if (!gl || document.querySelector('.fuckd')) return;

                const cl = document.createElement('a');

                cl.className = 'fuckd';
                cl.textContent = 'Email + Password Classic Login';
                cl.href = '?allow_password=1';

                cl.style.cssText = `
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    box-sizing: border-box;
                    width: 100%;
                    padding: 12px 18px;
                    margin: 12px 0;
                    color: #1DB954;
                    background: transparent;
                    border: 1.5px solid #1DB954;
                    border-radius: 12px;
                    font-size: 14px;
                    font-weight: 600;
                    letter-spacing: .2px;
                    text-align: center;
                    text-decoration: none;
                    cursor: pointer;
                    transform: translateY(0) scale(1);
                    box-shadow: 0 0 0 rgba(29,185,84,0);
                    transition:
                        background .25s ease,
                        color .25s ease,
                        border-color .25s ease,
                        box-shadow .25s ease,
                        transform .25s cubic-bezier(.2,.8,.2,1);
                `;

                cl.addEventListener('mouseenter', () => {
                    cl.style.background = 'rgba(29,185,84,.12)';
                    cl.style.borderColor = '#1ED760';
                    cl.style.color = '#1ED760';
                    cl.style.transform = 'translateY(-2px) scale(1.01)';
                    cl.style.boxShadow = '0 5px 16px rgba(29,185,84,.18)';
                });

                cl.addEventListener('mouseleave', () => {
                    cl.style.background = 'transparent';
                    cl.style.borderColor = '#1DB954';
                    cl.style.color = '#1DB954';
                    cl.style.transform = 'translateY(0) scale(1)';
                    cl.style.boxShadow = '0 0 0 rgba(29,185,84,0)';
                });

                gl.parentNode.insertBefore(cl, gl);
            }

            install();

            // The login page renders its sign-in anchors after this script
            // first runs, and a re-render can drop the pill again; retry the
            // (guarded) installer on every DOM change.
            const mo = new MutationObserver(() => { install(); });
            function watch(){
                if (document.documentElement) {
                    mo.observe(document.documentElement, { childList: true, subtree: true });
                } else {
                    setTimeout(watch, 25);
                }
            }
            watch();
        })();
    """
}
