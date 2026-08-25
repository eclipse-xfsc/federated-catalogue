$(document).ready(function() {

  var currentBackend = '';
  var currentClaimCount = 0;
  var rebuildPollHandle = null;

  $.ajax({
    url: '/admin/me', type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadStatus();
    },
    error: function() {
      $('#admin-content').html('<div class="alert alert-danger">Access denied.</div>').show();
    }
  });

  function showError(msg) {
    $('#errorBanner').text(msg).removeClass('d-none');
  }

  function clearError() {
    $('#errorBanner').addClass('d-none').text('');
  }

  function loadStatus() {
    clearError();
    $.ajax({
      url: '/admin/graph-database',
      type: 'GET',
      success: function(data) {
        currentBackend = data.activeBackend || 'UNKNOWN';
        currentClaimCount = typeof data.claimCount === 'number' ? data.claimCount : 0;
        $('#activeBackend').text(currentBackend);

        if (data.connected) {
          $('#connectionStatus').removeClass('bg-secondary bg-danger').addClass('bg-success').text('Connected');
        } else {
          $('#connectionStatus').removeClass('bg-secondary bg-success').addClass('bg-danger').text('Disconnected');
        }

        $('#claimCount').text(currentClaimCount >= 0 ? currentClaimCount.toLocaleString() : 'N/A');
        $('#versionInfo').text(data.version || '-');

        $('.backend-radio').prop('checked', false);
        $('.backend-radio[value="' + currentBackend + '"]').prop('checked', true);
        $('#switchBtn').prop('disabled', true);

        if (data.rebuildNeeded) {
          $('#rebuildAssetSummary').text(buildRebuildSummary(data));
          $('#rebuildBanner').removeClass('d-none');
        } else {
          $('#rebuildBanner').addClass('d-none');
        }

        $('#rebuildBtn').prop('disabled', currentBackend === 'NONE');
      },
      error: function(xhr) {
        showError('Failed to load graph database status: ' + (xhr.responseJSON?.message || xhr.status));
        $('#activeBackend').text('Error');
        $('#connectionStatus').removeClass('bg-secondary bg-success').addClass('bg-danger').text('Error');
      }
    });
  }

  $('.backend-radio').on('change', function() {
    var selected = $('input[name="backend"]:checked').val();
    $('#switchBtn').prop('disabled', selected === currentBackend);
  });

  $('#switchBtn').on('click', function() {
    var selected = $('input[name="backend"]:checked').val();
    $('#modalCurrentBackend').text(currentBackend);
    $('#modalNewBackend').text(selected);
    new bootstrap.Modal('#confirmSwitchModal').show();
  });

  $('#confirmSwitchBtn').on('click', function() {
    var selected = $('input[name="backend"]:checked').val();
    clearError();
    $.ajax({
      url: '/admin/graph-database/switch',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ backend: selected }),
      success: function() {
        bootstrap.Modal.getInstance(document.getElementById('confirmSwitchModal')).hide();
        loadStatus();
      },
      error: function(xhr) {
        bootstrap.Modal.getInstance(document.getElementById('confirmSwitchModal')).hide();
        var msg = xhr.responseJSON?.message || xhr.responseJSON?.detail || 'Failed to switch backend.';
        showError(msg);
      }
    });
  });

  $('#rebuildBtn').on('click', function() {
    if (currentClaimCount > 0) {
      $('#modalClaimCount').text(currentClaimCount.toLocaleString());
      new bootstrap.Modal('#confirmRebuildModal').show();
    } else {
      startRebuild();
    }
  });

  $('#confirmRebuildBtn').on('click', function() {
    bootstrap.Modal.getInstance(document.getElementById('confirmRebuildModal')).hide();
    startRebuild();
  });

  function startRebuild() {
    clearError();
    $('#rebuildBtn').prop('disabled', true).text('Starting…');
    $.ajax({
      url: '/admin/graph/rebuild',
      type: 'POST',
      success: function(data) {
        $('#rebuildBtn').text('Running…');
        $('#rebuildProgress').text(formatRebuildStatus(data));
        startRebuildPoll();
      },
      error: function(xhr) {
        $('#rebuildBtn').prop('disabled', false).text('Start rebuild');
        showError('Failed to start rebuild: ' + (xhr.responseJSON?.message || xhr.status));
      }
    });
  }

  function startRebuildPoll() {
    if (rebuildPollHandle) {
      return;
    }
    rebuildPollHandle = setInterval(function() {
      $.ajax({
        url: '/admin/graph/rebuild/status',
        type: 'GET',
        success: function(data) {
          $('#rebuildProgress').text(formatRebuildStatus(data));
          if (!data.running) {
            clearInterval(rebuildPollHandle);
            rebuildPollHandle = null;
            $('#rebuildBtn').prop('disabled', false).text('Start rebuild');
            loadStatus();
          }
        },
        error: function() {
          // keep polling — transient errors are common during rebuild
        }
      });
    }, 2000);
  }

  function formatRebuildStatus(s) {
    if (!s) return '';
    var parts = [];
    if (typeof s.processed === 'number' && typeof s.total === 'number') {
      parts.push(s.processed + ' / ' + s.total + ' assets');
    }
    if (typeof s.percentComplete === 'number') {
      parts.push(s.percentComplete + '%');
    }
    if (s.failed) parts.push('failed: ' + (s.errorMessage || 'unknown error'));
    else if (s.running) parts.push('running');
    else if (s.complete) parts.push('done');
    return parts.join(' · ');
  }

  // The banner must quote the same number the rebuild progress reports as its total, so
  // rebuildableAssetCount is the source. rdfAssetCount is narrower — it counts only assets
  // uploaded as credentials — so the difference is enriched non-RDF assets, shown as a
  // breakdown to explain where the total comes from.
  function buildRebuildSummary(data) {
    var rebuildable = typeof data.rebuildableAssetCount === 'number'
      ? data.rebuildableAssetCount
      : (typeof data.rdfAssetCount === 'number' ? data.rdfAssetCount : 0);
    var credentials = typeof data.rdfAssetCount === 'number' ? data.rdfAssetCount : 0;
    var enriched = rebuildable - credentials;

    var summary = rebuildable.toLocaleString()
      + (rebuildable === 1 ? ' asset is ' : ' assets are ')
      + 'ready to be re-indexed.';
    if (enriched > 0 && credentials > 0) {
      summary += ' ' + credentials.toLocaleString()
        + (credentials === 1 ? ' credential and ' : ' credentials and ')
        + enriched.toLocaleString()
        + (enriched === 1 ? ' enriched asset.' : ' enriched assets.');
    }
    return summary;
  }

});
