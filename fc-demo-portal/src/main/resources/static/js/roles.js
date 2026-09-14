/*-
 * ---license-start
 * fc-demo-portal
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

$(document).ready(function() {

  $.ajax({
    type: 'GET',
    url: 'roles',
    dataType: 'json',
    success: function(response) {
      var data = response.map(function(name) { return { name: name }; });
      $('#rolesTable').DataTable({
        data: data,
        layout: {
          topStart: 'search',
          topEnd: 'pageLength',
          bottomStart: 'info',
          bottomEnd: 'paging'
        },
        initComplete: addSearchIcon,
        columns: [{ data: 'name', render: $.fn.dataTable.render.text() }]
      });
    },
    error: function(xhr) {
      var msg = 'You do not have permission to view roles.';
      var cls = 'alert-warning';
      if (xhr.status !== 403) {
        msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load roles.';
        cls = 'alert-danger';
      }
      $('<div>', { class: 'alert ' + cls + ' mt-2' }).text(msg).appendTo($('#error-area').empty());
      $('#rolesTable').hide();
    }
  });

});

function addSearchIcon() {
  var $filter = $(this.api().table().container()).find('.dataTables_filter label');
  var $input = $filter.find('input').detach();
  $filter.empty().append(
    $('<div class="input-group input-group-sm">').append(
      $('<span class="input-group-text"><i class="bi bi-search"></i></span>'),
      $input
    )
  );
}
