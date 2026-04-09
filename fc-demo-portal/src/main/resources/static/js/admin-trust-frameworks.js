$(document).ready(function() {

  // Admin access check
  $.ajax({
    url: '/admin/me',
    type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadTrustFrameworks();
    },
    error: function() {
      $('#admin-content').html('<div class="alert alert-danger">Access denied.</div>');
    }
  });

  function loadTrustFrameworks() {
    var table = $('#tfTable').DataTable({
      ajax: {
        url: '/admin/trust-frameworks',
        dataSrc: function(json) {
          return json || [];
        },
        error: function(xhr) {
          $('#admin-content').html(
            '<div class="alert alert-danger">Failed to load trust frameworks.</div>');
        }
      },
      layout: {
        topStart: 'search',
        topEnd: 'pageLength',
        bottomStart: 'info',
        bottomEnd: 'paging'
      },
      initComplete: function() {
        var $filter = $(this.api().table().container()).find('.dataTables_filter label');
        var $input = $filter.find('input').detach();
        $filter.empty().append(
          $('<div class="input-group input-group-sm">').append(
            $('<span class="input-group-text"><i class="bi bi-search"></i></span>'),
            $input
          )
        );
      },
      columns: [
        { data: 'name', render: $.fn.dataTable.render.text() },
        {
          data: 'id',
          render: function(data) {
            return '<code>' + $('<span>').text(data).html() + '</code>';
          }
        },
        { data: 'serviceUrl', render: $.fn.dataTable.render.text() },
        {
          data: 'connected',
          render: function(data) {
            if (data) {
              return '<span class="badge bg-success">Connected</span>';
            }
            return '<span class="badge bg-danger">Disconnected</span>';
          }
        },
        {
          data: 'enabled',
          render: function(data, type, row) {
            var checked = data ? 'checked' : '';
            return '<div class="form-check form-switch">'
              + '<input class="form-check-input tf-toggle" type="checkbox" '
              + 'data-id="' + row.id + '" ' + checked + '>'
              + '</div>';
          }
        },
        {
          data: null,
          orderable: false,
          render: function(data) {
            return $('<button>', { class: 'btn btn-sm btn-outline-secondary tf-config' })
              .attr('data-id',      data.id)
              .attr('data-url',     data.serviceUrl || '')
              .attr('data-version', data.apiVersion || '')
              .attr('data-timeout', data.timeoutSeconds || 30)
              .append('<i class="bi bi-gear"></i>')
              .prop('outerHTML');
          }
        }
      ]
    });

    // Toggle handler
    $('#tfTable').on('change', '.tf-toggle', function() {
      var $toggle = $(this);
      var id = $toggle.data('id');
      var enabled = $toggle.is(':checked');

      $.ajax({
        url: '/admin/trust-frameworks/' + encodeURIComponent(id) + '/enabled?enabled=' + enabled,
        type: 'PUT',
        error: function() {
          $toggle.prop('checked', !enabled);
          alert('Failed to update trust framework status.');
        }
      });
    });

    // Config button handler
    $('#tfTable').on('click', '.tf-config', function() {
      var $btn = $(this);
      $('#tfConfigId').val($btn.data('id'));
      $('#tfServiceUrl').val($btn.data('url'));
      $('#tfApiVersion').val($btn.data('version'));
      $('#tfTimeout').val($btn.data('timeout'));
      new bootstrap.Modal('#tfConfigModal').show();
    });

    // Config save handler
    $('#tfConfigSave').on('click', function() {
      var id = $('#tfConfigId').val();
      var serviceUrl = $('#tfServiceUrl').val().trim();

      if (serviceUrl) {
        try {
          var parsed = new URL(serviceUrl);
          if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
            alert('Service URL must use http or https protocol.');
            return;
          }
        } catch (e) {
          alert('Service URL is not a valid URI.');
          return;
        }
      }

      var config = {
        serviceUrl: serviceUrl,
        apiVersion: $('#tfApiVersion').val(),
        timeoutSeconds: parseInt($('#tfTimeout').val()) || 30
      };

      $.ajax({
        url: '/admin/trust-frameworks/' + encodeURIComponent(id),
        type: 'PUT',
        contentType: 'application/json',
        data: JSON.stringify(config),
        success: function() {
          bootstrap.Modal.getInstance(document.getElementById('tfConfigModal')).hide();
          table.ajax.reload();
        },
        error: function() {
          alert('Failed to save trust framework configuration.');
        }
      });
    });
  }

});
