$(document).ready(function() {

  var currentBackend = '';

  $.ajax({
    url: '/admin/me', type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadStatus();
    },
    error: function() {
      $('#admin-content').html('<div class="alert alert-danger">Access denied.</div>');
    }
  });

  function loadStatus() {
    $.getJSON('/admin/graph-database', function(data) {
      currentBackend = data.activeBackend || 'UNKNOWN';
      $('#activeBackend').text(currentBackend);

      if (data.connected) {
        $('#connectionStatus').removeClass('bg-secondary bg-danger').addClass('bg-success').text('Connected');
      } else {
        $('#connectionStatus').removeClass('bg-secondary bg-success').addClass('bg-danger').text('Disconnected');
      }

      $('#claimCount').text(data.claimCount >= 0 ? data.claimCount.toLocaleString() : 'N/A');
      $('#versionInfo').text(data.version || '-');

      // Set current backend radio
      $('.backend-radio').prop('checked', false);
      $('.backend-radio[value="' + currentBackend + '"]').prop('checked', true);
      $('#switchBtn').prop('disabled', true);
    }).fail(function() {
      $('#activeBackend').text('Error');
      $('#connectionStatus').removeClass('bg-secondary bg-success').addClass('bg-danger').text('Error');
    });
  }

  // Radio change — enable switch button if different from current
  $('.backend-radio').on('change', function() {
    var selected = $('input[name="backend"]:checked').val();
    $('#switchBtn').prop('disabled', selected === currentBackend);
  });

  // Switch button — show modal
  $('#switchBtn').on('click', function() {
    var selected = $('input[name="backend"]:checked').val();
    $('#modalCurrentBackend').text(currentBackend);
    $('#modalNewBackend').text(selected);
    new bootstrap.Modal('#confirmSwitchModal').show();
  });

  // Confirm switch
  $('#confirmSwitchBtn').on('click', function() {
    var selected = $('input[name="backend"]:checked').val();
    $.ajax({
      url: '/admin/graph-database/switch',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ backend: selected }),
      success: function(data) {
        bootstrap.Modal.getInstance(document.getElementById('confirmSwitchModal')).hide();
        alert(data.message || 'Backend switch requested. Restart the server.');
        loadStatus();
      },
      error: function(xhr) {
        var msg = xhr.responseJSON ? xhr.responseJSON.message : 'Failed to switch backend.';
        alert(msg);
      }
    });
  });

});
