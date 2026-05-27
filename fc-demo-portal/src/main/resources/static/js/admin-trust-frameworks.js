$(document).ready(function() {

  var ADMIN_ME_URL    = '/admin/me';
  var TF_API_BASE     = '/admin/trust-frameworks';
  var MERGE_PATCH_JSON = 'application/merge-patch+json';

  // Bundle override property keys — must match server-recognised merge-patch+json fields
  var KEY_CLIENT_TYPE      = 'clientType';
  var KEY_SERVICE_URL      = 'serviceUrl';
  var KEY_COMPLIANCE_PATH  = 'compliancePath';
  var KEY_API_VERSION      = 'apiVersion';
  var KEY_TIMEOUT_SECONDS  = 'timeoutSeconds';
  var KEY_TRUST_ANCHOR_URL = 'trustAnchorUrl';

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
        },
        {
          data: null,
          orderable: false,
          render: function(data) {
            var bundles = data.bundles || [];
            if (bundles.length === 0) {
              return '';
            }
            return bundles.map(function(bundle) {
              return $('<button>', { class: 'btn btn-sm btn-outline-primary tf-bundle-configure me-1' })
                .attr('data-bundle-id', bundle.id)
                .append('<i class="bi bi-pencil"></i> ')
                .append($('<small>').text(bundle.id))
                .prop('outerHTML');
            }).join('');
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

    // ── Bundle client-config modal ──────────────────────────────────────────

    // Set of field IDs that have been explicitly marked "clear" (→ null in PATCH body).
    var tfBundleClearedFields = {};

    $('#tfTable').on('click', '.tf-bundle-configure', function() {
      var bundleId = $(this).data('bundle-id');
      $('#tfBundleConfigModalBundleId').text(bundleId);
      $('#tfBundleConfigModal').data('bundleId', bundleId);

      // Reset all fields and cleared-field state
      tfBundleClearedFields = {};
      $('.tf-bundle-field').val('').removeClass('tf-field-cleared');
      $('.tf-bundle-clear').removeClass('btn-danger').addClass('btn-outline-secondary');
      $('#tfBundleConfigErrorBanner').hide();

      new bootstrap.Modal('#tfBundleConfigModal').show();
    });

    // ✕ button: mark field as "explicitly clear" (will send null)
    $('#tfBundleConfigModal').on('click', '.tf-bundle-clear', function() {
      var targetId = $(this).data('target');
      var $field = $('#' + targetId);
      var key = $field.data('key');

      tfBundleClearedFields[key] = true;
      $field.val('').addClass('tf-field-cleared').prop('disabled', true);
      $(this).removeClass('btn-outline-secondary').addClass('btn-danger');
    });

    // Typing into a field removes the "cleared" state
    $('#tfBundleConfigModal').on('input', '.tf-bundle-field', function() {
      var $field = $(this);
      var key = $field.data('key');
      if (tfBundleClearedFields[key]) {
        delete tfBundleClearedFields[key];
        $field.removeClass('tf-field-cleared').prop('disabled', false);
        var $clearBtn = $('[data-target="' + $field.attr('id') + '"]');
        $clearBtn.removeClass('btn-danger').addClass('btn-outline-secondary');
      }
    });

    // Re-enable cleared field when modal is closed so it resets cleanly next open
    $('#tfBundleConfigModal').on('hidden.bs.modal', function() {
      tfBundleClearedFields = {};
      $('.tf-bundle-field').prop('disabled', false).val('').removeClass('tf-field-cleared');
      $('.tf-bundle-clear').removeClass('btn-danger').addClass('btn-outline-secondary');
    });

    $('#tfBundleConfigSave').on('click', function() {
      var bundleId = $('#tfBundleConfigModal').data('bundleId');
      if (!bundleId) { return; }

      var payload = {};

      // Fields explicitly cleared → null
      var clearKeys = [
        KEY_CLIENT_TYPE, KEY_SERVICE_URL, KEY_COMPLIANCE_PATH,
        KEY_API_VERSION, KEY_TIMEOUT_SECONDS, KEY_TRUST_ANCHOR_URL
      ];
      clearKeys.forEach(function(k) {
        if (tfBundleClearedFields[k]) {
          payload[k] = null;
        }
      });

      // Fields with a value → include value (with type coercion for integer)
      $('.tf-bundle-field').each(function() {
        var $f = $(this);
        var key = $f.data('key');
        if (tfBundleClearedFields[key]) { return; } // already handled as null
        var raw = $.trim($f.val());
        if (raw === '') { return; } // blank → omit (no-op)
        payload[key] = (key === KEY_TIMEOUT_SECONDS) ? parseInt(raw, 10) : raw;
      });

      if (Object.keys(payload).length === 0) {
        $('#tfBundleConfigErrorBanner').text('Nothing to save — fill in at least one field or mark one for clearing.').show();
        return;
      }

      $('#tfBundleConfigErrorBanner').hide();
      $('#tfBundleConfigSave').prop('disabled', true);

      fetch(TF_API_BASE + '/bundles/' + encodeURIComponent(bundleId), {
        method: 'PATCH',
        headers: { 'Content-Type': MERGE_PATCH_JSON },
        body: JSON.stringify(payload)
      })
        .then(function(resp) {
          if (resp.ok) {
            bootstrap.Modal.getInstance('#tfBundleConfigModal').hide();
          } else {
            return resp.json().then(function(err) {
              var msg = (err && err.message) ? err.message : ('Server error ' + resp.status);
              $('#tfBundleConfigErrorBanner').text(msg).show();
            });
          }
        })
        .catch(function() {
          $('#tfBundleConfigErrorBanner').text('Network error — please try again.').show();
        })
        .finally(function() {
          $('#tfBundleConfigSave').prop('disabled', false);
        });
    });

    $('#tfBundleConfigRevertAll').on('click', function() {
      var bundleId = $('#tfBundleConfigModal').data('bundleId');
      if (!bundleId) { return; }

      $('#tfBundleConfigErrorBanner').hide();
      $('#tfBundleConfigRevertAll').prop('disabled', true);

      fetch(TF_API_BASE + '/bundles/' + encodeURIComponent(bundleId), {
        method: 'DELETE'
      })
        .then(function(resp) {
          if (resp.ok) {
            bootstrap.Modal.getInstance('#tfBundleConfigModal').hide();
          } else {
            return resp.json().then(function(err) {
              var msg = (err && err.message) ? err.message : ('Server error ' + resp.status);
              $('#tfBundleConfigErrorBanner').text(msg).show();
            });
          }
        })
        .catch(function() {
          $('#tfBundleConfigErrorBanner').text('Network error — please try again.').show();
        })
        .finally(function() {
          $('#tfBundleConfigRevertAll').prop('disabled', false);
        });
    });
  }

});
