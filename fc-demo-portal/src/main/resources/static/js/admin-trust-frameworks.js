$(document).ready(function() {

  var ADMIN_ME_URL = '/admin/me';
  var TF_API_BASE  = '/admin/trust-frameworks';

  $.ajax({
    url: ADMIN_ME_URL,
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
    $('#tfTable').DataTable({
      ajax: {
        url: TF_API_BASE,
        dataSrc: function(json) {
          return json || [];
        },
        error: function() {
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
        {
          data: 'bundles',
          orderable: false,
          render: function(data) {
            if (!data || data.length === 0) {
              return '';
            }
            return data.map(function(bundle) {
              return '<code>' + $('<span>').text(bundle.id).html() + '</code>';
            }).join(', ');
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
        }
      ]
    });

    var MERGE_PATCH_JSON = 'application/merge-patch+json';

    $('#tfTable').on('change', '.tf-toggle', function() {
      var $toggle = $(this);
      var id = $toggle.data('id');
      var enabled = $toggle.is(':checked');

      $.ajax({
        url: TF_API_BASE + '/' + encodeURIComponent(id),
        type: 'PATCH',
        contentType: MERGE_PATCH_JSON,
        data: JSON.stringify({enabled: enabled}),
        error: function() {
          $toggle.prop('checked', !enabled);
          alert('Failed to update trust framework status.');
        }
      });
    });
  }

});
