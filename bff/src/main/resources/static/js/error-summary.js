/*
 * Error summary of the server-rendered forms (#529, finding 23; WCAG 3.3.1 and 3.3.3).
 *
 * After an invalid submit the page reloads with a summary of the field errors
 * ([data-error-summary], see fragments/components.html). This script moves focus to it,
 * so a keyboard or screen-reader user lands on the list of problems instead of at the
 * top of the page, and makes each link in it focus the invalid input (scrolling its
 * label into view) rather than only scrolling to it.
 *
 * Without JavaScript the summary is still rendered and its links still jump to the inputs.
 * No inline script and no dependencies: it loads as a same-origin file, so it also holds
 * under a strict Content-Security-Policy (script-src 'self').
 */
(function () {
  'use strict';

  var summary = document.querySelector('[data-error-summary]');
  if (!summary) {
    return;
  }

  summary.focus();

  summary.addEventListener('click', function (event) {
    var link = event.target.closest('a[href^="#"]');
    var input = link && document.getElementById(decodeURIComponent(link.getAttribute('href').slice(1)));
    if (!input) {
      return;
    }
    event.preventDefault();
    var label = document.querySelector('label[for="' + CSS.escape(input.id) + '"]');
    (label || input).scrollIntoView();
    input.focus({ preventScroll: true });
  });
})();
