$(document).ready(function() {

  // Admin access check
  $.ajax({
    url: '/admin/me',
    type: 'GET',
    success: function() {
      $('#admin-content').show();
      loadSchemaValidation();
    },
    error: function() {
      $('#admin-content').html('<div class="alert alert-danger">Access denied.</div>');
    }
  });

  function loadSchemaValidation() {
    $.getJSON('/admin/schema-validation', function(data) {
      $('#totalSchemaCount').text(data.totalSchemaCount || 0);

      $('#svTable').DataTable({
        data: data.modules || [],
        paging: false,
        searching: false,
        info: false,
        columns: [
          { data: 'name' },
          { data: 'description' },
          {
            data: 'schemaCount',
            render: function(data) {
              return '<span class="badge bg-secondary">' + data + '</span>';
            }
          },
          {
            data: 'enabled',
            render: function(data, type, row) {
              var checked = data ? 'checked' : '';
              return '<div class="form-check form-switch">'
                + '<input class="form-check-input sv-toggle" type="checkbox" '
                + 'data-type="' + row.type + '" ' + checked + '>'
                + '</div>';
            }
          }
        ]
      });
    }).fail(function() {
      $('#admin-content').html(
        '<div class="alert alert-danger">Failed to load schema validation status.</div>');
    });

    // Toggle handler
    $('#svTable').on('change', '.sv-toggle', function() {
      var $toggle = $(this);
      var type = $toggle.data('type');
      var enabled = $toggle.is(':checked');

      $.ajax({
        url: '/admin/schema-validation/modules/' + encodeURIComponent(type)
          + '?enabled=' + enabled,
        type: 'PUT',
        error: function() {
          $toggle.prop('checked', !enabled);
          alert('Failed to update module status.');
        }
      });
    });
  }

});
