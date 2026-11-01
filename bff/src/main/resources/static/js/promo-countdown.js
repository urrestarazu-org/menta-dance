(function(root) {
    'use strict';

    const SECOND = 1000;
    const MINUTE = 60 * SECOND;
    const HOUR = 60 * MINUTE;
    const DAY = 24 * HOUR;

    const FINISHED_MESSAGE =
        'Esta promoción finalizó. Actualizá la página para ver el precio vigente.';

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function unit(value, singular, plural) {
        return pad(value) + ' ' + (value === 1 ? singular : plural);
    }

    // Arma el texto del globo a partir de los milisegundos que faltan.
    // Los días se omiten el último día, cuando ya son cero.
    function formatRemaining(remainingMs) {
        const days = Math.floor(remainingMs / DAY);
        const hours = Math.floor((remainingMs % DAY) / HOUR);
        const minutes = Math.floor((remainingMs % HOUR) / MINUTE);
        const seconds = Math.floor((remainingMs % MINUTE) / SECOND);
        const daysPart = days > 0 ? unit(days, 'día', 'días') + ' y ' : '';

        return 'Esta promoción termina en ' + daysPart
            + unit(hours, 'hora', 'horas') + ':'
            + unit(minutes, 'minuto', 'minutos') + ':'
            + unit(seconds, 'segundo', 'segundos');
    }

    function start(balloon) {
        const expiresAt = Date.parse(balloon.dataset.expiresAt);
        const serverNow = Date.parse(balloon.dataset.serverNow);
        const textElement = balloon.querySelector('[data-countdown-text]');

        // Si falta algún dato se conserva el texto estático con la fecha.
        if (Number.isNaN(expiresAt) || !textElement) {
            return;
        }

        // El reloj del visitante puede estar desfasado: se corrige con la hora del servidor.
        const clockOffset = Number.isNaN(serverNow) ? 0 : serverNow - Date.now();
        let timerId = null;

        function tick() {
            const remaining = expiresAt - (Date.now() + clockOffset);

            if (remaining <= 0) {
                textElement.textContent = FINISHED_MESSAGE;
                clearInterval(timerId);
                return;
            }
            textElement.textContent = formatRemaining(remaining);
        }

        tick();
        timerId = setInterval(tick, SECOND);
    }

    function init() {
        root.document.querySelectorAll('[data-promo-countdown]').forEach(start);
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = { formatRemaining: formatRemaining };
    } else {
        init();
    }
})(typeof window !== 'undefined' ? window : globalThis);
