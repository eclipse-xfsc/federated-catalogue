$(document).ready(function() {

  var IFRAME_LOAD_TIMEOUT_MS = 5000;

  // AC-3: check admin access before loading content
  $.ajax({
    url: '/admin/me',
    type: 'GET',
    success: function() {
      loadKeycloakConsole();
    },
    error: function() {
      $('#loading-spinner').hide();
    }
  });

  function loadKeycloakConsole() {
    $.getJSON('/admin/keycloak-url', function(data) {
      $('#loading-spinner').hide();

      if (!data || !data.url) {
        $('#error-panel').show();
        return;
      }

      var keycloakUrl = data.url;
      $('#fallback-link').attr('href', keycloakUrl);

      // Try loading in iframe
      var iframe = $('#keycloak-iframe');
      iframe.attr('src', keycloakUrl);
      $('#iframe-container').show();

      // X-Frame-Options blocking doesn't fire an error event — the browser
      // fires 'load' on the blank/error page instead. We detect blocking by
      // trying to access the iframe's contentDocument after a timeout.
      setTimeout(function() {
        try {
          var doc = iframe[0].contentDocument || iframe[0].contentWindow.document;
          // Same-origin access succeeded — check if content is meaningful
          if (!doc || !doc.body || doc.body.innerHTML.length < 100) {
            showFallback();
          }
        } catch (e) {
          // Cross-origin error — iframe loaded Keycloak (different port),
          // this is the expected success case, keep iframe visible
        }
      }, IFRAME_LOAD_TIMEOUT_MS);

    }).fail(function() {
      $('#loading-spinner').hide();
      $('#error-panel').show();
    });
  }

  function showFallback() {
    $('#iframe-container').hide();
    $('#fallback-panel').show();
  }

});
