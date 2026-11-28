/* Métricas de DEVUTEC: visitas, eventos (como «pedido») y velocidad de carga. Anónimo: sin cookies ni datos personales. */
(function () {
  var script = document.currentScript
  var key = script && script.getAttribute('data-site')
  if (!key || window.devutec) return
  var endpoint = script.src.replace(/\/metrics\/sdk\.js.*$/, '/metrics/events')

  // Un número al azar por pestaña, solo para contar visitantes distintos (se borra al cerrar la pestaña)
  var session
  try {
    session = sessionStorage.getItem('devutec_sid')
    if (!session) {
      session = Math.random().toString(36).slice(2) + Date.now().toString(36)
      sessionStorage.setItem('devutec_sid', session)
    }
  } catch (e) {
    session = Math.random().toString(36).slice(2)
  }

  function send(type, label, loadMs) {
    var body = JSON.stringify({
      k: key,
      t: type,
      p: location.pathname.slice(0, 200),
      l: label ? String(label).slice(0, 60) : null,
      s: session,
      ms: loadMs || null,
    })
    // text/plain no necesita permiso previo del navegador (CORS) y sendBeacon no frena la página
    if (navigator.sendBeacon && navigator.sendBeacon(endpoint, new Blob([body], { type: 'text/plain' }))) return
    try {
      fetch(endpoint, { method: 'POST', body: body, keepalive: true, headers: { 'Content-Type': 'text/plain' } })
    } catch (e) {
      /* sin conexión: no pasa nada */
    }
  }

  function view() {
    var loadMs = null
    try {
      var nav = performance.getEntriesByType('navigation')[0]
      if (nav && nav.loadEventEnd > 0) loadMs = Math.round(nav.loadEventEnd)
    } catch (e) {
      /* navegador antiguo */
    }
    send('view', null, loadMs)
  }

  if (document.readyState === 'complete') setTimeout(view, 0)
  else window.addEventListener('load', function () { setTimeout(view, 0) })

  // Cualquier botón o link con data-devutec-event="pedido" cuenta como ese evento al hacer clic
  document.addEventListener('click', function (event) {
    var el = event.target && event.target.closest && event.target.closest('[data-devutec-event]')
    if (el) send('event', el.getAttribute('data-devutec-event'))
  }, true)

  // Para formularios o compras que terminan por código: devutec.track('pedido')
  window.devutec = { track: function (name) { if (name) send('event', name) } }
})()
