$(document).ready(function() {

  function loadData() {
    // Reset spinners
    $('#metric-total-assets, #metric-trust-frameworks, #metric-users, #metric-schemas, #metric-participants, #metric-claims').html(
      '<div class="spinner-border spinner-border-sm text-secondary" role="status"></div>'
    );
    $('#health-table tbody tr').removeClass('table-success table-danger table-warning');
    $('.badge', '#health-table').html('<span class="spinner-border spinner-border-sm" role="status"></span>');

    // Load statistics
    $.getJSON('/admin/stats', function(data) {
      $('#metric-total-assets').text(formatCount(data.totalAssets));
      $('#metric-active-assets').text(formatCount(data.activeAssets));
      $('#metric-trust-frameworks').text(formatCount(data.activeTrustFrameworks));
      $('#metric-users').text(formatCount(data.totalUsers));
      $('#metric-schemas').text(formatCount(data.totalSchemas));
      $('#metric-participants').text(formatCount(data.totalParticipants));
      $('#metric-claims').text(formatCount(data.graphClaimCount));
      $('#metric-graph-backend').text(data.graphBackend || '-');
      updateTimestamp();
    }).fail(function() {
      var msg = 'Failed to load';
      $('#metric-total-assets, #metric-trust-frameworks, #metric-users, #metric-schemas, #metric-participants, #metric-claims').text(msg);
      $('#metric-active-assets').text('-');
      $('#metric-graph-backend').text('-');
      updateTimestamp();
    });

    // Load health status
    $.getJSON('/admin/health', function(data) {
      renderStatus('catalogue', data.catalogueStatus);
      $('#status-catalogue-details').text('Federated Catalogue Service');

      renderStatus('keycloak', data.keycloakStatus);
      $('#status-keycloak-details').text(data.keycloakUrl || '-');

      renderStatus('filestore', data.fileStoreStatus);
      $('#status-filestore-details').text(data.fileStorePath || '-');

      renderStatus('database', data.databaseStatus);
      $('#status-database-details').text('PostgreSQL');

      if (data.graphDbStatus) {
        var graphUp = data.graphDbStatus.connected ? 'UP' : 'DOWN';
        renderStatus('graphdb', graphUp);
        $('#status-graphdb-details').text(
          'Backend: ' + (data.graphDbStatus.backend || '-') +
          ' | Claims: ' + formatCount(data.graphDbStatus.claimCount)
        );
      } else {
        renderStatus('graphdb', 'UNKNOWN');
        $('#status-graphdb-details').text('-');
      }
    }).fail(function() {
      renderStatus('catalogue', 'UNKNOWN');
      renderStatus('graphdb', 'UNKNOWN');
      renderStatus('keycloak', 'UNKNOWN');
      renderStatus('filestore', 'UNKNOWN');
      renderStatus('database', 'UNKNOWN');
      updateTimestamp();
    });
  }

  function renderStatus(component, status) {
    var badge = $('#status-' + component);
    var row = $('#row-' + component);

    badge.text(status);
    row.removeClass('table-success table-danger table-warning');

    if (status === 'UP') {
      badge.removeClass('bg-secondary bg-danger bg-warning').addClass('bg-success');
      row.addClass('table-success');
    } else if (status === 'DOWN') {
      badge.removeClass('bg-secondary bg-success bg-warning').addClass('bg-danger');
      row.addClass('table-danger');
    } else {
      badge.removeClass('bg-secondary bg-success bg-danger').addClass('bg-warning');
      row.addClass('table-warning');
    }
  }

  function formatCount(val) {
    if (val === null || val === undefined || val === -1) return 'N/A';
    return val.toLocaleString();
  }

  function updateTimestamp() {
    $('#last-updated').text(new Date().toLocaleTimeString());
  }

  // Refresh button
  $('#refresh-btn').on('click', function() {
    loadData();
  });

  // Auto-refresh every 30 seconds (guard against interval stacking on SPA re-navigation)
  if (window._dashboardTimer) clearInterval(window._dashboardTimer);
  window._dashboardTimer = setInterval(loadData, 30000);

  // Initial load
  loadData();
});
