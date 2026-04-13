$(document).ready(function() {

  var partDataTable = $('#participantTable').DataTable({
    ajax: {
      url: 'parts',
      error: function(xhr) {
        var msg = 'You do not have permission to view participants.';
        var cls = 'alert-warning';
        if (xhr.status !== 403) {
          msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load participants.';
          cls = 'alert-danger';
        }
        $('<div>', { class: 'alert ' + cls + ' mt-2' }).text(msg).appendTo($('#error-area').empty());
        $('#participantTable').hide();
      },
      dataSrc: function(json) {
        return json.items || [];
      }
    },
    layout: {
      topStart: 'search',
      topEnd: 'pageLength',
      bottomStart: 'info',
      bottomEnd: 'paging'
    },
    order: [[1, 'asc']],
    initComplete: addSearchIcon,
    columns: [
      {
        className: 'details-control',
        orderable: false,
        data: null,
        defaultContent: ''
      },
      { data: 'id', render: $.fn.dataTable.render.text() },
      { data: 'name', render: $.fn.dataTable.render.text() },
      { data: 'publicKey', render: $.fn.dataTable.render.text() },
      { data: 'asset', visible: false },
      {
        orderable: false,
        render: function() {
          return '<button type="button" class="btn btn-sm btn-success" id="editButton">Edit</button>';
        }
      },
      {
        orderable: false,
        render: function() {
          return '<button type="button" class="btn btn-sm btn-danger" id="deleteButton">Delete</button>';
        }
      }
    ]
  });

  var detailRows = [];
  $('#participantTable tbody').on('click', 'tr td.details-control', function() {
    var tr = $(this).closest('tr');
    var row = partDataTable.row(tr);
    var idx = detailRows.indexOf(tr.attr('id'));
    if (row.child.isShown()) {
      tr.removeClass('details');
      row.child.hide();
      detailRows.splice(idx, 1);
    } else {
      tr.addClass('details');
      row.child(format(row.data())).show();
      if (idx === -1) detailRows.push(tr.attr('id'));
    }
  });

  partDataTable.on('draw', function() {
    detailRows.forEach(function(id) {
      $('#' + id + ' td.details-control').trigger('click');
    });
  });

  $('#participantTable').on('click', '#editButton', function(e) {
    e.preventDefault();
    var data = partDataTable.row($(this).parents('tr')).data();
    $('#editData').val(JSON.stringify(JSON.parse(data['asset']), undefined, 4));
    bootstrap.Modal.getOrCreateInstance(document.getElementById('editModal')).show();
  });

  $('#participantTable').on('click', '#deleteButton', function(e) {
    e.preventDefault();
    if (!confirm('Are you sure you want to delete?')) return;
    var data = partDataTable.row($(this).parents('tr')).data();
    $.ajax('/parts/' + data.id, {
      type: 'DELETE',
      contentType: 'application/json;charset=utf-8',
      success: function() { partDataTable.ajax.reload(); },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#submitEditButton').on('click', function() {
    var data = JSON.parse($('#editData').val());
    $.ajax('/parts/' + data['id'], {
      type: 'PUT',
      contentType: 'application/json;charset=utf-8',
      data: JSON.stringify(data),
      success: function() {
        bootstrap.Modal.getInstance(document.getElementById('editModal')).hide();
        partDataTable.ajax.reload();
      },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#submitAddButton').on('click', function() {
    var data = JSON.parse($('#addData').val());
    $.ajax('/parts', {
      type: 'POST',
      contentType: 'application/json;charset=utf-8',
      data: JSON.stringify(data),
      success: function() {
        bootstrap.Modal.getInstance(document.getElementById('addModal')).hide();
        partDataTable.ajax.reload();
      },
      error: function(jqXhr) { alert(JSON.stringify(jqXhr.responseJSON)); }
    });
  });

  $('#addButton').on('click', function(e) {
    e.preventDefault();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('addModal')).show();
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

function format(d) {
  return 'Self-Description: ' + d['asset'];
}
