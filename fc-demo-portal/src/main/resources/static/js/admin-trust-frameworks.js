$(document).ready(function() {

  var ADMIN_ME_URL    = '/admin/me';
  var TF_API_BASE     = '/admin/trust-frameworks';
  var MERGE_PATCH_JSON = 'application/merge-patch+json';

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
        },
        {
          data: null,
          orderable: false,
          render: function(data) {
            return $('<button>', { class: 'btn btn-sm btn-outline-secondary tf-roles' })
              .attr('data-id',      data.id)
              .attr('data-enabled', data.enabled ? 'true' : 'false')
              .attr('data-bundles', JSON.stringify(data.bundles || []))
              .append('<i class="bi bi-gear"></i>')
              .prop('outerHTML');
          }
        }
      ]
    });

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

    $('#tfTable').on('click', '.tf-roles', function() {
      var $btn = $(this);
      var familyEnabled = $btn.data('enabled') === 'true' || $btn.data('enabled') === true;
      var bundles = $btn.data('bundles') || [];
      if (typeof bundles === 'string') {
        try { bundles = JSON.parse(bundles); } catch (e) { bundles = []; }
      }

      var $container = $('#tfRolesContainer').empty();
      $('#tfRoleErrorBanner').hide();
      var multiBundle = bundles.length > 1;
      bundles.forEach(function(bundle) {
        var roles = bundle.roles || {};
        Object.keys(roles).forEach(function(roleName) {
          var roleEnabled = roles[roleName];
          var inputId = 'role-' + bundle.id + '-' + roleName;
          var $check = $('<div class="form-check">').append(
            $('<input>', {
              type: 'checkbox',
              class: 'form-check-input tf-role-toggle',
              id: inputId,
              'data-bundle': bundle.id,
              'data-role': roleName
            })
              .prop('checked', roleEnabled)
              .prop('disabled', !familyEnabled),
            $('<label>', {
              class: 'form-check-label' + (!familyEnabled ? ' text-muted' : ''),
              for: inputId,
              title: 'When disabled, this role and all its OWL subclasses reject credentials with HTTP 400.',
              'data-bs-toggle': 'tooltip'
            }).append(
              document.createTextNode(roleName + ' '),
              multiBundle
                ? $('<small class="text-muted">').text('(' + bundle.id + ')')
                : $()
            )
          );
          $container.append($check);
        });
      });

      $container.find('[data-bs-toggle="tooltip"]').each(function() {
        new bootstrap.Tooltip(this);
      });

      $('#tfConfigModal').data('familyEnabled', familyEnabled);
      recomputeAllDisabledWarning(familyEnabled);
      new bootstrap.Modal('#tfConfigModal').show();
    });

    $('#tfConfigModal').on('change', '.tf-role-toggle', function() {
      var $cb = $(this);
      var bundleId = $cb.data('bundle');
      var roleName = $cb.data('role');
      var enabled = $cb.is(':checked');

      $.ajax({
        url: TF_API_BASE + '/' + encodeURIComponent(bundleId)
          + '/roles/' + encodeURIComponent(roleName),
        type: 'PATCH',
        contentType: MERGE_PATCH_JSON,
        data: JSON.stringify({enabled: enabled}),
        success: function() {
          $('#tfRoleErrorBanner').hide();
          recomputeAllDisabledWarning($('#tfConfigModal').data('familyEnabled'));
        },
        error: function() {
          $cb.prop('checked', !enabled);
          recomputeAllDisabledWarning($('#tfConfigModal').data('familyEnabled'));
          var $banner = $('#tfRoleErrorBanner');
          $banner.text('Failed to update role "' + roleName + '". Please try again.').show();
          clearTimeout($banner.data('hideTimer'));
          $banner.data('hideTimer', setTimeout(function() { $banner.hide(); }, 5000));
        }
      });
    });

    function recomputeAllDisabledWarning(familyEnabled) {
      if (!familyEnabled) {
        $('#tfRolesAllDisabledWarning').hide();
        return;
      }
      var allUnchecked = $('#tfRolesContainer .tf-role-toggle:checked').length === 0
        && $('#tfRolesContainer .tf-role-toggle').length > 0;
      $('#tfRolesAllDisabledWarning').toggle(allUnchecked);
    }
  }

});
