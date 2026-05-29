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
            var bundles = data.bundles || [];
            if (bundles.length === 0) {
              return '';
            }
            var familyEnabled = data.enabled ? 'true' : 'false';
            return bundles.map(function(bundle) {
              return $('<button>', { class: 'btn btn-sm btn-outline-secondary tf-bundle-configure me-1' })
                .attr('data-bundle-id',         bundle.id)
                .attr('data-family-enabled',    familyEnabled)
                .attr('data-base-classes',      JSON.stringify(bundle.baseClasses || {}))
                .attr('data-effective-config', JSON.stringify(bundle.effectiveConfig || {}))
                .attr('data-overridden',        JSON.stringify(bundle.overriddenFields || []))
                .append('<i class="bi bi-gear"></i> ')
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

    function recomputeAllDisabledWarning(familyEnabled) {
      if (!familyEnabled) {
        $('#tfBaseClassesAllDisabledWarning').hide();
        return;
      }
      var allUnchecked = $('#tfBaseClassesContainer .tf-base-class-toggle:checked').length === 0
        && $('#tfBaseClassesContainer .tf-base-class-toggle').length > 0;
      $('#tfBaseClassesAllDisabledWarning').toggle(allUnchecked);
    }

    function renderBundleBaseClasses(bundleId, familyEnabled, baseClasses) {
      var $container = $('#tfBaseClassesContainer').empty();
      $('#tfBaseClassErrorBanner').hide();
      Object.keys(baseClasses).forEach(function(baseClassName) {
        var baseClassEnabled = baseClasses[baseClassName];
        var inputId = 'base-class-' + bundleId + '-' + baseClassName;
        var $check = $('<div class="form-check">').append(
          $('<input>', {
            type: 'checkbox',
            class: 'form-check-input tf-base-class-toggle',
            id: inputId,
            'data-bundle': bundleId,
            'data-base-class': baseClassName
          })
            .prop('checked', baseClassEnabled)
            .prop('disabled', !familyEnabled),
          $('<label>', {
            class: 'form-check-label' + (!familyEnabled ? ' text-muted' : ''),
            for: inputId,
            title: 'When disabled, this base class and all its OWL subclasses reject credentials with HTTP 400.',
            'data-bs-toggle': 'tooltip'
          }).append(document.createTextNode(baseClassName)),
          $('<span class="badge bg-success ms-2 tf-base-class-saved-badge" style="display:none">')
            .text('✓ saved')
        );
        $container.append($check);
      });
      $container.find('[data-bs-toggle="tooltip"]').each(function() {
        new bootstrap.Tooltip(this);
      });
    }

    $('#tfBundleConfigModal').on('change', '.tf-base-class-toggle', function() {
      var $cb = $(this);
      var bundleId = $cb.data('bundle');
      var baseClassName = $cb.data('base-class');
      var enabled = $cb.is(':checked');

      $.ajax({
        url: TF_API_BASE + '/' + encodeURIComponent(bundleId)
          + '/base-classes/' + encodeURIComponent(baseClassName),
        type: 'PATCH',
        contentType: MERGE_PATCH_JSON,
        data: JSON.stringify({enabled: enabled}),
        success: function() {
          $('#tfBaseClassErrorBanner').hide();
          recomputeAllDisabledWarning($('#tfBundleConfigModal').data('familyEnabled'));
          // Flash a "✓ saved" badge next to this row so the operator sees the change
          // committed (mirrors the deferred-Save affordance of the config fields above).
          var $badge = $cb.closest('.form-check').find('.tf-base-class-saved-badge');
          clearTimeout($badge.data('hideTimer'));
          $badge.stop(true, true).show().css('opacity', 1);
          $badge.data('hideTimer', setTimeout(function() {
            $badge.fadeOut(400);
          }, 1500));
        },
        error: function() {
          $cb.prop('checked', !enabled);
          recomputeAllDisabledWarning($('#tfBundleConfigModal').data('familyEnabled'));
          var $banner = $('#tfBaseClassErrorBanner');
          $banner.text('Failed to update base class "' + baseClassName + '". Please try again.').show();
          clearTimeout($banner.data('hideTimer'));
          $banner.data('hideTimer', setTimeout(function() { $banner.hide(); }, 5000));
        }
      });
    });

    // ── Bundle client-config modal ──────────────────────────────────────────

    // Set of field IDs that have been explicitly marked "clear" (→ null in PATCH body).
    var tfBundleClearedFields = {};

    function parseJsonAttr(value, fallback) {
      if (typeof value !== 'string') {
        return value == null ? fallback : value;
      }
      try { return JSON.parse(value); } catch (e) { return fallback; }
    }

    function renderBundleEffectiveConfig(effective, overridden) {
      // Show effective values; mark fields whose value comes from a persisted override.
      $('.tf-bundle-field').each(function() {
        var $field = $(this);
        var key = $field.data('key');
        var value = effective[key];
        if (value === undefined || value === null) {
          $field.val('').attr('placeholder', '');
        } else {
          $field.val(value).attr('placeholder', '');
        }
      });
      $('.tf-bundle-field').removeClass('border-primary');
      $('.tf-bundle-clear').removeClass('btn-warning');
      (overridden || []).forEach(function(key) {
        var $field = $('.tf-bundle-field[data-key="' + key + '"]');
        $field.addClass('border-primary');
        var $btn = $('.tf-bundle-clear[data-target="' + $field.attr('id') + '"]');
        $btn.addClass('btn-warning').removeClass('btn-outline-secondary');
      });
    }

    // Snapshot of the effective values shown when the modal was opened, used to
    // detect which fields the operator actually changed so a no-op Save does not
    // accidentally promote YAML defaults to persisted overrides.
    var tfBundleInitialValues = {};

    $('#tfTable').on('click', '.tf-bundle-configure', function() {
      var $btn = $(this);
      var bundleId = $btn.data('bundle-id');
      var familyEnabled = $btn.data('family-enabled') === 'true' || $btn.data('family-enabled') === true;
      var baseClasses = parseJsonAttr($btn.data('base-classes'), {});
      var effective = parseJsonAttr($btn.data('effective-config'), {});
      var overridden = parseJsonAttr($btn.data('overridden'), []);

      $('#tfBundleConfigModalBundleId').text(bundleId);
      $('#tfBundleConfigModal').data('bundleId', bundleId);
      $('#tfBundleConfigModal').data('familyEnabled', familyEnabled);

      // Reset cleared-field state, then populate from the row's effective config.
      tfBundleClearedFields = {};
      tfBundleInitialValues = {};
      $('.tf-bundle-field').removeClass('tf-field-cleared').prop('disabled', false);
      $('.tf-bundle-clear').removeClass('btn-danger').addClass('btn-outline-secondary');
      $('#tfBundleConfigErrorBanner').hide();
      renderBundleEffectiveConfig(effective, overridden);
      // Snapshot the values shown so Save can detect what changed.
      // Stored as strings so the Save comparison is type-safe across all input types.
      $('.tf-bundle-field').each(function() {
        var $f = $(this);
        var v = $f.val();
        tfBundleInitialValues[$f.data('key')] = $.trim(String(v == null ? '' : v));
      });

      // Populate the base-classes section
      renderBundleBaseClasses(bundleId, familyEnabled, baseClasses);
      recomputeAllDisabledWarning(familyEnabled);

      new bootstrap.Modal('#tfBundleConfigModal').show();
    });

    // ✕ button: toggle "explicitly clear" (Save will send null for this field).
    // Click again to un-mark — restores the field for editing without re-opening the modal.
    $('#tfBundleConfigModal').on('click', '.tf-bundle-clear', function() {
      var $btn = $(this);
      var targetId = $btn.data('target');
      var $field = $('#' + targetId);
      var key = $field.data('key');

      if (tfBundleClearedFields[key]) {
        // Un-clear: restore initial value and editability.
        delete tfBundleClearedFields[key];
        $field
          .removeClass('tf-field-cleared')
          .prop('disabled', false)
          .val(tfBundleInitialValues[key] || '');
        $btn.removeClass('btn-danger').addClass('btn-outline-secondary');
      } else {
        tfBundleClearedFields[key] = true;
        $field.val('').addClass('tf-field-cleared').prop('disabled', true);
        $btn.removeClass('btn-outline-secondary btn-warning').addClass('btn-danger');
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

      // Fields with a value → include only when the operator actually changed them
      // (otherwise we would re-promote YAML defaults to overrides on every Save).
      $('.tf-bundle-field').each(function() {
        var $f = $(this);
        var key = $f.data('key');
        if (tfBundleClearedFields[key]) { return; } // already handled as null
        var raw = $.trim(String($f.val() == null ? '' : $f.val()));
        if (raw === '') { return; } // blank → omit (no-op)
        var initial = String(tfBundleInitialValues[key] == null ? '' : tfBundleInitialValues[key]);
        if (raw === initial) { return; } // unchanged → omit
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
            $('#tfTable').DataTable().ajax.reload(null, false);
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
            $('#tfTable').DataTable().ajax.reload(null, false);
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
